'use strict';

const { supabaseRequest } = require('./src/core/supabase');
const logger = require('./src/core/logger');

const SAMPLE_BINS = [
    {
        device_id: 'BIN_HCM_01',
        name: 'Thùng rác Chợ Bến Thành',
        location: 'Cửa Nam Chợ Bến Thành, Đường Lê Thánh Tôn, Quận 1',
        latitude: 10.7725,
        longitude: 106.6980,
        level_percent: 78,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true
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
        is_online: true
    },
    {
        device_id: 'BIN_HCM_03',
        name: 'Thùng rác Nhà Thờ Đức Bà',
        location: 'Công Xã Paris, Phường Bến Nghé, Quận 1',
        latitude: 10.7798,
        longitude: 106.6990,
        level_percent: 45,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true
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
        is_online: true
    },
    {
        device_id: 'BIN_HCM_05',
        name: 'Thùng rác Hồ Con Rùa',
        location: 'Vòng xoay Công Trường Quốc Tế, Quận 3',
        latitude: 10.7827,
        longitude: 106.6960,
        level_percent: 30,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true
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
        is_online: true
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
        is_online: true
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
        is_online: true
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
        is_online: true
    },
    {
        device_id: 'BIN_HCM_10',
        name: 'Thùng rác Cầu Ánh Sao',
        location: 'Khu Đô Thị Phú Mỹ Hưng, Tân Phú, Quận 7',
        latitude: 10.7297,
        longitude: 106.7202,
        level_percent: 25,
        state: 'CLOSED',
        control_mode: 'AUTO',
        is_online: true
    }
];

async function seedData() {
    logger.info('Seed', 'Bắt đầu nạp dữ liệu mẫu TP.HCM vào Supabase...');
    
    for (const bin of SAMPLE_BINS) {
        try {
            await supabaseRequest('smart_bins?on_conflict=device_id', {
                method: 'POST',
                headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
                body: JSON.stringify({
                    ...bin,
                    last_seen: new Date().toISOString(),
                    updated_at: new Date().toISOString()
                })
            });
            logger.info('Seed', `✓ Đã nạp ${bin.device_id} (${bin.name})`);
        } catch (error) {
            logger.error('Seed', `✗ Lỗi nạp ${bin.device_id}:`, error.message);
        }
    }
    
    logger.info('Seed', 'Hoàn tất nạp dữ liệu mẫu!');
}

seedData().catch(err => {
    logger.error('Seed Failed', err.message);
    process.exit(1);
});
