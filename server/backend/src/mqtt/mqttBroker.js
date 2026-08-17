'use strict';

const Aedes = require('aedes');
const net = require('net');
const env = require('../config/env');
const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const binService = require('../services/binService');
const jobsDb = require('../services/jobsDb');

function initMqttBroker() {
    const aedes = new Aedes();
    const mqttServer = net.createServer(aedes.handle);

    stateStore.setAedes(aedes);

    aedes.on('client', (client) => {
        logger.info('MQTT', 'Client connected:', client?.id || 'unknown');
    });

    aedes.on('clientDisconnect', (client) => {
        logger.info('MQTT', 'Client disconnected:', client?.id || 'unknown');
    });

    aedes.on('publish', async (packet, client) => {
        if (!client || !packet.topic.startsWith('wastebin/') || !packet.topic.endsWith('/status')) return;
        
        const parts = packet.topic.split('/');
        const binId = parts[1];
        if (!binId) return;

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
