'use strict';

const { supabaseServiceRequest, supabaseRequest } = require('./src/core/supabase');
const logger = require('./src/core/logger');

// 1. Realistic Smart Bins across Ho Chi Minh City
const SAMPLE_BINS = [
    {
        device_id: 'BIN_HCM_01',
        name: 'Thùng rác Chợ Bến Thành',
        location: 'Cửa Nam Chợ Bến Thành, Đường Lê Thánh Tôn, Quận 1',
        latitude: 10.7725,
        longitude: 106.6980,
        level_percent: 45,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 55.0,
        dist_user: 120.0
    },
    {
        device_id: 'BIN_HCM_02',
        name: 'Thùng rác Phố Đi Bộ Nguyễn Huệ',
        location: 'Khu vực Tượng đài Bác Hồ, Nguyễn Huệ, Quận 1',
        latitude: 10.7743,
        longitude: 106.7032,
        level_percent: 92,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 8.0,
        dist_user: 80.0
    },
    {
        device_id: 'BIN_HCM_03',
        name: 'Thùng rác Nhà Thờ Đức Bà',
        location: 'Công Xã Paris, Phường Bến Nghé, Quận 1',
        latitude: 10.7798,
        longitude: 106.6990,
        level_percent: 30,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 70.0,
        dist_user: 150.0
    },
    {
        device_id: 'BIN_HCM_04',
        name: 'Thùng rác Landmark 81',
        location: 'Công viên Central Park, Vinhomes Central Park, Bình Thạnh',
        latitude: 10.7950,
        longitude: 106.7219,
        level_percent: 88,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 12.0,
        dist_user: 60.0
    },
    {
        device_id: 'BIN_HCM_05',
        name: 'Thùng rác Hồ Con Rùa',
        location: 'Vòng xoay Công Trường Quốc Tế, Quận 3',
        latitude: 10.7827,
        longitude: 106.6960,
        level_percent: 25,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 75.0,
        dist_user: 140.0
    },
    {
        device_id: 'BIN_HCM_06',
        name: 'Thùng rác Công Viên Tao Đàn',
        location: 'Khu vui chơi Trương Định, Quận 1',
        latitude: 10.7745,
        longitude: 106.6917,
        level_percent: 62,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 38.0,
        dist_user: 110.0
    },
    {
        device_id: 'BIN_HCM_07',
        name: 'Thùng rác Sân Bay Tân Sơn Nhất',
        location: 'Ga Quốc Tế T2, Trường Sơn, Tân Bình',
        latitude: 10.8185,
        longitude: 106.6588,
        level_percent: 95,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 5.0,
        dist_user: 45.0
    },
    {
        device_id: 'BIN_HCM_08',
        name: 'Thùng rác ĐH Bách Khoa TP.HCM',
        location: 'Khu A Cổng Lý Thường Kiệt, Quận 10',
        latitude: 10.7721,
        longitude: 106.6579,
        level_percent: 50,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 50.0,
        dist_user: 130.0
    },
    {
        device_id: 'BIN_HCM_09',
        name: 'Thùng rác Chợ Lớn (Bình Tây)',
        location: 'Tháp Mười, Phường 2, Quận 6',
        latitude: 10.7533,
        longitude: 106.6534,
        level_percent: 82,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 18.0,
        dist_user: 70.0
    },
    {
        device_id: 'BIN_HCM_10',
        name: 'Thùng rác Cầu Ánh Sao',
        location: 'Khu Đô Thị Phú Mỹ Hưng, Tân Phú, Quận 7',
        latitude: 10.7297,
        longitude: 106.7202,
        level_percent: 20,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 80.0,
        dist_user: 160.0
    },
    {
        device_id: 'BIN_HCM_11',
        name: 'Thùng rác Bến Bạch Đằng',
        location: 'Công viên Bến Bạch Đằng, Tôn Đức Thắng, Quận 1',
        latitude: 10.7738,
        longitude: 106.7065,
        level_percent: 86,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 14.0,
        dist_user: 65.0
    },
    {
        device_id: 'BIN_HCM_12',
        name: 'Thùng rác Thảo Cầm Viên',
        location: 'Cổng Nguyễn Bỉnh Khiêm, Bến Nghé, Quận 1',
        latitude: 10.7875,
        longitude: 106.7053,
        level_percent: 35,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true,
        servo_angle: 0,
        dist_level: 65.0,
        dist_user: 140.0
    }
];

async function seedData() {
    logger.info('Seed', '=== BẮT ĐẦU CLEAN VÀ NẠP CƠ SỞ DỮ LIỆU CHUẨN CHO TÀI XẾ TEST ===');

    // 1. Ensure user "test12345" / "test" is found
    logger.info('Seed', '1. Tìm tài khoản test12345 đang đăng nhập trên điện thoại...');
    let testUser = null;
    try {
        const users = await supabaseServiceRequest('employee_accounts?select=id,username,full_name,role');
        testUser = users.find(u => u.username === 'test12345' || u.username === 'test' || u.full_name?.toLowerCase() === 'test');
        if (testUser) {
            logger.info('Seed', `✓ Đã tìm thấy tài khoản test: ${testUser.id} (${testUser.username} - ${testUser.full_name})`);
        }
    } catch (e) {
        logger.warn('Seed', `Lỗi query account: ${e.message}`);
    }

    if (!testUser) {
        testUser = { id: 'a49dbbe7-b2b2-44b6-8db8-11b827c5c6fb', full_name: 'test', username: 'test12345' };
    }

    const testEmployeeId = testUser.id;
    const testEmployeeName = testUser.full_name || 'test';

    // 2. Clean old test data
    logger.info('Seed', '2. Dọn dẹp dữ liệu cũ (job_bin_items, collection_jobs, incident_reports)...');
    try {
        await supabaseServiceRequest('job_bin_items?id=gt.0', { method: 'DELETE' });
        await supabaseServiceRequest('collection_jobs?id=neq.none', { method: 'DELETE' });
        await supabaseServiceRequest('incident_reports?id=gt.0', { method: 'DELETE' });
        await supabaseServiceRequest('bin_collections?id=gt.0', { method: 'DELETE' });
        logger.info('Seed', '✓ Đã dọn dẹp sạch sẽ toàn bộ ca làm và sự cố cũ.');
    } catch (e) {
        logger.warn('Seed', `Dọn dẹp cũ: ${e.message}`);
    }

    // 3. Insert / Update Smart Bins
    logger.info('Seed', '3. Nạp 12 điểm thùng rác thông minh tại TP.HCM...');
    for (const bin of SAMPLE_BINS) {
        try {
            await supabaseServiceRequest('smart_bins?on_conflict=device_id', {
                method: 'POST',
                headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
                body: JSON.stringify({
                    ...bin,
                    collection_status: 'IDLE',
                    collection_employee_id: null,
                    collection_employee_name: null,
                    last_seen: new Date().toISOString(),
                    updated_at: new Date().toISOString()
                })
            });
            logger.info('Seed', `  ✓ Đã nạp ${bin.device_id} - ${bin.name} (${bin.level_percent}%)`);
        } catch (e) {
            logger.error('Seed', `  ✗ Lỗi nạp ${bin.device_id}: ${e.message}`);
        }
    }

    // 4. Seed Active Assigned Job for user "test" (Status: ASSIGNED with live countdown)
    logger.info('Seed', '4. Tạo Ca Làm Mới (ASSIGNED) cho tài xế test...');
    const now = new Date();
    const assignedTime = new Date(now.getTime() - 60 * 1000); // 1 minute ago (leaving ~4 mins countdown)
    const activeJobId = `JOB_${Date.now()}`;
    const activeTargetBins = ['BIN_HCM_02', 'BIN_HCM_04', 'BIN_HCM_07'];

    try {
        await supabaseServiceRequest('collection_jobs', {
            method: 'POST',
            body: JSON.stringify({
                id: activeJobId,
                employee_id: testEmployeeId,
                employee_name: testEmployeeName,
                source: 'ADMIN_ASSIGNED',
                status: 'ASSIGNED',
                target_bin_ids: activeTargetBins,
                assigned_at: assignedTime.toISOString(),
                created_at: assignedTime.toISOString(),
                route_data: {
                    distanceMeters: 4800,
                    durationSeconds: 1560,
                    optimizedOrder: activeTargetBins,
                    coordinates: [
                        [106.7032, 10.7743],
                        [106.7219, 10.7950],
                        [106.6588, 10.8185]
                    ]
                }
            })
        });

        // Insert job items
        for (const bId of activeTargetBins) {
            await supabaseServiceRequest('job_bin_items', {
                method: 'POST',
                body: JSON.stringify({
                    job_id: activeJobId,
                    bin_id: bId,
                    status: 'PENDING'
                })
            });
        }
        logger.info('Seed', `✓ Đã tạo ca đang giao #${activeJobId} với 3 điểm: ${activeTargetBins.join(', ')}`);
    } catch (e) {
        logger.error('Seed', `✗ Lỗi tạo active job: ${e.message}`);
    }

    // 5. Seed History Jobs (Completed & Cancelled)
    logger.info('Seed', '5. Tạo lịch sử các ca đã hoàn thành và hủy...');
    
    // History 1: Hoàn thành hôm qua
    const hist1Id = 'JOB_1723798000';
    const hist1Bins = ['BIN_HCM_01', 'BIN_HCM_03', 'BIN_HCM_05', 'BIN_HCM_06'];
    const yesterday = new Date(now.getTime() - 24 * 3600 * 1000);
    try {
        await supabaseServiceRequest('collection_jobs', {
            method: 'POST',
            body: JSON.stringify({
                id: hist1Id,
                employee_id: testEmployeeId,
                employee_name: testEmployeeName,
                source: 'ADMIN_ASSIGNED',
                status: 'COMPLETED',
                target_bin_ids: hist1Bins,
                assigned_at: new Date(yesterday.getTime() - 3600 * 1000).toISOString(),
                started_at: new Date(yesterday.getTime() - 3000 * 1000).toISOString(),
                completed_at: yesterday.toISOString(),
                created_at: new Date(yesterday.getTime() - 3600 * 1000).toISOString(),
                route_data: {
                    distanceMeters: 5200,
                    durationSeconds: 1800,
                    optimizedOrder: hist1Bins
                }
            })
        });
        for (const bId of hist1Bins) {
            await supabaseServiceRequest('job_bin_items', {
                method: 'POST',
                body: JSON.stringify({
                    job_id: hist1Id,
                    bin_id: bId,
                    status: 'COLLECTED',
                    collected_at: yesterday.toISOString()
                })
            });
        }
        logger.info('Seed', `✓ Đã tạo ca lịch sử hoàn thành #${hist1Id} (4/4 điểm)`);
    } catch (e) {
        logger.warn('Seed', `Lỗi tạo hist1: ${e.message}`);
    }

    // History 2: Hoàn thành 2 ngày trước
    const hist2Id = 'JOB_1723712000';
    const hist2Bins = ['BIN_HCM_08', 'BIN_HCM_09', 'BIN_HCM_10'];
    const twoDaysAgo = new Date(now.getTime() - 48 * 3600 * 1000);
    try {
        await supabaseServiceRequest('collection_jobs', {
            method: 'POST',
            body: JSON.stringify({
                id: hist2Id,
                employee_id: testEmployeeId,
                employee_name: testEmployeeName,
                source: 'STAFF_SELF_PICK',
                status: 'COMPLETED',
                target_bin_ids: hist2Bins,
                assigned_at: new Date(twoDaysAgo.getTime() - 2400 * 1000).toISOString(),
                started_at: new Date(twoDaysAgo.getTime() - 2400 * 1000).toISOString(),
                completed_at: twoDaysAgo.toISOString(),
                created_at: new Date(twoDaysAgo.getTime() - 2400 * 1000).toISOString(),
                route_data: {
                    distanceMeters: 7400,
                    durationSeconds: 2100,
                    optimizedOrder: hist2Bins
                }
            })
        });
        for (const bId of hist2Bins) {
            await supabaseServiceRequest('job_bin_items', {
                method: 'POST',
                body: JSON.stringify({
                    job_id: hist2Id,
                    bin_id: bId,
                    status: 'COLLECTED',
                    collected_at: twoDaysAgo.toISOString()
                })
            });
        }
        logger.info('Seed', `✓ Đã tạo ca lịch sử hoàn thành #${hist2Id} (3/3 điểm)`);
    } catch (e) {
        logger.warn('Seed', `Lỗi tạo hist2: ${e.message}`);
    }

    // History 3: Đã hủy do kẹt xe
    const hist3Id = 'JOB_1723654000';
    const hist3Bins = ['BIN_HCM_11', 'BIN_HCM_12'];
    const threeDaysAgo = new Date(now.getTime() - 72 * 3600 * 1000);
    try {
        await supabaseServiceRequest('collection_jobs', {
            method: 'POST',
            body: JSON.stringify({
                id: hist3Id,
                employee_id: testEmployeeId,
                employee_name: testEmployeeName,
                source: 'ADMIN_ASSIGNED',
                status: 'CANCELLED',
                pause_reason: 'Kẹt xe đường Tôn Đức Thắng',
                target_bin_ids: hist3Bins,
                assigned_at: new Date(threeDaysAgo.getTime() - 1800 * 1000).toISOString(),
                cancelled_at: threeDaysAgo.toISOString(),
                created_at: new Date(threeDaysAgo.getTime() - 1800 * 1000).toISOString(),
                route_data: {
                    distanceMeters: 3100,
                    durationSeconds: 1200
                }
            })
        });
        logger.info('Seed', `✓ Đã tạo ca lịch sử đã hủy #${hist3Id}`);
    } catch (e) {
        logger.warn('Seed', `Lỗi tạo hist3: ${e.message}`);
    }

    // 6. Seed Incidents
    logger.info('Seed', '6. Tạo một vài sự cố mẫu...');
    try {
        await supabaseServiceRequest('incident_reports', {
            method: 'POST',
            body: JSON.stringify({
                device_id: 'BIN_HCM_02',
                employee_id: testEmployeeId,
                employee_name: testEmployeeName,
                reason: 'Rác quá đầy tràn ra ngoài',
                description: 'Thùng rác phố đi bộ quá tải vào giờ cao điểm, rác tràn nắp.',
                status: 'NEW',
                created_at: new Date(now.getTime() - 15 * 60 * 1000).toISOString()
            })
        });
        await supabaseServiceRequest('incident_reports', {
            method: 'POST',
            body: JSON.stringify({
                device_id: 'BIN_HCM_07',
                employee_id: testEmployeeId,
                employee_name: testEmployeeName,
                reason: 'Kẹt nắp cơ khí',
                description: 'Nắp mở kêu cọt kẹt, cảm biến siêu âm chập chờn.',
                status: 'RESOLVED',
                created_at: new Date(now.getTime() - 5 * 3600 * 1000).toISOString(),
                resolved_at: new Date(now.getTime() - 2 * 3600 * 1000).toISOString()
            })
        });
        logger.info('Seed', '✓ Đã tạo 2 sự cố mẫu.');
    } catch (e) {
        logger.warn('Seed', `Lỗi tạo sự cố: ${e.message}`);
    }

    logger.info('Seed', '=== HOÀN TẤT NẠP DỮ LIỆU MẪU VIỆT NAM THÀNH CÔNG 100%! ===');
}

seedData().catch(err => {
    logger.error('Seed Failed', err.message);
    process.exit(1);
});
