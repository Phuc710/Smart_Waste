'use strict';

const env = require('../config/env');
const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const { supabaseServiceRequest, storageServiceRequest, callRpc } = require('../core/supabase');

function validIncidentObjectPath(objectPath) {
    if (!objectPath) return false;
    return /^incidents\/[A-Za-z0-9_-]+\/[A-Za-z0-9_.-]+\.jpg$/i.test(String(objectPath || ''));
}

async function createIncidentSignedUrl(objectPath, employeeId) {
    if (!objectPath) return null;
    if (/^https?:\/\//i.test(objectPath) || objectPath.startsWith('data:image')) {
        return objectPath;
    }
    
    const cached = stateStore.signedUrlCache.get(objectPath);
    if (cached && cached.expireAt > Date.now()) {
        return cached.url;
    }
    
    try {
        const encodedPath = objectPath.split('/').map(encodeURIComponent).join('/');
        const data = await storageServiceRequest(`object/sign/incident-images/${encodedPath}`, {
            method: 'POST',
            body: JSON.stringify({ expiresIn: 3600 })
        });
        const signedPath = data.signedURL || data.signedUrl;
        if (!signedPath) return null;
        
        const fullUrl = /^https?:\/\//i.test(signedPath) 
            ? signedPath 
            : `${env.SUPABASE_URL}/storage/v1${signedPath.startsWith('/') ? '' : '/'}${signedPath}`;
            
        stateStore.signedUrlCache.set(objectPath, { url: fullUrl, expireAt: Date.now() + 3500 * 1000 });
        return fullUrl;
    } catch (err) {
        logger.warn('Incident Image', 'Signing error:', err.message);
        return null;
    }
}

async function getIncidents() {
    const resource = 'incident_reports'
        + '?select=id,device_id,employee_id,employee_name,reason,description,has_photo,proof_image_url,status,created_at,resolved_at'
        + '&order=created_at.desc&limit=100';
    const rows = await supabaseServiceRequest(resource);
    
    return (rows || []).map((row) => {
        let imageUrl = null;
        if (row.has_photo && row.proof_image_url) {
            if (/^https?:\/\//i.test(row.proof_image_url) || row.proof_image_url.startsWith('data:image')) {
                imageUrl = row.proof_image_url;
            } else {
                imageUrl = `/api/employees/${encodeURIComponent(row.employee_id || 'anonymous')}/incidents/${encodeURIComponent(row.id)}/image`;
            }
        }
        const bin = stateStore.latestBins.get(row.device_id) || {};
        return {
            id: String(row.id),
            employee_id: row.employee_id,
            employee_name: row.employee_name || 'Nhân viên thực địa',
            device_id: String(row.device_id || ''),
            bin_name: bin.name || row.device_id,
            bin_location: bin.location || '',
            reason: row.reason,
            description: row.description,
            status: row.status,
            created_at: row.created_at,
            resolved_at: row.resolved_at,
            has_photo: Boolean(row.has_photo && row.proof_image_url),
            image_url: imageUrl
        };
    });
}

async function getEmployeeIncidents(employeeId, tokenHash) {
    const employees = await callRpc('employee_list', { p_token_hash: tokenHash });
    const employee = (employees || []).find((item) => String(item.id).toLowerCase() === employeeId.toLowerCase());
    if (!employee) {
        const err = new Error('Không tìm thấy nhân viên.');
        err.statusCode = 404;
        throw err;
    }

    const resource = 'incident_reports'
        + '?select=id,device_id,reason,description,has_photo,proof_image_url,status,created_at,resolved_at'
        + `&employee_id=eq.${encodeURIComponent(employeeId)}`
        + '&order=created_at.desc&limit=100';
    const rows = await supabaseServiceRequest(resource);
    
    const reports = (rows || []).map((row) => {
        let imageUrl = null;
        if (row.has_photo && row.proof_image_url) {
            if (/^https?:\/\//i.test(row.proof_image_url) || row.proof_image_url.startsWith('data:image')) {
                imageUrl = row.proof_image_url;
            } else {
                imageUrl = `/api/employees/${encodeURIComponent(employeeId)}/incidents/${encodeURIComponent(row.id)}/image`;
            }
        }
        const bin = stateStore.latestBins.get(row.device_id) || {};
        return {
            id: String(row.id),
            employee_id: employeeId,
            device_id: String(row.device_id || ''),
            bin_name: bin.name || row.device_id,
            bin_location: bin.location || '',
            reason: row.reason,
            description: row.description,
            status: row.status,
            created_at: row.created_at,
            resolved_at: row.resolved_at,
            has_photo: Boolean(row.has_photo && row.proof_image_url),
            image_url: imageUrl
        };
    });

    return { employee, reports };
}

async function getIncidentImageRedirect(employeeId, reportId) {
    const resource = 'incident_reports'
        + '?select=has_photo,proof_image_url'
        + `&id=eq.${encodeURIComponent(reportId)}`
        + `&employee_id=eq.${encodeURIComponent(employeeId)}`
        + '&limit=1';
    const rows = await supabaseServiceRequest(resource);
    const report = Array.isArray(rows) ? rows[0] : null;
    if (!report || !report.has_photo || !report.proof_image_url) {
        return null;
    }
    return createIncidentSignedUrl(report.proof_image_url, employeeId);
}

async function updateIncidentStatus(reportId, rawStatus) {
    let status = String(rawStatus || 'RESOLVED').toUpperCase();
    if (status === 'RESOLVED' || status === 'DONE') status = 'RESOLVED';
    else if (status === 'IN_REVIEW' || status === 'REVIEWING') status = 'IN_REVIEW';
    else status = 'NEW';

    const updated = await supabaseServiceRequest(`incident_reports?id=eq.${encodeURIComponent(reportId)}`, {
        method: 'PATCH',
        headers: { Prefer: 'return=representation' },
        body: JSON.stringify({ 
            status, 
            resolved_at: status === 'RESOLVED' ? new Date().toISOString() : null 
        })
    });
    
    return updated?.[0] || null;
}

async function createIncident({ deviceId, employeeId, employeeName, reason, description, photoUrl }) {
    const payload = {
        device_id: deviceId,
        employee_id: employeeId || null,
        employee_name: employeeName || 'Nhân viên thu gom',
        reason: reason || 'Sự cố khác',
        description: description || '',
        has_photo: Boolean(photoUrl),
        proof_image_url: photoUrl || null,
        status: 'NEW',
        created_at: new Date().toISOString()
    };
    
    let report = payload;
    try {
        const rows = await supabaseServiceRequest('incident_reports', {
            method: 'POST',
            headers: { Prefer: 'return=representation' },
            body: JSON.stringify(payload)
        });
        if (Array.isArray(rows) && rows.length) {
            report = rows[0];
        }
    } catch (err) {
        logger.warn('Incident Create', 'Database write fallback:', err.message);
    }
    
    stateStore.emitTo('admins', 'incidentReported', report);
    return report;
}

async function prepareIncidentImageUpload({ tokenHash, deviceId, reason, description }) {
    const upload = await callRpc('employee_incident_upload_prepare', {
        p_token_hash: tokenHash,
        p_device_id: deviceId,
        p_reason: reason,
        p_description: description || ''
    });
    const data = Array.isArray(upload) ? upload[0] : upload;
    if (!data?.upload_id || !data?.object_path) {
        throw new Error('Không thể tạo phiên tải ảnh sự cố.');
    }

    const encodedPath = String(data.object_path).split('/').map(encodeURIComponent).join('/');
    const signed = await storageServiceRequest(`object/upload/sign/incident-images/${encodedPath}`, {
        method: 'POST',
        body: JSON.stringify({})
    });
    const signedPath = signed?.url || signed?.signedURL || signed?.signedUrl;
    if (!signedPath) throw new Error('Không thể tạo Signed URL cho ảnh sự cố.');

    return {
        uploadId: data.upload_id,
        objectPath: data.object_path,
        expiresAt: data.expires_at,
        uploadUrl: /^https?:\/\//i.test(signedPath)
            ? signedPath
            : `${env.SUPABASE_URL}/storage/v1${signedPath.startsWith('/') ? '' : '/'}${signedPath}`
    };
}

async function finalizeIncidentImageUpload(tokenHash, uploadId) {
    return callRpc('employee_incident_upload_finalize', {
        p_token_hash: tokenHash,
        p_upload_id: uploadId
    });
}

async function getMyIncidents(employeeId) {
    try {
        const resource = 'incident_reports'
            + '?select=id,device_id,reason,description,has_photo,proof_image_url,status,created_at,resolved_at'
            + `&employee_id=eq.${encodeURIComponent(employeeId)}`
            + '&order=created_at.desc&limit=50';
        const rows = await supabaseServiceRequest(resource);
        
        return await Promise.all((rows || []).map(async (row) => {
            let imageUrl = row.proof_image_url || null;
            if (row.has_photo && row.proof_image_url && !/^https?:\/\//i.test(row.proof_image_url) && !row.proof_image_url.startsWith('data:image')) {
                imageUrl = await createIncidentSignedUrl(row.proof_image_url, employeeId);
            }
            const bin = stateStore.latestBins.get(row.device_id) || {};
            return {
                id: String(row.id),
                device_id: String(row.device_id || ''),
                bin_name: bin.name || row.device_id,
                bin_location: bin.location || '',
                reason: row.reason,
                description: row.description,
                status: row.status || 'NEW',
                created_at: row.created_at,
                resolved_at: row.resolved_at,
                has_photo: Boolean(row.has_photo),
                image_url: imageUrl
            };
        }));
    } catch (err) {
        logger.warn('Get My Incidents', err.message);
        return [];
    }
}

module.exports = {
    validIncidentObjectPath,
    createIncidentSignedUrl,
    createIncident,
    prepareIncidentImageUpload,
    finalizeIncidentImageUpload,
    getIncidents,
    getMyIncidents,
    getEmployeeIncidents,
    getIncidentImageRedirect,
    updateIncidentStatus
};
