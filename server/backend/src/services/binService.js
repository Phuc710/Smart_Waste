'use strict';

const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const { supabaseRequest } = require('../core/supabase');

const { isBinOnline } = require('../jobs/binLivenessWorker');

async function loadBinsFromSupabase() {
    try {
        const rows = await supabaseRequest('smart_bins?select=*&order=last_seen.desc');
        for (const row of rows || []) {
            const online = isBinOnline(row);
            const enriched = { ...row, is_online: online };
            stateStore.latestBins.set(row.device_id, enriched);
        }
        stateStore.reportDatabaseStatus(true);
        return [...stateStore.latestBins.values()];
    } catch (error) {
        stateStore.reportDatabaseStatus(false, error);
        return null;
    }
}

async function saveBin(binId, data) {
    const previous = stateStore.latestBins.get(binId) || {};
    const row = {
        device_id: binId,
        state: String(data.state || 'CLOSED').toUpperCase(),
        control_mode: String(data.controlMode || 'AUTO').toUpperCase(),
        servo_angle: Number(data.servoAngle ?? (data.state === 'OPEN' ? 90 : 0)),
        dist_user: Number(data.distUser || 0),
        dist_level: Number(data.distLevel || 0),
        level_percent: Math.max(0, Math.min(100, Number(data.levelPercent || 0))),
        collection_paused: Boolean(data.collectionPaused),
        ip_address: data.ipAddress || null,
        is_online: true,
        last_seen: new Date().toISOString()
    };
    
    if (typeof data.name === 'string' && data.name.trim()) {
        row.name = data.name.trim().slice(0, 120);
    }
    if (typeof data.location === 'string' && data.location.trim()) {
        row.location = data.location.trim().slice(0, 240);
    }
    
    const latitude = Number(data.latitude);
    const longitude = Number(data.longitude);
    if (data.latitude !== null && data.latitude !== undefined && data.latitude !== '' &&
        data.longitude !== null && data.longitude !== undefined && data.longitude !== '' &&
        Number.isFinite(latitude) && latitude >= -90 && latitude <= 90 &&
        Number.isFinite(longitude) && longitude >= -180 && longitude <= 180) {
        row.latitude = latitude;
        row.longitude = longitude;
    }
    
    stateStore.latestBins.set(binId, { ...previous, ...row });
    
    try {
        await supabaseRequest('smart_bins?on_conflict=device_id', {
            method: 'POST',
            headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
            body: JSON.stringify(row)
        });
        stateStore.reportDatabaseStatus(true);
        return row;
    } catch (error) {
        stateStore.reportDatabaseStatus(false, error);
        return row;
    }
}

async function saveEvent(binId, type, payload) {
    const eventObj = {
        id: Date.now(),
        device_id: binId,
        event_type: type,
        payload,
        created_at: new Date().toISOString()
    };
    stateStore.emit('newEvent', eventObj);
    
    try {
        await supabaseRequest('bin_events', {
            method: 'POST',
            headers: { Prefer: 'return=minimal' },
            body: JSON.stringify({ device_id: binId, event_type: type, payload })
        });
        stateStore.reportDatabaseStatus(true);
    } catch (error) {
        stateStore.reportDatabaseStatus(false, error);
    }
}

function publishCommand(binId, action, commandId) {
    return new Promise((resolve, reject) => {
        const aedes = stateStore.getAedes();
        if (!aedes) return resolve();
        
        const packet = {
            topic: `wastebin/${binId}/command`,
            payload: Buffer.from(JSON.stringify({ action, commandId })),
            qos: 1,
            // Retain so ESP32 receives command even if briefly disconnected
            retain: true
        };
        aedes.publish(packet, (error) => error ? reject(error) : resolve());
    });
}

function clearRetainedCommand(binId) {
    return new Promise((resolve) => {
        const aedes = stateStore.getAedes();
        if (!aedes) return resolve();
        
        aedes.publish({
            topic: `wastebin/${binId}/command`,
            payload: Buffer.alloc(0),
            qos: 1,
            retain: true
        }, () => resolve());
    });
}

async function executeBinCommand(binId, action, commandId = new Date().toISOString()) {
    const current = stateStore.latestBins.get(binId) || { device_id: binId };
    
    // Kiểm tra tính xác thực 2 chiều: Thiết bị BẮT BUỘC phải Online mới nhận lệnh
    if (!isBinOnline(current)) {
        const err = new Error(`Thiết bị #${binId} hiện đang ngoại tuyến (Offline), không thể thực thi lệnh.`);
        err.statusCode = 503;
        err.isOffline = true;
        throw err;
    }

    const patch = {
        ...current,
        last_command: action,
        last_command_at: commandId,
        command_status: 'sent',
        command_processed_at: null
    };
    
    stateStore.latestBins.set(binId, patch);
    stateStore.pendingCommands.set(binId, { action, commandId, lastPublishedAt: Date.now() });
    stateStore.emit('binData', { binId, data: patch });

    await supabaseRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}`, {
        method: 'PATCH',
        headers: { Prefer: 'return=minimal' },
        body: JSON.stringify({
            last_command: action,
            last_command_at: commandId,
            command_status: 'sent',
            command_processed_at: null
        })
    }).catch((error) => stateStore.reportDatabaseStatus(false, error));

    // Phát lệnh qua MQTT tới thiết bị thật
    await publishCommand(binId, action, commandId);

    // Không dùng fake simulation! Chỉ có ACK từ thiết bị thật hoặc MQTT simulator mới hoàn tất lệnh
    return patch;
}

async function acknowledgeBinCommand(binId, data) {
    const tracked = stateStore.pendingCommands.get(binId);
    let commandId = String(data.commandAckId || '');
    let action = String(data.commandAckAction || '').toUpperCase();

    // Legacy firmware fallback
    if ((!commandId || !action) && tracked) {
        const state = String(data.state || '').toUpperCase();
        const mode = String(data.controlMode || data.control_mode || '').toUpperCase();
        const paused = Boolean(data.collectionPaused ?? data.collection_paused);
        const applied = (tracked.action === 'OPEN' && state === 'OPEN') ||
            (tracked.action === 'CLOSE' && state === 'CLOSED') ||
            (tracked.action === 'PAUSE' && paused) ||
            (tracked.action === 'RESUME' && !paused && mode === 'AUTO') ||
            (tracked.action === 'AUTO' && mode === 'AUTO') ||
            (tracked.action === 'MANUAL' && mode === 'MANUAL');
        if (applied) {
            commandId = tracked.commandId;
            action = tracked.action;
        }
    }
    if (!commandId || !['OPEN', 'CLOSE', 'AUTO', 'MANUAL', 'PAUSE', 'RESUME'].includes(action)) return;

    if (!tracked && stateStore.processedCommandTimes.get(binId) === commandId) return;

    const current = stateStore.latestBins.get(binId) || {};
    const expectedId = String(tracked?.commandId || current.last_command_at || '');
    if (expectedId && commandId && expectedId !== commandId) {
        const expTime = Date.parse(expectedId);
        const cmdTime = Date.parse(commandId);
        if (Number.isFinite(expTime) && Number.isFinite(cmdTime) && Math.abs(expTime - cmdTime) > 5000) {
            return;
        }
    }

    const processedAt = new Date().toISOString();
    stateStore.pendingCommands.delete(binId);
    const updated = { ...current, ...data, command_status: 'done', command_processed_at: processedAt };
    stateStore.latestBins.set(binId, updated);
    stateStore.emit('binData', { binId, data: updated });
    stateStore.resolveDeviceAck(binId, commandId, { ok: true, data: updated, action });
    logger.info('MQTT ACK', `${binId}: ${action}`);

    clearRetainedCommand(binId).catch(() => {});
    Promise.all([
        supabaseRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}`, {
            method: 'PATCH',
            headers: { Prefer: 'return=minimal' },
            body: JSON.stringify({ command_status: 'done', command_processed_at: processedAt })
        }).catch((error) => stateStore.reportDatabaseStatus(false, error)),
        saveEvent(binId, 'command', { action, acknowledged: true, commandId })
    ]).catch((err) => logger.error('ACK DB save', err.message));
}

async function updateBinDetails(binId, body) {
    const patch = {};
    if (typeof body.name === 'string') patch.name = body.name.trim().slice(0, 120);
    if (typeof body.location === 'string') patch.location = body.location.trim().slice(0, 240);
    if (body.latitude !== undefined && body.latitude !== null && body.latitude !== '') {
        const lat = Number(body.latitude);
        if (Number.isFinite(lat) && lat >= -90 && lat <= 90) patch.latitude = lat;
    }
    if (body.longitude !== undefined && body.longitude !== null && body.longitude !== '') {
        const lng = Number(body.longitude);
        if (Number.isFinite(lng) && lng >= -180 && lng <= 180) patch.longitude = lng;
    }

    await supabaseRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}`, {
        method: 'PATCH',
        headers: { Prefer: 'return=minimal' },
        body: JSON.stringify(patch)
    });
    
    const current = stateStore.latestBins.get(binId) || { device_id: binId };
    const updated = { ...current, ...patch };
    stateStore.latestBins.set(binId, updated);
    stateStore.emit('binData', { binId, data: updated });
    return updated;
}

async function updateBinCoordinates(binId, latitude, longitude) {
    await supabaseRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}`, {
        method: 'PATCH',
        headers: { Prefer: 'return=minimal' },
        body: JSON.stringify({ latitude, longitude })
    });
    const current = stateStore.latestBins.get(binId);
    if (current) {
        stateStore.latestBins.set(binId, { ...current, latitude, longitude });
    }
}

async function getEvents(deviceId, limit = 100) {
    const safeLimit = Math.max(1, Math.min(200, Number(limit) || 100));
    let resource = `bin_events?select=id,device_id,event_type,payload,created_at&order=created_at.desc&limit=${safeLimit}`;
    if (deviceId) {
        resource += `&device_id=eq.${encodeURIComponent(deviceId)}`;
    }
    const rows = await supabaseRequest(resource);
    stateStore.reportDatabaseStatus(true);
    return rows || [];
}

module.exports = {
    loadBinsFromSupabase,
    saveBin,
    saveEvent,
    publishCommand,
    clearRetainedCommand,
    executeBinCommand,
    acknowledgeBinCommand,
    updateBinDetails,
    updateBinCoordinates,
    getEvents
};
