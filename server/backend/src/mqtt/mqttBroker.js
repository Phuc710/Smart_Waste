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

    // Helper kiểm tra quyền truy cập Topic MQTT theo Zero-Trust Device Isolation
    function isAuthorizedMqttTopic(clientId, topic) {
        if (!topic || typeof topic !== 'string' || !topic.startsWith('wastebin/')) {
            return false;
        }
        const parts = topic.split('/');
        const topicBinId = parts[1];
        if (!topicBinId) return false;

        // 1. Khớp trực tiếp: wastebin/{clientId}/...
        if (topic.startsWith(`wastebin/${clientId}/`)) {
            return true;
        }

        // 2. Khớp với Client ID có tiền tố phần cứng (ESP32-SmartBin-{binId}, ESP32-{binId}, SmartBin-{binId})
        const cleanClientId = clientId
            .replace(/^ESP32[-_]SmartBin[-_]/i, '')
            .replace(/^ESP32[-_]/i, '')
            .replace(/^SmartBin[-_]/i, '');

        if (topicBinId === cleanClientId || clientId.endsWith(topicBinId)) {
            return true;
        }

        return false;
    }

    // 2. MQTT Topic Authorization (ACL) Hook
    aedes.authorizePublish = (client, packet, callback) => {
        // Cho phép backend internal publisher (null client)
        if (!client) return callback(null);

        const clientId = String(client.id || '');
        if (isAuthorizedMqttTopic(clientId, packet.topic)) {
            return callback(null);
        }
        logger.warn('MQTT ACL Blocked Pub', `Client ${clientId} tried to publish to unauthorized topic: ${packet.topic}`);
        callback(new Error(`Unauthorized publish to topic: ${packet.topic}`));
    };

    aedes.authorizeSubscribe = (client, sub, callback) => {
        if (!client) return callback(null, sub);

        const clientId = String(client.id || '');
        if (isAuthorizedMqttTopic(clientId, sub.topic)) {
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
        if (!packet || !packet.topic || typeof packet.topic !== 'string' || !packet.topic.startsWith('wastebin/')) return;
        
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
            const nowIso = new Date().toISOString();
            data.last_seen = nowIso;
            data.lastSeen = nowIso;
            data.is_online = true;
            data.isOnline = true;

            // Cập nhật bộ nhớ RAM stateStore ngay lập tức không chờ I/O
            const existing = stateStore.latestBins.get(binId) || {};
            const enrichedBin = {
                ...existing,
                ...data,
                device_id: binId,
                is_online: true,
                last_seen: nowIso,
                lastSeen: nowIso
            };
            stateStore.latestBins.set(binId, enrichedBin);
            
            // Phát sóng Realtime ngay lập tức tới Web Admin (Dashboard, Map, SmartBins)
            stateStore.emit('binData', { binId, data: enrichedBin });

            binService.acknowledgeBinCommand(binId, data).catch((error) => {
                logger.error('MQTT ACK', error.message);
            });

            const levelPercent = Number(data.levelPercent || data.level_percent || 0);
            
            // Overfill alert logic
            if (levelPercent >= 85) {
                let nearestTruck = null;
                let minDist = Infinity;
                const binObj = stateStore.latestBins.get(binId) || {};
                const bLat = Number(data.latitude ?? binObj.latitude);
                const bLng = Number(data.longitude ?? binObj.longitude);

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
