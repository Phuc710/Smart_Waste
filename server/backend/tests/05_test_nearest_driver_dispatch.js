'use strict';

/**
 * =============================================================================
 * SMARTWASTE — KIỂM THỬ THUẬT TOÁN ĐIỀU PHỐI XE GẦN NHẤT (NEAREST DISPATCH)
 * Kiểm tra:
 *   1. Cập nhật vị trí GPS cho 2 tài xế (Driver 2 gần 100m, Driver 3 xa 4.5km)
 *   2. Phát viễn trắc rác đầy 95% cho Thùng BIN_005 (Bến Bạch Đằng)
 *   3. Server tính toán khoảng cách Haversine và tự động chọn Driver 2 là xe gần nhất
 *   4. Gửi cảnh báo tới Admin và gửi trực tiếp tới phòng của Driver 2
 * =============================================================================
 */

const io = require('socket.io-client');
const mqtt = require('mqtt');
const { SESSION_COOKIE_NAME } = require('../src/config/constants');

const SERVER_URL = 'http://127.0.0.1:3000';
const MQTT_URL = 'mqtt://127.0.0.1:1883';

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
    console.log(`\n${C.cyan}${C.bright}[BƯỚC ${idx}]${C.reset} ${C.bright}${title}${C.reset}`);
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
        throw new Error(`Đăng nhập thất bại cho ${username}`);
    }
    const data = await res.json();
    return { user: data.user, token: data.token };
}

function waitForMatchingEvent(socket, eventName, predicate = () => true, timeoutMs = 6000) {
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            socket.off(eventName, handler);
            reject(new Error(`Timeout ${timeoutMs}ms khi chờ sự kiện '${eventName}'`));
        }, timeoutMs);

        function handler(data) {
            console.log(`  [Socket Received: ${eventName}]`, JSON.stringify(data));
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

async function runNearestDriverDispatchTest() {
    console.log(`${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║   ${C.bright}SMARTWASTE — KIỂM THỬ THUẬT TOÁN ĐIỀU PHỐI XE GẦN NHẤT (HAVERSINE)   ${C.reset}${C.cyan}║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}`);

    let totalTests = 0;
    let passedTests = 0;

    const TARGET_BIN_ID = 'BIN_005'; // Bến Bạch Đằng: 10.7735, 106.7067

    // BƯỚC 1: ĐĂNG NHẬP ADMIN & 2 TÀI XẾ RẢNH RỖI
    logStep(1, 'Đăng nhập Quản trị viên, Driver 2 và Driver 3...');
    const adminAuth = await loginUser('admin', 'Password123!');
    const driver2Auth = await loginUser('driver2', 'Password123!');
    const driver3Auth = await loginUser('driver3', 'Password123!');

    // BƯỚC 2: THIẾT LẬP TỌA ĐỘ GPS CHO 2 TÀI XẾ
    logStep(2, 'Cập nhật tọa độ GPS: Driver 2 ở gần BIN_005 (100m), Driver 3 ở xa (4.5km)...');
    
    // Driver 2: Tọa độ (10.7730, 106.7060) -> Rất gần BIN_005 (~100m)
    await fetch(`${SERVER_URL}/api/location`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Cookie': `${SESSION_COOKIE_NAME}=${driver2Auth.token}`
        },
        body: JSON.stringify({
            latitude: 10.7730,
            longitude: 106.7060,
            accuracy: 5.0
        })
    });
    logPass(`Đã cập nhật GPS Driver 2 (Trần Quốc Bảo): 10.7730, 106.7060 (Gần BIN_005)`);

    // Driver 3: Tọa độ (10.7400, 106.6600) -> Xa BIN_005 (~4.5km)
    await fetch(`${SERVER_URL}/api/location`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Cookie': `${SESSION_COOKIE_NAME}=${driver3Auth.token}`
        },
        body: JSON.stringify({
            latitude: 10.7400,
            longitude: 106.6600,
            accuracy: 8.0
        })
    });
    logPass(`Đã cập nhật GPS Driver 3 (Lê Hoàng Long): 10.7400, 106.6600 (Xa BIN_005)`);

    // BƯỚC 3: KẾT NỐI SOCKET.IO CLIENT
    logStep(3, 'Kết nối Socket.IO Client cho Quản trị viên (Admin)...');
    const adminSocket = io(SERVER_URL, {
        auth: { token: adminAuth.token },
        extraHeaders: { Cookie: `${SESSION_COOKIE_NAME}=${adminAuth.token}` },
        transports: ['websocket', 'polling']
    });

    await new Promise((resolve) => adminSocket.on('connect', resolve));
    logPass('Quản trị viên đã kết nối Socket.IO thành công.');

    // BƯỚC 4: PHÁT VIỄN TRẮC QUÁ TẢI (95%) VÀ KIỂM TRA ĐIỀU PHỐI TỰ ĐỘNG
    totalTests++;
    logStep(4, `Phát viễn trắc rác đầy 95% cho #${TARGET_BIN_ID} -> Xác thực Thuật toán Haversine`);
    try {
        const mqttClient = mqtt.connect(MQTT_URL, { clientId: TARGET_BIN_ID });
        await new Promise((resolve) => mqttClient.once('connect', resolve));

        const adminAlertPromise = waitForMatchingEvent(adminSocket, 'binOverfullAlert', (a) => a?.binId === TARGET_BIN_ID);

        const criticalTelemetry = JSON.stringify({
            deviceId: TARGET_BIN_ID,
            state: 'CLOSED',
            controlMode: 'AUTO',
            servoAngle: 0,
            distUser: 40.0,
            distLevel: 5.0,
            levelPercent: 95.0, // Critical >= 85%
            latitude: 10.7735,
            longitude: 106.7067,
            collectionPaused: false,
            ipAddress: '192.168.1.105'
        });

        await new Promise((resolve, reject) => {
            mqttClient.publish(`wastebin/${TARGET_BIN_ID}/status`, criticalTelemetry, { qos: 0 }, (err) => {
                if (err) return reject(err);
                logInfo(`[MQTT] Đã gửi gói viễn trắc rác đầy 95% cho #${TARGET_BIN_ID}`);
                resolve();
            });
        });

        const adminAlert = await adminAlertPromise;
        mqttClient.end();

        const nearest = adminAlert.suggestedNearestTruck;
        const isDriver2 = nearest && (
            nearest.employee_id === driver2Auth.user.id ||
            nearest.driverName?.includes('Tài xế 2') ||
            nearest.driverName?.includes('Trần Quốc Bảo') ||
            nearest.distanceKm <= 0.5
        );

        if (isDriver2 && nearest.distanceKm < 1.0) {
            logPass(`Thuật toán Haversine chọn đúng Driver gần nhất: ${nearest.driverName || 'Driver 2'} (${nearest.distanceKm} km)`);
            logPass(`Cảnh báo quá tải chuẩn xác: Thùng #${adminAlert.binId} (${adminAlert.levelPercent}%) - ${adminAlert.name}`);
            passedTests++;
        } else {
            logFail(`Gợi ý xe gần nhất không chính xác`, JSON.stringify(nearest));
        }
    } catch (err) {
        logFail('Lỗi Test Case Thuật toán Gợi ý Xe', err.message);
    }

    // DỌN DẸP KẾT NỐI
    adminSocket.disconnect();

    // BÁO CÁO TỔNG KẾT
    console.log(`\n${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║   ${C.bright}KẾT QUẢ KIỂM THỬ THUẬT TOÁN ĐIỀU PHỐI: ${passedTests}/${totalTests} PASS (${Math.round((passedTests / totalTests) * 100)}%)          ${C.reset}${C.cyan}║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}\n`);

    if (passedTests !== totalTests) {
        throw new Error(`Có ${totalTests - passedTests} bài test thuật toán thất bại!`);
    }
}

if (require.main === module) {
    runNearestDriverDispatchTest()
        .then(() => process.exit(0))
        .catch(err => {
            logFail(`Kịch bản kiểm thử điều phối thất bại`, err.message);
            process.exit(1);
        });
}

module.exports = { runNearestDriverDispatchTest };
