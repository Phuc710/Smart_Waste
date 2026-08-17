package com.example.app_smart_waste.ui.jobs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.model.JobDisplayModel
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.utils.TimeUtils
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

class JobsViewModel(application: Application) : AndroidViewModel(application) {

    private val jobsRepo = JobsRepository(application)
    private val binsRepo = BinsRepository(application)

    // 1. Live Countdown Ticker (1-second precision) & Admin Assign Timeout
    private val _countdownTicker = MutableStateFlow(System.currentTimeMillis())
    val countdownTicker: StateFlow<Long> = _countdownTicker.asStateFlow()

    private val _assignTimeoutMinutes = MutableStateFlow(
        com.example.app_smart_waste.core.storage.AppConfig.getAssignTimeoutMinutes(application)
    )
    val assignTimeoutMinutes: StateFlow<Int> = _assignTimeoutMinutes.asStateFlow()

    // 2. Top-Level Selected Tab: 0 = "Đang xử lý", 1 = "Lịch sử thu gom"
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // 3. Sub-Tab in Active Jobs: 0 = "Tất cả", 1 = "Đang chờ" (Assigned/Accepted), 2 = "Đang làm" (InProgress/Paused)
    private val _activeSubTab = MutableStateFlow(0)
    val activeSubTab: StateFlow<Int> = _activeSubTab.asStateFlow()

    // 4. History Filter: "ALL", "COMPLETED", "CANCELLED"
    private val _historyFilter = MutableStateFlow("ALL")
    val historyFilter: StateFlow<String> = _historyFilter.asStateFlow()

    // 5. Active Jobs Raw List
    private val _activeJobs = MutableStateFlow<List<JobDto>>(emptyList())
    val activeJobs: StateFlow<List<JobDto>> = _activeJobs.asStateFlow()

    // 6. Filtered Active Jobs based on activeSubTab
    val displayedActiveJobs: StateFlow<List<JobDto>> = combine(_activeJobs, _activeSubTab) { list, subTab ->
        when (subTab) {
            1 -> list.filter { it.status in listOf("ASSIGNED", "PENDING", "ACCEPTED") }
            2 -> list.filter { it.status in listOf("IN_PROGRESS", "PAUSED") }
            else -> list // 0 = Tất cả
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Counts for Badges
    val allActiveCount: StateFlow<Int> = _activeJobs.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val pendingCount: StateFlow<Int> = _activeJobs.map { list ->
        list.count { it.status in listOf("ASSIGNED", "PENDING", "ACCEPTED") }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val inProgressCount: StateFlow<Int> = _activeJobs.map { list ->
        list.count { it.status in listOf("IN_PROGRESS", "PAUSED") }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // 7. History Jobs List (100% Real from Backend API)
    private val _historyJobs = MutableStateFlow<List<JobDisplayModel>>(emptyList())
    val historyJobs: StateFlow<List<JobDisplayModel>> = _historyJobs.asStateFlow()

    // 8. Loading & Pause State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    init {
        // Start background 1-second ticker for live countdown
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _countdownTicker.value = System.currentTimeMillis()
            }
        }
        loadAllJobData()
    }

    fun selectTopTab(index: Int) {
        _selectedTab.value = index
    }

    fun selectActiveSubTab(subTabIndex: Int) {
        _activeSubTab.value = subTabIndex
    }

    fun setHistoryFilter(filter: String) {
        _historyFilter.value = filter
    }

    fun loadAllJobData() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withTimeoutOrNull(5000L) {
                    val settingsDeferred = async { jobsRepo.getSystemSettings() }
                    val activeDeferred = async { jobsRepo.getActiveJob() }
                    val historyDeferred = async { jobsRepo.getHistory(100) }
                    val binsDeferred = async { binsRepo.getBins() }

                    val settingsRes = settingsDeferred.await()
                    settingsRes.getOrNull()?.assignTimeoutMinutes?.let { mins ->
                        if (mins in 1..120) {
                            _assignTimeoutMinutes.value = mins
                            com.example.app_smart_waste.core.storage.AppConfig.setAssignTimeoutMinutes(getApplication(), mins)
                        }
                    }

                    val activeRes = activeDeferred.await()
                    val historyRes = historyDeferred.await()
                    val binsRes = binsDeferred.await()

                    val binsList = binsRes.getOrDefault(emptyList())
                    val binsMap = binsList.associateBy { it.deviceId }

                    // 1. Process Active Jobs
                    val activeJob = activeRes.getOrNull()
                    val activeList = mutableListOf<JobDto>()

                    if (activeJob != null && activeJob.status in listOf("ASSIGNED", "PENDING", "ACCEPTED", "IN_PROGRESS", "PAUSED")) {
                        activeList.add(activeJob)
                        _isPaused.value = activeJob.status == "PAUSED"
                    }
                    _activeJobs.value = activeList

                    // 2. Process History Jobs
                    val historyList = historyRes.getOrDefault(emptyList())
                    val displayHistory = mutableListOf<JobDisplayModel>()

                    historyList.forEach { j ->
                        val upper = j.status?.uppercase()?.trim() ?: ""
                        if (upper in listOf("COMPLETED", "DONE", "SUCCESS", "FINISHED", "CANCELLED", "CANCELED", "REJECTED", "EXPIRED") || j.completedAt != null) {
                            displayHistory.add(mapJobToHistoryDisplayModel(j, binsMap))
                        }
                    }
                    _historyJobs.value = displayHistory
                }
            } catch (_: Exception) {
                // Network or timeout exception
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun acceptJob(jobId: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val result = jobsRepo.acceptJob(jobId)
            if (result.isSuccess) {
                val updated = _activeJobs.value.map {
                    if (it.id == jobId) it.copy(status = "ACCEPTED") else it
                }
                _activeJobs.value = updated
                onComplete?.invoke(true)
            } else {
                onComplete?.invoke(false)
            }
        }
    }

    fun rejectJob(jobId: String, reason: String? = null, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val result = jobsRepo.rejectJob(jobId, reason)
            if (result.isSuccess) {
                _activeJobs.value = _activeJobs.value.filter { it.id != jobId }
                onComplete?.invoke(true)
                loadAllJobData()
            } else {
                onComplete?.invoke(false)
            }
        }
    }

    fun togglePauseActiveJob(reason: String = "Tạm dừng ngoài hiện trường", onComplete: ((Boolean) -> Unit)? = null) {
        val currentActive = _activeJobs.value.firstOrNull { it.status in listOf("IN_PROGRESS", "PAUSED") } ?: return
        val willPause = currentActive.status != "PAUSED"
        viewModelScope.launch {
            val result = if (willPause) {
                jobsRepo.pauseJob(currentActive.id, reason)
            } else {
                jobsRepo.resumeJob(currentActive.id)
            }
            if (result.isSuccess) {
                _isPaused.value = willPause
                val newStatus = if (willPause) "PAUSED" else "IN_PROGRESS"
                _activeJobs.value = _activeJobs.value.map {
                    if (it.id == currentActive.id) it.copy(status = newStatus) else it
                }
                onComplete?.invoke(true)
            } else {
                onComplete?.invoke(false)
            }
        }
    }

    suspend fun confirmCollectCurrentBin(jobId: String, binId: String, note: String? = null): Boolean {
        val res = jobsRepo.collectBin(jobId, binId, note)
        if (res.isSuccess) {
            loadAllJobData()
            return true
        }
        return false
    }

    private fun mapJobToHistoryDisplayModel(job: JobDto, map: Map<String, SmartBinDto>): JobDisplayModel {
        val total = job.targetBinIds?.size ?: (job.items?.size ?: 0)
        val distKm = if ((job.routeData?.distanceMeters ?: 0.0) > 0.0) ((job.routeData!!.distanceMeters!!) / 100.0).roundToInt() / 10.0 else 0.0
        val durMins = if ((job.routeData?.durationSeconds ?: 0.0) > 0.0) ((job.routeData!!.durationSeconds!!) / 60.0).toInt() else 0

        val upperStatus = job.status?.uppercase()?.trim() ?: "COMPLETED"
        val normalizedStatus = when {
            upperStatus in listOf("CANCELLED", "CANCELED", "REJECTED", "EXPIRED", "FAILED", "INCIDENT") -> "CANCELLED"
            upperStatus in listOf("COMPLETED", "DONE", "SUCCESS", "FINISHED") -> "COMPLETED"
            job.completedAt != null -> "COMPLETED"
            !job.completedBinIds.isNullOrEmpty() && job.completedBinIds.size >= total && total > 0 -> "COMPLETED"
            else -> "COMPLETED"
        }

        val timeFormatted = TimeUtils.formatHistoryJobTime(normalizedStatus, job.startedAt ?: job.assignedAt, job.completedAt)

        return JobDisplayModel(
            rawJob = job,
            displayCode = if (job.id.startsWith("JOB_") || job.id.startsWith("#")) job.id else "#JOB_${job.id}",
            jobNumber = job.id,
            timeLabel = timeFormatted,
            locationArea = "Khu vực thu gom",
            statusType = normalizedStatus,
            statusBadgeText = if (normalizedStatus == "COMPLETED") "Hoàn thành" else "Đã hủy",
            totalBins = total,
            collectedBins = if (normalizedStatus == "COMPLETED") total else (job.completedBinIds?.size ?: 0),
            distanceKm = distKm,
            durationMinutes = durMins
        )
    }
}
