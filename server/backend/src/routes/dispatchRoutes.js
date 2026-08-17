'use strict';

const express = require('express');
const router = express.Router();
const { requireAuth, requireAdmin } = require('../middleware/auth');
const jobsDb = require('../services/jobsDb');
const dispatchService = require('../services/dispatchService');
const { asyncHandler } = require('../middleware/errorHandler');

// GET /api/dispatch/active-jobs - Get all active jobs with progress attached
router.get('/active-jobs', requireAuth, asyncHandler(async (_req, res) => {
    const jobs = await jobsDb.getActiveJobs();
    const enriched = await jobsDb.attachProgressBulk(jobs);
    res.json(enriched);
}));

// GET /api/dispatch/history - Get collection history jobs
router.get('/history', requireAuth, asyncHandler(async (req, res) => {
    const limit = Math.max(1, Math.min(200, Number.parseInt(req.query.limit, 10) || 100));
    const jobs = await jobsDb.getHistoryJobs(limit);
    const enriched = await jobsDb.attachProgressBulk(jobs);
    res.json(enriched);
}));

// POST /api/dispatch/assign - Admin assigns collection job
router.post('/assign', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const employeeId = String(req.body.employeeId || '');
    const employeeName = String(req.body.employeeName || 'Nhân viên thu gom');
    const binIds = Array.isArray(req.body.binIds) ? req.body.binIds.filter(Boolean) : [];

    const newJob = await dispatchService.assignJob({ employeeId, employeeName, binIds });
    res.status(201).json({ ok: true, job: newJob });
}));

// POST /api/dispatch/jobs/:id/reassign - Admin reassigns job to another driver
router.post('/jobs/:id/reassign', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const { employeeId, employeeName } = req.body;
    const result = await dispatchService.reassignJob(req.params.id, employeeId, employeeName);
    res.json({ ok: true, ...result });
}));

// POST /api/dispatch/jobs/:id/cancel - Admin cancels job
router.post('/jobs/:id/cancel', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const job = await dispatchService.cancelJob(req.params.id);
    res.json({ ok: true, job });
}));

module.exports = router;
