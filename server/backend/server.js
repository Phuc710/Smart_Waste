'use strict';

// Set Vietnam standard timezone (UTC+7) for entire server process
process.env.TZ = 'Asia/Ho_Chi_Minh';

process.on('unhandledRejection', (reason) => {
    console.error('[Unhandled Rejection]', reason && reason.message ? reason.message : reason);
});

process.on('uncaughtException', (err) => {
    console.error('[Uncaught Exception]', err && err.message ? err.message : err);
});

const express = require('express');
const http = require('http');
const path = require('path');
const fs = require('fs');

const env = require('./src/config/env');
const logger = require('./src/core/logger');
const stateStore = require('./src/core/stateStore');
const { securityHeaders } = require('./src/middleware/security');
const { errorHandler } = require('./src/middleware/errorHandler');
const apiRoutes = require('./src/routes');

const { initSocketServer } = require('./src/websocket/socketServer');
const { initMqttBroker } = require('./src/mqtt/mqttBroker');
const { startCommandPoller } = require('./src/jobs/commandPoller');
const { startJobMonitorCron } = require('./src/jobs/jobMonitorCron');
const { startBinLivenessWorker } = require('./src/jobs/binLivenessWorker');

const binService = require('./src/services/binService');
const { supabaseRequest } = require('./src/core/supabase');

const app = express();
const server = http.createServer(app);

// 1. Core Middlewares
app.disable('x-powered-by');
app.use(securityHeaders);
app.use(express.json());

// 2. Static Frontend Assets
const distPath = fs.existsSync(path.join(__dirname, '../frontend/dist'))
    ? path.join(__dirname, '../frontend/dist')
    : path.join(__dirname, 'dist');
app.use(express.static(distPath));

// 3. API Routes
app.use('/api', apiRoutes);

// 4. SPA HTML5 Fallback
app.use((req, res, next) => {
    if (req.method !== 'GET' || req.path.startsWith('/api') || req.path.startsWith('/socket.io')) {
        return next();
    }
    const indexFile = path.join(distPath, 'index.html');
    if (fs.existsSync(indexFile)) {
        return res.sendFile(indexFile);
    }
    next();
});

// 5. Global Error Handler
app.use(errorHandler);

// 6. Realtime Communication Servers
const io = initSocketServer(server);
const { mqttServer } = initMqttBroker();

const configService = require('./src/services/configService');

// 7. System Bootstrap Sequence
async function bootstrap() {
    logger.info('Bootstrap', 'Khởi tạo dịch vụ SmartWaste Backend Server...');

    // Load dynamic system config
    await configService.loadConfig();

    // Load initial smart bins
    const initialBins = await binService.loadBinsFromSupabase();
    for (const bin of initialBins || []) {
        if (bin.last_command_at && bin.command_status !== 'pending') {
            stateStore.processedCommandTimes.set(bin.device_id, bin.last_command_at);
        }
    }

    // Load employee locations
    try {
        const rows = await supabaseRequest('employee_locations?select=*');
        for (const loc of rows || []) {
            if (loc.employee_id) {
                stateStore.employeeLocationsCache.set(loc.employee_id, loc);
            }
        }
    } catch (_) { }

    // Start background jobs & cron
    startCommandPoller(400);
    startJobMonitorCron(30_000);
    startBinLivenessWorker(5000);

    // Start listeners (bind to 0.0.0.0 to allow Android Emulator 10.0.2.2 and LAN devices)
    mqttServer.listen(env.MQTT_PORT, '0.0.0.0', () => {
        logger.info('MQTT', `Broker listening on port ${env.MQTT_PORT}`);
    });

    server.listen(env.HTTP_PORT, '0.0.0.0', () => {
        logger.info('HTTP', `Admin & API Server running at http://0.0.0.0:${env.HTTP_PORT} (http://localhost:${env.HTTP_PORT})`);
    });
}

bootstrap().catch((error) => {
    logger.error('Startup', error);
    process.exitCode = 1;
});

// Graceful Shutdown
function gracefulShutdown(signal) {
    logger.info('Shutdown', `Received ${signal}, closing server gracefully...`);
    server.close(() => {
        mqttServer.close(() => {
            logger.info('Shutdown', 'Closed all network servers. Exiting.');
            process.exit(0);
        });
    });
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

module.exports = { app, server, io };
