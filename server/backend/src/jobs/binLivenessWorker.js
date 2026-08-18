const stateStore = require('../core/stateStore');
const { supabaseRequest } = require('../core/supabase');
const configService = require('../services/configService');

function getBinOfflineThresholdMs() {
    return (configService.getConfig('bin_offline_timeout_seconds') || 15) * 1000;
}

function getEmployeeOfflineThresholdMs() {
    return (configService.getConfig('employee_offline_timeout_seconds') || 120) * 1000;
}

function isBinOnline(bin) {
    if (!bin) return false;
    const seen = bin.last_seen || bin.lastSeen;
    if (!seen) return false;
    const diff = Date.now() - new Date(seen).getTime();
    return Number.isFinite(diff) && diff < getBinOfflineThresholdMs();
}

function isEmployeeOnline(loc) {
    if (!loc || !loc.recorded_at) return false;
    const diff = Date.now() - new Date(loc.recorded_at).getTime();
    return Number.isFinite(diff) && diff < getEmployeeOfflineThresholdMs();
}

function checkLiveness() {
    const now = Date.now();
    const binThresholdMs = getBinOfflineThresholdMs();
    const empThresholdMs = getEmployeeOfflineThresholdMs();
    
    // 1. Quét trạng thái Thùng rác IoT
    for (const [binId, bin] of stateStore.latestBins.entries()) {
        const lastSeenTime = bin.last_seen ? new Date(bin.last_seen).getTime() : 0;
        const currentlyOnline = (now - lastSeenTime) < binThresholdMs;
        
        if (bin.is_online !== currentlyOnline) {
            const updated = { ...bin, is_online: currentlyOnline };
            stateStore.latestBins.set(binId, updated);
            stateStore.emit('binData', { binId, data: updated });
            
            supabaseRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}`, {
                method: 'PATCH',
                headers: { Prefer: 'return=minimal' },
                body: JSON.stringify({ is_online: currentlyOnline })
            }).catch(() => {});
        }
    }

    // 2. Quét trạng thái Nhân viên di động
    for (const [empId, loc] of stateStore.employeeLocationsCache.entries()) {
        const recordedTime = loc.recorded_at ? new Date(loc.recorded_at).getTime() : 0;
        const isOnline = (now - recordedTime) < empThresholdMs;
        if (loc.is_online !== isOnline) {
            const updatedLoc = { ...loc, is_online: isOnline };
            stateStore.employeeLocationsCache.set(empId, updatedLoc);
            stateStore.emitTo('admins', 'employeeLocation', updatedLoc);
        }
    }
}

function startBinLivenessWorker(intervalMs = 3000) {
    const timer = setInterval(checkLiveness, intervalMs);
    timer.unref();
    return timer;
}

module.exports = {
    isBinOnline,
    isEmployeeOnline,
    checkBinsLiveness: checkLiveness,
    startBinLivenessWorker,
    getBinOfflineThresholdMs,
    getEmployeeOfflineThresholdMs
};
