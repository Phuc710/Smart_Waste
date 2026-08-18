'use strict';

const express = require('express');
const router = express.Router();
const { requireAuth, requireAdmin } = require('../middleware/auth');
const binService = require('../services/binService');
const stateStore = require('../core/stateStore');
const { VALID_BIN_ACTIONS } = require('../config/constants');
const { asyncHandler } = require('../middleware/errorHandler');

// GET /api/bins - List all bins
router.get('/bins', requireAuth, asyncHandler(async (_req, res) => {
    const rows = await binService.loadBinsFromSupabase();
    if (!rows) {
        return res.status(502).json({ error: 'Không thể đọc danh sách thùng rác từ Supabase.' });
    }
    res.json(rows);
}));

// PATCH /api/bins/:id - Update bin info
router.patch('/bins/:id', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const binId = String(req.params.id || '');
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(binId)) {
        return res.status(400).json({ error: 'Mã thiết bị không hợp lệ.' });
    }

    const updated = await binService.updateBinDetails(binId, req.body);
    res.json({ ok: true, bin: updated });
}));

// PATCH /api/bins/:id/coordinates - Update bin GPS coordinates
router.patch('/bins/:id/coordinates', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const binId = String(req.params.id || '');
    const latitude = Number(req.body.latitude);
    const longitude = Number(req.body.longitude);

    if (!/^[A-Za-z0-9_-]{1,64}$/.test(binId) || 
        !Number.isFinite(latitude) || !Number.isFinite(longitude) || 
        latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
        return res.status(400).json({ error: 'Mã thiết bị hoặc tọa độ không hợp lệ.' });
    }

    await binService.updateBinCoordinates(binId, latitude, longitude);
    res.json({ ok: true });
}));

const crypto = require('crypto');
const jobsDb = require('../services/jobsDb');

// POST /api/bins/:id/command - Send hardware lid / mode command (Zero-Trust RBAC & Ownership)
router.post('/bins/:id/command', requireAuth, asyncHandler(async (req, res) => {
    const binId = String(req.params.id || '');
    const action = String(req.body.action || '').toUpperCase();

    if (!/^[A-Za-z0-9_-]{1,64}$/.test(binId) || !VALID_BIN_ACTIONS.includes(action)) {
        return res.status(400).json({ error: 'Lệnh hoặc mã thiết bị không hợp lệ.' });
    }

    // Zero-Trust Authorization:
    // 1. Admin has global authority to trigger hardware commands.
    // 2. Staff can ONLY control bins that belong to their currently active IN_PROGRESS collection job.
    if (req.auth.user.role !== 'admin') {
        const activeJob = await jobsDb.getEmployeeActiveJob(req.auth.user.id);
        const isAssigned = activeJob && 
            activeJob.status === 'IN_PROGRESS' && 
            Array.isArray(activeJob.target_bin_ids) && 
            activeJob.target_bin_ids.includes(binId);

        if (!isAssigned) {
            return res.status(403).json({
                error: `FORBIDDEN: Bạn không có quyền điều khiển thùng rác #${binId}. Chỉ được điều khiển thùng rác thuộc ca thu gom đang thực hiện của bạn.`
            });
        }
    }

    const commandId = crypto.randomUUID();
    const waitPromise = stateStore.waitForDeviceAck(binId, commandId, 4500);
    
    await binService.executeBinCommand(binId, action, commandId, req.auth.user.id);
    const result = await waitPromise;
    
    if (result.ok) {
        res.json({ ok: true, bin: result.data, commandId, message: `Thiết bị #${binId} đã thực thi thành công.` });
    } else {
        res.status(504).json({ error: `Thiết bị #${binId} không phản hồi (Ngoại tuyến hoặc timeout).` });
    }
}));

// GET /api/events - History log events
router.get('/events', requireAuth, asyncHandler(async (req, res) => {
    const limit = Math.max(1, Math.min(200, Number.parseInt(req.query.limit, 10) || 100));
    const deviceId = String(req.query.deviceId || '');
    
    if (deviceId && !/^[A-Za-z0-9_-]{1,64}$/.test(deviceId)) {
        return res.status(400).json({ error: 'Mã thiết bị không hợp lệ.' });
    }

    const events = await binService.getEvents(deviceId, limit);
    res.json(events);
}));

module.exports = router;
