'use strict';

const express = require('express');
const router = express.Router();
const { requireAuth } = require('../middleware/auth');
const jobsDb = require('../services/jobsDb');
const dispatchService = require('../services/dispatchService');
const mobileHomeService = require('../services/mobileHomeService');
const { asyncHandler } = require('../middleware/errorHandler');

// GET /api/mobile/home - Field-driver home data, aggregated server-side from Supabase.
router.get('/home', requireAuth, asyncHandler(async (req, res) => {
    const home = await mobileHomeService.getHome(req.auth.user.id, req.auth.tokenHash);
    res.setHeader('Cache-Control', 'private, no-store, max-age=0');
    res.json(home);
}));

// GET /api/mobile/jobs/active - Current active job for authenticated employee
router.get('/jobs/active', requireAuth, asyncHandler(async (req, res) => {
    const job = await jobsDb.getEmployeeActiveJob(req.auth.user.id);
    const enriched = job ? await jobsDb.attachProgress(job) : null;
    res.json({ job: enriched });
}));

// POST /api/mobile/jobs/self-pick - Staff self-picks bins
router.post('/jobs/self-pick', requireAuth, asyncHandler(async (req, res) => {
    const empId = req.auth.user.id;
    const empName = req.auth.user.full_name || req.auth.user.username || 'Nhân viên thu gom';
    const binIds = Array.isArray(req.body.binIds) ? req.body.binIds.filter(Boolean) : [];

    const newJob = await dispatchService.selfPickJob({ employeeId: empId, employeeName: empName, binIds });
    res.status(201).json({ ok: true, job: newJob });
}));

// POST /api/mobile/jobs/:id/accept - Staff accepts job
router.post('/jobs/:id/accept', requireAuth, asyncHandler(async (req, res) => {
    const enriched = await dispatchService.acceptJob(req.params.id);
    res.json({ ok: true, job: enriched });
}));

// POST /api/mobile/jobs/:id/reject - Staff rejects job
router.post('/jobs/:id/reject', requireAuth, asyncHandler(async (req, res) => {
    const enriched = await dispatchService.rejectJob(req.params.id);
    res.json({ ok: true, job: enriched });
}));

// POST /api/mobile/jobs/:id/start - Staff starts collection route
router.post('/jobs/:id/start', requireAuth, asyncHandler(async (req, res) => {
    const enriched = await dispatchService.startJob(req.params.id);
    res.json({ ok: true, job: enriched });
}));

// POST /api/mobile/jobs/:id/pause - Staff pauses collection
router.post('/jobs/:id/pause', requireAuth, asyncHandler(async (req, res) => {
    const enriched = await dispatchService.pauseJob(req.params.id, req.body.reason);
    res.json({ ok: true, job: enriched });
}));

// POST /api/mobile/jobs/:id/resume - Staff resumes collection
router.post('/jobs/:id/resume', requireAuth, asyncHandler(async (req, res) => {
    const enriched = await dispatchService.resumeJob(req.params.id);
    res.json({ ok: true, job: enriched });
}));

// POST /api/mobile/jobs/:id/collect-bin - Staff marks a bin as collected
router.post('/jobs/:id/collect-bin', requireAuth, asyncHandler(async (req, res) => {
    const { binId, status = 'COLLECTED', note, photoUrl } = req.body;
    const result = await dispatchService.collectBin({
        jobId: req.params.id,
        binId,
        status,
        note,
        photoUrl
    });
    res.json(result);
}));

module.exports = router;
