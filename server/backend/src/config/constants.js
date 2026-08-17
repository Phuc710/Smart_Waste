'use strict';

const SYSTEM_CONFIG = {
    fill_threshold_warning:      Number(process.env.FILL_THRESHOLD_WARNING)  || 70,
    fill_threshold_critical:     Number(process.env.FILL_THRESHOLD_CRITICAL) || 85,
    assign_timeout_minutes:      Number(process.env.ASSIGN_TIMEOUT_MINUTES)  || 5,
    paused_timeout_minutes:      Number(process.env.PAUSED_TIMEOUT_MINUTES)  || 30,
    offline_timeout_seconds:     Number(process.env.OFFLINE_TIMEOUT_SECONDS) || 300,
    gps_throttle_min_distance_m: Number(process.env.GPS_THROTTLE_MIN_DISTANCE) || 10,
    auto_assign: process.env.AUTO_ASSIGN === 'true'
};

const VALID_BIN_ACTIONS = ['OPEN', 'CLOSE', 'AUTO', 'MANUAL', 'PAUSE', 'RESUME'];

const ACTION_LABELS = {
    OPEN: 'Mở nắp',
    CLOSE: 'Đóng nắp',
    AUTO: 'Chế độ Tự động',
    MANUAL: 'Chế độ Thủ công',
    PAUSE: 'Tạm dừng thu gom',
    RESUME: 'Tiếp tục thu gom'
};

const JOB_STATUSES = {
    PENDING: 'PENDING',
    ASSIGNED: 'ASSIGNED',
    ACCEPTED: 'ACCEPTED',
    REJECTED: 'REJECTED',
    IN_PROGRESS: 'IN_PROGRESS',
    PAUSED: 'PAUSED',
    COMPLETED: 'COMPLETED',
    CANCELLED: 'CANCELLED',
    EXPIRED: 'EXPIRED'
};

const SESSION_COOKIE_NAME = 'smartwaste_session';
const SESSION_MAX_AGE = 28800; // 8 hours in seconds

module.exports = {
    SYSTEM_CONFIG,
    VALID_BIN_ACTIONS,
    ACTION_LABELS,
    JOB_STATUSES,
    SESSION_COOKIE_NAME,
    SESSION_MAX_AGE
};
