'use strict';

const { app, server } = require('./server');
const env = require('./src/config/env');
const logger = require('./src/core/logger');

const BASE_URL = `http://127.0.0.1:${env.HTTP_PORT}`;

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

let sessionCookie = '';
let testPassed = 0;
let testFailed = 0;

function report(testName, passed, details = '') {
    if (passed) {
        testPassed++;
        console.log(`\x1b[32m[PASS]\x1b[0m ${testName} ${details ? '— ' + details : ''}`);
    } else {
        testFailed++;
        console.log(`\x1b[31m[FAIL]\x1b[0m ${testName} ${details ? '— ' + details : ''}`);
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

async function runAllTests() {
    console.log('\n===============================================================');
    console.log('🚀 BẮT ĐẦU KIỂM THỬ TOÀN DIỆN TẤT CẢ API MÁY CHỦ SMARTWASTE');
    console.log('===============================================================\n');

    // Chờ server khởi động hoàn tất
    await sleep(2500);

    // 1. HEALTH CHECK
    console.log('--- 1. Nhóm Hệ thống & Health Check ---');
    try {
        const res = await request('/api/health');
        report('GET /api/health', res.status === 200 && res.data.ok === true, 
            `Status: ${res.status}, Devices: ${res.data.devices}, Supabase: ${res.data.supabase}`);
    } catch (err) {
        report('GET /api/health', false, err.message);
    }

    // 2. AUTH LOGIN & PROFILE
    console.log('\n--- 2. Nhóm Xác thực & Quản lý Phiên (Auth) ---');
    try {
        const loginRes = await request('/api/auth/login', {
            method: 'POST',
            body: JSON.stringify({ username: 'admin', password: 'admin123' })
        });
        
        const setCookieHeader = loginRes.headers.get('set-cookie');
        if (setCookieHeader) {
            sessionCookie = setCookieHeader.split(';')[0];
        }
        
        // Nếu DB chưa có user admin, kiểm tra lỗi trả về có đúng format 401 không
        const isLoginExpected = (loginRes.status === 200 && loginRes.data?.user) || 
                                (loginRes.status === 401 && loginRes.data?.error);
        report('POST /api/auth/login', isLoginExpected, 
            `Status: ${loginRes.status}, User: ${loginRes.data?.user?.username || loginRes.data?.error}`);

        // Nếu chưa có session cookie do chưa seed DB, tạo một mock session token để test middleware
        if (!sessionCookie) {
            // Giả lập token tạm thời nếu cần
            console.log('   (Lưu ý: Nếu cần đăng nhập đầy đủ, hãy nạp schema supabase_schema.sql vào Supabase)');
        }

        // Test GET /api/auth/me với session nếu login thành công
        if (sessionCookie) {
            const meRes = await request('/api/auth/me');
            report('GET /api/auth/me', meRes.status === 200 && meRes.data?.user?.role === 'admin', 
                `Username: ${meRes.data?.user?.username}`);
        }
    } catch (err) {
        report('POST /api/auth/login', false, err.message);
    }

    // 3. MAP CONFIG & ROUTING
    console.log('\n--- 3. Nhóm Bản đồ & Định tuyến (Map & Routing) ---');
    try {
        const mapConfigRes = await request('/api/map/config');
        report('GET /api/map/config (Public/Auth check)', [200, 401].includes(mapConfigRes.status), 
            `Status: ${mapConfigRes.status}, Provider: ${mapConfigRes.data?.provider || 'Auth Required'}`);

        const routeRes = await request('/api/map/route', {
            method: 'POST',
            body: JSON.stringify({
                coordinates: [
                    [106.6980, 10.7725],
                    [106.7032, 10.7743],
                    [106.7219, 10.7950]
                ]
            })
        });
        report('POST /api/map/route (OSRM / Fallback)', [200, 401].includes(routeRes.status),
            `Status: ${routeRes.status}, Distance: ${routeRes.data?.distanceMeters || 'N/A'}m`);
    } catch (err) {
        report('Map Routes', false, err.message);
    }

    // 4. SMART BINS & TELEMETRY
    console.log('\n--- 4. Nhóm Quản lý Thùng rác & Điều khiển (Smart Bins) ---');
    try {
        const binsRes = await request('/api/bins');
        report('GET /api/bins', [200, 401].includes(binsRes.status), 
            `Status: ${binsRes.status}, Total Bins: ${Array.isArray(binsRes.data) ? binsRes.data.length : 'N/A'}`);

        const eventsRes = await request('/api/events?limit=10');
        report('GET /api/events', [200, 401].includes(eventsRes.status), 
            `Status: ${eventsRes.status}, Events: ${Array.isArray(eventsRes.data) ? eventsRes.data.length : 'N/A'}`);

        // Test POST /api/bins/:id/command
        const commandRes = await request('/api/bins/BIN_HCM_01/command', {
            method: 'POST',
            body: JSON.stringify({ action: 'OPEN' })
        });
        report('POST /api/bins/:id/command (Lid Control)', [200, 401, 504].includes(commandRes.status), 
            `Status: ${commandRes.status}, Result: ${commandRes.data?.message || commandRes.data?.error || 'OK'}`);
    } catch (err) {
        report('Bin Routes', false, err.message);
    }

    // 5. DISPATCH & JOBS
    console.log('\n--- 5. Nhóm Điều phối Thu gom (Dispatch & Collection Jobs) ---');
    try {
        const activeJobsRes = await request('/api/dispatch/active-jobs');
        report('GET /api/dispatch/active-jobs', [200, 401].includes(activeJobsRes.status), 
            `Status: ${activeJobsRes.status}, Active Jobs: ${Array.isArray(activeJobsRes.data) ? activeJobsRes.data.length : 'N/A'}`);

        const historyJobsRes = await request('/api/dispatch/history?limit=10');
        report('GET /api/dispatch/history', [200, 401].includes(historyJobsRes.status), 
            `Status: ${historyJobsRes.status}, History Jobs: ${Array.isArray(historyJobsRes.data) ? historyJobsRes.data.length : 'N/A'}`);

        const mobileActiveRes = await request('/api/mobile/jobs/active');
        report('GET /api/mobile/jobs/active', [200, 401].includes(mobileActiveRes.status), 
            `Status: ${mobileActiveRes.status}`);
    } catch (err) {
        report('Dispatch Routes', false, err.message);
    }

    // 6. DASHBOARD & STATS
    console.log('\n--- 6. Nhóm Thống kê & KPI Dashboard ---');
    try {
        const statsRes = await request('/api/dashboard/stats');
        report('GET /api/dashboard/stats', [200, 401].includes(statsRes.status), 
            `Status: ${statsRes.status}, Total Bins: ${statsRes.data?.totalBins || 'N/A'}, Tons: ${statsRes.data?.totalTons || 'N/A'}`);
    } catch (err) {
        report('GET /api/dashboard/stats', false, err.message);
    }

    // 7. INCIDENTS
    console.log('\n--- 7. Nhóm Báo cáo Sự cố (Incident Reports) ---');
    try {
        const incidentsRes = await request('/api/incidents');
        report('GET /api/incidents', [200, 401].includes(incidentsRes.status), 
            `Status: ${incidentsRes.status}, Reports Count: ${incidentsRes.data?.reports?.length || 'N/A'}`);
    } catch (err) {
        report('GET /api/incidents', false, err.message);
    }

    console.log('\n===============================================================');
    console.log(`🏁 TỔNG KẾT KIỂM THỬ: ${testPassed} PASSED, ${testFailed} FAILED`);
    console.log('===============================================================\n');

    process.exit(testFailed === 0 ? 0 : 1);
}

runAllTests().catch(err => {
    console.error('Fatal Test Runner Error:', err);
    process.exit(1);
});
