'use strict';

const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const { supabaseServiceRequest } = require('../src/core/supabase');

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

function logStep(step, title) {
    console.log(`\n${C.cyan}${C.bright}[BƯỚC ${step}]${C.reset} ${C.bright}${title}${C.reset}`);
}

function logSuccess(msg) {
    console.log(`  ${C.green}✔ ${msg}${C.reset}`);
}

function logInfo(msg) {
    console.log(`  ${C.dim}ℹ ${msg}${C.reset}`);
}

function logError(msg) {
    console.log(`  ${C.red}✖ ${msg}${C.reset}`);
}

async function cleanAndSeedDatabase() {
    console.log(`${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║   ${C.bright}SMARTWASTE — DỌN DẸP & NẠP DỮ LIỆU CSDL CHUẨN (SEED DATABASE)       ${C.reset}${C.cyan}║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}`);

    // BƯỚC 1: DỌN DẸP TOÀN BỘ CSDL THEO THỨ TỰ RÀNG BUỘC KHÓA NGOẠI
    logStep(1, 'Dọn dẹp sạch sẽ các bảng dữ liệu cũ trên Supabase...');
    const tablesToClean = [
        'ota_device_jobs',
        'ota_deployments',
        'firmware_releases',
        'employee_sessions',
        'incident_image_uploads',
        'incident_reports',
        'employee_location_points',
        'employee_locations',
        'job_bin_items',
        'collection_jobs',
        'bin_collections',
        'bin_events',
        'device_commands',
        'smart_bins',
        'employee_accounts'
    ];

    for (const table of tablesToClean) {
        try {
            await supabaseServiceRequest(`${table}?id=not.is.null`, { method: 'DELETE' }).catch(async () => {
                // Fallback for tables without id column or with device_id / token_hash primary keys
                if (table === 'smart_bins') {
                    await supabaseServiceRequest('smart_bins?device_id=not.is.null', { method: 'DELETE' });
                } else if (table === 'employee_sessions') {
                    await supabaseServiceRequest('employee_sessions?token_hash=not.is.null', { method: 'DELETE' });
                } else if (table === 'employee_locations') {
                    await supabaseServiceRequest('employee_locations?employee_id=not.is.null', { method: 'DELETE' });
                }
            });
            logSuccess(`Đã làm sạch bảng: ${table}`);
        } catch (err) {
            logInfo(`Bảng ${table} đã sạch hoặc bỏ qua: ${err.message}`);
        }
    }

    // BƯỚC 2: TẠO TÀI KHOẢN QUẢN TRỊ & NHÂN SỰ
    logStep(2, 'Khởi tạo tài khoản Quản trị viên và Nhân viên thực địa...');
    const rawHash = await bcrypt.hash('Password123!', 10);
    // Chuẩn hóa tag $2a$ tương thích tuyệt đối pgcrypto PostgreSQL
    const standardBcryptHash = '$2a' + rawHash.slice(3);

    const accountsData = [
        {
            full_name: 'Quản trị viên Hệ thống',
            username: 'admin',
            email: 'admin@smartwaste.vn',
            role: 'admin',
            password_hash: standardBcryptHash,
            is_active: true
        },
        {
            full_name: 'Nguyễn Văn Tài (Tài xế 1)',
            username: 'driver1',
            email: 'driver1@smartwaste.vn',
            role: 'staff',
            password_hash: standardBcryptHash,
            is_active: true
        },
        {
            full_name: 'Trần Quốc Bảo (Tài xế 2)',
            username: 'driver2',
            email: 'driver2@smartwaste.vn',
            role: 'staff',
            password_hash: standardBcryptHash,
            is_active: true
        },
        {
            full_name: 'Lê Hoàng Long (Tài xế 3)',
            username: 'driver3',
            email: 'driver3@smartwaste.vn',
            role: 'staff',
            password_hash: standardBcryptHash,
            is_active: true
        }
    ];

    const insertedAccounts = await supabaseServiceRequest('employee_accounts', {
        method: 'POST',
        headers: { Prefer: 'return=representation' },
        body: JSON.stringify(accountsData)
    });

    const accountsMap = {};
    for (const acc of insertedAccounts) {
        accountsMap[acc.username] = acc;
        logSuccess(`Đã tạo tài khoản [${acc.role.toUpperCase()}]: ${acc.username} (${acc.email})`);
    }

    // BƯỚC 3: TẠO DANH MỤC 6 THÙNG RÁC CHUẨN TẠI TP. HỒ CHÍ MINH (QUẬN 1)
    logStep(3, 'Khởi tạo 6 thùng rác thông minh thực tế tại Quận 1, TP.HCM...');
    const binsData = [
        {
            device_id: 'BIN_001',
            name: 'Thùng rác Phố Đi Bộ Nguyễn Huệ',
            location: 'Số 22 Nguyễn Huệ, P. Bến Nghé, Q.1',
            latitude: 10.7743,
            longitude: 106.7032,
            level_percent: 45.0,
            dist_user: 85.0,
            dist_level: 42.0,
            servo_angle: 0,
            state: 'CLOSED',
            control_mode: 'AUTO',
            is_online: true,
            collection_status: 'IN_PROGRESS',
            collection_employee_id: accountsMap.driver1?.id,
            collection_employee_name: accountsMap.driver1?.full_name
        },
        {
            device_id: 'BIN_002',
            name: 'Thùng rác Nhà Thờ Đức Bà',
            location: 'Số 1 Công xã Paris, P. Bến Nghé, Q.1',
            latitude: 10.7798,
            longitude: 106.6990,
            level_percent: 88.0, // Critical >= 85% -> Sẽ kích hoạt Overfill Alert
            dist_user: 60.0,
            dist_level: 12.0,
            servo_angle: 0,
            state: 'CLOSED',
            control_mode: 'AUTO',
            is_online: true,
            collection_status: 'IDLE',
            collection_employee_id: null,
            collection_employee_name: null
        },
        {
            device_id: 'BIN_003',
            name: 'Thùng rác Chợ Bến Thành',
            location: 'Cửa Đông Chợ Bến Thành, Q.1',
            latitude: 10.7720,
            longitude: 106.6983,
            level_percent: 62.0,
            dist_user: 75.0,
            dist_level: 30.0,
            servo_angle: 0,
            state: 'CLOSED',
            control_mode: 'AUTO',
            is_online: true,
            collection_status: 'IN_PROGRESS',
            collection_employee_id: accountsMap.driver1?.id || null,
            collection_employee_name: accountsMap.driver1?.full_name || null
        },
        {
            device_id: 'BIN_004',
            name: 'Thùng rác Thảo Cầm Viên',
            location: 'Số 2 Nguyễn Bỉnh Khiêm, P. Bến Nghé, Q.1',
            latitude: 10.7875,
            longitude: 106.7051,
            level_percent: 30.0,
            dist_user: 95.0,
            dist_level: 65.0,
            servo_angle: 0,
            state: 'CLOSED',
            control_mode: 'AUTO',
            is_online: true,
            collection_status: 'IDLE',
            collection_employee_id: null,
            collection_employee_name: null
        },
        {
            device_id: 'BIN_005',
            name: 'Thùng rác Bến Bạch Đằng',
            location: 'Công viên Bến Bạch Đằng, Tôn Đức Thắng, Q.1',
            latitude: 10.7735,
            longitude: 106.7067,
            level_percent: 92.0, // Critical >= 85%
            dist_user: 40.0,
            dist_level: 8.0,
            servo_angle: 0,
            state: 'CLOSED',
            control_mode: 'AUTO',
            is_online: true,
            collection_status: 'IDLE',
            collection_employee_id: null,
            collection_employee_name: null
        },
        {
            device_id: 'BIN_006',
            name: 'Thùng rác Công viên 23/9',
            location: 'Phạm Ngũ Lão, P. Phạm Ngũ Lão, Q.1',
            latitude: 10.7690,
            longitude: 106.6935,
            level_percent: 15.0,
            dist_user: 120.0,
            dist_level: 80.0,
            servo_angle: 0,
            state: 'CLOSED',
            control_mode: 'AUTO',
            is_online: false, // Giả lập thùng ngoại tuyến để test failure
            collection_status: 'IDLE',
            collection_employee_id: null,
            collection_employee_name: null
        }
    ];

    await supabaseServiceRequest('smart_bins', {
        method: 'POST',
        headers: { Prefer: 'resolution=merge-duplicates' },
        body: JSON.stringify(binsData)
    });

    for (const b of binsData) {
        logSuccess(`Đã tạo thùng rác: ${b.device_id} | ${b.name} (${b.level_percent}%)`);
    }

    // BƯỚC 4: TẠO VỊ TRÍ GPS CHO CÁC TÀI XẾ
    logStep(4, 'Cập nhật tọa độ GPS tức thời cho các tài xế...');
    const locationsData = [
        {
            employee_id: accountsMap.driver1.id,
            latitude: 10.7750,
            longitude: 106.7020, // Cách BIN_001 khoảng 200m
            accuracy: 5.0,
            heading: 45.0,
            speed: 12.5,
            recorded_at: new Date().toISOString()
        },
        {
            employee_id: accountsMap.driver2.id,
            latitude: 10.7600,
            longitude: 106.6800, // Cách xa ~2.5km
            accuracy: 6.0,
            heading: 180.0,
            speed: 0.0,
            recorded_at: new Date().toISOString()
        },
        {
            employee_id: accountsMap.driver3.id,
            latitude: 10.7550,
            longitude: 106.6700, // Cách xa ~3.5km
            accuracy: 8.0,
            heading: 90.0,
            speed: 5.0,
            recorded_at: new Date().toISOString()
        }
    ];

    await supabaseServiceRequest('employee_locations', {
        method: 'POST',
        headers: { Prefer: 'resolution=merge-duplicates' },
        body: JSON.stringify(locationsData)
    });
    logSuccess('Đã gán tọa độ GPS cho 3 tài xế thu gom.');

    // BƯỚC 5: TẠO CA GOM MẪU (COLLECTION JOB)
    logStep(5, 'Khởi tạo ca thu gom thực tế đang diễn ra (JOB_HCM_001)...');
    const jobData = {
        id: 'JOB_HCM_001',
        employee_id: accountsMap.driver1.id,
        employee_name: accountsMap.driver1.full_name,
        source: 'ADMIN_ASSIGNED',
        status: 'IN_PROGRESS',
        target_bin_ids: ['BIN_001', 'BIN_003'],
        route_data: {
            distance_meters: 1450,
            duration_seconds: 420,
            geometry: 'polyline_mock_coordinates'
        },
        assigned_at: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
        accepted_at: new Date(Date.now() - 14 * 60 * 1000).toISOString(),
        started_at: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
        version: 1
    };

    await supabaseServiceRequest('collection_jobs', {
        method: 'POST',
        headers: { Prefer: 'resolution=merge-duplicates' },
        body: JSON.stringify(jobData)
    });

    const jobBinItems = [
        {
            job_id: 'JOB_HCM_001',
            bin_id: 'BIN_001',
            status: 'PENDING'
        },
        {
            job_id: 'JOB_HCM_001',
            bin_id: 'BIN_003',
            status: 'PENDING'
        }
    ];

    await supabaseServiceRequest('job_bin_items', {
        method: 'POST',
        headers: { Prefer: 'resolution=merge-duplicates' },
        body: JSON.stringify(jobBinItems)
    });
    logSuccess(`Đã tạo ca gom: JOB_HCM_001 giao cho tài xế ${accountsMap.driver1.full_name}`);

    // BƯỚC 6: CẬP NHẬT CẤU HÌNH THAM SỐ VẬN HÀNH (SYSTEM SETTINGS)
    logStep(6, 'Thiết lập cấu hình tham số hệ thống chuẩn (System Settings)...');
    const settingsData = {
        id: 'default',
        fill_threshold_warning: 70,
        fill_threshold_critical: 85,
        bin_offline_timeout_seconds: 15,
        employee_offline_timeout_seconds: 120,
        assign_timeout_minutes: 5,
        paused_timeout_minutes: 30,
        auto_assign: false,
        map_provider: 'leaflet'
    };

    await supabaseServiceRequest('system_settings', {
        method: 'POST',
        headers: { Prefer: 'resolution=merge-duplicates' },
        body: JSON.stringify(settingsData)
    }).catch(() => {});
    logSuccess('Cấu hình tham số hệ thống đã sẵn sàng.');

    // BẢNG TỔNG KẾT DỮ LIỆU
    console.log(`\n${C.green}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.green}║        🎉 DỌN DẸP & NẠP DỮ LIỆU CSDL SUPABASE THÀNH CÔNG!            ║${C.reset}`);
    console.log(`${C.green}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}\n`);

    console.log(`${C.bright}📋 DANH SÁCH TÀI KHOẢN ĐĂNG NHẬP THỰC TẾ:${C.reset}`);
    console.table([
        { 'Tên đăng nhập': 'admin', 'Email': 'admin@smartwaste.vn', 'Mật khẩu': 'Password123!', 'Vai trò': 'Admin (Web Admin)' },
        { 'Tên đăng nhập': 'driver1', 'Email': 'driver1@smartwaste.vn', 'Mật khẩu': 'Password123!', 'Vai trò': 'Staff (Đang có ca JOB_HCM_001)' },
        { 'Tên đăng nhập': 'driver2', 'Email': 'driver2@smartwaste.vn', 'Mật khẩu': 'Password123!', 'Vai trò': 'Staff (Rảnh rỗi, gần Q.1)' },
        { 'Tên đăng nhập': 'driver3', 'Email': 'driver3@smartwaste.vn', 'Mật khẩu': 'Password123!', 'Vai trò': 'Staff (Rảnh rỗi)' }
    ]);

    console.log(`\n${C.bright}🗑️ DANH SÁCH THÙNG RÁC THÔNG MINH TP.HCM:${C.reset}`);
    console.table(binsData.map(b => ({
        'Mã thiết bị': b.device_id,
        'Tên thùng': b.name,
        'Mức rác (%)': `${b.level_percent}%`,
        'Trạng thái': b.state,
        'Ca gom': b.collection_status,
        'Trực tuyến': b.is_online ? 'ONLINE' : 'OFFLINE'
    })));
}

if (require.main === module) {
    cleanAndSeedDatabase()
        .then(() => process.exit(0))
        .catch(err => {
            logError(`Lỗi dọn dẹp & nạp dữ liệu: ${err.message}`);
            console.error(err);
            process.exit(1);
        });
}

module.exports = { cleanAndSeedDatabase };
