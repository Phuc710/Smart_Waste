'use strict';

const crypto = require('crypto');
const mqtt = require('mqtt');
const ioClient = require('socket.io-client');
const env = require('./src/config/env');
const { supabaseServiceRequest } = require('./src/core/supabase');

const BASE_URL = `http://127.0.0.1:${env.HTTP_PORT}`;
const MQTT_URL = `mqtt://127.0.0.1:${env.MQTT_PORT}`;

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

let sessionCookie = '';
let testPassed = 0;
let testFailed = 0;

function report(testName, passed, details = '') {
    if (passed) {
        testPassed++;
        console.log(`\x1b[32m[PASS]\x1b[0m ${testName} ${details ? '— \x1b[36m' + details + '\x1b[0m' : ''}`);
    } else {
        testFailed++;
        console.log(`\x1b[31m[FAIL]\x1b[0m ${testName} ${details ? '— \x1b[31m' + details + '\x1b[0m' : ''}`);
    }
}

async function request(path, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        ...(sessionCookie ? { 'Cookie': sessionCookie } : {}),
        ...(options.headers || {})
    };
    const res = await fetch(`${BASE_URL}${path}`, {
        ...options,
        headers
    });
    let data = null;
    const text = await res.text();
    try { data = JSON.parse(text); } catch (_) { data = text; }
    return { status: res.status, data, headers: res.headers };
}

async function setupActiveSession() {
    const rawToken = crypto.randomBytes(32).toString('hex');
    const tokenHash = crypto.createHash('sha256').update(rawToken).digest('hex');
    const expire = new Date(Date.now() + 8 * 3600 * 1000).toISOString();

    const accounts = await supabaseServiceRequest('employee_accounts?username=eq.admin&limit=1');
    const adminUser = Array.isArray(accounts) && accounts.length ? accounts[0] : null;
    if (!adminUser) throw new Error('Không tìm thấy tài khoản admin trong CSDL.');

    await supabaseServiceRequest('employee_sessions', {
        method: 'POST',
        headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
        body: JSON.stringify({
            token_hash: tokenHash,
            employee_id: adminUser.id,
            expires_at: expire
        })
    });

    sessionCookie = `smartwaste_session=${rawToken}`;
    return { rawToken, adminUser };
}

async function runLiveTests() {
    console.log('\n========================================================================');
    console.log('🚀 BẮT ĐẦU KIỂM THỬ XÁC THỰC 2 CHIỀU (REAL 2-WAY HANDSHAKE & MQTT IOT)');
    console.log(`📍 Endpoint Target: ${BASE_URL} | MQTT Broker: ${MQTT_URL}`);
    console.log('========================================================================\n');

    // 1. Tạo phiên Admin hợp lệ
    const { adminUser } = await setupActiveSession();
    console.log(`🔑 Đã khởi tạo phiên xác thực cho: ${adminUser.full_name} (${adminUser.username}) - Role: ${adminUser.role}\n`);

    // 2. KHỞI TẠO MÔ PHỎNG ESP32 THẬT KẾT NỐI MQTT
    console.log('--- 1. Khởi tạo Thiết bị IoT Thật kết nối MQTT Broker ---');
    const esp32Device = mqtt.connect(MQTT_URL, { clientId: 'ESP32_SMART_BIN_TEST' });
    let lastAckSent = null;

    await new Promise((resolve) => {
        esp32Device.on('connect', () => {
            console.log('   📡 [ESP32 Mock] Đã kết nối TCP tới Broker port 1883.');
            esp32Device.subscribe('wastebin/BIN_HCM_01/command', () => {
                console.log('   📡 [ESP32 Mock] Đã subscribe topic: wastebin/BIN_HCM_01/command');
                
                // Gửi bản tin trạng thái đầu tiên (làm thiết bị chuyển sang ONLINE)
                esp32Device.publish('wastebin/BIN_HCM_01/status', JSON.stringify({
                    state: 'CLOSED',
                    controlMode: 'AUTO',
                    servoAngle: 0,
                    distUser: 45.0,
                    distLevel: 15.0,
                    levelPercent: 75.0,
                    collectionPaused: false,
                    ipAddress: '192.168.1.101',
                    name: 'Thùng rác Chợ Bến Thành',
                    location: 'Cửa Nam Chợ Bến Thành, Quận 1'
                }), { qos: 1 });

                resolve();
            });
        });

        // Khi nhận lệnh từ Server -> Thực thi xoay servo và gửi lại gói tin ACK 2 chiều
        esp32Device.on('message', (topic, payload) => {
            if (!payload || payload.length === 0) return; // Bỏ qua gói tin xóa retain
            try {
                const cmd = JSON.parse(payload.toString());
                console.log(`   ⚡ [ESP32 Mock] Nhận lệnh từ Server: ${cmd.action} (CommandID: ${cmd.commandId})`);
                
                const isLidOpen = cmd.action === 'OPEN';
                const ackPayload = {
                    state: isLidOpen ? 'OPEN' : 'CLOSED',
                    controlMode: 'MANUAL',
                    servoAngle: isLidOpen ? 90 : 0,
                    distUser: 20.0,
                    distLevel: 15.0,
                    levelPercent: 75.0,
                    collectionPaused: false,
                    ipAddress: '192.168.1.101',
                    commandAckId: cmd.commandId,
                    commandAckAction: cmd.action
                };

                lastAckSent = ackPayload;
                // Gửi ACK về cho Backend
                esp32Device.publish('wastebin/BIN_HCM_01/status', JSON.stringify(ackPayload), { qos: 1 });
                console.log(`   ✅ [ESP32 Mock] Đã phát ACK 2 chiều về topic wastebin/BIN_HCM_01/status`);
            } catch (err) {
                console.error('   ❌ [ESP32 Mock Error]', err.message);
            }
        });
    });

    await sleep(500);

    // 3. HEALTH CHECK
    console.log('\n--- 2. Nhóm Hệ thống & Health Check ---');
    try {
        const res = await request('/api/health');
        report('GET /api/health', res.status === 200 && res.data.ok === true, 
            `Devices: ${res.data.devices}, Supabase: ${res.data.supabase}, MQTT Port: ${res.data.mqttPort}`);
    } catch (err) {
        report('GET /api/health', false, err.message);
    }

    // 4. AUTH ME
    console.log('\n--- 3. Nhóm Xác thực & Hồ sơ cá nhân (Auth) ---');
    try {
        const meRes = await request('/api/auth/me');
        report('GET /api/auth/me', meRes.status === 200 && meRes.data?.user?.username === 'admin', 
            `User: ${meRes.data?.user?.full_name} (${meRes.data?.user?.role})`);
    } catch (err) {
        report('GET /api/auth/me', false, err.message);
    }

    // 5. TEST XÁC THỰC 2 CHIỀU & TRẠNG THÁI ONLINE / OFFLINE
    console.log('\n--- 4. Kiểm thử Xác thực 2 chiều (2-Way Handshake & Online/Offline) ---');
    try {
        // Kiểm tra danh sách thùng rác
        const binsRes = await request('/api/bins');
        const bins = Array.isArray(binsRes.data) ? binsRes.data : [];
        const onlineBin = bins.find(b => b.device_id === 'BIN_HCM_01');
        const offlineBin = bins.find(b => b.device_id === 'BIN_HCM_02');

        report('Kiểm tra Trạng thái Online của BIN_HCM_01', onlineBin && onlineBin.is_online === true,
            `BIN_HCM_01 is_online: ${onlineBin?.is_online}`);

        report('Kiểm tra Trạng thái Offline của BIN_HCM_02 (Không gửi MQTT)', offlineBin && offlineBin.is_online === false,
            `BIN_HCM_02 is_online: ${offlineBin?.is_online}`);

        // Gửi lệnh tới thiết bị OFFLINE -> Phải bị chặn ngay lập tức
        console.log('   ⏳ Gửi lệnh tới thiết bị ngoại tuyến (BIN_HCM_02)...');
        const offlineCmdRes = await request('/api/bins/BIN_HCM_02/command', {
            method: 'POST',
            body: JSON.stringify({ action: 'OPEN' })
        });
        report('Chặn lệnh điều khiển khi thiết bị Ngoại tuyến', offlineCmdRes.status === 503,
            `Mã lỗi HTTP: ${offlineCmdRes.status}, Thông báo: "${offlineCmdRes.data?.error}"`);

        // Gửi lệnh tới thiết bị ONLINE -> Phải nhận được ACK 2 chiều thực sự từ ESP32
        console.log('   ⏳ Gửi lệnh Mở nắp tới thiết bị trực tuyến (BIN_HCM_01) và chờ ACK 2 chiều...');
        const onlineCmdRes = await request('/api/bins/BIN_HCM_01/command', {
            method: 'POST',
            body: JSON.stringify({ action: 'OPEN' })
        });
        report('Thực thi Lệnh Mở Nắp 2 Chiều thành công (Real MQTT ACK)', 
            onlineCmdRes.status === 200 && onlineCmdRes.data?.ok === true && onlineCmdRes.data?.bin?.state === 'OPEN',
            `Thông báo: "${onlineCmdRes.data?.message}", Trạng thái nắp mới: ${onlineCmdRes.data?.bin?.state}`);

        // Gửi lệnh Đóng nắp qua WebSocket Socket.IO (2-Way Handshake)
        const socket = ioClient(BASE_URL, {
            extraHeaders: { 'Cookie': sessionCookie },
            transports: ['websocket']
        });

        await new Promise((resolve) => socket.on('connect', resolve));
        
        console.log('   ⏳ Gửi sự kiện Socket.IO lidCommand Đóng nắp tới BIN_HCM_01...');
        const socketAck = await new Promise((resolve) => {
            socket.emit('lidCommand', { binId: 'BIN_HCM_01', action: 'CLOSE' }, (ack) => {
                resolve(ack);
            });
        });
        report('Socket.IO Realtime 2-Way Handshake (Đóng nắp)',
            socketAck && socketAck.ok === true && socketAck.bin?.state === 'CLOSED',
            `Phản hồi xác thực từ thiết bị: "${socketAck?.message}", Trạng thái: ${socketAck?.bin?.state}`);

        socket.disconnect();
    } catch (err) {
        report('Nhóm Handshake & Online Check', false, err.message);
    }

    // 6. EMPLOYEES & GPS LOCATION
    console.log('\n--- 5. Nhóm Quản lý Nhân viên & Vị trí GPS (Employees & Map) ---');
    try {
        const empRes = await request('/api/employees');
        report('GET /api/employees', empRes.status === 200 && Array.isArray(empRes.data), 
            `Tổng nhân viên: ${empRes.data?.length || 0} người`);

        const locRes = await request('/api/location', {
            method: 'POST',
            body: JSON.stringify({
                latitude: 10.7769,
                longitude: 106.7009,
                accuracy: 4.5,
                heading: 90,
                speed: 20
            })
        });
        report('POST /api/location (Cập nhật GPS nhân viên)', locRes.status === 200 && locRes.data?.ok === true, 
            'Tọa độ: 10.7769, 106.7009 (Phát sóng Socket.IO tới Admins)');

        const mapLocsRes = await request('/api/map/locations');
        report('GET /api/map/locations', mapLocsRes.status === 200 && Array.isArray(mapLocsRes.data), 
            `Số lượng vị trí GPS nhân viên: ${mapLocsRes.data?.length || 0}`);
    } catch (err) {
        report('Nhóm Employees', false, err.message);
    }

    // 7. MAP CONFIG & OSRM ROUTE
    console.log('\n--- 6. Nhóm Bản đồ & Tối ưu Lộ trình (Map & Routing) ---');
    try {
        const mapConfigRes = await request('/api/map/config');
        report('GET /api/map/config', mapConfigRes.status === 200 && mapConfigRes.data?.provider, 
            `Map Provider: ${mapConfigRes.data?.provider}, Routes: ${mapConfigRes.data?.routesProvider}`);

        const routeRes = await request('/api/map/route', {
            method: 'POST',
            body: JSON.stringify({
                coordinates: [
                    [106.7009, 10.7769],
                    [106.6980, 10.7725],
                    [106.7219, 10.7950]
                ]
            })
        });
        report('POST /api/map/route (Tính tuyến đường OSRM)', routeRes.status === 200 && routeRes.data?.distanceMeters > 0, 
            `Khoảng cách: ${(routeRes.data?.distanceMeters / 1000).toFixed(2)} km, Thời gian: ${Math.round(routeRes.data?.durationSeconds / 60)} phút, Provider: ${routeRes.data?.provider}`);
    } catch (err) {
        report('Nhóm Map Routing', false, err.message);
    }

    // 8. DISPATCH & JOBS
    console.log('\n--- 7. Nhóm Điều phối Thu gom (Dispatch & Collection Jobs) ---');
    try {
        const activeJobsRes = await request('/api/dispatch/active-jobs');
        report('GET /api/dispatch/active-jobs', activeJobsRes.status === 200 && Array.isArray(activeJobsRes.data), 
            `Số lượng Jobs đang chạy: ${activeJobsRes.data?.length || 0}`);

        const historyJobsRes = await request('/api/dispatch/history?limit=10');
        report('GET /api/dispatch/history', historyJobsRes.status === 200 && Array.isArray(historyJobsRes.data), 
            `Số lượng Jobs lịch sử: ${historyJobsRes.data?.length || 0}`);

        const mobileActiveRes = await request('/api/mobile/jobs/active');
        report('GET /api/mobile/jobs/active', mobileActiveRes.status === 200, 
            `Job hiện tại của nhân viên: ${mobileActiveRes.data?.job ? mobileActiveRes.data.job.id : 'Không có'}`);
    } catch (err) {
        report('Nhóm Dispatch', false, err.message);
    }

    // 9. INCIDENTS
    console.log('\n--- 8. Nhóm Báo cáo Sự cố (Incident Reports) ---');
    try {
        const incidentsRes = await request('/api/incidents');
        report('GET /api/incidents', incidentsRes.status === 200 && incidentsRes.data?.ok === true, 
            `Tổng số báo cáo sự cố: ${incidentsRes.data?.reports?.length || 0}`);
    } catch (err) {
        report('GET /api/incidents', false, err.message);
    }

    // 10. DASHBOARD STATS
    console.log('\n--- 9. Nhóm Thống kê & KPI Dashboard ---');
    try {
        const statsRes = await request('/api/dashboard/stats');
        report('GET /api/dashboard/stats', statsRes.status === 200 && statsRes.data?.ok === true, 
            `Online: ${statsRes.data?.onlineBins}, Offline: ${statsRes.data?.offlineBins}, Thùng đầy: ${statsRes.data?.overfullBins}, Xe hoạt động: ${statsRes.data?.activeTrucks}`);
    } catch (err) {
        report('GET /api/dashboard/stats', false, err.message);
    }

    // Dọn dẹp kết nối
    esp32Device.end();

    console.log('\n========================================================================');
    console.log(`🏁 TỔNG KẾT KIỂM THỬ: \x1b[32m${testPassed} PASSED\x1b[0m, \x1b[31m${testFailed} FAILED\x1b[0m`);
    console.log('========================================================================\n');

    process.exit(testFailed === 0 ? 0 : 1);
}

runLiveTests().catch(err => {
    console.error('Fatal Test Runner Error:', err);
    process.exit(1);
});
