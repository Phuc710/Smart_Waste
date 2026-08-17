'use strict';

const logger = require('./logger');

class StateStore {
    constructor() {
        this.latestBins = new Map();
        this.employeeLocationsCache = new Map();
        this.pendingCommands = new Map();
        this.processedCommandTimes = new Map();
        this.historyTimers = new Map();
        this.signedUrlCache = new Map();
        this.commandWaiters = new Map();
        this.pauseAlertSentSet = new Set();
        
        this.databaseConnected = null;
        this.lastDatabaseError = '';
        this.commandPollInFlight = false;
        
        this._io = null;
        this._aedes = null;
    }

    setIO(ioInstance) {
        this._io = ioInstance;
    }

    getIO() {
        return this._io;
    }

    setAedes(aedesInstance) {
        this._aedes = aedesInstance;
    }

    getAedes() {
        return this._aedes;
    }

    emit(event, ...args) {
        if (this._io) {
            this._io.emit(event, ...args);
        }
    }

    emitTo(room, event, ...args) {
        if (this._io) {
            this._io.to(room).emit(event, ...args);
        }
    }

    reportDatabaseStatus(connected, error) {
        const message = error ? String(error.message || error).slice(0, 180) : '';
        if (!connected && message !== this.lastDatabaseError) {
            logger.error('Database', message);
        }
        const changed = this.databaseConnected !== connected || (!connected && message !== this.lastDatabaseError);
        this.databaseConnected = connected;
        this.lastDatabaseError = connected ? '' : message;
        if (changed) {
            this.emit('databaseStatus', { connected, message });
        }
    }

    waitForDeviceAck(binId, commandId, timeoutMs = 4000) {
        return new Promise((resolve) => {
            if (this.commandWaiters.has(binId)) {
                clearTimeout(this.commandWaiters.get(binId).timer);
            }
            const timer = setTimeout(() => {
                this.commandWaiters.delete(binId);
                resolve({ ok: false, reason: 'timeout' });
            }, timeoutMs);

            this.commandWaiters.set(binId, { commandId, resolve, timer });
        });
    }

    resolveDeviceAck(binId, commandId, result) {
        const waiter = this.commandWaiters.get(binId);
        if (waiter) {
            const waiterTime = Date.parse(waiter.commandId);
            const incomingTime = Date.parse(commandId);
            const isMatch = !commandId || !waiter.commandId || 
                waiter.commandId === commandId || 
                (Number.isFinite(waiterTime) && Number.isFinite(incomingTime) && Math.abs(waiterTime - incomingTime) < 5000);
                
            if (isMatch) {
                clearTimeout(waiter.timer);
                this.commandWaiters.delete(binId);
                waiter.resolve(result);
                return true;
            }
        }
        return false;
    }
}

const stateStore = new StateStore();
module.exports = stateStore;
