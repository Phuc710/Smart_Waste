'use strict';

const { supabaseServiceRequest } = require('../core/supabase');
const jobsDb = require('./jobsDb');

const ESTIMATED_KG_PER_COLLECTION = 40.0;
const HO_CHI_MINH_OFFSET = '+07:00';

function hoChiMinhDayBounds(now = new Date()) {
    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Asia/Ho_Chi_Minh',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    }).formatToParts(now);
    const datePart = (type) => parts.find((part) => part.type === type)?.value;
    const dateString = `${datePart('year')}-${datePart('month')}-${datePart('day')}`;
    const start = `${dateString}T00:00:00${HO_CHI_MINH_OFFSET}`;
    const endDate = new Date(`${start}`).getTime() + 24 * 60 * 60 * 1000;
    return { date: dateString, start, end: new Date(endDate).toISOString() };
}

function asNumber(value) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
}

async function getHome(employeeId, tokenHash) {
    const bounds = hoChiMinhDayBounds();

    // Query active job and all completed jobs for this employee
    const completedJobsResource = `collection_jobs?select=id,target_bin_ids,route_data,completed_at&employee_id=eq.${encodeURIComponent(employeeId)}&status=eq.COMPLETED&order=completed_at.desc`;

    const [activeJob, completedJobs] = await Promise.all([
        jobsDb.getEmployeeActiveJob(employeeId).catch(() => null),
        supabaseServiceRequest(completedJobsResource).catch(() => [])
    ]);

    const jobsList = Array.isArray(completedJobs) ? completedJobs : [];

    // Calculate total collection stats across completed jobs
    let totalBinsCollected = 0;
    let totalDistanceMeters = 0;

    for (const job of jobsList) {
        const binCount = Array.isArray(job.target_bin_ids) ? job.target_bin_ids.length : 0;
        totalBinsCollected += binCount;
        totalDistanceMeters += asNumber(job?.route_data?.distanceMeters);
    }

    const estimatedWeightKg = Math.round(totalBinsCollected * ESTIMATED_KG_PER_COLLECTION);

    return {
        job: activeJob ? await jobsDb.attachProgress(activeJob) : null,
        stats: {
            collectionCount: totalBinsCollected,
            distanceMeters: Math.round(totalDistanceMeters),
            estimatedWeightKg: estimatedWeightKg,
            estimateKgPerCollection: ESTIMATED_KG_PER_COLLECTION,
            day: bounds.date,
            timezone: 'Asia/Ho_Chi_Minh'
        }
    };
}

module.exports = { getHome, hoChiMinhDayBounds };
