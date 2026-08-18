'use strict';

/**
 * =============================================================================
 * SMARTWASTE — KIỂM THỬ CÁC CA LỖI, TIMEOUT & BẢO MẬT ZERO-TRUST (FAILURE MATRIX)
 * Kiểm tra 5 kịch bản lỗi & bảo mật:
 *   1. Hardware Timeout 504: ESP32 không phản hồi ACK sau 4.5s
 *   2. Zero-Trust RBAC 403: Tài xế không được phân công cố tình mở nắp
 *   3. Unauthorized 401: Gọi API không có Token hoặc Token giả
 *   4. Invalid Payload 400: Gửi action không hợp lệ (ví dụ: 'EXPLODE')
 *   5. Admin Global Authority: Quản trị viên luôn có quyền điều khiển mọi thiết bị
 * =============================================================================
 */

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
    console.log(`\n${C.cyan}${C.bright}[FAILURE CASE ${idx}]${C.reset} ${C.bright}${title}${C.reset}`);
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

async function runFailureAndSecurityTests() {
    console.log(`${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║   ${C.bright}SMARTWASTE — KIỂM THỬ BẢO MẬT ZERO-TRUST & CÁC TRƯỜNG HỢP LỖI       ${C.reset}${C.cyan}║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}`);

    let totalTests = 0;
    let passedTests = 0;

    // Đăng nhập các tài khoản
    logInfo('Đăng nhập tài xế driver1 (có ca BIN_001), driver2 (không có ca) và admin...');
    const driver1Auth = await loginUser('driver1', 'Password123!');
    const driver2Auth = await loginUser('driver2', 'Password123!');
    const adminAuth = await loginUser('admin', 'Password123!');

    // Gửi bản tin Online cho BIN_001 để đảm bảo thiết bị đang online
    const mqttClient = mqtt.connect(MQTT_URL, { clientId: 'BIN_001' });
    await new Promise((resolve) => {
        mqttClient.once('connect', () => {
            mqttClient.publish('wastebin/BIN_001/status', JSON.stringify({
                deviceId: 'BIN_001',
                state: 'CLOSED',
                controlMode: 'AUTO',
                servoAngle: 0,
                distUser: 85.0,
                distLevel: 42.0,
                levelPercent: 45.0,
                collectionPaused: false,
                ipAddress: '192.168.1.101'
            }), { qos: 1 }, () => {
                resolve();
            });
        });
    });

    // TEST CASE 1: ESP32 TIMEOUT 504 (PHẦN CỨNG MẤT NGUỒN / KHÔNG TRẢ ACK)
    totalTests++;
    logStep(1, 'Kiểm thử Phần cứng không phản hồi ACK -> Server trả lỗi 504 Timeout sau 4.5s');
    try {
        const startTime = Date.now();
        // Gửi lệnh mở nắp nhưng mqttClient cố ý KHÔNG trả ACK
        const res = await fetch(`${SERVER_URL}/api/bins/BIN_001/command`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `${SESSION_COOKIE_NAME}=${driver1Auth.token}`
            },
            body: JSON.stringify({ action: 'OPEN' })
        });

        const elapsedMs = Date.now() - startTime;
        const data = await res.json();

        if (res.status === 504 && elapsedMs >= 4000) {
            logPass(`Server bắt đúng Timeout 504 sau ${elapsedMs}ms: "${data.error}"`);
            passedTests++;
        } else {
            logFail(`Kỳ vọng mã lỗi 504 (đã nhận ${res.status}) sau >= 4000ms`, JSON.stringify(data));
        }
    } catch (err) {
        logFail('Lỗi Test Case 1', err.message);
    }

    // TEST CASE 2: ZERO-TRUST RBAC 403 (TÀI XẾ KHÔNG ĐƯỢC PHÂN CÔNG ĐIỀU KHIỂN THÙNG)
    totalTests++;
    logStep(2, 'Kiểm thử Phân quyền Zero-Trust (Driver 2 bấm mở nắp thùng BIN_001 không thuộc ca)');
    try {
        const res = await fetch(`${SERVER_URL}/api/bins/BIN_001/command`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `${SESSION_COOKIE_NAME}=${driver2Auth.token}`
            },
            body: JSON.stringify({ action: 'OPEN' })
        });

        const data = await res.json();

        if (res.status === 403 && data.error?.includes('FORBIDDEN')) {
            logPass(`Server từ chối đúng chuẩn Zero-Trust RBAC 403: "${data.error}"`);
            passedTests++;
        } else {
            logFail(`Kỳ vọng mã lỗi 403 Forbidden (đã nhận ${res.status})`, JSON.stringify(data));
        }
    } catch (err) {
        logFail('Lỗi Test Case 2', err.message);
    }

    // TEST CASE 3: UNAUTHORIZED 401 (GỌI API KHÔNG CÓ TOKEN HOẶC TOKEN GIẢ)
    totalTests++;
    logStep(3, 'Kiểm thử Bảo vệ Xác thực (Gọi API với Token rác -> 401 Unauthorized)');
    try {
        const res = await fetch(`${SERVER_URL}/api/bins/BIN_001/command`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `${SESSION_COOKIE_NAME}=fake_invalid_token_1234567890abcdef1234567890abcdef1234567890abcdef`
            },
            body: JSON.stringify({ action: 'OPEN' })
        });

        const data = await res.json();

        if (res.status === 401) {
            logPass(`Server chặn đúng chuẩn 401 Unauthorized: "${data.error}"`);
            passedTests++;
        } else {
            logFail(`Kỳ vọng mã lỗi 401 (đã nhận ${res.status})`, JSON.stringify(data));
        }
    } catch (err) {
        logFail('Lỗi Test Case 3', err.message);
    }

    // TEST CASE 4: INVALID ACTION 400 (GỬI LỆNH KHÔNG HỢP LỆ)
    totalTests++;
    logStep(4, "Kiểm thử Kiểm soát Payload (Gửi lệnh không tồn tại 'EXPLODE' -> 400 Bad Request)");
    try {
        const res = await fetch(`${SERVER_URL}/api/bins/BIN_001/command`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `${SESSION_COOKIE_NAME}=${driver1Auth.token}`
            },
            body: JSON.stringify({ action: 'EXPLODE' })
        });

        const data = await res.json();

        if (res.status === 400) {
            logPass(`Server từ chối đúng chuẩn 400 Bad Request: "${data.error}"`);
            passedTests++;
        } else {
            logFail(`Kỳ vọng mã lỗi 400 (đã nhận ${res.status})`, JSON.stringify(data));
        }
    } catch (err) {
        logFail('Lỗi Test Case 4', err.message);
    }

    // TEST CASE 5: ADMIN GLOBAL OVERRIDE
    totalTests++;
    logStep(5, 'Kiểm thử Quyền Quản trị viên Toàn quyền (Admin điều khiển không bị chặn RBAC)');
    try {
        // Trả ACK tự động cho lệnh của Admin
        mqttClient.on('message', (_t, p) => {
            if (!p || p.length === 0) return;
            try {
                const cmd = JSON.parse(p.toString());
                mqttClient.publish('wastebin/BIN_001/status', JSON.stringify({
                    deviceId: 'BIN_001',
                    state: 'OPEN',
                    controlMode: 'MANUAL',
                    servoAngle: 90,
                    commandAckId: cmd.commandId,
                    commandAckAction: cmd.action
                }), { qos: 1 });
            } catch (_) {}
        });

        mqttClient.subscribe('wastebin/BIN_001/command', { qos: 1 });

        const res = await fetch(`${SERVER_URL}/api/bins/BIN_001/command`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cookie': `${SESSION_COOKIE_NAME}=${adminAuth.token}`
            },
            body: JSON.stringify({ action: 'OPEN' })
        });

        const data = await res.json();

        if (res.ok && data.ok === true) {
            logPass(`Admin gửi lệnh thành công không bị chặn: "${data.message}"`);
            passedTests++;
        } else {
            logFail(`Admin gửi lệnh thất bại (Status ${res.status})`, JSON.stringify(data));
        }
    } catch (err) {
        logFail('Lỗi Test Case 5', err.message);
    }

    mqttClient.end();

    // BÁO CÁO TỔNG KẾT
    console.log(`\n${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║   ${C.bright}KẾT QUẢ KIỂM THỬ LỖI & BẢO MẬT: ${passedTests}/${totalTests} PASS (${Math.round((passedTests / totalTests) * 100)}%)              ${C.reset}${C.cyan}║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}\n`);

    if (passedTests !== totalTests) {
        throw new Error(`Có ${totalTests - passedTests} bài test lỗi/bảo mật thất bại!`);
    }
}

if (require.main === module) {
    runFailureAndSecurityTests()
        .then(() => process.exit(0))
        .catch(err => {
            logFail(`Kịch bản kiểm thử lỗi & bảo mật thất bại`, err.message);
            process.exit(1);
        });
}

module.exports = { runFailureAndSecurityTests };
