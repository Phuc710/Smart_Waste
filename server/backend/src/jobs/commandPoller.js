'use strict';

const logger = require('../core/logger');
const stateStore = require('../core/stateStore');
const { supabaseRequest } = require('../core/supabase');
const { VALID_BIN_ACTIONS } = require('../config/constants');
const binService = require('../services/binService');

async function pollDatabaseCommands() {
    if (stateStore.commandPollInFlight) return;
    stateStore.commandPollInFlight = true;

    try {
        const rows = await supabaseRequest('smart_bins?select=*&command_status=in.(pending,sent)');
        for (const row of rows || []) {
            const binId = String(row.device_id || '');
            const action = String(row.last_command || '').toUpperCase();
            const commandAt = String(row.last_command_at || '');
            const commandTime = Date.parse(commandAt);

            if (!Number.isFinite(commandTime) || !VALID_BIN_ACTIONS.includes(action)) continue;
            
            const processedTime = Date.parse(stateStore.processedCommandTimes.get(binId) || '');
            if (Number.isFinite(processedTime) && Math.abs(processedTime - commandTime) < 5000) continue;

            const tracked = stateStore.pendingCommands.get(binId);
            if (tracked) {
                const trackedTime = Date.parse(tracked.commandId);
                if (Number.isFinite(trackedTime) && Math.abs(trackedTime - commandTime) < 5000 && Date.now() - tracked.lastPublishedAt < 5000) {
                    continue;
                }
            }

            stateStore.latestBins.set(binId, { ...(stateStore.latestBins.get(binId) || {}), ...row });

            try {
                await binService.executeBinCommand(binId, action, commandAt);
                logger.info('Supabase command', `${binId}: ${action}`);
            } catch (error) {
                logger.error('Supabase command', error.message);
            }
        }
    } catch (error) {
        stateStore.reportDatabaseStatus(false, error);
    } finally {
        stateStore.commandPollInFlight = false;
    }
}

function startCommandPoller(intervalMs = 400) {
    const timer = setInterval(pollDatabaseCommands, intervalMs);
    timer.unref();
    return timer;
}

module.exports = {
    startCommandPoller,
    pollDatabaseCommands
};
