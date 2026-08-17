'use strict';

const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const configService = require('../services/configService');
const { callServiceRpc } = require('../core/supabase');
const jobsDb = require('../services/jobsDb');

async function checkJobTimeouts() {
    const now = Date.now();
    let activeJobs;
    try { 
        activeJobs = await jobsDb.getActiveJobs(); 
    } catch (_) { 
        return; 
    }

    const assignTimeoutMin = configService.getConfig('assign_timeout_minutes') || 5;
    const pausedTimeoutMin = configService.getConfig('paused_timeout_minutes') || 30;

    for (const job of activeJobs) {
        // Check ASSIGNED timeout
        if (job.status === 'ASSIGNED' && job.assigned_at) {
            const timeoutMs = assignTimeoutMin * 60_000;
            if (now - new Date(job.assigned_at).getTime() > timeoutMs) {
                const expired = await callServiceRpc('rpc_expire_job', { p_job_id: job.id })
                    .then(r => (Array.isArray(r) ? r[0] : r))
                    .catch(e => { 
                        logger.error('Cron Expire', job.id, e.message); 
                        return null; 
                    });

                if (expired) {
                    const enriched = await jobsDb.attachProgress(expired);
                    stateStore.emit('jobUpdated', enriched);
                    logger.info('Job EXPIRED', `${job.id} — ${job.employee_name} không phản hồi trong ${assignTimeoutMin} phút`);
                }
            }
        }

        // Check PAUSED timeout -> Alert admin
        if (job.status === 'PAUSED' && job.paused_at && !stateStore.pauseAlertSentSet.has(job.id)) {
            const pausedMs = now - new Date(job.paused_at).getTime();
            if (pausedMs > pausedTimeoutMin * 60_000) {
                stateStore.pauseAlertSentSet.add(job.id);
                stateStore.emitTo('admins', 'jobPausedTooLong', {
                    jobId: job.id,
                    employeeName: job.employee_name,
                    pausedMinutes: Math.round(pausedMs / 60_000),
                    message: `${job.employee_name} đã tạm dừng ${Math.round(pausedMs / 60_000)} phút — cần kiểm tra.`
                });
            }
        }
    }
}

function startJobMonitorCron(intervalMs = 30_000) {
    const timer = setInterval(checkJobTimeouts, intervalMs);
    timer.unref();
    return timer;
}

module.exports = {
    startJobMonitorCron,
    checkJobTimeouts
};
