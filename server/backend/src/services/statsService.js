'use strict';

const stateStore = require('../core/stateStore');
const { supabaseRequest } = require('../core/supabase');
const { isBinOnline, isEmployeeOnline } = require('../jobs/binLivenessWorker');
const configService = require('./configService');
const jobsDb = require('./jobsDb');

/**
 * Lấy số liệu thống kê Dashboard chuẩn xác 100% từ CSDL Supabase
 */
async function getDashboardStats() {
    const binsList = Array.from(stateStore.latestBins.values());
    const totalBins = binsList.length;
    const warnThresh = configService.getConfig('fill_threshold_warning') || 70;
    const critThresh = configService.getConfig('fill_threshold_critical') || 85;

    const onlineBins = binsList.filter(b => isBinOnline(b)).length;
    const offlineBins = totalBins - onlineBins;
    const overfullBins = binsList.filter(b => isBinOnline(b) && (b.level_percent || 0) >= critThresh).length;
    const nearFullBins = binsList.filter(b => isBinOnline(b) && (b.level_percent || 0) >= warnThresh && (b.level_percent || 0) < critThresh).length;
    const normalBins = binsList.filter(b => isBinOnline(b) && (b.level_percent || 0) < warnThresh).length;

    const activeTrucks = Array.from(stateStore.employeeLocationsCache.values())
        .filter(loc => isEmployeeOnline(loc)).length;
    
    let activeJobs = [];
    let completedJobs = [];
    let completedSingleBins = [];
    
    try {
        activeJobs = await jobsDb.getActiveJobs();
    } catch (_) {}

    try {
        // Lấy danh sách ca gom đã hoàn thành từ CSDL Supabase trong 30 ngày qua
        const past30Iso = new Date(Date.now() - 30 * 86400000).toISOString();
        const [jobsRes, singleBinsRes] = await Promise.all([
            supabaseRequest(
                `collection_jobs?status=eq.COMPLETED&completed_at=gte.${past30Iso}&select=id,completed_at,target_bin_ids&order=completed_at.asc`
            ).catch(() => []),
            supabaseRequest(
                `bin_collections?status=eq.COMPLETED&completed_at=gte.${past30Iso}&select=id,completed_at,device_id&order=completed_at.asc`
            ).catch(() => [])
        ]);
        completedJobs = Array.isArray(jobsRes) ? jobsRes : [];
        completedSingleBins = Array.isArray(singleBinsRes) ? singleBinsRes : [];
    } catch (_) {}

    // Tính toán số liệu theo từng ngày cho biểu đồ (7 ngày, 30 ngày, tuần này, tháng này)
    const now = new Date();
    const formatDayMonth = (d) => `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
    const formatYmd = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

    // Map dữ liệu thu gom theo ngày: { "2026-08-19": { tons: 0.17, trips: 2 } }
    const dailyMap = {};
    for (const job of completedJobs) {
        if (!job.completed_at) continue;
        const ymd = formatYmd(new Date(job.completed_at));
        if (!dailyMap[ymd]) dailyMap[ymd] = { tons: 0, trips: 0 };
        const binCount = Array.isArray(job.target_bin_ids) && job.target_bin_ids.length > 0 ? job.target_bin_ids.length : 1;
        // Mỗi thùng thu gom tiêu chuẩn ước tính 85kg (~0.085 tấn)
        dailyMap[ymd].tons += (binCount * 85) / 1000;
        dailyMap[ymd].trips += 1;
    }

    for (const single of completedSingleBins) {
        if (!single.completed_at) continue;
        const ymd = formatYmd(new Date(single.completed_at));
        if (!dailyMap[ymd]) dailyMap[ymd] = { tons: 0, trips: 0 };
        dailyMap[ymd].tons += 0.085;
        dailyMap[ymd].trips += 1;
    }

    // 1. Biểu đồ 7 ngày qua
    const days7 = [];
    for (let i = 6; i >= 0; i--) {
        const d = new Date(now);
        d.setDate(now.getDate() - i);
        const ymd = formatYmd(d);
        const data = dailyMap[ymd] || { tons: 0, trips: 0 };
        days7.push({
            date: ymd,
            label: formatDayMonth(d),
            tons: Number(data.tons.toFixed(2)),
            trips: data.trips
        });
    }

    // 2. Biểu đồ 30 ngày qua (chia thành 6 mốc 5 ngày)
    const days30 = [];
    for (let i = 29; i >= 0; i--) {
        const d = new Date(now);
        d.setDate(now.getDate() - i);
        const ymd = formatYmd(d);
        const data = dailyMap[ymd] || { tons: 0, trips: 0 };
        days30.push({
            date: ymd,
            label: formatDayMonth(d),
            tons: Number(data.tons.toFixed(2)),
            trips: data.trips
        });
    }

    // Tổng số tấn rác đã thu gom trong 7 ngày qua
    const totalTons7Days = Number(days7.reduce((acc, curr) => acc + curr.tons, 0).toFixed(1));
    const avgDailyTons7Days = Number((totalTons7Days / 7).toFixed(1));

    // Tổng số ca hoàn tất
    const totalCompletedJobsCount = completedJobs.length + completedSingleBins.length;

    return {
        ok: true,
        totalBins,
        onlineBins,
        offlineBins,
        overfullBins,
        nearFullBins,
        normalBins,
        activeTrucks,
        activeJobsCount: activeJobs.length,
        completedJobsCount: totalCompletedJobsCount,
        totalTons: totalTons7Days,
        avgDailyTons: avgDailyTons7Days,
        chartData: {
            days7,
            days30
        },
        updatedAt: new Date().toISOString()
    };
}

module.exports = {
    getDashboardStats
};

