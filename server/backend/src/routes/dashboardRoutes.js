'use strict';

const express = require('express');
const router = express.Router();
const env = require('../config/env');
const { requireAuth } = require('../middleware/auth');
const stateStore = require('../core/stateStore');
const statsService = require('../services/statsService');
const { asyncHandler } = require('../middleware/errorHandler');

// GET /api/health - Public health check
router.get('/health', (_req, res) => {
    res.json({
        ok: true,
        mqttPort: env.MQTT_PORT,
        devices: stateStore.latestBins.size,
        supabase: stateStore.databaseConnected ? 'connected' : 'error'
    });
});

// GET /api/dashboard/stats - Realtime statistics
router.get('/dashboard/stats', requireAuth, asyncHandler(async (_req, res) => {
    const stats = await statsService.getDashboardStats();
    res.json(stats);
}));

module.exports = router;
