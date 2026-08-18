package com.example.app_smart_waste.ui.jobs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.model.ActiveJobUiModel
import com.example.app_smart_waste.core.model.GeoCoordinate
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.JobHistoryUiModel
import com.example.app_smart_waste.core.model.JobItemDto
import com.example.app_smart_waste.core.model.JobOperation
import com.example.app_smart_waste.core.model.JobStatus
import com.example.app_smart_waste.core.model.JobStopStatus
import com.example.app_smart_waste.core.model.JobStopUiModel
import com.example.app_smart_waste.core.model.JobsActiveFilter
import com.example.app_smart_waste.core.model.JobsHistoryFilter
import com.example.app_smart_waste.core.model.JobsNetworkState
import com.example.app_smart_waste.core.model.JobsScreenState
import com.example.app_smart_waste.core.model.JobsUiState
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.utils.TimeUtils
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

// =============================================================================
// 1. JOBS USER ACTIONS & EFFECTS
// =============================================================================

sealed interface JobsAction {
    data object LoadData : JobsAction
    data object Refresh : JobsAction
    data class SelectTab(val tabIndex: Int) : JobsAction
    data class SelectStop(val binId: String) : JobsAction

    data class AcceptJob(val jobId: String) : JobsAction
    data class RejectJob(val jobId: String, val reason: String = "Từ chối") : JobsAction
    data class StartJob(val jobId: String) : JobsAction
    data class PauseJob(val jobId: String, val reason: String = "Tạm dừng") : JobsAction
    data class ResumeJob(val jobId: String) : JobsAction

    data class CollectBin(
        val jobId: String,
        val binId: String,
        val note: String? = null,
        val photoUrl: String? = null
    ) : JobsAction

    data class ApplyActiveFilter(val filter: JobsActiveFilter) : JobsAction
    data object ResetActiveFilter : JobsAction
    data class ApplyHistoryFilter(val filter: JobsHistoryFilter) : JobsAction
    data object ResetHistoryFilter : JobsAction
    data class ChangeSortOrder(val sortOrder: String) : JobsAction
    data class ChangeHistorySort(val sortOrder: String) : JobsAction
    data class SelectActiveQuickFilter(val filter: String) : JobsAction
    data class SelectHistoryQuickFilter(val filter: String) : JobsAction
}

sealed interface JobsEffect {
    data class ShowToast(val message: String) : JobsEffect
    data class NavigateToJobDetail(val jobId: String) : JobsEffect
    data class NavigateToJobExecution(val jobId: String) : JobsEffect
    data class OpenMapForJob(val jobId: String) : JobsEffect
    data class OpenMapForBin(val binId: String) : JobsEffect
    data class JobCompleted(val jobId: String, val message: String) : JobsEffect
    data class OperationFailed(val operation: String, val message: String) : JobsEffect
}

// =============================================================================
// 2. JOBS VIEW MODEL (SINGLE SOURCE OF TRUTH)
// =============================================================================

class JobsViewModel(application: Application) : AndroidViewModel(application) {

    private val jobsRepo = JobsRepository(application)
    private val binsRepo = BinsRepository(application)

    // Raw History Cache for Instant Quick Filtering
    private var allRawHistory: List<JobHistoryUiModel> = emptyList()

    // Single Source of Truth
    private val _uiState = MutableStateFlow(JobsUiState())
    val uiState: StateFlow<JobsUiState> = _uiState.asStateFlow()

    // Side Effects Channel
    private val _effectChannel = Channel<JobsEffect>(Channel.BUFFERED)
    val effects: Flow<JobsEffect> = _effectChannel.receiveAsFlow()

    // Operation Mutex & Debounce
    private var inFlightOperation: JobOperation? = null
    private var realtimeDebounceJob: Job? = null

    init {
        // Live 1-second Countdown Ticker
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(countdownTimestamp = System.currentTimeMillis()) }
            }
        }
        handleAction(JobsAction.LoadData)
    }

    // =========================================================================
    // 3. ACTION DISPATCHER
    // =========================================================================

    fun handleAction(action: JobsAction) {
        when (action) {
            is JobsAction.LoadData -> executeLoadAllJobData(isRefreshing = false)
            is JobsAction.Refresh -> {
                realtimeDebounceJob?.cancel()
                realtimeDebounceJob = viewModelScope.launch {
                    delay(250)
                    executeLoadAllJobData(isRefreshing = true)
                }
            }

            is JobsAction.SelectTab -> {
                _uiState.update { it.copy(activeTab = action.tabIndex) }
            }

            is JobsAction.SelectActiveQuickFilter -> {
                _uiState.update { it.copy(activeQuickFilter = action.filter) }
            }

            is JobsAction.SelectHistoryQuickFilter -> {
                val updatedFilter = when (action.filter) {
                    "COMPLETED" -> _uiState.value.historyFilter.copy(showCompleted = true, showCancelled = false, showExpired = false)
                    "CANCELLED" -> _uiState.value.historyFilter.copy(showCompleted = false, showCancelled = true, showExpired = false)
                    "EXPIRED" -> _uiState.value.historyFilter.copy(showCompleted = false, showCancelled = false, showExpired = true)
                    else -> _uiState.value.historyFilter.copy(showCompleted = true, showCancelled = true, showExpired = true)
                }
                _uiState.update { current ->
                    current.copy(
                        historyQuickFilter = action.filter,
                        historyFilter = updatedFilter,
                        history = sortHistoryList(filterHistoryList(allRawHistory, updatedFilter), current.sortOrder)
                    )
                }
            }

            is JobsAction.SelectStop -> {
                _uiState.update { it.copy(selectedStopId = action.binId) }
            }

            is JobsAction.AcceptJob -> executeJobTransition(action.jobId, JobOperation.Accepting) { jobsRepo.acceptJob(action.jobId) }
            is JobsAction.RejectJob -> executeJobTransition(action.jobId, JobOperation.Rejecting) { jobsRepo.rejectJob(action.jobId, action.reason) }
            is JobsAction.StartJob -> executeJobTransition(action.jobId, JobOperation.Starting) { jobsRepo.startJob(action.jobId) }
            is JobsAction.PauseJob -> executeJobTransition(action.jobId, JobOperation.Pausing) { jobsRepo.pauseJob(action.jobId, action.reason) }
            is JobsAction.ResumeJob -> executeJobTransition(action.jobId, JobOperation.Resuming) { jobsRepo.resumeJob(action.jobId) }

            is JobsAction.CollectBin -> executeCollectBin(action.jobId, action.binId, action.note, action.photoUrl)

            is JobsAction.ApplyActiveFilter -> {
                _uiState.update { it.copy(activeFilter = action.filter) }
            }

            is JobsAction.ResetActiveFilter -> {
                _uiState.update { it.copy(activeFilter = JobsActiveFilter(), activeQuickFilter = "ALL") }
            }

            is JobsAction.ApplyHistoryFilter -> {
                _uiState.update { current ->
                    val updatedOrder = action.filter.sortOrder
                    val updated = current.copy(historyFilter = action.filter, sortOrder = updatedOrder)
                    updated.copy(history = sortHistoryList(filterHistoryList(allRawHistory, action.filter), updatedOrder))
                }
            }

            is JobsAction.ResetHistoryFilter -> {
                val defaultFilter = JobsHistoryFilter(sortOrder = "NEWEST")
                _uiState.update { current ->
                    current.copy(
                        sortOrder = "NEWEST",
                        historyQuickFilter = "ALL",
                        historyFilter = defaultFilter,
                        history = sortHistoryList(filterHistoryList(allRawHistory, defaultFilter), "NEWEST")
                    )
                }
                executeLoadAllJobData(isRefreshing = false)
            }

            is JobsAction.ChangeSortOrder -> {
                val order = if (action.sortOrder == "OLDEST") "OLDEST" else "NEWEST"
                _uiState.update { current ->
                    val updatedFilter = current.historyFilter.copy(sortOrder = order)
                    val sortedHistory = sortHistoryList(filterHistoryList(allRawHistory, updatedFilter), order)
                    current.copy(
                        sortOrder = order,
                        historyFilter = updatedFilter,
                        history = sortedHistory
                    )
                }
            }

            is JobsAction.ChangeHistorySort -> {
                handleAction(JobsAction.ChangeSortOrder(action.sortOrder))
            }
        }
    }

    // =========================================================================
    // 4. ASYNC REPOSITORY OPERATIONS
    // =========================================================================

    private fun executeLoadAllJobData(isRefreshing: Boolean) {
        if (!isRefreshing && _uiState.value.screenState == JobsScreenState.InitialLoading) {
            // Keep loading indicator
        } else {
            _uiState.update { it.copy(isRefreshing = isRefreshing) }
        }

        viewModelScope.launch {
            try {
                // Tier 1: Immediate Active Job & Bins lookup (renders in 100-250ms)
                val binsDeferred = async { binsRepo.getBins() }
                val activeDeferred = async { jobsRepo.getActiveJob() }

                val activeRes = activeDeferred.await()
                val binsRes = binsDeferred.await()

                // Fast Bins Map (uses fresh bins or fallback cached bins)
                val binsList = binsRes.getOrDefault(BinsRepository.getCachedBins())
                val binsMap = binsList.associateBy { it.deviceId }

                // Process Active Job
                val rawActiveJob = activeRes.getOrNull()
                val activeUiModel = rawActiveJob?.let { mapJobToActiveUiModel(it, binsMap) }

                val assignedModel = if (activeUiModel?.status == JobStatus.ASSIGNED) activeUiModel else null
                val inProgressModel = if (activeUiModel?.status in listOf(JobStatus.ACCEPTED, JobStatus.IN_PROGRESS, JobStatus.PAUSED)) activeUiModel else null

                // Screen State Resolution for Tier 1
                val screenState = if (activeUiModel != null) {
                    JobsScreenState.Content(activeUiModel)
                } else if (activeRes.isSuccess) {
                    JobsScreenState.NoActiveJob
                } else if (_uiState.value.assignedJob != null || _uiState.value.inProgressJob != null) {
                    // Keep current content on transient network hiccup
                    _uiState.value.screenState
                } else {
                    JobsScreenState.Error("Không thể tải danh sách nhiệm vụ. Vui lòng kiểm tra kết nối.")
                }

                val networkState = if (activeRes.isFailure) {
                    JobsNetworkState.BackendUnavailable
                } else {
                    JobsNetworkState.Online
                }

                _uiState.update { current ->
                    current.copy(
                        screenState = screenState,
                        assignedJob = assignedModel,
                        inProgressJob = inProgressModel,
                        isRefreshing = false,
                        networkState = networkState
                    )
                }

                // Tier 2: Background History & System Settings (does not block Active Tab)
                launch {
                    try {
                        val historyDeferred = async { jobsRepo.getHistory(50) }
                        val settingsDeferred = async { jobsRepo.getSystemSettings() }

                        val historyRes = historyDeferred.await()
                        val settingsRes = settingsDeferred.await()

                        // Apply settings
                        settingsRes.getOrNull()?.assignTimeoutMinutes?.let { mins ->
                            if (mins in 1..120) {
                                com.example.app_smart_waste.core.storage.AppConfig.setAssignTimeoutMinutes(getApplication(), mins)
                            }
                        }

                        // Process History Jobs
                        val historyRaw = historyRes.getOrDefault(emptyList())
                        val currentBins = BinsRepository.getCachedBins().associateBy { it.deviceId }
                        val historyList = historyRaw.mapNotNull { raw ->
                            mapJobToHistoryUiModel(raw, currentBins.ifEmpty { binsMap })
                        }
                        allRawHistory = historyList
                        val filteredHistory = sortHistoryList(
                            filterHistoryList(allRawHistory, _uiState.value.historyFilter),
                            _uiState.value.sortOrder
                        )

                        _uiState.update { it.copy(history = filteredHistory, rawHistory = allRawHistory) }
                    } catch (_: Exception) {
                        // History failure does not crash or degrade active screen
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        networkState = JobsNetworkState.BackendUnavailable,
                        screenState = if (it.assignedJob == null && it.inProgressJob == null) {
                            JobsScreenState.Error("Lỗi nạp dữ liệu: ${e.message}")
                        } else it.screenState
                    )
                }
            }
        }
    }


    private fun executeJobTransition(jobId: String, operation: JobOperation, apiCall: suspend () -> Result<JobDto?>) {
        if (inFlightOperation != null || jobId.isBlank()) return

        inFlightOperation = operation
        _uiState.update { it.copy(activeOperation = operation) }

        viewModelScope.launch {
            try {
                val result = apiCall()
                if (result.isSuccess) {
                    val updated = result.getOrNull()
                    val opName = when (operation) {
                        is JobOperation.Accepting -> "Chấp nhận"
                        is JobOperation.Rejecting -> {
                            com.example.app_smart_waste.core.location.GpsTracker.getInstance(getApplication()).stopRouteTracking()
                            "Từ chối"
                        }
                        is JobOperation.Starting -> {
                            com.example.app_smart_waste.core.location.GpsTracker.getInstance(getApplication()).startRouteTracking(jobId)
                            "Bắt đầu"
                        }
                        is JobOperation.Pausing -> {
                            "Tạm dừng"
                        }
                        is JobOperation.Resuming -> {
                            com.example.app_smart_waste.core.location.GpsTracker.getInstance(getApplication()).startRouteTracking(jobId)
                            "Tiếp tục"
                        }
                        else -> "Thao tác"
                    }
                    sendEffect(JobsEffect.ShowToast("✓ $opName nhiệm vụ #${jobId} thành công!"))
                    executeLoadAllJobData(isRefreshing = false)
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Lỗi cập nhật trạng thái"
                    sendEffect(JobsEffect.OperationFailed("JobTransition", errMsg))
                    executeLoadAllJobData(isRefreshing = false) // reconcile
                }
            } catch (e: Exception) {
                sendEffect(JobsEffect.OperationFailed("JobTransition", e.message ?: "Lỗi kết nối"))
            } finally {
                inFlightOperation = null
                _uiState.update { it.copy(activeOperation = null) }
            }
        }
    }

    private fun executeCollectBin(jobId: String, binId: String, note: String?, photoUrl: String?) {
        if (inFlightOperation != null || jobId.isBlank() || binId.isBlank()) return

        val operation = JobOperation.Collecting(binId)
        inFlightOperation = operation
        _uiState.update { it.copy(activeOperation = operation) }

        viewModelScope.launch {
            try {
                val result = jobsRepo.collectBin(jobId, binId, note, photoUrl)
                if (result.isSuccess) {
                    val body = result.getOrNull()
                    if (body != null) {
                        if (body.allDone) {
                            com.example.app_smart_waste.core.location.GpsTracker.getInstance(getApplication()).stopRouteTracking()
                            sendEffect(JobsEffect.JobCompleted(jobId, "🎉 Tuyệt vời! Bạn đã hoàn thành tất cả các điểm thu gom trong ca #${jobId}!"))
                        } else if (body.idempotent) {
                            sendEffect(JobsEffect.ShowToast("Thùng #$binId đã được ghi nhận hoàn tất trước đó."))
                        } else {
                            sendEffect(JobsEffect.ShowToast("✓ Đã thu gom thùng #$binId thành công!"))
                        }
                        executeLoadAllJobData(isRefreshing = false)
                    }
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Lỗi ghi nhận thu gom"
                    sendEffect(JobsEffect.OperationFailed("CollectBin", errMsg))
                }
            } catch (e: Exception) {
                sendEffect(JobsEffect.OperationFailed("CollectBin", e.message ?: "Lỗi kết nối"))
            } finally {
                inFlightOperation = null
                _uiState.update { it.copy(activeOperation = null) }
            }
        }
    }

    private fun sendEffect(effect: JobsEffect) {
        viewModelScope.launch { _effectChannel.send(effect) }
    }

    // =========================================================================
    // 5. DOMAIN MAPPING HELPERS
    // =========================================================================

    private fun mapJobToActiveUiModel(job: JobDto, binsMap: Map<String, SmartBinDto>): ActiveJobUiModel {
        val status = JobStatus.fromString(job.status)
        val rawTargets = when {
            !job.targetBinIds.isNullOrEmpty() -> job.targetBinIds!!
            !job.items.isNullOrEmpty() -> job.items!!.map { it.binId }
            else -> emptyList()
        }
        val completedIds = job.completedBinIds.orEmpty().toSet()

        // Build sorted stops list
        val rawItems = job.items.orEmpty()
        val stops = if (rawItems.isNotEmpty()) {
            rawItems.sortedBy { it.sortOrder ?: 0 }.mapIndexed { index, item ->
                val bin = binsMap[item.binId]
                val stopStatus = when {
                    item.status == "COLLECTED" || completedIds.contains(item.binId) -> JobStopStatus.COLLECTED
                    item.status == "SKIPPED" -> JobStopStatus.SKIPPED
                    item.status == "INCIDENT" -> JobStopStatus.INCIDENT
                    else -> JobStopStatus.PENDING
                }
                JobStopUiModel(
                    binId = item.binId,
                    order = item.sortOrder ?: (index + 1),
                    status = stopStatus,
                    binName = bin?.name ?: "Thùng ${item.binId}",
                    address = bin?.location ?: "Chưa có thông tin vị trí",
                    levelPercent = bin?.levelPercent,
                    isOnline = bin?.isOnline ?: true,
                    latitude = bin?.latitude,
                    longitude = bin?.longitude,
                    collectedAt = item.collectedAt,
                    note = item.note,
                    photoUrl = item.photoUrl,
                    coordinate = if (bin?.latitude != null && bin.longitude != null) GeoCoordinate(bin.latitude, bin.longitude) else null,
                    bin = bin
                )
            }
        } else {
            rawTargets.mapIndexed { index, binId ->
                val bin = binsMap[binId]
                val isDone = completedIds.contains(binId)
                JobStopUiModel(
                    binId = binId,
                    order = index + 1,
                    status = if (isDone) JobStopStatus.COLLECTED else JobStopStatus.PENDING,
                    binName = bin?.name ?: "Thùng $binId",
                    address = bin?.location ?: "Chưa có thông tin vị trí",
                    levelPercent = bin?.levelPercent,
                    isOnline = bin?.isOnline ?: true,
                    latitude = bin?.latitude,
                    longitude = bin?.longitude,
                    coordinate = if (bin?.latitude != null && bin.longitude != null) GeoCoordinate(bin.latitude, bin.longitude) else null,
                    bin = bin
                )
            }
        }

        val totalStops = stops.size
        val completedStops = stops.count { it.status == JobStopStatus.COLLECTED }
        val progressPercent = if (totalStops > 0) (completedStops * 100 / totalStops).coerceIn(0, 100) else 0

        // Determine next stop: first PENDING stop
        val nextStop = stops.firstOrNull { it.status == JobStopStatus.PENDING }
        val stopsWithNextFlag = stops.map { stop ->
            if (nextStop != null && stop.binId == nextStop.binId) stop.copy(isNext = true) else stop.copy(isNext = false)
        }

        val distanceMeters = (job.routeData?.distanceMeters ?: (stops.size * 950.0)).roundToInt()
        val durationSeconds = (job.routeData?.durationSeconds ?: (stops.size * 360.0)).roundToInt()
        val overfullCount = stops.count { (it.levelPercent ?: 0.0) >= 85.0 }

        return ActiveJobUiModel(
            id = job.id,
            status = status,
            source = "Điều phối giao",
            employeeName = job.employeeName ?: "Nhân viên thu gom",
            version = job.version,
            stops = stopsWithNextFlag,
            completedStops = completedStops,
            totalStops = totalStops,
            progressPercent = progressPercent,
            nextStop = nextStop,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            assignedAt = job.assignedAt ?: job.createdAt,
            startedAt = job.startedAt,
            pausedAt = job.pausedAt,
            pauseReason = job.pauseReason,
            completedAt = job.completedAt,
            fromArea = "Quận 1",
            toArea = "Quận 3",
            routeDescription = "Quận 1 → Quận 3",
            overfullBinsCount = overfullCount,
            rawJob = job
        )
    }

    private fun mapJobToHistoryUiModel(job: JobDto, binsMap: Map<String, SmartBinDto>): JobHistoryUiModel? {
        val upper = job.status?.uppercase()?.trim().orEmpty()
        if (upper !in listOf("COMPLETED", "DONE", "FINISHED", "CANCELLED", "CANCELED", "REJECTED", "EXPIRED") && job.completedAt == null) {
            return null
        }

        val status = when (upper) {
            "COMPLETED", "DONE", "FINISHED" -> JobStatus.COMPLETED
            "CANCELLED", "CANCELED" -> JobStatus.CANCELLED
            "EXPIRED" -> JobStatus.EXPIRED
            "REJECTED" -> JobStatus.REJECTED
            else -> JobStatus.COMPLETED
        }

        val badgeText = when (status) {
            JobStatus.COMPLETED -> "Hoàn thành"
            JobStatus.CANCELLED -> "Đã hủy"
            JobStatus.EXPIRED -> "Hết hạn"
            JobStatus.REJECTED -> "Từ chối"
            else -> "Hoàn thành"
        }

        val dateStr = job.completedAt?.let { TimeUtils.formatDisplayDate(it) }
            ?: job.createdAt?.let { TimeUtils.formatDisplayDate(it) }
            ?: "Hôm nay"

        val timeRangeStr = if (job.startedAt != null && job.completedAt != null) {
            "${TimeUtils.formatDisplayTime(job.startedAt)} - ${TimeUtils.formatDisplayTime(job.completedAt)}"
        } else if (job.completedAt != null) {
            TimeUtils.formatDisplayTime(job.completedAt)
        } else if (job.startedAt != null) {
            TimeUtils.formatDisplayTime(job.startedAt)
        } else if (job.assignedAt != null) {
            TimeUtils.formatDisplayTime(job.assignedAt)
        } else if (job.createdAt != null) {
            TimeUtils.formatDisplayTime(job.createdAt)
        } else {
            val fromId = TimeUtils.formatDisplayTime(job.id)
            if (fromId != "--") fromId else TimeUtils.getCurrentVnTimeOnly()
        }

        val totalStops = job.targetBinIds?.size ?: (job.items?.size ?: 0)
        val distanceKm = (job.routeData?.distanceMeters ?: (totalStops * 950.0)) / 1000.0
        val durationMins = job.routeData?.durationSeconds?.let { (it / 60.0).roundToInt() }

        val routeOrReason = when (status) {
            JobStatus.CANCELLED, JobStatus.REJECTED -> {
                val r = job.pauseReason?.takeIf { it.isNotBlank() } ?: "Sự cố kỹ thuật"
                "Lý do: $r"
            }
            JobStatus.EXPIRED -> "Lý do: Không nhận trong thời gian quy định"
            else -> "Tuyến thu gom $totalStops điểm"
        }

        val cleanId = job.id.removePrefix("#")
        val displayCode = if (cleanId.startsWith("JOB_")) "#$cleanId" else "#JOB_$cleanId"

        return JobHistoryUiModel(
            id = job.id,
            displayCode = displayCode,
            status = status,
            statusBadgeText = badgeText,
            dateStr = dateStr,
            timeRangeStr = timeRangeStr,
            totalStops = totalStops,
            distanceKm = distanceKm,
            durationMinutes = durationMins,
            routeOrReason = routeOrReason,
            rawJob = job
        )
    }

    private fun filterHistoryList(list: List<JobHistoryUiModel>, filter: JobsHistoryFilter): List<JobHistoryUiModel> {
        return list.filter { item ->
            val matchStatus = when (item.status) {
                JobStatus.COMPLETED -> filter.showCompleted
                JobStatus.CANCELLED, JobStatus.REJECTED -> filter.showCancelled
                JobStatus.EXPIRED -> filter.showExpired
                else -> true
            }
            matchStatus
        }
    }

    private fun getJobTimestamp(item: JobHistoryUiModel): Long {
        val raw = item.rawJob
        val candidates = listOfNotNull(
            raw?.completedAt,
            raw?.startedAt,
            raw?.assignedAt,
            raw?.createdAt
        )
        for (iso in candidates) {
            val ms = TimeUtils.parseIsoToMillis(iso)
            if (ms > 0L) return ms
        }
        val cleanId = item.id.removePrefix("#").removePrefix("JOB_")
        cleanId.toLongOrNull()?.let { epoch ->
            return if (epoch < 100000000000L) epoch * 1000L else epoch
        }
        return 0L
    }

    private fun sortHistoryList(list: List<JobHistoryUiModel>, sortOrder: String): List<JobHistoryUiModel> {
        return if (sortOrder == "OLDEST") {
            list.sortedBy { getJobTimestamp(it) }
        } else {
            list.sortedByDescending { getJobTimestamp(it) }
        }
    }

    // =========================================================================
    // 6. COMPATIBILITY GETTERS
    // =========================================================================

    val selectedTab: StateFlow<Int> = _uiState.map { it.activeTab }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val activeJobs: StateFlow<List<JobDto>> = _uiState.map { state ->
        listOfNotNull(state.assignedJob?.rawJob, state.inProgressJob?.rawJob)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val displayedActiveJobs: StateFlow<List<JobDto>> = activeJobs
    val allActiveCount: StateFlow<Int> = activeJobs.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val isLoading: StateFlow<Boolean> = _uiState.map { it.screenState == JobsScreenState.InitialLoading || it.isRefreshing }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isPaused: StateFlow<Boolean> = _uiState.map { it.inProgressJob?.status == JobStatus.PAUSED }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Compatibility methods
    fun loadAllJobData() = handleAction(JobsAction.LoadData)
    fun selectTopTab(index: Int) = handleAction(JobsAction.SelectTab(index))
    fun selectActiveSubTab(subTabIndex: Int) {}
    fun setHistoryFilter(filter: String) {}

    fun acceptJob(jobId: String, onComplete: ((Boolean) -> Unit)? = null) = handleAction(JobsAction.AcceptJob(jobId))
    fun rejectJob(jobId: String, reason: String? = null, onComplete: ((Boolean) -> Unit)? = null) = handleAction(JobsAction.RejectJob(jobId, reason ?: "Từ chối"))
    fun startJob(jobId: String, onComplete: ((Boolean) -> Unit)? = null) = handleAction(JobsAction.StartJob(jobId))
    fun pauseJob(jobId: String, reason: String = "Tạm dừng", onComplete: ((Boolean) -> Unit)? = null) = handleAction(JobsAction.PauseJob(jobId, reason))
    fun resumeJob(jobId: String, onComplete: ((Boolean) -> Unit)? = null) = handleAction(JobsAction.ResumeJob(jobId))
    fun collectBin(jobId: String, binId: String, note: String? = null, onComplete: ((Boolean) -> Unit)? = null) = handleAction(JobsAction.CollectBin(jobId, binId, note))
}
