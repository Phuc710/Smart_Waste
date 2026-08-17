'use strict';

const stateStore = require('../core/stateStore');
const { supabaseRequest } = require('../core/supabase');
const { isBinOnline, isEmployeeOnline } = require('../jobs/binLivenessWorker');
const configService = require('./configService');
const jobsDb = require('./jobsDb');

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
    
    try {
        activeJobs = await jobsDb.getActiveJobs();
    } catch (_) {}

    try {
        completedJobs = await supabaseRequest(
            'collection_jobs?status=eq.COMPLETED&select=id,completed_at,target_bin_ids&order=completed_at.desc&limit=100'
        );
    } catch (_) {}

    const totalCompletedJobsCount = Array.isArray(completedJobs) ? completedJobs.length : 0;
    let totalCollectedKg = 0;
    for (const c of completedJobs || []) {
        const count = Array.isArray(c.target_bin_ids) ? c.target_bin_ids.length : 1;
        totalCollectedKg += count * 85;
    }
    const totalTons = Number((totalCollectedKg / 1000).toFixed(1));

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
        totalTons: totalTons,
        updatedAt: new Date().toISOString()
    };
}

module.exports = {
    getDashboardStats
};
