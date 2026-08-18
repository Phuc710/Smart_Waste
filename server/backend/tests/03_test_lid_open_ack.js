'use strict';

/**
 * =============================================================================
 * SMARTWASTE — KIỂM THỬ ĐIỀU KHIỂN NẮP & 2-WAY HANDSHAKE ACK (HARDWARE VERIFICATION)
 * Kiểm tra chu trình điều khiển phần cứng từ Mobile App:
 *   1. Mobile App gọi POST /api/bins/:id/command (Lệnh 'OPEN')
 *   2. Server tạo Promise Waiter (4.5s) và phát lệnh qua MQTT (Topic: wastebin/{id}/command)
 *   3. Phần cứng ESP32 / Simulator nhận lệnh, quay Servo 90° và phát lại Status kèm ACK (commandAckId)
 *   4. Server giải phóng Promise Waiter, trả về HTTP 200 OK cho Mobile trong mili-giây
 *   5. Mobile Socket.IO nhận broadcast 'binData' cập nhật trạng thái nắp mở
 *   6. Tiếp tục kiểm thử lệnh 'CLOSE' đóng nắp an toàn
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
    console.log(`\n${C.cyan}${C.bright}[GIAI ĐOẠN ${idx}]${C.reset} ${C.bright}${title}${C.reset}`);
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

function waitForBinDataEvent(socket, binId, expectedState, timeoutMs = 5000) {
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            socket.off('binData', handler);
            reject(new Error(`Timeout ${timeoutMs}ms khi chờ Socket.IO binData với state=${expectedState}`));
        }, timeoutMs);

        function handler(payload) {
            if (payload?.binId === binId && payload?.data?.state === expectedState) {
                clearTimeout(timer);
                socket.off('binData', handler);
                resolve(payload);
            }
        }

        socket.on('binData', handler);
    });
}

async function runLidOpenAckTest() {
    console.log(`${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║   ${C.bright}SMARTWASTE — KIỂM THỬ BẤM MỞ NẮP MOBILE APP & 2-WAY HANDSHAKE ACK  ${C.reset}${C.cyan}║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}`);

    let totalTests = 0;
    let passedTests = 0;

    const BIN_ID = 'BIN_001';

    // BƯỚC 1: ĐĂNG NHẬP TÀI XẾ ĐƯỢC PHÂN CÔNG (DRIVER1 CÓ CA GOM BIN_001)
    logStep(1, 'Đăng nhập tài xế driver1 & Kết nối Mobile Socket.IO Client...');
    const driverAuth = await loginUser('driver1', 'Password123!');
    logPass(`Tài xế '${driverAuth.user.full_name}' đã đăng nhập thành công.`);

    const mobileSocket = io(SERVER_URL, {
        auth: { token: driverAuth.token },
        extraHeaders: { Cookie: `${SESSION_COOKIE_NAME}=${driverAuth.token}` },
        transports: ['websocket', 'polling']
    });

    await new Promise((resolve) => mobileSocket.on('connect', resolve));
    logPass('Mobile Client đã kết nối Socket.IO thành công.');

    // BƯỚC 2: KHỞI CHẠY PHẦN CỨNG IOT ESP32 (MQTT CLIENT CHO BIN_001)
    logStep(2, `Khởi tạo kết nối IoT Hardware ESP32 cho Thùng rác #${BIN_ID}...`);
    const esp32Client = mqtt.connect(MQTT_URL, { clientId: BIN_ID });

    let currentHardwareState = 'CLOSED';
    let currentServoAngle = 0;

    await new Promise((resolve, reject) => {
        esp32Client.once('connect', () => {
            esp32Client.subscribe(`wastebin/${BIN_ID}/command`, { qos: 1 }, (subErr) => {
                if (subErr) return reject(subErr);
                logPass(`ESP32 #${BIN_ID} đã kết nối MQTT Broker và subscribe topic: wastebin/${BIN_ID}/command`);

                // Gửi ngay bản tin viễn trắc chào mừng với QoS 1 để Server cập nhật trạng thái Online
                const initialStatus = JSON.stringify({
                    deviceId: BIN_ID,
                    state: 'CLOSED',
                    controlMode: 'AUTO',
                    servoAngle: 0,
                    distUser: 85.0,
                    distLevel: 42.0,
                    levelPercent: 45.0,
                    collectionPaused: false,
                    ipAddress: '192.168.1.101'
                });

                esp32Client.publish(`wastebin/${BIN_ID}/status`, initialStatus, { qos: 1 }, (pubErr) => {
                    if (pubErr) return reject(pubErr);
                    logPass(`ESP32 #${BIN_ID} đã gửi gói tin Online thành công.`);
                    setTimeout(resolve, 500); // Chờ 500ms cho Server cập nhật cache
                });
            });
        });
        esp32Client.once('error', reject);
    });

    // Xử lý lệnh phần cứng và trả ACK từ ESP32
    esp32Client.on('message', (topic, payloadBuffer) => {
        // Bỏ qua gói tin xóa retain rỗng từ Broker (giống firmware C++ Esp32_S3/src/main.cpp)
        if (!payloadBuffer || payloadBuffer.length === 0) return;

        try {
            const cmd = JSON.parse(payloadBuffer.toString());
            const action = String(cmd.action || '').toUpperCase();
            const commandId = cmd.commandId || '';

            logInfo(`[ESP32 HW] Đã nhận lệnh từ Broker: ${action} (CmdID: ${commandId})`);

            if (action === 'OPEN' || action === 'OPEN_LID') {
                currentHardwareState = 'OPEN';
                currentServoAngle = 90;
                logInfo(`[ESP32 HW] Servo quay 90° -> Nắp mở (OPEN)`);
            } else if (action === 'CLOSE' || action === 'CLOSE_LID') {
                currentHardwareState = 'CLOSED';
                currentServoAngle = 0;
                logInfo(`[ESP32 HW] Servo quay 0° -> Nắp đóng (CLOSED)`);
            }

            // Gửi gói tin Status phản hồi mang commandAckId để hoàn tất 2-Way ACK
            const statusAckPayload = {
                deviceId: BIN_ID,
                state: currentHardwareState,
                controlMode: 'MANUAL',
                servoAngle: currentServoAngle,
                distUser: 50.0,
                distLevel: 30.0,
                levelPercent: 45.0,
                collectionPaused: false,
                ipAddress: '192.168.1.101',
                commandAckId: commandId,
                commandAckAction: action
            };

            esp32Client.publish(`wastebin/${BIN_ID}/status`, JSON.stringify(statusAckPayload), { qos: 1 }, () => {
                logPass(`[ESP32 HW] Đã phản hồi gói tin Status ACK cho lệnh ${action} (commandAckId: ${commandId})`);
            });
        } catch (e) {
            logFail('[ESP32 HW] Lỗi xử lý MQTT command', e.message);
        }
    });

    // TEST CASE 1: MOBILE APP BẤM MỞ NẮP (ACTION = 'OPEN') & NHẬN ACK < 4.5s
    totalTests++;
    logStep(3, "Kiểm thử Mobile App gửi lệnh 'OPEN' -> ESP32 mở nắp và phản hồi 2-Way ACK");
    try {
        const startTime = Date.now();
        const socketWaitPromise = waitForBinDataEvent(mobileSocket, BIN_ID, 'OPEN', 4500);

        // Mobile gửi yêu cầu mở nắp qua HTTP POST
        const res = await fetch(`${SERVER_URL}/api/bins/${BIN_ID}/command`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `${SESSION_COOKIE_NAME}=${driverAuth.token}`
            },
            body: JSON.stringify({ action: 'OPEN' })
        });

        const elapsedMs = Date.now() - startTime;
        const data = await res.json();

        if (res.ok && data.ok === true && data.bin?.state === 'OPEN') {
            logPass(`Server trả về HTTP 200 OK: Thiết bị #${BIN_ID} đã mở nắp thành công! (Thời gian: ${elapsedMs}ms)`);
            passedTests++;
        } else {
            logFail(`Phản hồi lệnh mở nắp thất bại (Status ${res.status})`, JSON.stringify(data));
        }

        // Kiểm tra Socket.IO Realtime update
        const socketEvent = await socketWaitPromise;
        if (socketEvent) {
            logPass(`Mobile App nhận được Broadcast 'binData' cập nhật trạng thái: state = 'OPEN'`);
        }
    } catch (err) {
        logFail('Lỗi Test Case 1 (Mở nắp)', err.message);
    }

    // TEST CASE 2: MOBILE APP BẤM ĐÓNG NẮP (ACTION = 'CLOSE') & NHẬN ACK
    totalTests++;
    logStep(4, "Kiểm thử Mobile App gửi lệnh 'CLOSE' -> ESP32 đóng nắp và phản hồi 2-Way ACK");
    try {
        const startTime = Date.now();
        const socketWaitPromise = waitForBinDataEvent(mobileSocket, BIN_ID, 'CLOSED', 4500);

        const res = await fetch(`${SERVER_URL}/api/bins/${BIN_ID}/command`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `${SESSION_COOKIE_NAME}=${driverAuth.token}`
            },
            body: JSON.stringify({ action: 'CLOSE' })
        });

        const elapsedMs = Date.now() - startTime;
        const data = await res.json();

        if (res.ok && data.ok === true && data.bin?.state === 'CLOSED') {
            logPass(`Server trả về HTTP 200 OK: Thiết bị #${BIN_ID} đã đóng nắp thành công! (Thời gian: ${elapsedMs}ms)`);
            passedTests++;
        } else {
            logFail(`Phản hồi lệnh đóng nắp thất bại (Status ${res.status})`, JSON.stringify(data));
        }

        const socketEvent = await socketWaitPromise;
        if (socketEvent) {
            logPass(`Mobile App nhận được Broadcast 'binData' cập nhật trạng thái: state = 'CLOSED'`);
        }
    } catch (err) {
        logFail('Lỗi Test Case 2 (Đóng nắp)', err.message);
    }

    // DỌN DẸP KẾT NỐI
    mobileSocket.disconnect();
    esp32Client.end();

    // BÁO CÁO TỔNG KẾT
    console.log(`\n${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║   ${C.bright}KẾT QUẢ KIỂM THỬ MỞ NẮP & 2-WAY ACK: ${passedTests}/${totalTests} PASS (${Math.round((passedTests / totalTests) * 100)}%)              ${C.reset}${C.cyan}║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}\n`);

    if (passedTests !== totalTests) {
        throw new Error(`Có ${totalTests - passedTests} bài test mở nắp thất bại!`);
    }
}

if (require.main === module) {
    runLidOpenAckTest()
        .then(() => process.exit(0))
        .catch(err => {
            logFail(`Kịch bản kiểm thử mở nắp 2-Way ACK thất bại`, err.message);
            process.exit(1);
        });
}

module.exports = { runLidOpenAckTest };
