'use strict';

const { supabaseServiceRequest } = require('../core/supabase');

let serviceReq = supabaseServiceRequest;

function init(customServiceReq) {
    if (typeof customServiceReq === 'function') {
        serviceReq = customServiceReq;
    }
}

function mapErrCode(err) {
    const msg = String(err && err.message ? err.message : err);
    if (/BINS_CONFLICT|TRANSFER_CONFLICT|VERSION_CONFLICT|409/i.test(msg)) return 409;
    if (/JOB_NOT_FOUND|BIN_NOT_IN_JOB|404/i.test(msg)) return 404;
    if (/INVALID_STATUS|NO_REMAINING_BINS|400/i.test(msg)) return 400;
    return 400;
}

async function getJob(jobId) {
    if (!jobId) return null;
    const rows = await serviceReq(`collection_jobs?id=eq.${encodeURIComponent(jobId)}&limit=1`);
    return Array.isArray(rows) && rows.length ? rows[0] : null;
}

async function getEmployeeActiveJob(employeeId) {
    if (!employeeId) return null;
    const rows = await serviceReq(
        `collection_jobs?employee_id=eq.${encodeURIComponent(employeeId)}`
        + '&status=in.(ASSIGNED,ACCEPTED,IN_PROGRESS,PAUSED)'
        + '&order=created_at.desc&limit=1'
    );
    return Array.isArray(rows) && rows.length ? rows[0] : null;
}

async function getActiveJobs() {
    const rows = await serviceReq(
        'collection_jobs?status=in.(PENDING,ASSIGNED,ACCEPTED,IN_PROGRESS,PAUSED)&order=created_at.desc'
    );
    return rows || [];
}

async function getHistoryJobs(limit = 100) {
    const safeLimit = Math.max(1, Math.min(200, Number(limit) || 100));
    const rows = await serviceReq(
        `collection_jobs?status=in.(COMPLETED,CANCELLED,REJECTED,EXPIRED)&order=created_at.desc&limit=${safeLimit}`
    );
    return rows || [];
}

async function attachProgress(job) {
    if (!job || !job.id) return job;
    try {
        const items = await serviceReq(`job_bin_items?job_id=eq.${encodeURIComponent(job.id)}&order=id.asc`);
        return calculateJobProgress(job, items || []);
    } catch (_) {
        return calculateJobProgress(job, []);
    }
}

async function attachProgressBulk(jobs = []) {
    if (!Array.isArray(jobs) || jobs.length === 0) return [];
    try {
        const jobIds = jobs.map(j => encodeURIComponent(j.id)).filter(Boolean);
        if (jobIds.length === 0) return jobs;
        
        const items = await serviceReq(`job_bin_items?job_id=in.(${jobIds.join(',')})&order=id.asc`);
        const itemMap = new Map();
        for (const item of items || []) {
            if (!itemMap.has(item.job_id)) itemMap.set(item.job_id, []);
            itemMap.get(item.job_id).push(item);
        }
        
        return jobs.map(j => calculateJobProgress(j, itemMap.get(j.id) || []));
    } catch (_) {
        return jobs.map(j => calculateJobProgress(j, []));
    }
}

function calculateJobProgress(job, items = []) {
    const totalBins = Array.isArray(job.target_bin_ids) ? job.target_bin_ids.length : items.length;
    const collectedItems = items.filter(i => i.status === 'COLLECTED');
    const completedBinIds = collectedItems.map(i => i.bin_id);
    const collectedCount = completedBinIds.length;
    const progressPercent = totalBins > 0 ? Math.round((collectedCount / totalBins) * 100) : 0;
    
    return {
        ...job,
        items,
        completed_bin_ids: completedBinIds,
        progress: {
            total: totalBins,
            collected: collectedCount,
            percent: progressPercent
        }
    };
}

async function transitionJob(jobId, allowedStatuses, targetStatus, extraFields = {}) {
    const job = await getJob(jobId);
    if (!job) {
        const err = new Error('Không tìm thấy job.');
        err.statusCode = 404;
        throw err;
    }
    if (!allowedStatuses.includes(job.status)) {
        const err = new Error(`Trạng thái không hợp lệ: Job hiện tại đang ở trạng thái "${job.status}" (yêu cầu: ${allowedStatuses.join(', ')}).`);
        err.statusCode = 400;
        throw err;
    }

    const payload = {
        status: targetStatus,
        version: (job.version || 1) + 1,
        ...extraFields
    };

    const updated = await serviceReq(
        `collection_jobs?id=eq.${encodeURIComponent(jobId)}&status=in.(${allowedStatuses.join(',')})`,
        {
            method: 'PATCH',
            headers: { Prefer: 'return=representation' },
            body: JSON.stringify(payload)
        }
    );

    if (!Array.isArray(updated) || updated.length === 0) {
        const err = new Error('Xung đột trạng thái job khi cập nhật.');
        err.statusCode = 409;
        throw err;
    }

    return updated[0];
}

module.exports = {
    init,
    mapErrCode,
    getJob,
    getEmployeeActiveJob,
    getActiveJobs,
    getHistoryJobs,
    attachProgress,
    attachProgressBulk,
    transitionJob
};
