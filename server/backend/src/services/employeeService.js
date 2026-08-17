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
    const rows = await callRpc('employee_login', {
        p_username: username,
        p_password: password,
        p_token_hash: tokenHash
    });
    if (!Array.isArray(rows) || !rows.length) {
        const err = new Error('Sai tên đăng nhập, mật khẩu hoặc tài khoản đã bị khóa.');
        err.statusCode = 401;
        throw err;
    }
    return rows[0];
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
    listLocations
};
