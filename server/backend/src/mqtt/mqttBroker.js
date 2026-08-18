'use strict';

const Aedes = require('aedes');
const net = require('net');
const env = require('../config/env');
const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const binService = require('../services/binService');
const jobsDb = require('../services/jobsDb');
const otaService = require('../services/otaService');

function initMqttBroker() {
    const aedes = new Aedes();
    const mqttServer = net.createServer(aedes.handle);

    stateStore.setAedes(aedes);

    // 1. MQTT Client Authentication Hook
    aedes.authenticate = (client, username, password, callback) => {
        if (!client) return callback(null, true);

        const clientId = String(client.id || '');
        if (!/^[A-Za-z0-9_-]{1,64}$/.test(clientId)) {
            const error = new Error('Invalid Client ID format');
            error.returnCode = 2; // Bad identifier
            logger.warn('MQTT Auth Rejected', `Invalid client ID: ${clientId}`);
            return callback(error, false);
        }

        if (process.env.MQTT_USERNAME && process.env.MQTT_PASSWORD) {
            const user = username ? username.toString() : '';
            const pass = password ? password.toString() : '';
            if (user !== process.env.MQTT_USERNAME || pass !== process.env.MQTT_PASSWORD) {
                const error = new Error('Bad username or password');
                error.returnCode = 4;
                logger.warn('MQTT Auth Rejected', `Invalid credentials for client: ${clientId}`);
                return callback(error, false);
            }
        }
        callback(null, true);
    };

    // 2. MQTT Topic Authorization (ACL) Hook
    aedes.authorizePublish = (client, packet, callback) => {
        // Allow internal backend publishers (null client)
        if (!client) return callback(null);

        const clientId = String(client.id || '');
        // An IoT device can ONLY publish telemetry to wastebin/{clientId}/*
        if (packet.topic.startsWith(`wastebin/${clientId}/`)) {
            return callback(null);
        }
        logger.warn('MQTT ACL Blocked Pub', `Client ${clientId} tried to publish to unauthorized topic: ${packet.topic}`);
        callback(new Error(`Unauthorized publish to topic: ${packet.topic}`));
    };

    aedes.authorizeSubscribe = (client, sub, callback) => {
        if (!client) return callback(null, sub);

        const clientId = String(client.id || '');
        // An IoT device can ONLY subscribe to its own command topic wastebin/{clientId}/#
        if (sub.topic.startsWith(`wastebin/${clientId}/`)) {
            return callback(null, sub);
        }
        logger.warn('MQTT ACL Blocked Sub', `Client ${clientId} tried to subscribe to unauthorized topic: ${sub.topic}`);
        callback(new Error(`Unauthorized subscription to topic: ${sub.topic}`));
    };

    aedes.on('client', (client) => {
        logger.info('MQTT', 'Client connected:', client?.id || 'unknown');
    });

    aedes.on('clientDisconnect', (client) => {
        logger.info('MQTT', 'Client disconnected:', client?.id || 'unknown');
    });

    aedes.on('publish', async (packet, client) => {
        if (!client || !packet.topic.startsWith('wastebin/')) return;
        
        const parts = packet.topic.split('/');
        const binId = parts[1];
        if (!binId) return;

        // Handle OTA Status & Progress Telemetry
        if (packet.topic.endsWith('/ota/status')) {
            try {
                const data = JSON.parse(packet.payload.toString());
                otaService.processDeviceOtaStatus(binId, data).catch(err => {
                    logger.error('MQTT OTA Status Error', `${binId}: ${err.message}`);
                });
            } catch (err) {
                logger.error('MQTT OTA Status Parse', err.message);
            }
            return;
        }

        if (!packet.topic.endsWith('/status')) return;

        try {
            const data = JSON.parse(packet.payload.toString());
            data.lastSeen = new Date().toISOString();
            data.isOnline = true;
            
            binService.acknowledgeBinCommand(binId, data).catch((error) => {
                logger.error('MQTT ACK', error.message);
            });

            const levelPercent = Number(data.levelPercent || data.level_percent || 0);
            
            // Overfill alert logic
            if (levelPercent >= 85) {
                let nearestTruck = null;
                let minDist = Infinity;
                const binObj = stateStore.latestBins.get(binId) || {};
                const bLat = Number(binObj.latitude);
                const bLng = Number(binObj.longitude);

                if (Number.isFinite(bLat) && Number.isFinite(bLng)) {
                    let activeJobs = [];
                    try { activeJobs = await jobsDb.getActiveJobs(); } catch (_) {}

                    for (const loc of stateStore.employeeLocationsCache.values()) {
                        const empLat = Number(loc.latitude);
                        const empLng = Number(loc.longitude);
                        const empId = String(loc.employee_id || loc.id || '').toLowerCase();
                        
                        const isBusy = activeJobs.some(j => 
                            String(j.employee_id).toLowerCase() === empId && 
                            ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(j.status)
                        );

                        if (!isBusy && Number.isFinite(empLat) && Number.isFinite(empLng)) {
                            const dist = Math.hypot(bLat - empLat, bLng - empLng) * 111;
                            if (dist < minDist) {
                                minDist = dist;
                                nearestTruck = {
                                    employee_id: loc.employee_id || loc.id,
                                    driverName: loc.full_name || loc.username || 'Tài xế',
                                    distanceKm: Number(dist.toFixed(1))
                                };
                            }
                        }
                    }
                }

                const alert = {
                    binId,
                    name: binObj.name || binId,
                    location: binObj.location || '',
                    levelPercent,
                    suggestedNearestTruck: nearestTruck,
                    occurredAt: new Date().toISOString()
                };
                stateStore.emitTo('admins', 'binOverfullAlert', alert);
                if (nearestTruck?.employee_id) {
                    stateStore.emitTo(`employee_${nearestTruck.employee_id}`, 'binOverfullAlert', alert);
                }
            }

            // Throttling telemetry DB history saves to 30 seconds
            const now = Date.now();
            const shouldSaveHistory = now - (stateStore.historyTimers.get(binId) || 0) >= 30000;
            if (shouldSaveHistory) {
                stateStore.historyTimers.set(binId, now);
            }

            saveTelemetry(binId, data, shouldSaveHistory);
        } catch (error) {
            logger.error('MQTT', 'Invalid JSON on', packet.topic, error.message);
        }
    });

    mqttServer.on('error', (err) => {
        if (err.code === 'EADDRINUSE') {
            logger.error('MQTT', `Port ${env.MQTT_PORT} is already in use by another process.`);
        } else {
            logger.error('MQTT', 'Server error:', err);
        }
    });

    return { aedes, mqttServer };
}

function saveTelemetry(binId, data, shouldSaveHistory) {
    binService.saveBin(binId, data).then(() => {
        if (shouldSaveHistory) {
            binService.saveEvent(binId, 'telemetry', data);
        }
    }).catch(err => logger.error('Save Bin', err.message));
}

module.exports = {
    initMqttBroker
};
