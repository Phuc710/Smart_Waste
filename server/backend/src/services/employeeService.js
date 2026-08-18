'use strict';

const crypto = require('crypto');
const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const { callRpc, authAdminRequest } = require('../core/supabase');

async function createEmployeeAuthUser({ email, fullName, username }) {
    const result = await authAdminRequest('users', {
        method: 'POST',
        body: JSON.stringify({
            email,
            password: crypto.randomBytes(32).toString('base64url'),
            email_confirm: true,
            user_metadata: { full_name: fullName, username }
        })
    });
    const user = result.user || result;
    if (!user.id) throw new Error('Supabase không trả về UUID của Auth User.');
    return user;
}

async function deleteEmployeeAuthUser(userId) {
    if (!userId) return;
    await authAdminRequest(`users/${encodeURIComponent(userId)}`, { method: 'DELETE' });
}

async function login(username, password, rawToken, hashTokenFn) {
    const tokenHash = hashTokenFn(rawToken);
    try {
        const rows = await callRpc('employee_login', {
            p_username: username,
            p_password: password,
            p_token_hash: tokenHash
        });
        if (Array.isArray(rows) && rows.length) {
            return rows[0];
        }
    } catch (rpcErr) {
        logger.warn('Auth RPC fallback', `RPC employee_login failed (${rpcErr.message}), falling back to direct table auth.`);
        const bcrypt = require('bcryptjs');
        const { supabaseServiceRequest } = require('../core/supabase');
        const employees = await supabaseServiceRequest(
            `employee_accounts?username=eq.${encodeURIComponent(username)}&is_active=eq.true&deleted_at=is.null`
        );
        if (Array.isArray(employees) && employees.length) {
            const emp = employees[0];
            const isMatch = await bcrypt.compare(password, emp.password_hash);
            if (isMatch) {
                const expiresAt = new Date(Date.now() + 8 * 3600 * 1000).toISOString();
                await supabaseServiceRequest('employee_sessions', {
                    method: 'POST',
                    headers: { Prefer: 'resolution=merge-duplicates' },
                    body: JSON.stringify({
                        token_hash: tokenHash,
                        employee_id: emp.id,
                        expires_at: expiresAt
                    })
                });
                await supabaseServiceRequest(`employee_accounts?id=eq.${encodeURIComponent(emp.id)}`, {
                    method: 'PATCH',
                    body: JSON.stringify({ last_login: new Date().toISOString() })
                });
                return {
                    id: emp.id,
                    username: emp.username,
                    full_name: emp.full_name,
                    role: emp.role
                };
            }
        }
    }
    const err = new Error('Sai tên đăng nhập, mật khẩu hoặc tài khoản đã bị khóa.');
    err.statusCode = 401;
    throw err;
}

async function logout(tokenHash) {
    await callRpc('employee_logout', { p_token_hash: tokenHash }).catch(() => {});
}

const { isEmployeeOnline } = require('../jobs/binLivenessWorker');

async function listEmployees(tokenHash) {
    const rows = await callRpc('employee_list', { p_token_hash: tokenHash });
    return (rows || []).map(emp => {
        const loc = stateStore.employeeLocationsCache.get(emp.id);
        const online = Boolean(loc && isEmployeeOnline(loc));
        return { ...emp, is_online: online, location: loc || null };
    });
}

async function createEmployee({ tokenHash, fullName, username, email, password, role }) {
    let authUser = null;
    let employeeCreated = false;
    try {
        authUser = await createEmployeeAuthUser({ email, fullName, username });
        const rows = await callRpc('employee_create', {
            p_token_hash: tokenHash,
            p_full_name: fullName,
            p_username: username,
            p_email: email,
            p_password: password,
            p_role: role,
            p_auth_user_id: authUser.id
        });
        if (!Array.isArray(rows) || !rows.length) throw new Error('Không tạo được tài khoản nhân viên.');
        employeeCreated = true;
        return rows[0];
    } catch (error) {
        if (authUser?.id && !employeeCreated) {
            await deleteEmployeeAuthUser(authUser.id).catch((cleanupError) => {
                logger.error('Auth cleanup', cleanupError.message);
            });
        }
        throw error;
    }
}

async function setEmployeeActive(tokenHash, employeeId, isActive) {
    await callRpc('employee_set_active', {
        p_token_hash: tokenHash,
        p_employee_id: employeeId,
        p_is_active: isActive
    });
}

async function deleteEmployee(tokenHash, employeeId, currentUserId) {
    if (employeeId.toLowerCase() === String(currentUserId).toLowerCase()) {
        const err = new Error('Không thể xóa tài khoản đang đăng nhập.');
        err.statusCode = 400;
        throw err;
    }

    const result = await callRpc('employee_delete', {
        p_token_hash: tokenHash,
        p_employee_id: employeeId
    });
    const authUserId = result?.auth_user_id;
    let authUserDeleted = true;
    if (authUserId) {
        try {
            await deleteEmployeeAuthUser(authUserId);
        } catch (error) {
            authUserDeleted = false;
            logger.error('Delete employee Auth cleanup', error.message);
        }
    }
    return { ok: true, authUserDeleted };
}

async function updateLocation(tokenHash, user, { latitude, longitude, accuracy, heading, speed }) {
    await callRpc('employee_location_update', {
        p_token_hash: tokenHash,
        p_latitude: latitude,
        p_longitude: longitude,
        p_accuracy: Number.isFinite(Number(accuracy)) ? Number(accuracy) : null,
        p_heading: Number.isFinite(Number(heading)) ? Number(heading) : null,
        p_speed: Number.isFinite(Number(speed)) ? Number(speed) : null
    });

    const locObj = {
        employee_id: user.id,
        username: user.username,
        full_name: user.full_name,
        role: user.role,
        latitude,
        longitude,
        accuracy: Number(accuracy) || null,
        heading: Number(heading) || null,
        speed: Number(speed) || null,
        recorded_at: new Date().toISOString()
    };
    
    stateStore.employeeLocationsCache.set(user.id, locObj);
    stateStore.emitTo('admins', 'employeeLocation', locObj);
    return locObj;
}

async function updateLocationBatch(tokenHash, user, { trackingSessionId, jobId, locations }) {
    if (!Array.isArray(locations) || locations.length === 0) {
        return { ok: true, syncedCount: 0, serverTime: new Date().toISOString() };
    }

    // 1. Filter valid location points
    const validPoints = locations.filter(pt => 
        pt && 
        pt.latitude !== null && pt.latitude !== undefined &&
        pt.longitude !== null && pt.longitude !== undefined &&
        Number.isFinite(Number(pt.latitude)) && 
        Number.isFinite(Number(pt.longitude)) &&
        Number(pt.latitude) >= -90 && Number(pt.latitude) <= 90 &&
        Number(pt.longitude) >= -180 && Number(pt.longitude) <= 180
    );

    if (validPoints.length === 0) {
        return { ok: true, syncedCount: 0, serverTime: new Date().toISOString() };
    }

    // 2. Sort by satellite/device recording timestamp in ascending order
    validPoints.sort((a, b) => {
        const timeA = Date.parse(a.timestamp || '') || 0;
        const timeB = Date.parse(b.timestamp || '') || 0;
        return timeA - timeB;
    });

    // 3. Persist all breadcrumb points into employee_location_points history table
    const breadcrumbs = validPoints.map(pt => ({
        employee_id: user.id,
        tracking_session_id: trackingSessionId || null,
        job_id: jobId || null,
        latitude: Number(pt.latitude),
        longitude: Number(pt.longitude),
        accuracy: Number.isFinite(Number(pt.accuracy)) ? Number(pt.accuracy) : null,
        heading: Number.isFinite(Number(pt.heading)) ? Number(pt.heading) : null,
        speed: Number.isFinite(Number(pt.speed)) ? Number(pt.speed) : null,
        recorded_at: pt.timestamp || new Date().toISOString()
    }));

    await supabaseServiceRequest('employee_location_points', {
        method: 'POST',
        headers: { Prefer: 'return=minimal' },
        body: JSON.stringify(breadcrumbs)
    }).catch((err) => logger.error('Breadcrumbs save', err.message));

    // 4. Newest location point in the batch
    const latestPoint = validPoints[validPoints.length - 1];
    const latestTimestamp = latestPoint.timestamp || new Date().toISOString();

    // 5. Conditionally update current location ONLY if this point is newer than existing DB recorded_at
    await callRpc('employee_location_update_if_newer', {
        p_token_hash: tokenHash,
        p_latitude: Number(latestPoint.latitude),
        p_longitude: Number(latestPoint.longitude),
        p_accuracy: Number.isFinite(Number(latestPoint.accuracy)) ? Number(latestPoint.accuracy) : null,
        p_heading: Number.isFinite(Number(latestPoint.heading)) ? Number(latestPoint.heading) : null,
        p_speed: Number.isFinite(Number(latestPoint.speed)) ? Number(latestPoint.speed) : null,
        p_recorded_at: latestTimestamp
    });

    const cachedLoc = stateStore.employeeLocationsCache.get(user.id);
    const cachedTime = cachedLoc ? Date.parse(cachedLoc.recorded_at || '') : 0;
    const batchTime = Date.parse(latestTimestamp) || 0;

    let locObj = cachedLoc;
    if (!cachedLoc || batchTime >= cachedTime) {
        locObj = {
            employee_id: user.id,
            username: user.username,
            full_name: user.full_name,
            role: user.role,
            latitude: Number(latestPoint.latitude),
            longitude: Number(latestPoint.longitude),
            accuracy: Number(latestPoint.accuracy) || null,
            heading: Number(latestPoint.heading) || null,
            speed: Number(latestPoint.speed) || null,
            tracking_session_id: trackingSessionId || null,
            job_id: jobId || null,
            recorded_at: latestTimestamp
        };
        stateStore.employeeLocationsCache.set(user.id, locObj);
        stateStore.emitTo('admins', 'employeeLocation', locObj);
    }

    logger.info('GPS Batch Sync', `Synced ${validPoints.length} points for driver ${user.username} (${user.id})`);

    return {
        ok: true,
        syncedCount: validPoints.length,
        serverTime: new Date().toISOString(),
        latestLocation: locObj
    };
}

async function listLocations(tokenHash) {
    const rows = await callRpc('employee_location_list', { p_token_hash: tokenHash });
    const result = [];
    for (const row of rows || []) {
        const empId = row.id || row.employee_id;
        const online = isEmployeeOnline(row);
        const enriched = { ...row, is_online: online };
        if (empId) {
            stateStore.employeeLocationsCache.set(empId, enriched);
        }
        result.push(enriched);
    }
    return result;
}

async function updateEmployee({ tokenHash, employeeId, fullName, password, role }) {
    try {
        const rows = await callRpc('employee_update', {
            p_token_hash: tokenHash,
            p_employee_id: employeeId,
            p_full_name: fullName || null,
            p_password: password || null,
            p_role: role || null
        });
        if (Array.isArray(rows) && rows.length) return rows[0];
    } catch (err) {
        logger.warn('employee_update RPC fallback', err.message);
    }

    // Direct table update fallback
    const patchBody = {};
    if (fullName) patchBody.full_name = fullName;
    if (role) patchBody.role = role;

    if (Object.keys(patchBody).length > 0) {
        const { supabaseServiceRequest } = require('../core/supabase');
        await supabaseServiceRequest(`employee_accounts?id=eq.${employeeId}`, {
            method: 'PATCH',
            body: JSON.stringify(patchBody)
        });
    }
    return { id: employeeId, full_name: fullName, role };
}

module.exports = {
    createEmployeeAuthUser,
    deleteEmployeeAuthUser,
    login,
    logout,
    listEmployees,
    createEmployee,
    updateEmployee,
    setEmployeeActive,
    deleteEmployee,
    updateLocation,
    updateLocationBatch,
    listLocations
};
