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
        const now = Date.now();
        // 1. Fetch active commands from dedicated queue table
        const rows = await supabaseRequest('device_commands?status=in.(pending,processing,sent)&order=issued_at.asc&limit=10');

        for (const cmd of rows || []) {
            const cmdId = String(cmd.id);
            const binId = String(cmd.device_id);
            const action = String(cmd.action).toUpperCase();
            const expiresTime = Date.parse(cmd.expires_at || '');
            const attempts = Number(cmd.attempts || 0);
            const maxAttempts = Number(cmd.max_attempts || 3);
            const lastAttemptTime = Date.parse(cmd.last_attempt_at || '');

            if (!VALID_BIN_ACTIONS.includes(action)) continue;

            // Check Expiry (TTL)
            if (Number.isFinite(expiresTime) && expiresTime < now) {
                await supabaseRequest(`device_commands?id=eq.${encodeURIComponent(cmdId)}`, {
                    method: 'PATCH',
                    headers: { Prefer: 'return=minimal' },
                    body: JSON.stringify({ status: 'timeout', error_message: 'Command TTL expired before receiving ACK' })
                }).catch(() => { });

                await supabaseRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}&command_status=eq.sent`, {
                    method: 'PATCH',
                    headers: { Prefer: 'return=minimal' },
                    body: JSON.stringify({ command_status: 'failed' })
                }).catch(() => { });

                stateStore.pendingCommands.delete(binId);
                logger.warn('Command Timeout', `Lệnh ${cmdId} cho #${binId} đã hết hạn (TTL).`);
                continue;
            }

            // If command is currently 'sent', check backoff interval before any retry
            if (cmd.status === 'sent') {
                const backoffInterval = Math.min(30000, 5000 * Math.pow(2, attempts - 1));
                if (Number.isFinite(lastAttemptTime) && (now - lastAttemptTime) < backoffInterval) {
                    // Still within wait window for ACK, DO NOT re-publish
                    continue;
                }
            }

            // Check Max Retry Attempts
            if (attempts >= maxAttempts) {
                await supabaseRequest(`device_commands?id=eq.${encodeURIComponent(cmdId)}`, {
                    method: 'PATCH',
                    headers: { Prefer: 'return=minimal' },
                    body: JSON.stringify({ status: 'failed', error_message: `Max retry attempts reached (${maxAttempts})` })
                }).catch(() => { });

                await supabaseRequest(`smart_bins?device_id=eq.${encodeURIComponent(binId)}&command_status=eq.sent`, {
                    method: 'PATCH',
                    headers: { Prefer: 'return=minimal' },
                    body: JSON.stringify({ command_status: 'failed' })
                }).catch(() => { });

                stateStore.pendingCommands.delete(binId);
                logger.error('Command Failed', `Lệnh ${cmdId} cho #${binId} vượt quá ${maxAttempts} lần thử.`);
                continue;
            }

            // Execute retry with atomic attempt count update
            const nextAttempt = attempts + 1;
            const attemptIso = new Date().toISOString();

            await supabaseRequest(`device_commands?id=eq.${encodeURIComponent(cmdId)}`, {
                method: 'PATCH',
                headers: { Prefer: 'return=minimal' },
                body: JSON.stringify({ status: 'processing', attempts: nextAttempt, last_attempt_at: attemptIso })
            }).catch(() => { });

            try {
                await binService.executeBinCommand(binId, action, cmdId, cmd.issued_by);
                logger.info('Command Poller Executed', `${binId}: ${action} (Attempt ${nextAttempt}/${maxAttempts})`);
            } catch (error) {
                logger.error('Command Poller Exec Failed', `${binId}: ${error.message}`);
            }
        }
    } catch (error) {
        stateStore.reportDatabaseStatus(false, error);
    } finally {
        stateStore.commandPollInFlight = false;
    }
}

function startCommandPoller(intervalMs = 1000) {
    const timer = setInterval(pollDatabaseCommands, intervalMs);
    timer.unref();
    return timer;
}

module.exports = {
    startCommandPoller,
    pollDatabaseCommands
};

