'use strict';

const express = require('express');
const router = express.Router();
const { requireAuth, requireAdmin } = require('../middleware/auth');
const { asyncHandler } = require('../middleware/errorHandler');
const firmwareService = require('../services/firmwareService');
const otaService = require('../services/otaService');

// Middleware to parse raw binary buffer if content-type is octet-stream
const rawBodyParser = express.raw({
    type: ['application/octet-stream', 'application/x-binary'],
    limit: '5mb'
});

// =========================================================
// 1. FIRMWARE RELEASES API (ADMIN ONLY)
// =========================================================

// POST /api/firmware/releases/upload - Upload and register a new ESP32 firmware release
router.post('/releases/upload', requireAuth, requireAdmin, rawBodyParser, asyncHandler(async (req, res) => {
    let fileBuffer;
    let fileName = req.headers['x-filename'] || 'firmware.bin';
    let version = req.headers['x-version'] || req.query.version;
    let deviceModel = req.headers['x-device-model'] || req.query.deviceModel || 'ESP32-S3-SMARTBIN';
    let releaseNotes = req.headers['x-release-notes'] ? decodeURIComponent(req.headers['x-release-notes']) : (req.query.releaseNotes || '');

    if (Buffer.isBuffer(req.body) && req.body.length > 0) {
        fileBuffer = req.body;
    } else if (req.body && typeof req.body === 'object' && req.body.base64) {
        fileBuffer = Buffer.from(req.body.base64, 'base64');
        fileName = req.body.fileName || fileName;
        version = req.body.version || version;
        deviceModel = req.body.deviceModel || deviceModel;
        releaseNotes = req.body.releaseNotes || releaseNotes;
    }

    if (!fileBuffer || fileBuffer.length === 0) {
        return res.status(400).json({ error: 'Vui lòng cung cấp nội dung file firmware .bin hợp lệ.' });
    }

    if (!version) {
        return res.status(400).json({ error: 'Vui lòng cung cấp số phiên bản (version) theo chuẩn SemVer (ví dụ: v1.0.0).' });
    }

    const release = await firmwareService.createFirmwareRelease({
        fileBuffer,
        fileName,
        version: String(version).trim(),
        deviceModel: String(deviceModel).trim(),
        releaseNotes: String(releaseNotes || '').trim(),
        userId: req.auth.user.id
    });

    res.status(201).json({
        ok: true,
        message: `Bản phát hành ${release.version} (${release.device_model}) đã được tạo và lưu trữ thành công!`,
        release
    });
}));

// GET /api/firmware/releases - List all firmware releases
router.get('/releases', requireAuth, asyncHandler(async (req, res) => {
    const deviceModel = req.query.deviceModel ? String(req.query.deviceModel) : null;
    const releases = await firmwareService.listReleases(deviceModel);
    res.json(releases);
}));

// GET /api/firmware/releases/:id - Get release details
router.get('/releases/:id', requireAuth, asyncHandler(async (req, res) => {
    const release = await firmwareService.getReleaseById(req.params.id);
    if (!release) {
        return res.status(404).json({ error: 'Bản phát hành firmware không tồn tại.' });
    }
    res.json(release);
}));

// =========================================================
// 2. OTA DEPLOYMENT CAMPAIGNS API (ADMIN ONLY)
// =========================================================

// POST /api/ota/deployments - Launch a new OTA Deployment campaign
router.post('/deployments', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const { releaseId, targetDeviceIds } = req.body;

    if (!releaseId) {
        return res.status(400).json({ error: 'Vui lòng chỉ định releaseId cần triển khai.' });
    }

    if (!targetDeviceIds || (!Array.isArray(targetDeviceIds) && targetDeviceIds !== 'ALL')) {
        return res.status(400).json({ error: 'Vui lòng cung cấp danh sách targetDeviceIds hoặc "ALL".' });
    }

    const result = await otaService.createDeployment({
        releaseId,
        targetDeviceIds,
        userId: req.auth.user.id
    });

    res.status(201).json({
        ok: true,
        message: `Đã phát động chiến dịch OTA tới ${result.jobs.length} thiết bị thành công!`,
        deployment: result.deployment,
        jobs: result.jobs
    });
}));

// GET /api/ota/deployments - List all deployments
router.get('/deployments', requireAuth, asyncHandler(async (_req, res) => {
    const deployments = await otaService.listDeployments();
    res.json(deployments);
}));

// GET /api/ota/deployments/:id - Get detailed deployment with all device jobs
router.get('/deployments/:id', requireAuth, asyncHandler(async (req, res) => {
    const details = await otaService.getDeploymentDetails(req.params.id);
    if (!details) {
        return res.status(404).json({ error: 'Chiến dịch OTA không tồn tại.' });
    }
    res.json(details);
}));

// POST /api/ota/deployments/:id/cancel - Safe Cancel of an ongoing deployment
router.post('/deployments/:id/cancel', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const result = await otaService.cancelDeployment(req.params.id);
    res.json(result);
}));

// POST /api/ota/device-jobs/:id/retry - Retry a failed/timed-out device job
router.post('/device-jobs/:id/retry', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const result = await otaService.retryDeviceJob(req.params.id);
    res.json(result);
}));

module.exports = router;
