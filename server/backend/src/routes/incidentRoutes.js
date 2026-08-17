'use strict';

const express = require('express');
const router = express.Router();
const { requireAuth, requireAdmin } = require('../middleware/auth');
const incidentService = require('../services/incidentService');
const { asyncHandler } = require('../middleware/errorHandler');

// POST /api/incidents/uploads - Reserve a signed, short-lived JPEG upload URL.
router.post('/uploads', requireAuth, asyncHandler(async (req, res) => {
    const deviceId = String(req.body.deviceId || req.body.device_id || '').trim();
    const reason = String(req.body.issueType || req.body.reason || '').trim();
    const description = String(req.body.description || '');
    if (!deviceId || !reason) {
        return res.status(400).json({ error: 'Thiếu mã thùng rác hoặc loại sự cố.' });
    }
    const upload = await incidentService.prepareIncidentImageUpload({
        tokenHash: req.auth.tokenHash,
        deviceId,
        reason,
        description
    });
    res.status(201).json({ ok: true, upload });
}));

// POST /api/incidents/uploads/:uploadId/complete - Verify storage object and create the report.
router.post('/uploads/:uploadId/complete', requireAuth, asyncHandler(async (req, res) => {
    if (!/^[a-f0-9-]{36}$/i.test(String(req.params.uploadId || ''))) {
        return res.status(400).json({ error: 'Phiên tải ảnh không hợp lệ.' });
    }
    const result = await incidentService.finalizeIncidentImageUpload(req.auth.tokenHash, req.params.uploadId);
    res.status(201).json({ ok: true, result: Array.isArray(result) ? result[0] : result });
}));

// GET /api/incidents/my - List current employee's reported incidents
router.get('/my', requireAuth, asyncHandler(async (req, res) => {
    const reports = await incidentService.getMyIncidents(req.auth.user.id);
    res.json({ ok: true, reports });
}));

// GET /api/incidents/my/:reportId/image - Signed image redirection for current employee
router.get('/my/:reportId/image', requireAuth, asyncHandler(async (req, res) => {
    const reportId = String(req.params.reportId || '');
    const imageUrl = await incidentService.getIncidentImageRedirect(req.auth.user.id, reportId);
    if (!imageUrl) {
        return res.status(404).send('Ảnh minh chứng không tồn tại hoặc hết hạn.');
    }
    res.setHeader('Cache-Control', 'private, no-store, max-age=0');
    res.redirect(302, imageUrl);
}));

// POST /api/incidents - Report a new incident (Staff & Admin)
router.post('/', requireAuth, asyncHandler(async (req, res) => {
    const { deviceId, device_id, issueType, reason, description, photoUrl, photo_url } = req.body;
    const targetDeviceId = deviceId || device_id;
    const targetReason = issueType || reason || 'Sự cố khác';
    const targetPhoto = photoUrl || photo_url || null;

    if (!targetDeviceId) {
        return res.status(400).json({ error: 'Vui lòng cung cấp mã thiết bị / thùng rác.' });
    }

    const report = await incidentService.createIncident({
        deviceId: targetDeviceId,
        employeeId: req.auth.user.id,
        employeeName: req.auth.user.full_name || req.auth.user.username,
        reason: targetReason,
        description: description || '',
        photoUrl: targetPhoto
    });

    res.status(201).json({ ok: true, message: 'Báo cáo sự cố đã được gửi thành công!', report });
}));

// GET /api/incidents - List all incidents (Admin only)
router.get('/', requireAuth, requireAdmin, asyncHandler(async (_req, res) => {
    const reports = await incidentService.getIncidents();
    res.json({ ok: true, reports });
}));

// PATCH /api/incidents/:id/status - Update incident status (Admin only)
router.patch('/:id/status', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const updated = await incidentService.updateIncidentStatus(req.params.id, req.body.status);
    res.json({ ok: true, report: updated });
}));

module.exports = router;
