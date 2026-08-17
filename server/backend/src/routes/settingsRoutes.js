'use strict';

const express = require('express');
const router = express.Router();
const { requireAuth, requireAdmin } = require('../middleware/auth');
const configService = require('../services/configService');
const { asyncHandler } = require('../middleware/errorHandler');

// GET /api/settings - Lấy toàn bộ thông số cài đặt hệ thống
router.get('/settings', requireAuth, asyncHandler(async (_req, res) => {
    const settings = configService.getAllConfig();
    res.json({ ok: true, settings });
}));

// PATCH /api/settings - Cập nhật thông số hệ thống và lưu vào CSDL (Yêu cầu quyền Admin)
router.patch('/settings', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const updated = await configService.updateConfig(req.body);
    res.json({ ok: true, message: 'Đã cập nhật cài đặt hệ thống thành công!', settings: updated });
}));

// POST /api/settings/reset - Khôi phục cài đặt về mặc định từ .env (Yêu cầu quyền Admin)
router.post('/settings/reset', requireAuth, requireAdmin, asyncHandler(async (_req, res) => {
    const defaults = await configService.resetConfig();
    res.json({ ok: true, message: 'Đã khôi phục thông số về mặc định ban đầu.', settings: defaults });
}));

module.exports = router;
