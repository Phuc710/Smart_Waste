'use strict';

const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const { callServiceRpc, supabaseServiceRequest } = require('../core/supabase');
const jobsDb = require('./jobsDb');
const { calculateOsrmRoute } = require('./routingService');

async function assignJob({ employeeId, employeeName = 'Nhân viên thu gom', binIds = [] }) {
    if (!employeeId || !Array.isArray(binIds) || binIds.length === 0) {
        const err = new Error('Cần chọn nhân viên và ít nhất 1 thùng rác.');
        err.statusCode = 400;
        throw err;
    }

    const existing = await jobsDb.getEmployeeActiveJob(employeeId);
    if (existing) {
        const err = new Error('Nhân viên này đang có nhiệm vụ chưa hoàn tất. Không thể gán thêm nhiệm vụ mới.');
        err.statusCode = 409;
        throw err;
    }

    const loc = stateStore.employeeLocationsCache.get(employeeId) || { latitude: 10.7769, longitude: 106.7009 };
    const targetBins = binIds
        .map(id => stateStore.latestBins.get(id))
        .filter(b => b && Number(b.latitude) && Number(b.longitude));
        
    const coordinates = [
        [Number(loc.longitude), Number(loc.latitude)],
        ...targetBins.map(b => [Number(b.longitude), Number(b.latitude)])
    ];
    const routeData = await calculateOsrmRoute(coordinates).catch(() => null);

    const result = await callServiceRpc('rpc_assign_job', {
        p_job_id: 'JOB_' + Date.now(),
        p_employee_id: employeeId,
        p_employee_name: employeeName,
        p_bin_ids: binIds,
        p_source: 'ADMIN_ASSIGNED',
        p_route_data: routeData
    });
    
    const rawJob = Array.isArray(result) ? result[0] : result;
    const newJob = await jobsDb.attachProgress(rawJob);

    stateStore.emitTo(`employee_${employeeId}`, 'jobAssigned', newJob);
    stateStore.emit('jobUpdated', newJob);
    logger.info('Dispatch', `${employeeName} được gán ${binIds.length} thùng (${newJob.id})`);
    
    return newJob;
}

async function reassignJob(oldJobId, newEmployeeId, newEmployeeName = 'Nhân viên thu gom') {
    if (!newEmployeeId) {
        const err = new Error('Thiếu employeeId.');
        err.statusCode = 400;
        throw err;
    }

    const targetBusy = await jobsDb.getEmployeeActiveJob(newEmployeeId);
    if (targetBusy) {
        const err = new Error('Tài xế tiếp nhận đang có nhiệm vụ chưa hoàn tất. Vui lòng chọn tài xế khác.');
        err.statusCode = 409;
        throw err;
    }

    const oldJob = await jobsDb.getJob(oldJobId);
    if (!oldJob) {
        const err = new Error('Không tìm thấy job.');
        err.statusCode = 404;
        throw err;
    }

    const newJobId = 'JOB_' + Date.now();
    const rpcResult = await callServiceRpc('rpc_reassign_job', {
        p_old_job_id: oldJobId,
        p_old_version: oldJob.version,
        p_new_job_id: newJobId,
        p_new_employee_id: newEmployeeId,
        p_new_employee_name: newEmployeeName
    });
    const result = Array.isArray(rpcResult) ? rpcResult[0] : rpcResult;

    // Phase 2: Compute route for remaining bins
    const remainingBinIds = result.remaining_bin_ids || [];
    const loc = stateStore.employeeLocationsCache.get(newEmployeeId) || { latitude: 10.7769, longitude: 106.7009 };
    const targetBins = remainingBinIds
        .map(id => stateStore.latestBins.get(id))
        .filter(b => b && Number(b.latitude) && Number(b.longitude));
        
    const coordinates = [
        [Number(loc.longitude), Number(loc.latitude)],
        ...targetBins.map(b => [Number(b.longitude), Number(b.latitude)])
    ];
    const routeData = await calculateOsrmRoute(coordinates).catch(() => null);
    
    if (routeData) {
        await supabaseServiceRequest(`collection_jobs?id=eq.${encodeURIComponent(newJobId)}`, {
            method: 'PATCH',
            headers: { Prefer: 'return=minimal' },
            body: JSON.stringify({ route_data: routeData })
        }).catch(e => logger.warn('Reassign route patch', e.message));
        result.new_job = { ...result.new_job, route_data: routeData };
    }

    const [oldJobEnriched, newJobEnriched] = await Promise.all([
        jobsDb.attachProgress(result.old_job),
        jobsDb.attachProgress(result.new_job)
    ]);

    stateStore.emit('jobUpdated', oldJobEnriched);
    stateStore.emitTo(`employee_${newEmployeeId}`, 'jobAssigned', newJobEnriched);
    stateStore.emit('jobUpdated', newJobEnriched);

    return { old_job: oldJobEnriched, new_job: newJobEnriched };
}

async function cancelJob(jobId) {
    const oldJob = await jobsDb.getJob(jobId);
    if (!oldJob) {
        const err = new Error('Không tìm thấy job.');
        err.statusCode = 404;
        throw err;
    }

    const result = await callServiceRpc('rpc_cancel_job', {
        p_job_id: jobId,
        p_expected_version: oldJob.version
    });
    const job = Array.isArray(result) ? result[0] : result;
    const enrichedJob = await jobsDb.attachProgress(job);

    stateStore.pauseAlertSentSet.delete(jobId);
    stateStore.emit('jobUpdated', enrichedJob);
    return enrichedJob;
}

async function selfPickJob({ tokenHash, employeeId, employeeName = 'Nhân viên thu gom', binIds = [] }) {
    if (!Array.isArray(binIds) || binIds.length === 0) {
        const err = new Error('Cần chọn ít nhất 1 thùng rác để tự nhận thu gom.');
        err.statusCode = 400;
        throw err;
    }

    const existing = await jobsDb.getEmployeeActiveJob(employeeId);
    if (existing) {
        const err = new Error('Bạn đang có nhiệm vụ chưa hoàn tất. Vui lòng hoàn thành trước khi nhận nhiệm vụ mới.');
        err.statusCode = 409;
        throw err;
    }

    const loc = stateStore.employeeLocationsCache.get(employeeId) || { latitude: 10.7769, longitude: 106.7009 };
    const targetBins = binIds
        .map(id => stateStore.latestBins.get(id))
        .filter(b => b && Number(b.latitude) && Number(b.longitude));
        
    const coordinates = [
        [Number(loc.longitude), Number(loc.latitude)],
        ...targetBins.map(b => [Number(b.longitude), Number(b.latitude)])
    ];
    const routeData = await calculateOsrmRoute(coordinates).catch(() => null);

    const result = await callServiceRpc('rpc_driver_self_pick_job', {
        p_token_hash: tokenHash,
        p_job_id: 'JOB_' + Date.now(),
        p_bin_ids: binIds,
        p_route_data: routeData
    });
    
    const rawJob = Array.isArray(result) ? result[0] : result;
    const newJob = await jobsDb.attachProgress(rawJob);

    stateStore.emit('jobUpdated', newJob);
    logger.info('Self-Pick', `${employeeName} tự chọn ${binIds.length} thùng (${newJob.id})`);
    return newJob;
}

async function acceptJob({ tokenHash, jobId }) {
    if (!jobId) {
        const err = new Error('Thiếu jobId.');
        err.statusCode = 400;
        throw err;
    }
    const result = await callServiceRpc('rpc_driver_accept_job', {
        p_token_hash: tokenHash,
        p_job_id: jobId
    });
    const rawJob = Array.isArray(result) ? result[0] : result;
    const enriched = await jobsDb.attachProgress(rawJob);
    stateStore.emit('jobUpdated', enriched);
    return enriched;
}

async function rejectJob({ tokenHash, jobId }) {
    if (!jobId) {
        const err = new Error('Thiếu jobId.');
        err.statusCode = 400;
        throw err;
    }
    const result = await callServiceRpc('rpc_driver_reject_job', {
        p_token_hash: tokenHash,
        p_job_id: jobId
    });
    const rawJob = Array.isArray(result) ? result[0] : result;
    const enriched = await jobsDb.attachProgress(rawJob);
    stateStore.emit('jobUpdated', enriched);
    return enriched;
}

async function startJob({ tokenHash, jobId }) {
    if (!jobId) {
        const err = new Error('Thiếu jobId.');
        err.statusCode = 400;
        throw err;
    }
    const result = await callServiceRpc('rpc_driver_start_job', {
        p_token_hash: tokenHash,
        p_job_id: jobId
    });
    const rawJob = Array.isArray(result) ? result[0] : result;
    const enriched = await jobsDb.attachProgress(rawJob);
    stateStore.emit('jobUpdated', enriched);
    return enriched;
}

async function pauseJob({ tokenHash, jobId, reason = '' }) {
    if (!jobId) {
        const err = new Error('Thiếu jobId.');
        err.statusCode = 400;
        throw err;
    }
    const result = await callServiceRpc('rpc_driver_pause_job', {
        p_token_hash: tokenHash,
        p_job_id: jobId,
        p_reason: String(reason || '')
    });
    const rawJob = Array.isArray(result) ? result[0] : result;
    const enriched = await jobsDb.attachProgress(rawJob);
    stateStore.emit('jobUpdated', enriched);
    return enriched;
}

async function resumeJob({ tokenHash, jobId }) {
    if (!jobId) {
        const err = new Error('Thiếu jobId.');
        err.statusCode = 400;
        throw err;
    }
    const result = await callServiceRpc('rpc_driver_resume_job', {
        p_token_hash: tokenHash,
        p_job_id: jobId
    });
    stateStore.pauseAlertSentSet.delete(jobId);
    const rawJob = Array.isArray(result) ? result[0] : result;
    const enriched = await jobsDb.attachProgress(rawJob);
    stateStore.emit('jobUpdated', enriched);
    return enriched;
}

async function collectBin({ tokenHash, jobId, binId, status = 'COLLECTED', note, photoUrl }) {
    if (!jobId || !binId) {
        const err = new Error('Thiếu jobId hoặc binId.');
        err.statusCode = 400;
        throw err;
    }

    const rpcResult = await callServiceRpc('rpc_driver_collect_bin', {
        p_token_hash: tokenHash,
        p_job_id:    jobId,
        p_bin_id:    binId,
        p_status:    status,
        p_note:      note || null,
        p_photo_url: photoUrl || null
    });
    const result = Array.isArray(rpcResult) ? rpcResult[0] : rpcResult;

    // Update in-memory bin cache
    const binCache = stateStore.latestBins.get(binId);
    if (binCache) {
        stateStore.latestBins.set(binId, {
            ...binCache,
            level_percent: 0,
            collection_status: 'IDLE',
            collection_employee_id: null,
            collection_employee_name: null
        });
    }

    const enrichedJob = await jobsDb.attachProgress(result.job);
    stateStore.emit('binData', {
        binId,
        data: stateStore.latestBins.get(binId) || { device_id: binId, level_percent: 0, collection_status: 'IDLE' }
    });
    stateStore.emit('jobUpdated', enrichedJob);

    if (result.all_done && !result.idempotent) {
        stateStore.emit('jobCompleted', { jobId, employeeId: result.job?.employee_id });
    }

    return {
        ok: true,
        allDone: result.all_done,
        idempotent: Boolean(result.idempotent),
        job: enrichedJob
    };
}

module.exports = {
    assignJob,
    reassignJob,
    cancelJob,
    selfPickJob,
    acceptJob,
    rejectJob,
    startJob,
    pauseJob,
    resumeJob,
    collectBin
};
