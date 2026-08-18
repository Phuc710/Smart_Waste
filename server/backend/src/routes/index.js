'use strict';

const express = require('express');
const router = express.Router();

const authRoutes = require('./authRoutes');
const employeeRoutes = require('./employeeRoutes');
const incidentRoutes = require('./incidentRoutes');
const mapRoutes = require('./mapRoutes');
const binRoutes = require('./binRoutes');
const dispatchRoutes = require('./dispatchRoutes');
const mobileRoutes = require('./mobileRoutes');
const dashboardRoutes = require('./dashboardRoutes');
const settingsRoutes = require('./settingsRoutes');
const firmwareRoutes = require('./firmwareRoutes');

router.use('/auth', authRoutes);
router.use('/', employeeRoutes);
router.use('/incidents', incidentRoutes);
router.use('/map', mapRoutes);
router.use('/', binRoutes);
router.use('/dispatch', dispatchRoutes);
router.use('/mobile', mobileRoutes);
router.use('/', dashboardRoutes);
router.use('/', settingsRoutes);
router.use('/firmware', firmwareRoutes);
router.use('/ota', firmwareRoutes);

module.exports = router;
