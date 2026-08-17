'use strict';

const express = require('express');
const router = express.Router();
const env = require('../config/env');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const employeeService = require('../services/employeeService');
const { calculateOsrmRoute } = require('../services/routingService');
const { asyncHandler } = require('../middleware/errorHandler');

const configService = require('../services/configService');

// GET /api/map/locations - List employee locations
router.get('/locations', requireAuth, requireAdmin, asyncHandler(async (req, res) => {
    const locations = await employeeService.listLocations(req.auth.tokenHash);
    res.json(locations);
}));

// GET /api/map/config - Map provider config (100% Free OpenStreetMap & Leaflet GIS)
router.get('/config', requireAuth, requireAdmin, (_req, res) => {
    res.json({
        provider: 'leaflet',
        routesProvider: 'osrm',
        tileLayer: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    });
});

// POST /api/map/route - Calculate route
router.post('/route', requireAuth, asyncHandler(async (req, res) => {
    const coordinates = Array.isArray(req.body.coordinates) ? req.body.coordinates : [];
    const valid = coordinates.length >= 2 && coordinates.length <= 20 && coordinates.every((point) =>
        Array.isArray(point) && point.length === 2 && 
        Number.isFinite(Number(point[0])) && Number.isFinite(Number(point[1])) &&
        Number(point[0]) >= -180 && Number(point[0]) <= 180 && 
        Number(point[1]) >= -90 && Number(point[1]) <= 90
    );
    
    if (!valid) {
        return res.status(400).json({ error: 'Cần từ 2 đến 20 tọa độ hợp lệ.' });
    }

    const result = await calculateOsrmRoute(coordinates);
    if (!result) {
        return res.status(502).json({ error: 'Không thể tính toán định tuyến.' });
    }
    
    res.json(result);
}));

module.exports = router;
