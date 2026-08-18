'use strict';

/**
 * =============================================================================
 * SMARTWASTE — KIỂM THỬ THÔNG BÁO MOBILE REALTIME (SOCKET.IO + PUSH CONTRACT)
 * Kiểm tra 4 loại thông báo:
 *   1. Phân công ca mới (jobAssigned / jobUpdated: ASSIGNED)
 *   2. Hủy ca thu gom (jobUpdated: CANCELLED)
 *   3. Cảnh báo Thùng rác Quá tải (binOverfullAlert >= 85%)
 *   4. Sự cố khẩn cấp / Thông báo toàn hệ thống (newEvent)
 * =============================================================================
 */

const io = require('socket.io-client');
const { SESSION_COOKIE_NAME } = require('../src/config/constants');
const { cleanAndSeedDatabase } = require('./00_clean_and_seed_data');

const SERVER_URL = 'http://127.0.0.1:3000';

const C = {
    reset: '\x1b[0m',
    bright: '\x1b[1m',
    dim: '\x1b[2m',
    green: '\x1b[32m',
    red: '\x1b[31m',
    yellow: '\x1b[33m',
    cyan: '\x1b[36m',
    blue: '\x1b[34m',
    magenta: '\x1b[35m'
};

function logStep(idx, title) {
    console.log(`\n${C.cyan}${C.bright}[TEST CASE ${idx}]${C.reset} ${C.bright}${title}${C.reset}`);
}

function logPass(msg) {
    console.log(`  ${C.green}✔ PASS:${C.reset} ${msg}`);
}

function logFail(msg, detail = '') {
    console.log(`  ${C.red}✖ FAIL:${C.reset} ${msg} ${detail ? `(${detail})` : ''}`);
}

function logInfo(msg) {
    console.log(`  ${C.dim}ℹ ${msg}${C.reset}`);
}

async function loginUser(username, password) {
    const res = await fetch(`${SERVER_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
    });
    if (!res.ok) {
        throw new Error(`Đăng nhập thất bại cho user ${username} (Status ${res.status})`);
    }
    const data = await res.json();
    const cookieHeader = res.headers.get('set-cookie') || '';
    return { user: data.user, token: data.token, cookieHeader };
}

function waitForMatchingEvent(socket, eventName, predicate = () => true, timeoutMs = 5000) {
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            socket.off(eventName, handler);
            reject(new Error(`Timeout ${timeoutMs}ms khi chờ sự kiện '${eventName}'`));
        }, timeoutMs);

        function handler(data) {
            try {
                if (predicate(data)) {
                    clearTimeout(timer);
                    socket.off(eventName, handler);
                    resolve(data);
                }
            } catch (_) {}
        }

        socket.on(eventName, handler);
    });
}

async function runMobileNotificationTests() {
    console.log(`${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║   ${C.bright}SMARTWASTE — KIỂM THỬ HỆ THỐNG THÔNG BÁO MOBILE REALTIME           ${C.reset}${C.cyan}║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}`);

    let totalTests = 0;
    let passedTests = 0;

    // BƯỚC 0: ĐĂNG NHẬP LẤY PHIÊN MOBILE & ADMIN
    logInfo('Đăng nhập tài xế driver2 (rảnh rỗi) và admin...');
    const driver2Auth = await loginUser('driver2', 'Password123!');
    const adminAuth = await loginUser('admin', 'Password123!');

    // Cập nhật vị trí GPS của driver2 trên server cache (gần BIN_002)
    await fetch(`${SERVER_URL}/api/employees/location`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Cookie': `${SESSION_COOKIE_NAME}=${driver2Auth.token}`
        },
        body: JSON.stringify({
            latitude: 10.7795,
            longitude: 106.6992,
            accuracy: 5.0
        })
    });

    // KẾT NỐI SOCKET.IO CỦA MOBILE CLIENT (DRIVER2) VÀ ADMIN
    const mobileSocket = io(SERVER_URL, {
        auth: { token: driver2Auth.token },
        extraHeaders: { Cookie: `${SESSION_COOKIE_NAME}=${driver2Auth.token}` },
        transports: ['websocket', 'polling']
    });

    const adminSocket = io(SERVER_URL, {
        auth: { token: adminAuth.token },
        extraHeaders: { Cookie: `${SESSION_COOKIE_NAME}=${adminAuth.token}` },
        transports: ['websocket', 'polling']
    });

    await Promise.all([
        new Promise((resolve) => mobileSocket.on('connect', resolve)),
        new Promise((resolve) => adminSocket.on('connect', resolve))
    ]);
    logPass(`Mobile Client & Admin Socket đã kết nối thành công!`);

    // TEST CASE 1: THÔNG BÁO PHÂN CÔNG CA MỚI (JOB ASSIGNED)
    totalTests++;
    logStep(1, 'Kiểm thử Thông báo Phân công Ca mới (POST /api/dispatch/assign -> jobAssigned)');
    let assignedJobId = null;
    try {
        const waitPromise = waitForMatchingEvent(mobileSocket, 'jobAssigned', (job) => Boolean(job?.id));

        const assignRes = await fetch(`${SERVER_URL}/api/dispatch/assign`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `${SESSION_COOKIE_NAME}=${adminAuth.token}`
            },
            body: JSON.stringify({
                employeeId: driver2Auth.user.id,
                employeeName: driver2Auth.user.full_name,
                binIds: ['BIN_004']
            })
        });

        if (!assignRes.ok) {
            const errText = await assignRes.text();
            throw new Error(`API phân công trả về mã ${assignRes.status}: ${errText}`);
        }

        const assignData = await assignRes.json();
        assignedJobId = assignData.job?.id || assignData.id;

        const receivedJob = await waitPromise;

        if (receivedJob && (receivedJob.id === assignedJobId || receivedJob.job_id === assignedJobId)) {
            logPass(`Nhận đúng sự kiện 'jobAssigned' cho Ca gom #${assignedJobId} (${receivedJob.target_bin_ids?.length || 1} thùng rác)`);
            passedTests++;
        } else {
            logFail('Payload jobAssigned không đúng cấu trúc', JSON.stringify(receivedJob));
        }
    } catch (err) {
        logFail('Lỗi Test Case 1', err.message);
    }

    // TEST CASE 2: THÔNG BÁO HỦY CA THU GOM (JOB CANCELLED)
    totalTests++;
    logStep(2, 'Kiểm thử Thông báo Hủy Ca thu gom (POST /api/dispatch/jobs/:id/cancel -> jobUpdated: CANCELLED)');
    try {
        if (!assignedJobId) {
            assignedJobId = 'JOB_HCM_001';
        }

        const waitPromise = waitForMatchingEvent(mobileSocket, 'jobUpdated', (job) => job?.status === 'CANCELLED' || job?.status === 'CANCEL');

        const cancelRes = await fetch(`${SERVER_URL}/api/dispatch/jobs/${encodeURIComponent(assignedJobId)}/cancel`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `${SESSION_COOKIE_NAME}=${adminAuth.token}`
            },
            body: JSON.stringify({
                reason: 'Admin điều phối lại tuyến do sự cố giao thông'
            })
        });

        if (!cancelRes.ok) {
            const errText = await cancelRes.text();
            throw new Error(`API hủy ca trả về mã ${cancelRes.status}: ${errText}`);
        }

        const receivedCancelled = await waitPromise;

        if (receivedCancelled && (receivedCancelled.status === 'CANCELLED' || receivedCancelled.status === 'CANCEL')) {
            logPass(`Nhận đúng sự kiện 'jobUpdated' chuyển sang CANCELLED (Ca #${receivedCancelled.id})`);
            passedTests++;
        } else {
            logFail('Payload hủy ca không đúng', JSON.stringify(receivedCancelled));
        }
    } catch (err) {
        logFail('Lỗi Test Case 2', err.message);
    }

    // TEST CASE 3: THÔNG BÁO THÙNG RÁC QUÁ TẢI (BIN OVERFULL ALERT >= 85%)
    totalTests++;
    logStep(3, 'Kiểm thử Cảnh báo Thùng rác Quá tải Khẩn cấp (MQTT status >= 85% -> binOverfullAlert)');
    try {
        const mqtt = require('mqtt');
        const mqttClient = mqtt.connect('mqtt://127.0.0.1:1883', { clientId: 'BIN_002' });

        await new Promise((resolve) => mqttClient.on('connect', resolve));

        // Lắng nghe trên admin socket (phòng 'admins' luôn nhận được tất cả Overfill alerts)
        const waitPromise = waitForMatchingEvent(adminSocket, 'binOverfullAlert', (alert) => alert?.binId === 'BIN_002');

        const overfullPayload = JSON.stringify({
            deviceId: 'BIN_002',
            state: 'CLOSED',
            controlMode: 'AUTO',
            servoAngle: 0,
            distUser: 60.0,
            distLevel: 10.0,
            levelPercent: 91, // Vượt ngưỡng 85% Critical
            collectionPaused: false,
            ipAddress: '192.168.1.102'
        });

        mqttClient.publish('wastebin/BIN_002/status', overfullPayload, { qos: 0 });
        const receivedAlert = await waitPromise;
        mqttClient.end();

        if (receivedAlert && receivedAlert.binId === 'BIN_002' && receivedAlert.levelPercent >= 85) {
            logPass(`Nhận đúng cảnh báo Overfill: #${receivedAlert.binId} (${receivedAlert.levelPercent}%) - Vị trí: ${receivedAlert.location}`);
            passedTests++;
        } else {
            logFail('Payload binOverfullAlert không hợp lệ', JSON.stringify(receivedAlert));
        }
    } catch (err) {
        logFail('Lỗi Test Case 3', err.message);
    }

    // TEST CASE 4: SỰ CỐ KHẨN CẤP / THÔNG BÁO TOÀN HỆ THỐNG
    totalTests++;
    logStep(4, 'Kiểm thử Báo cáo Sự cố Hiện trường (POST /api/incidents -> incidentReported)');
    try {
        const incidentWaitPromise = waitForMatchingEvent(adminSocket, 'incidentReported', (inc) => inc?.reason === 'Cảm biến nắp bị kẹt');

        // Driver2 gửi báo cáo sự cố
        const incRes = await fetch(`${SERVER_URL}/api/incidents`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `${SESSION_COOKIE_NAME}=${driver2Auth.token}`
            },
            body: JSON.stringify({
                deviceId: 'BIN_001',
                reason: 'Cảm biến nắp bị kẹt',
                description: 'Thùng rác không tự mở khi người đến gần, cần bảo trì.'
            })
        });

        if (!incRes.ok) {
            const errText = await incRes.text();
            throw new Error(`API sự cố trả về mã ${incRes.status}: ${errText}`);
        }

        const receivedIncident = await incidentWaitPromise;

        if (receivedIncident && receivedIncident.reason === 'Cảm biến nắp bị kẹt') {
            logPass(`Nhận đúng sự kiện 'incidentReported' cho Thùng #${receivedIncident.device_id || receivedIncident.deviceId}: "${receivedIncident.reason}"`);
            passedTests++;
        } else {
            logFail('Payload incidentReported không đúng', JSON.stringify(receivedIncident));
        }
    } catch (err) {
        logFail('Lỗi Test Case 4', err.message);
    }

    // DỌN DẸP KẾT NỐI
    mobileSocket.disconnect();
    adminSocket.disconnect();

    // BÁO CÁO TỔNG KẾT
    console.log(`\n${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║   ${C.bright}KẾT QUẢ KIỂM THỬ THÔNG BÁO MOBILE: ${passedTests}/${totalTests} PASS (${Math.round((passedTests / totalTests) * 100)}%)             ${C.reset}${C.cyan}║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}\n`);

    if (passedTests !== totalTests) {
        throw new Error(`Có ${totalTests - passedTests} bài test thông báo thất bại!`);
    }
}

if (require.main === module) {
    runMobileNotificationTests()
        .then(() => process.exit(0))
        .catch(err => {
            logFail(`Kịch bản kiểm thử thông báo thất bại`, err.message);
            process.exit(1);
        });
}

module.exports = { runMobileNotificationTests };
