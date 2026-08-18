'use strict';

const { Server } = require('socket.io');
const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const { parseCookies, extractToken, getSessionUser } = require('../middleware/auth');
const { SESSION_COOKIE_NAME, VALID_BIN_ACTIONS, ACTION_LABELS } = require('../config/constants');
const binService = require('../services/binService');

function initSocketServer(httpServer) {
    const io = new Server(httpServer, {
        cors: { origin: true, credentials: true }
    });

    stateStore.setIO(io);

    // Authentication middleware
    io.use(async (socket, next) => {
        try {
            let rawToken = extractToken(socket.handshake);
            if (!rawToken && socket.handshake.auth && socket.handshake.auth.token) {
                rawToken = socket.handshake.auth.token;
            }
            const user = await getSessionUser(rawToken);
            if (!user) return next(new Error('unauthorized'));
            socket.user = user;
            next();
        } catch (_error) {
            next(new Error('unauthorized'));
        }
    });

    io.on('connection', (socket) => {
        logger.info('Socket.IO', 'Client connected:', socket.id, `(${socket.user.username})`);
        
        if (socket.user.role === 'admin') {
            socket.join('admins');
        }
        
        socket.join(`employee_${socket.user.id}`);

        socket.emit('initialBins', [...stateStore.latestBins.values()]);
        socket.emit('databaseStatus', { 
            connected: !stateStore.lastDatabaseError, 
            message: stateStore.lastDatabaseError 
        });

        binService.loadBinsFromSupabase().then((rows) => {
            if (rows) socket.emit('binsSnapshot', rows);
        });

        socket.on('lidCommand', async (payload = {}, acknowledge = () => {}) => {
            const binId = String(payload.binId || '');
            const action = String(payload.action || '').toUpperCase();

            if (!/^[A-Za-z0-9_-]{1,64}$/.test(binId) || !VALID_BIN_ACTIONS.includes(action)) {
                return acknowledge({ ok: false, message: 'Lệnh hoặc mã thiết bị không hợp lệ.' });
            }

            try {
                const commandId = new Date().toISOString();
                const waitPromise = stateStore.waitForDeviceAck(binId, commandId, 4500);

                await binService.executeBinCommand(binId, action, commandId);
                const result = await waitPromise;

                if (result.ok) {
                    const actionLabel = ACTION_LABELS[action] || action;
                    acknowledge({ 
                        ok: true, 
                        message: `Thiết bị #${binId} đã thực thi "${actionLabel}" thành công!`,
                        bin: result.data 
                    });
                } else {
                    acknowledge({ 
                        ok: false, 
                        message: `Thiết bị #${binId} không phản hồi (Ngoại tuyến hoặc timeout).` 
                    });
                }
            } catch (error) {
                logger.error('MQTT Command', 'Cannot publish command:', error.message);
                acknowledge({ ok: false, message: 'Lỗi gửi lệnh: ' + error.message });
            }
        });

        socket.on('disconnect', () => {
            // Client disconnected
        });
    });

    return io;
}

module.exports = {
    initSocketServer
};
