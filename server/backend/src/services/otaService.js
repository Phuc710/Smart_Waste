'use strict';

const crypto = require('crypto');
const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const { supabaseServiceRequest } = require('../core/supabase');
const firmwareService = require('./firmwareService');

// Valid device OTA statuses in order of progressive lifecycle
const VALID_OTA_STATUSES = new Set([
    'PENDING',
    'COMMAND_SENT',
    'DOWNLOADING',
    'VERIFYING',
    'INSTALLING',
    'REBOOTING',
    'BOOT_VERIFYING',
    'SUCCESS',
    'FAILED',
    'ROLLBACK_STARTED',
    'ROLLBACK_SUCCESS',
    'ROLLBACK_FAILED',
    'TIMED_OUT'
]);

const TERMINAL_STATUSES = new Set(['SUCCESS', 'FAILED', 'ROLLBACK_SUCCESS', 'ROLLBACK_FAILED', 'TIMED_OUT', 'CANCELLED']);

function publishMqttOtaCommand(binId, envelope) {
    return new Promise((resolve, reject) => {
        const aedes = stateStore.getAedes();
        if (!aedes) return resolve();

        const packet = {
            topic: `wastebin/${binId}/ota`,
            payload: Buffer.from(JSON.stringify(envelope)),
            qos: 1,
            // CRITICAL: NEVER retain OTA command to prevent re-flashing device unexpectedly on reboot
            retain: false
        };

        aedes.publish(packet, (error) => error ? reject(error) : resolve());
    });
}

/**
 * Creates a new OTA Deployment campaign for selected devices
 */
async function createDeployment({ releaseId, targetDeviceIds, userId = null }) {
    // 1. Fetch & Verify Release
    const release = await firmwareService.getReleaseById(releaseId);
    if (!release) {
        const err = new Error('Bản firmware phát hành không tồn tại.');
        err.statusCode = 404;
        throw err;
    }

    if (release.status !== 'READY') {
        const err = new Error(`Bản firmware đang ở trạng thái '${release.status}', không sẵn sàng để triển khai OTA.`);
        err.statusCode = 400;
        throw err;
    }

    // 2. Resolve Target Device List
    const allBins = await supabaseServiceRequest('smart_bins?select=*');
    const binMap = new Map((allBins || []).map(b => [b.device_id, b]));

    let targetBins = [];
    if (targetDeviceIds === 'ALL') {
        targetBins = (allBins || []).filter(b => (b.device_model || 'ESP32-S3-SMARTBIN') === release.device_model);
    } else if (Array.isArray(targetDeviceIds)) {
        targetBins = targetDeviceIds.map(id => binMap.get(id)).filter(Boolean);
    }

    if (targetBins.length === 0) {
        const err = new Error('Không có thiết bị phù hợp nào được chọn.');
        err.statusCode = 400;
        throw err;
    }

    // 3. Create Deployment Record
    const deploymentRecord = {
        release_id: release.id,
        status: 'RUNNING',
        target_count: targetBins.length,
        success_count: 0,
        failed_count: 0,
        created_by: userId || null,
        created_at: new Date().toISOString(),
        started_at: new Date().toISOString()
    };

    const insertedDep = await supabaseServiceRequest('ota_deployments', {
        method: 'POST',
        headers: { Prefer: 'return=representation' },
        body: JSON.stringify(deploymentRecord)
    });
    const deployment = Array.isArray(insertedDep) ? insertedDep[0] : insertedDep;

    // 4. Generate Short-Lived Signed Download URL (Valid 1 hour)
    let signedDownloadUrl;
    try {
        signedDownloadUrl = await firmwareService.getSignedFirmwareUrl(release.object_path, 3600);
    } catch (urlErr) {
        logger.error('OTA Signed URL Error', urlErr.message);
        throw urlErr;
    }

    // 5. Create Individual Device Jobs and Dispatch MQTT Commands
    const createdJobs = [];
    for (const bin of targetBins) {
        const binId = bin.device_id;
        const commandId = crypto.randomUUID();
        const nowIso = new Date().toISOString();
        const expiresIso = new Date(Date.now() + 3600 * 1000).toISOString();

        const jobRecord = {
            deployment_id: deployment.id,
            release_id: release.id,
            device_id: binId,
            previous_version: bin.firmware_version || 'v1.0.0',
            target_version: release.version,
            status: 'COMMAND_SENT',
            progress_percent: 0,
            downloaded_bytes: 0,
            total_bytes: release.size_bytes,
            attempts: 1,
            command_id: commandId,
            boot_id_before: bin.last_boot_id || null,
            created_at: nowIso,
            started_at: nowIso,
            updated_at: nowIso
        };

        const insertedJob = await supabaseServiceRequest('ota_device_jobs', {
            method: 'POST',
            headers: { Prefer: 'return=representation' },
            body: JSON.stringify(jobRecord)
        });
        const job = Array.isArray(insertedJob) ? insertedJob[0] : insertedJob;
        createdJobs.push(job);

        // Update smart_bins ota_status
        await supabaseServiceRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}`, {
            method: 'PATCH',
            headers: { Prefer: 'return=minimal' },
            body: JSON.stringify({ ota_status: 'UPDATING' })
        }).catch(() => {});

        // Build Command Envelope
        const envelope = {
            type: 'OTA_UPDATE',
            commandId,
            deploymentId: deployment.id,
            deviceJobId: job.id,
            releaseId: release.id,
            version: release.version,
            deviceModel: release.device_model,
            sizeBytes: release.size_bytes,
            sha256: release.sha256,
            signature: release.signature || null,
            downloadUrl: signedDownloadUrl,
            issuedAt: nowIso,
            expiresAt: expiresIso
        };

        // Publish to device specific MQTT topic
        publishMqttOtaCommand(binId, envelope).catch(err => {
            logger.error('OTA MQTT Publish Error', `${binId}: ${err.message}`);
        });
    }

    stateStore.emitTo('admins', 'otaDeploymentCreated', { deployment, jobs: createdJobs });
    logger.info('OTA Deployment Dispatched', `Deployment ${deployment.id} dispatched to ${createdJobs.length} devices.`);

    return {
        deployment,
        jobs: createdJobs
    };
}

/**
 * Handles incoming MQTT status telemetry from ESP32 device
 * Topic: wastebin/{binId}/ota/status
 */
async function processDeviceOtaStatus(binId, data) {
    if (!data || typeof data !== 'object') return;

    const deviceId = String(data.deviceId || binId);
    if (deviceId !== binId) {
        logger.warn('OTA Spoof Attempt', `Device ID mismatch: topic=${binId}, payload=${deviceId}`);
        return;
    }

    const commandId = String(data.commandId || '');
    const rawStatus = String(data.status || '').toUpperCase();

    if (!VALID_OTA_STATUSES.has(rawStatus)) {
        logger.warn('OTA Invalid Status', `Unknown status '${rawStatus}' from #${binId}`);
        return;
    }

    // Find active job for this device
    let query = `ota_device_jobs?device_id=eq.${encodeURIComponent(binId)}&status=not.in.(SUCCESS,FAILED,ROLLBACK_SUCCESS,ROLLBACK_FAILED,TIMED_OUT)&order=created_at.desc&limit=1`;
    if (commandId) {
        query = `ota_device_jobs?command_id=eq.${encodeURIComponent(commandId)}&limit=1`;
    }

    const jobs = await supabaseServiceRequest(query);
    const job = Array.isArray(jobs) && jobs.length > 0 ? jobs[0] : null;
    if (!job) {
        return;
    }

    // Anti-Spoofing: State Machine Progression Validation
    // Cannot jump from PENDING / COMMAND_SENT directly to SUCCESS without REBOOTING/BOOT_VERIFYING
    if (rawStatus === 'SUCCESS' && (job.status === 'PENDING' || job.status === 'COMMAND_SENT')) {
        logger.warn('OTA Invalid Transition', `Device #${binId} attempted illegal skip from ${job.status} to SUCCESS`);
        return;
    }

    const nowIso = new Date().toISOString();
    const progressPercent = Math.min(100, Math.max(0, Number(data.progressPercent ?? data.progress ?? job.progress_percent)));
    const downloadedBytes = Number(data.downloadedBytes || job.downloaded_bytes || 0);
    const bootIdAfter = String(data.bootId || job.boot_id_after || '');
    const errorCode = data.errorCode ? String(data.errorCode) : null;
    const errorMessage = data.errorMessage ? String(data.errorMessage) : null;

    const patch = {
        status: rawStatus,
        progress_percent: rawStatus === 'SUCCESS' ? 100 : progressPercent,
        downloaded_bytes: downloadedBytes,
        boot_id_after: bootIdAfter || null,
        error_code: errorCode,
        error_message: errorMessage,
        updated_at: nowIso
    };

    if (rawStatus === 'SUCCESS' || rawStatus === 'ROLLBACK_SUCCESS' || rawStatus === 'FAILED') {
        patch.acknowledged_at = nowIso;
    }

    await supabaseServiceRequest(`ota_device_jobs?id=eq.${encodeURIComponent(job.id)}`, {
        method: 'PATCH',
        headers: { Prefer: 'return=minimal' },
        body: JSON.stringify(patch)
    }).catch(() => {});

    // Update smart_bins device state
    if (rawStatus === 'SUCCESS') {
        const targetVer = job.target_version;
        await supabaseServiceRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}`, {
            method: 'PATCH',
            headers: { Prefer: 'return=minimal' },
            body: JSON.stringify({
                firmware_version: targetVer,
                ota_status: 'IDLE',
                last_ota_at: nowIso,
                last_boot_id: bootIdAfter || null
            })
        }).catch(() => {});
    } else if (rawStatus === 'FAILED' || rawStatus === 'ROLLBACK_SUCCESS') {
        await supabaseServiceRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}`, {
            method: 'PATCH',
            headers: { Prefer: 'return=minimal' },
            body: JSON.stringify({ ota_status: 'IDLE' })
        }).catch(() => {});
    } else {
        await supabaseServiceRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}`, {
            method: 'PATCH',
            headers: { Prefer: 'return=minimal' },
            body: JSON.stringify({ ota_status: 'UPDATING' })
        }).catch(() => {});
    }

    // Check & Update Parent Deployment Progress
    await syncDeploymentProgress(job.deployment_id);

    stateStore.emitTo('admins', 'otaJobUpdated', {
        deviceJobId: job.id,
        deploymentId: job.deployment_id,
        deviceId: binId,
        status: rawStatus,
        progressPercent: patch.progress_percent,
        downloadedBytes,
        totalBytes: job.total_bytes,
        targetVersion: job.target_version,
        bootId: bootIdAfter,
        errorCode,
        errorMessage,
        updatedAt: nowIso
    });

    logger.info('OTA Progress', `${binId}: ${rawStatus} (${patch.progress_percent}%)`);
}

/**
 * Recalculates total success/failed counts for an OTA deployment
 */
async function syncDeploymentProgress(deploymentId) {
    if (!deploymentId) return;

    const allJobs = await supabaseServiceRequest(`ota_device_jobs?deployment_id=eq.${encodeURIComponent(deploymentId)}`);
    if (!Array.isArray(allJobs) || allJobs.length === 0) return;

    let successCount = 0;
    let failedCount = 0;
    let inFlightCount = 0;

    for (const j of allJobs) {
        if (j.status === 'SUCCESS') successCount++;
        else if (j.status === 'FAILED' || j.status === 'ROLLBACK_SUCCESS' || j.status === 'ROLLBACK_FAILED' || j.status === 'TIMED_OUT' || j.status === 'CANCELLED') failedCount++;
        else inFlightCount++;
    }

    let depStatus = 'RUNNING';
    let completedAt = null;

    if (inFlightCount === 0) {
        depStatus = failedCount === 0 ? 'COMPLETED' : 'PARTIAL_FAILED';
        completedAt = new Date().toISOString();
    }

    await supabaseServiceRequest(`ota_deployments?id=eq.${encodeURIComponent(deploymentId)}`, {
        method: 'PATCH',
        headers: { Prefer: 'return=minimal' },
        body: JSON.stringify({
            status: depStatus,
            success_count: successCount,
            failed_count: failedCount,
            completed_at: completedAt
        })
    }).catch(() => {});
}

/**
 * Safe Cancel Rule:
 * Only permits cancelling jobs that are in PENDING, COMMAND_SENT, or DOWNLOADING.
 * STRICTLY PREVENTS cancelling when device is in INSTALLING/FLASHING state to avoid corrupting hardware!
 */
async function cancelDeployment(deploymentId) {
    const deployment = await supabaseServiceRequest(`ota_deployments?id=eq.${encodeURIComponent(deploymentId)}`);
    if (!Array.isArray(deployment) || deployment.length === 0) {
        const err = new Error('Chiến dịch OTA không tồn tại.');
        err.statusCode = 404;
        throw err;
    }

    const activeJobs = await supabaseServiceRequest(`ota_device_jobs?deployment_id=eq.${encodeURIComponent(deploymentId)}&status=in.(PENDING,COMMAND_SENT,DOWNLOADING)`);
    for (const job of activeJobs || []) {
        await supabaseServiceRequest(`ota_device_jobs?id=eq.${encodeURIComponent(job.id)}`, {
            method: 'PATCH',
            headers: { Prefer: 'return=minimal' },
            body: JSON.stringify({ status: 'CANCELLED', error_message: 'Admin đã huỷ đợt cập nhật trước khi flash.' })
        }).catch(() => {});
        
        await supabaseServiceRequest(`smart_bins?device_id=eq.${encodeURIComponent(job.device_id)}`, {
            method: 'PATCH',
            headers: { Prefer: 'return=minimal' },
            body: JSON.stringify({ ota_status: 'IDLE' })
        }).catch(() => {});
    }

    await syncDeploymentProgress(deploymentId);
    return { ok: true, message: 'Đã huỷ an toàn các tiến trình chưa thực hiện flash.' };
}

/**
 * Retries a failed or timed out device job with a fresh signed URL
 */
async function retryDeviceJob(deviceJobId) {
    const jobs = await supabaseServiceRequest(`ota_device_jobs?id=eq.${encodeURIComponent(deviceJobId)}`);
    const job = Array.isArray(jobs) && jobs.length > 0 ? jobs[0] : null;
    if (!job) {
        const err = new Error('Tiến trình thiết bị không tồn tại.');
        err.statusCode = 404;
        throw err;
    }

    const release = await firmwareService.getReleaseById(job.release_id);
    if (!release) {
        const err = new Error('Bản firmware không tồn tại.');
        err.statusCode = 404;
        throw err;
    }

    const signedUrl = await firmwareService.getSignedFirmwareUrl(release.object_path, 3600);
    const newCmdId = crypto.randomUUID();
    const nowIso = new Date().toISOString();
    const nextAttempts = (job.attempts || 1) + 1;

    await supabaseServiceRequest(`ota_device_jobs?id=eq.${encodeURIComponent(job.id)}`, {
        method: 'PATCH',
        headers: { Prefer: 'return=minimal' },
        body: JSON.stringify({
            status: 'COMMAND_SENT',
            attempts: nextAttempts,
            command_id: newCmdId,
            error_code: null,
            error_message: null,
            updated_at: nowIso
        })
    });

    const envelope = {
        type: 'OTA_UPDATE',
        commandId: newCmdId,
        deploymentId: job.deployment_id,
        deviceJobId: job.id,
        releaseId: release.id,
        version: release.version,
        deviceModel: release.device_model,
        sizeBytes: release.size_bytes,
        sha256: release.sha256,
        signature: release.signature || null,
        downloadUrl: signedUrl,
        issuedAt: nowIso,
        expiresAt: new Date(Date.now() + 3600 * 1000).toISOString()
    };

    await publishMqttOtaCommand(job.device_id, envelope);
    await syncDeploymentProgress(job.deployment_id);

    return { ok: true, job: { ...job, command_id: newCmdId, status: 'COMMAND_SENT', attempts: nextAttempts } };
}

async function listDeployments() {
    const rows = await supabaseServiceRequest('ota_deployments?select=*,release:firmware_releases(*)&order=created_at.desc');
    return rows || [];
}

async function getDeploymentDetails(id) {
    const deps = await supabaseServiceRequest(`ota_deployments?select=*,release:firmware_releases(*)&id=eq.${encodeURIComponent(id)}`);
    const dep = Array.isArray(deps) && deps.length > 0 ? deps[0] : null;
    if (!dep) return null;

    const jobs = await supabaseServiceRequest(`ota_device_jobs?deployment_id=eq.${encodeURIComponent(id)}&order=created_at.asc`);
    return {
        ...dep,
        jobs: jobs || []
    };
}

module.exports = {
    VALID_OTA_STATUSES,
    TERMINAL_STATUSES,
    publishMqttOtaCommand,
    createDeployment,
    processDeviceOtaStatus,
    syncDeploymentProgress,
    cancelDeployment,
    retryDeviceJob,
    listDeployments,
    getDeploymentDetails
};
