'use strict';

/**
 * =============================================================================
 * SMARTWASTE — MASTER TEST RUNNER & SUITE ORCHESTRATOR
 * Tự động thực thi toàn bộ chuỗi kiểm thử theo quy chuẩn Senior QA/DevOps:
 *   1. [00] Clean & Seed CSDL chuẩn (Database Sandbox)
 *   2. [02] Kiểm thử Thông báo Mobile Realtime (Socket.IO + Push)
 *   3. [03] Kiểm thử Bấm Mở Nắp & 2-Way Handshake ACK (<4.5s)
 *   4. [04] Kiểm thử Lỗi, Timeout 504 & Bảo mật Zero-Trust 403
 *   5. [05] Kiểm thử Thuật toán Gợi ý Xe gần nhất (Haversine Distance)
 * =============================================================================
 */

const { fork } = require('child_process');
const path = require('path');

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

const TEST_SUITES = [
    {
        id: '00',
        file: '00_clean_and_seed_data.js',
        name: 'Dọn dẹp CSDL & Nạp dữ liệu mẫu chuẩn',
        desc: 'Khởi tạo Admin, 3 Drivers, 6 Thùng rác Q.1 TP.HCM, Ca gom JOB_HCM_001'
    },
    {
        id: '02',
        file: '02_test_mobile_notifications.js',
        name: 'Kiểm thử Thông báo Mobile Realtime',
        desc: 'Xác thực 4 loại thông báo: Job Assigned, Job Cancelled, Overfull Alert, Broadcast'
    },
    {
        id: '03',
        file: '03_test_lid_open_ack.js',
        name: 'Kiểm thử Mở nắp & 2-Way Handshake ACK',
        desc: 'Chu trình điều khiển phần cứng Mobile -> Server -> ESP32 -> Mobile (<4.5s)'
    },
    {
        id: '04',
        file: '04_test_failure_and_security.js',
        name: 'Kiểm thử Ca lỗi & Bảo mật Zero-Trust',
        desc: 'Timeout 504, Zero-Trust 403, Unauthorized 401, Invalid Action 400'
    },
    {
        id: '05',
        file: '05_test_nearest_driver_dispatch.js',
        name: 'Kiểm thử Thuật toán Gợi ý Xe gần nhất',
        desc: 'Tính khoảng cách Haversine & tự động điều phối xe khi rác đầy >= 85%'
    }
];

function runSingleTest(testFile) {
    return new Promise((resolve) => {
        const filePath = path.join(__dirname, testFile);
        const startTime = Date.now();
        const child = fork(filePath, [], { stdio: 'inherit' });

        child.on('exit', (code) => {
            const durationMs = Date.now() - startTime;
            resolve({ ok: code === 0, code, durationMs });
        });
    });
}

async function runAllSuites() {
    console.clear();
    console.log(`${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║   ${C.bright}SMARTWASTE — MASTER TEST RUNNER (ENTERPRISE TEST SUITE)             ${C.reset}${C.cyan}║${C.reset}`);
    console.log(`${C.cyan}║   Phiên bản: 1.0.0 • Môi trường: Development / Testing Engine         ║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}\n`);

    const results = [];
    const globalStart = Date.now();

    for (let i = 0; i < TEST_SUITES.length; i++) {
        const suite = TEST_SUITES[i];
        console.log(`\n${C.yellow}═══════════════════════════════════════════════════════════════════════${C.reset}`);
        console.log(`${C.bright}[SUITE ${i + 1}/${TEST_SUITES.length}] ${suite.name}${C.reset}`);
        console.log(`${C.dim}File: tests/${suite.file} — ${suite.desc}${C.reset}`);
        console.log(`${C.yellow}═══════════════════════════════════════════════════════════════════════${C.reset}\n`);

        const res = await runSingleTest(suite.file);
        results.push({
            ...suite,
            passed: res.ok,
            duration: `${(res.durationMs / 1000).toFixed(2)}s`
        });

        if (!res.ok) {
            console.log(`\n${C.red}✖ Bài test '${suite.name}' thất bại! Tiếp tục các bài tiếp theo...${C.reset}`);
        }
    }

    const totalDuration = ((Date.now() - globalStart) / 1000).toFixed(2);
    const passedCount = results.filter(r => r.passed).length;
    const totalCount = results.length;
    const allPassed = passedCount === totalCount;

    console.log(`\n\n${C.cyan}╔═══════════════════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.cyan}║                   📊 BẢNG TỔNG KẾT KẾT QUẢ KIỂM THỬ                   ║${C.reset}`);
    console.log(`${C.cyan}╚═══════════════════════════════════════════════════════════════════════╝${C.reset}\n`);

    console.table(results.map(r => ({
        'STT': r.id,
        'Tên Kịch Bản Kiểm Thử': r.name,
        'File Test': r.file,
        'Thời gian': r.duration,
        'Kết quả': r.passed ? '✔ PASS' : '✖ FAIL'
    })));

    console.log(`\n${C.bright}Tổng thời gian:${C.reset} ${totalDuration}s`);
    console.log(`${C.bright}Tỷ lệ thành công:${C.reset} ${passedCount}/${totalCount} (${Math.round((passedCount / totalCount) * 100)}%)\n`);

    if (allPassed) {
        console.log(`${C.green}${C.bright}🎉 CHÚC MỪNG: TOÀN BỘ ${totalCount} BỘ KIỂM THỬ ĐÃ PASS 100%! HỆ THỐNG ĐÃ SẴN SÀNG.${C.reset}\n`);
        process.exit(0);
    } else {
        console.log(`${C.red}${C.bright}⚠ CẢNH BÁO: CÓ ${totalCount - passedCount} BÀI TEST THẤT BẠI. HÃY KIỂM TRA LẠI LOG CHI TIẾT!${C.reset}\n`);
        process.exit(1);
    }
}

if (require.main === module) {
    runAllSuites();
}

module.exports = { runAllSuites };
