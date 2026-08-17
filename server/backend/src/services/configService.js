'use strict';

const fs = require('fs');
const path = require('path');
const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const { supabaseServiceRequest } = require('../core/supabase');

const DATA_DIR = path.resolve(__dirname, '../../data');
const SETTINGS_FILE = path.join(DATA_DIR, 'system_settings.json');

function getDefaultConfig() {
    return {
        fill_threshold_warning: Number(process.env.FILL_THRESHOLD_WARNING) || 70,
        fill_threshold_critical: Number(process.env.FILL_THRESHOLD_CRITICAL) || 85,
        bin_offline_timeout_seconds: Number(process.env.BIN_OFFLINE_TIMEOUT_SECONDS) || 15,
        employee_offline_timeout_seconds: Number(process.env.EMPLOYEE_OFFLINE_TIMEOUT_SECONDS) || 120,
        assign_timeout_minutes: Number(process.env.ASSIGN_TIMEOUT_MINUTES) || 5,
        paused_timeout_minutes: Number(process.env.PAUSED_TIMEOUT_MINUTES) || 30,
        offline_timeout_seconds: Number(process.env.OFFLINE_TIMEOUT_SECONDS) || 300,
        gps_throttle_min_distance: Number(process.env.GPS_THROTTLE_MIN_DISTANCE) || 10,
        auto_assign: process.env.AUTO_ASSIGN === 'true',
        map_provider: 'leaflet',
        routes_provider: 'osrm'
    };
}

let currentConfig = getDefaultConfig();

function ensureDataDir() {
    if (!fs.existsSync(DATA_DIR)) {
        try { fs.mkdirSync(DATA_DIR, { recursive: true }); } catch (_) {}
    }
}

async function loadConfig() {
    ensureDataDir();

    // 1. Thử tải từ file lưu trữ cục bộ
    if (fs.existsSync(SETTINGS_FILE)) {
        try {
            const raw = fs.readFileSync(SETTINGS_FILE, 'utf8');
            const saved = JSON.parse(raw);
            delete saved.google_maps_browser_key;
            delete saved.google_map_id;
            currentConfig = { ...getDefaultConfig(), ...saved };
        } catch (e) {
            logger.error('Config', 'Không đọc được file settings cục bộ:', e.message);
        }
    }

    // 2. Thử đồng bộ từ CSDL Supabase
    try {
        const rows = await supabaseServiceRequest('system_settings?id=eq.default&select=*');
        if (Array.isArray(rows) && rows.length > 0) {
            const dbSettings = rows[0];
            delete dbSettings.id;
            delete dbSettings.created_at;
            delete dbSettings.updated_at;
            delete dbSettings.google_maps_browser_key;
            delete dbSettings.google_map_id;
            currentConfig = { ...currentConfig, ...dbSettings };
        } else {
            await supabaseServiceRequest('system_settings', {
                method: 'POST',
                headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
                body: JSON.stringify({ id: 'default', ...currentConfig })
            }).catch(() => {});
        }
    } catch (_) {
        // Fallback gracefully to JSON & .env
    }

    logger.info('Config', 'Hệ thống đã nạp cấu hình thông số vận hành động (GIS: OpenStreetMap / Leaflet / OSRM).');
    return currentConfig;
}

async function updateConfig(patch = {}) {
    ensureDataDir();

    const updated = { ...currentConfig };
    
    if (patch.fill_threshold_warning !== undefined) {
        updated.fill_threshold_warning = Math.max(1, Math.min(99, Number(patch.fill_threshold_warning) || 70));
    }
    if (patch.fill_threshold_critical !== undefined) {
        updated.fill_threshold_critical = Math.max(1, Math.min(100, Number(patch.fill_threshold_critical) || 85));
    }
    if (patch.bin_offline_timeout_seconds !== undefined) {
        updated.bin_offline_timeout_seconds = Math.max(3, Math.min(3600, Number(patch.bin_offline_timeout_seconds) || 15));
    }
    if (patch.employee_offline_timeout_seconds !== undefined) {
        updated.employee_offline_timeout_seconds = Math.max(10, Math.min(7200, Number(patch.employee_offline_timeout_seconds) || 120));
    }
    if (patch.assign_timeout_minutes !== undefined) {
        updated.assign_timeout_minutes = Math.max(1, Math.min(120, Number(patch.assign_timeout_minutes) || 5));
    }
    if (patch.paused_timeout_minutes !== undefined) {
        updated.paused_timeout_minutes = Math.max(1, Math.min(240, Number(patch.paused_timeout_minutes) || 30));
    }
    if (patch.offline_timeout_seconds !== undefined) {
        updated.offline_timeout_seconds = Math.max(10, Math.min(7200, Number(patch.offline_timeout_seconds) || 300));
    }
    if (patch.gps_throttle_min_distance !== undefined) {
        updated.gps_throttle_min_distance = Math.max(1, Math.min(500, Number(patch.gps_throttle_min_distance) || 10));
    }
    if (patch.auto_assign !== undefined) {
        updated.auto_assign = Boolean(patch.auto_assign);
    }

    currentConfig = updated;

    // Lưu vào file cục bộ
    try {
        fs.writeFileSync(SETTINGS_FILE, JSON.stringify(currentConfig, null, 2), 'utf8');
    } catch (err) {
        logger.error('Config save file', err.message);
    }

    // Lưu vào CSDL Supabase
    try {
        await supabaseServiceRequest('system_settings', {
            method: 'POST',
            headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
            body: JSON.stringify({ id: 'default', ...currentConfig, updated_at: new Date().toISOString() })
        });
    } catch (_) {}

    stateStore.emit('systemSettingsUpdated', currentConfig);
    logger.info('Config', 'Đã cập nhật thông số hệ thống và đồng bộ Realtime.');

    return currentConfig;
}

async function resetConfig() {
    const defaults = getDefaultConfig();
    return updateConfig(defaults);
}

function getConfig(key) {
    return key ? currentConfig[key] : currentConfig;
}

function getAllConfig() {
    return { ...currentConfig };
}

module.exports = {
    loadConfig,
    getConfig,
    getAllConfig,
    updateConfig,
    resetConfig,
    getDefaultConfig
};
