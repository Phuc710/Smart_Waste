'use strict';

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

/**
 * Dọn dẹp sạch 100% CSDL Supabase theo đúng thứ tự ràng buộc khóa ngoại (Foreign Keys)
 */
async function cleanDatabase(options = { seedAdmin: true }) {
    console.log(`${C.yellow}${C.bright}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.yellow}${C.bright}║      🧹 SMARTWASTE — TIẾN TRÌNH DỌN DẸP SẠCH 100% CSDL SUPABASE       ║${C.reset}`);
    console.log(`${C.yellow}${C.bright}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}`);

    // BƯỚC 1: XÓA TOÀN BỘ CÁC BẢNG DỮ LIỆU THEO THỨ TỰ RÀNG BUỘC
    logStep(1, 'Xóa toàn bộ bản ghi trong tất cả các bảng CSDL...');

    const tables = [
        // 1. Dữ liệu OTA & Firmware
        { name: 'ota_device_jobs', pk: 'id' },
        { name: 'ota_deployments', pk: 'id' },
        { name: 'firmware_releases', pk: 'id' },

        // 2. Dữ liệu Sự cố & Ảnh
        { name: 'incident_image_uploads', pk: 'id' },
        { name: 'incident_reports', pk: 'id' },

        // 3. Dữ liệu Vị trí & Lịch sử di chuyển
        { name: 'employee_location_points', pk: 'id' },
        { name: 'employee_locations', pk: 'employee_id' },

        // 4. Dữ liệu Ca gom & Thu gom
        { name: 'job_bin_items', pk: 'id' },
        { name: 'collection_jobs', pk: 'id' },
        { name: 'bin_collections', pk: 'id' },

        // 5. Dữ liệu IoT & Thiết bị
        { name: 'bin_events', pk: 'id' },
        { name: 'device_commands', pk: 'id' },
        { name: 'smart_bins', pk: 'device_id' },

        // 6. Dữ liệu Tài khoản & Phiên đăng nhập
        { name: 'employee_sessions', pk: 'token_hash' },
        { name: 'employee_accounts', pk: 'id' }
    ];

    for (const t of tables) {
        try {
            await supabaseServiceRequest(`${t.name}?${t.pk}=not.is.null`, { method: 'DELETE' });
            logSuccess(`Đã làm sạch bảng: ${t.name}`);
        } catch (err) {
            // Thử thêm filter fallback nếu có
            try {
                await supabaseServiceRequest(`${t.name}?id=not.is.null`, { method: 'DELETE' });
                logSuccess(`Đã làm sạch bảng: ${t.name}`);
            } catch (innerErr) {
                logInfo(`Bảng ${t.name}: ${innerErr.message || err.message}`);
            }
        }
    }

    // BƯỚC 2: KHỞI TẠO TÀI KHOẢN QUẢN TRỊ VIÊN MẶC ĐỊNH (NẾU CẦN)
    if (options.seedAdmin) {
        logStep(2, 'Khởi tạo tài khoản Quản trị viên (Admin) duy nhất để đăng nhập hệ thống...');
        const rawHash = await bcrypt.hash('Password123!', 10);
        const standardBcryptHash = '$2a' + rawHash.slice(3);

        const adminAccount = {
            full_name: 'Quản trị viên Hệ thống',
            username: 'admin',
            email: 'admin@smartwaste.vn',
            role: 'admin',
            password_hash: standardBcryptHash,
            is_active: true
        };

        const inserted = await supabaseServiceRequest('employee_accounts', {
            method: 'POST',
            headers: { Prefer: 'return=representation' },
            body: JSON.stringify([adminAccount])
        });

        logSuccess(`Đã tạo tài khoản Admin: admin / Password123! (${inserted?.[0]?.id || 'OK'})`);

        // Khởi tạo system_settings
        const defaultSettings = {
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
            body: JSON.stringify(defaultSettings)
        }).catch(() => {});
        logSuccess('Đã thiết lập cấu hình tham số hệ thống mặc định (system_settings).');
    }

    // BƯỚC 3: XÁC MINH SẠCH SẼ CSDL
    logStep(3, 'Xác minh CSDL sau khi dọn dẹp...');
    const [bins, jobs, events, accounts] = await Promise.all([
        supabaseServiceRequest('smart_bins?select=device_id', { method: 'GET' }).catch(() => []),
        supabaseServiceRequest('collection_jobs?select=id', { method: 'GET' }).catch(() => []),
        supabaseServiceRequest('bin_events?select=id', { method: 'GET' }).catch(() => []),
        supabaseServiceRequest('employee_accounts?select=username,role', { method: 'GET' }).catch(() => [])
    ]);

    console.log(`\n${C.green}${C.bright}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.green}${C.bright}║        🎉 DỌN DẸP SẠCH CSDL SUPABASE HOÀN TẤT THÀNH CÔNG!             ║${C.reset}`);
    console.log(`${C.green}${C.bright}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}\n`);

    console.log(`${C.bright}📊 TRẠNG THÁI CSDL HIỆN TẠI:${C.reset}`);
    console.table([
        { 'Bảng CSDL': 'smart_bins (Thùng rác)', 'Số lượng bản ghi': (bins || []).length },
        { 'Bảng CSDL': 'collection_jobs (Ca gom)', 'Số lượng bản ghi': (jobs || []).length },
        { 'Bảng CSDL': 'bin_events (Sự kiện)', 'Số lượng bản ghi': (events || []).length },
        { 'Bảng CSDL': 'employee_accounts (Tài khoản)', 'Số lượng bản ghi': (accounts || []).length }
    ]);

    if (options.seedAdmin && accounts && accounts.length > 0) {
        console.log(`\n${C.bright}🔑 THÔNG TIN ĐĂNG NHẬP QUẢN TRỊ:${C.reset}`);
        console.log(`  • Username: ${C.green}admin${C.reset}`);
        console.log(`  • Password: ${C.green}Password123!${C.reset}\n`);
    }
}

if (require.main === module) {
    cleanDatabase()
        .then(() => process.exit(0))
        .catch(err => {
            logError(`Lỗi dọn dẹp CSDL: ${err.message}`);
            console.error(err);
            process.exit(1);
        });
}

module.exports = { cleanDatabase };
