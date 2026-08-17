'use strict';

const express = require('express');
const router = express.Router();
const { requireAuth, requireAdmin } = require('../middleware/auth');
const employeeService = require('../services/employeeService');
const incidentService = require('../services/incidentService');
const { asyncHandler } = require('../middleware/errorHandler');

// GET /api/employees - List all employees
router.get('/employees', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const list = await employeeService.listEmployees(req.auth.tokenHash);
    res.json(list);
}));

// POST /api/employees - Create new employee
router.post('/employees', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const fullName = String(req.body.fullName || '').trim();
    const username = String(req.body.username || '').trim().toLowerCase();
    const email = String(req.body.email || '').trim().toLowerCase();
    const password = String(req.body.password || '');
    const role = String(req.body.role || 'staff');

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
        return res.status(400).json({ error: 'Email không hợp lệ.' });
    }

    const employee = await employeeService.createEmployee({
        tokenHash: req.auth.tokenHash,
        fullName,
        username,
        email,
        password,
        role
    });
    
    res.status(201).json({ employee });
}));

// PATCH /api/employees/:id/active - Activate / Deactivate employee
router.patch('/employees/:id/active', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    if (!/^[a-f0-9-]{36}$/i.test(req.params.id) || typeof req.body.isActive !== 'boolean') {
        return res.status(400).json({ error: 'Dữ liệu cập nhật không hợp lệ.' });
    }
    
    await employeeService.setEmployeeActive(req.auth.tokenHash, req.params.id, req.body.isActive);
    res.json({ ok: true });
}));

// DELETE /api/employees/:id - Delete employee
router.delete('/employees/:id', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    if (!/^[a-f0-9-]{36}$/i.test(req.params.id)) {
        return res.status(400).json({ error: 'Mã nhân viên không hợp lệ.' });
    }
    
    const result = await employeeService.deleteEmployee(req.auth.tokenHash, req.params.id, req.auth.user.id);
    res.json(result);
}));

// POST /api/location - Employee GPS update
router.post('/location', requireAuth, asyncHandler(async (req, res) => {
    const latitude = Number(req.body.latitude);
    const longitude = Number(req.body.longitude);
    
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || 
        latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
        return res.status(400).json({ error: 'Tọa độ GPS không hợp lệ.' });
    }

    await employeeService.updateLocation(req.auth.tokenHash, req.auth.user, {
        latitude,
        longitude,
        accuracy: req.body.accuracy,
        heading: req.body.heading,
        speed: req.body.speed
    });
    
    res.json({ ok: true });
}));

// GET /api/employees/:id/incidents - Employee incident reports
router.get('/employees/:id/incidents', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const employeeId = String(req.params.id || '');
    if (!/^[a-f0-9-]{36}$/i.test(employeeId)) {
        return res.status(400).json({ error: 'Mã nhân viên không hợp lệ.' });
    }

    const data = await incidentService.getEmployeeIncidents(employeeId, req.auth.tokenHash);
    res.setHeader('Cache-Control', 'private, no-store, max-age=0');
    res.json(data);
}));

// GET /api/employees/:id/incidents/:reportId/image - Signed image redirection
router.get('/employees/:id/incidents/:reportId/image', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const employeeId = String(req.params.id || '');
    const reportId = String(req.params.reportId || '');
    
    if (!/^[a-f0-9-]{36}$/i.test(employeeId) || !/^\d+$/.test(reportId)) {
        return res.status(400).send('Yêu cầu xem ảnh không hợp lệ.');
    }

    const imageUrl = await incidentService.getIncidentImageRedirect(employeeId, reportId);
    if (!imageUrl) {
        return res.status(404).send('Ảnh minh chứng không còn tồn tại hoặc chưa sẵn sàng.');
    }

    res.setHeader('Cache-Control', 'private, no-store, max-age=0');
    res.redirect(302, imageUrl);
}));

module.exports = router;
