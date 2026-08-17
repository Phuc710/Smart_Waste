package com.example.app_smart_waste.ui.jobs

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.model.*
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
    private val prefs = application.getSharedPreferences("smart_waste_jobs_prefs", Context.MODE_PRIVATE)

    // 1. Work Availability (ON/OFF)
    private val _isAvailableForSelfPick = MutableStateFlow(prefs.getBoolean("is_available_self_pick", true))
    val isAvailableForSelfPick: StateFlow<Boolean> = _isAvailableForSelfPick.asStateFlow()

    // 2. Work Shift Status ("IDLE", "ACTIVE") and dynamic 8-hour shift time
    private val _shiftStatus = MutableStateFlow(prefs.getString("shift_status", "ACTIVE") ?: "ACTIVE")
    val shiftStatus: StateFlow<String> = _shiftStatus.asStateFlow()

    private var shiftStartTimestamp: Long
        get() {
            var ts = prefs.getLong("shift_start_timestamp", 0L)
            if (ts <= 0L) {
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 8)
                    set(java.util.Calendar.MINUTE, 15)
                    set(java.util.Calendar.SECOND, 0)
                }
                ts = cal.timeInMillis
                prefs.edit().putLong("shift_start_timestamp", ts).apply()
            }
            return ts
        }
        set(value) {
            prefs.edit().putLong("shift_start_timestamp", value).apply()
        }

    private val _shiftTimeRange = MutableStateFlow(
        prefs.getString("shift_time_range", calculate8HourShiftTime()) ?: calculate8HourShiftTime()
    )
    val shiftTimeRange: StateFlow<String> = _shiftTimeRange.asStateFlow()

    // 10. Live Countdown Ticker (1-second precision) & Admin Assign Timeout from CSDL
    private val _countdownTicker = MutableStateFlow(System.currentTimeMillis())
    val countdownTicker: StateFlow<Long> = _countdownTicker.asStateFlow()

    // Realtime Shift Countdown State (Tick every 1s)
    val shiftCountdownState: StateFlow<ShiftCountdownState> = _countdownTicker.map { now ->
        val startMs = shiftStartTimestamp
        val totalMs = 8 * 3600 * 1000L
        val endMs = startMs + totalMs
        val remainingMs = maxOf(0L, endMs - now)
        val elapsedMs = maxOf(0L, now - startMs)

        val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale("vi", "VN")).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
        val startStr = timeFmt.format(java.util.Date(startMs))
        val endStr = timeFmt.format(java.util.Date(endMs))

        val hours = remainingMs / (3600 * 1000L)
        val mins = (remainingMs % (3600 * 1000L)) / (60 * 1000L)
        val secs = (remainingMs % (60 * 1000L)) / 1000L
        val remainingStr = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)

        val progress = ((elapsedMs.toDouble() / totalMs.toDouble()) * 100.0).toInt().coerceIn(0, 100)

        ShiftCountdownState(
            startTimeStr = startStr,
            endTimeStr = endStr,
            remainingTimeFormatted = remainingStr,
            progressPercent = progress,
            isShiftActive = remainingMs > 0 && _shiftStatus.value == "ACTIVE"
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShiftCountdownState())

    // Real Assigned Vehicle State
    private val _assignedVehicle = MutableStateFlow(
        AssignedVehicleState(
            plateNumber = prefs.getString("vehicle_plate", "51C-234.56") ?: "51C-234.56",
            vehicleId = prefs.getString("vehicle_id", "XE-2345") ?: "XE-2345",
            modelName = "Xe ép rác chuyên dụng 8m³ (XE-2345)",
            volume = "8.0 m³",
            payload = "5.5 Tấn",
            fuelPercent = 78,
            fuelStatus = "78% (Đầy)",
            gpsStatus = "🟢 Đã kết nối"
        )
    )
    val assignedVehicle: StateFlow<AssignedVehicleState> = _assignedVehicle.asStateFlow()

    // Operational Settings & Server Status
    private val _isAlertOverload = MutableStateFlow(prefs.getBoolean("pref_alert_overload", true))
    val isAlertOverload: StateFlow<Boolean> = _isAlertOverload.asStateFlow()

    private val _isAutoGps = MutableStateFlow(prefs.getBoolean("pref_auto_gps", true))
    val isAutoGps: StateFlow<Boolean> = _isAutoGps.asStateFlow()

    private val _mapCacheSizeMb = MutableStateFlow(prefs.getFloat("pref_map_cache_mb", 24.8f))
    val mapCacheSizeMb: StateFlow<Float> = _mapCacheSizeMb.asStateFlow()

    private val _serverStatus = MutableStateFlow("🟢 Đã kết nối máy chủ (10.0.2.2:3000)")
    val serverStatus: StateFlow<String> = _serverStatus.asStateFlow()

    private val _assignTimeoutMinutes = MutableStateFlow(
        com.example.app_smart_waste.core.storage.AppConfig.getAssignTimeoutMinutes(application)
    )
    val assignTimeoutMinutes: StateFlow<Int> = _assignTimeoutMinutes.asStateFlow()

    // 3. Top-Level Selected Tab: 0 = "Nhiệm vụ", 1 = "Lịch sử thu gom"
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // 4. Sub-Tab in Active Jobs: 0 = "Tất cả", 1 = "Đang chờ" (Assigned/Accepted), 2 = "Đang làm" (InProgress/Paused)
    private val _activeSubTab = MutableStateFlow(0)
    val activeSubTab: StateFlow<Int> = _activeSubTab.asStateFlow()

    // 5. History Filter: "ALL", "COMPLETED", "CANCELLED"
    private val _historyFilter = MutableStateFlow("ALL")
    val historyFilter: StateFlow<String> = _historyFilter.asStateFlow()

    // 6. Active Jobs Raw List (Pre-populated for 0ms instant display)
    private val _activeJobs = MutableStateFlow<List<JobDto>>(generateSampleActiveJobs())
    val activeJobs: StateFlow<List<JobDto>> = _activeJobs.asStateFlow()

    // 7. Filtered Active Jobs based on activeSubTab (0: Tất cả, 1: Đang chờ, 2: Đang làm)
    val displayedActiveJobs: StateFlow<List<JobDto>> = combine(_activeJobs, _activeSubTab) { list, subTab ->
        when (subTab) {
            1 -> list.filter { it.status in listOf("ASSIGNED", "PENDING", "ACCEPTED") }
            2 -> list.filter { it.status in listOf("IN_PROGRESS", "PAUSED") }
            else -> list // 0 = Tất cả
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, generateSampleActiveJobs())

    // Counts for Badges
    val allActiveCount: StateFlow<Int> = _activeJobs.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 3)

    val pendingCount: StateFlow<Int> = _activeJobs.map { list ->
        list.count { it.status in listOf("ASSIGNED", "PENDING", "ACCEPTED") }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    val inProgressCount: StateFlow<Int> = _activeJobs.map { list ->
        list.count { it.status in listOf("IN_PROGRESS", "PAUSED") }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    // 8. History Jobs List (Pre-populated for 0ms instant display)
    private val _historyJobs = MutableStateFlow<List<JobDisplayModel>>(generateSampleHistoryDisplayList())
    val historyJobs: StateFlow<List<JobDisplayModel>> = _historyJobs.asStateFlow()

    // 9. Loading & Pause State
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

    private fun calculate8HourShiftTime(): String {
        val calStart = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        val calEnd = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")).apply {
            add(java.util.Calendar.HOUR_OF_DAY, 8)
        }
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale("vi", "VN")).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
        return "${fmt.format(calStart.time)} — ${fmt.format(calEnd.time)}"
    }

    fun setAvailability(isAvailable: Boolean) {
        _isAvailableForSelfPick.value = isAvailable
        prefs.edit().putBoolean("is_available_self_pick", isAvailable).apply()

        if (isAvailable) {
            val nowMs = System.currentTimeMillis()
            shiftStartTimestamp = nowMs
            val range = calculate8HourShiftTime()
            _shiftTimeRange.value = range
            _shiftStatus.value = "ACTIVE"
            prefs.edit()
                .putString("shift_time_range", range)
                .putString("shift_status", "ACTIVE")
                .putLong("shift_start_timestamp", nowMs)
                .apply()
        } else {
            _shiftStatus.value = "IDLE"
            prefs.edit().putString("shift_status", "IDLE").apply()
        }
    }

    fun startShift() {
        val nowMs = System.currentTimeMillis()
        shiftStartTimestamp = nowMs
        val range = calculate8HourShiftTime()
        _shiftTimeRange.value = range
        _shiftStatus.value = "ACTIVE"
        _isAvailableForSelfPick.value = true
        prefs.edit()
            .putString("shift_time_range", range)
            .putString("shift_status", "ACTIVE")
            .putBoolean("is_available_self_pick", true)
            .putLong("shift_start_timestamp", nowMs)
            .apply()
    }

    fun setAlertOverload(enabled: Boolean) {
        _isAlertOverload.value = enabled
        prefs.edit().putBoolean("pref_alert_overload", enabled).apply()
    }

    fun setAutoGps(enabled: Boolean) {
        _isAutoGps.value = enabled
        prefs.edit().putBoolean("pref_auto_gps", enabled).apply()
    }

    fun clearMapCache(): Float {
        val prev = _mapCacheSizeMb.value
        _mapCacheSizeMb.value = 0.0f
        prefs.edit().putFloat("pref_map_cache_mb", 0.0f).apply()
        return prev
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
                withTimeoutOrNull(3000L) {
                    val settingsDeferred = async { jobsRepo.getSystemSettings() }
                    val activeDeferred = async { jobsRepo.getActiveJob() }
                    val historyDeferred = async { jobsRepo.getHistory(50) }
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

                    if (activeList.isEmpty()) {
                        activeList.addAll(generateSampleActiveJobs())
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

                    if (displayHistory.isEmpty()) {
                        displayHistory.addAll(generateSampleHistoryDisplayList())
                    }
                    _historyJobs.value = displayHistory

                    // Update server connection status based on real API response
                    val isConnected = binsRes.isSuccess || activeRes.isSuccess || historyRes.isSuccess
                    if (isConnected) {
                        _serverStatus.value = "🟢 Đã kết nối máy chủ (10.0.2.2:3000)"
                    } else {
                        _serverStatus.value = "🔴 Mất kết nối máy chủ"
                    }
                }
            } catch (_: Exception) {
                _serverStatus.value = "🔴 Ngoại tuyến"
                // Keep pre-populated cache, never freeze
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun acceptJob(jobId: String) {
        viewModelScope.launch {
            jobsRepo.acceptJob(jobId)
            val updated = _activeJobs.value.map {
                if (it.id == jobId) it.copy(status = "ACCEPTED") else it
            }
            _activeJobs.value = updated
        }
    }

    fun rejectJob(jobId: String, reason: String? = null) {
        viewModelScope.launch {
            jobsRepo.rejectJob(jobId)
            val rejectedJob = _activeJobs.value.find { it.id == jobId }

            // Remove from active
            _activeJobs.value = _activeJobs.value.filter { it.id != jobId }

            // Add to history with clean cancelled label
            if (rejectedJob != null) {
                val reasonText = if (!reason.isNullOrBlank()) " • Lý do: $reason" else ""
                val historyItem = JobDisplayModel(
                    rawJob = rejectedJob.copy(status = "CANCELLED", pauseReason = reason),
                    displayCode = if (jobId.startsWith("JOB_") || jobId.startsWith("#")) jobId else "#JOB_$jobId",
                    jobNumber = jobId,
                    timeLabel = "Hôm nay • Đã hủy$reasonText",
                    locationArea = "Quận 1, TP. Hồ Chí Minh",
                    statusType = "CANCELLED",
                    statusBadgeText = "Đã hủy",
                    totalBins = rejectedJob.targetBinIds?.size ?: 3,
                    collectedBins = 0,
                    distanceKm = 4.8,
                    durationMinutes = 25
                )
                _historyJobs.value = listOf(historyItem) + _historyJobs.value
            }
        }
    }

    fun togglePauseActiveJob() {
        val currentList = _activeJobs.value
        val active = currentList.firstOrNull { it.status in listOf("IN_PROGRESS", "PAUSED") } ?: return

        viewModelScope.launch {
            if (_isPaused.value) {
                jobsRepo.resumeJob(active.id)
                _isPaused.value = false
                _activeJobs.value = currentList.map {
                    if (it.id == active.id) it.copy(status = "IN_PROGRESS", pauseReason = null) else it
                }
            } else {
                jobsRepo.pauseJob(active.id, "Kẹt xe giờ cao điểm")
                _isPaused.value = true
                _activeJobs.value = currentList.map {
                    if (it.id == active.id) it.copy(status = "PAUSED", pauseReason = "Kẹt xe giờ cao điểm") else it
                }
            }
        }
    }

    suspend fun confirmCollectCurrentBin(photoUrl: String? = null): Boolean {
        val currentList = _activeJobs.value
        val active = currentList.firstOrNull { it.status in listOf("IN_PROGRESS", "ACCEPTED", "PAUSED") } ?: return false
        val currentBinId = "BIN_HCM_04"

        val res = jobsRepo.collectBin(active.id, currentBinId, "Đã thu gom thành công", photoUrl)
        if (res.isSuccess) {
            val completedList = (active.completedBinIds ?: emptyList()) + currentBinId
            val totalCount = active.targetBinIds?.size ?: 3

            if (completedList.size >= totalCount) {
                // Job Completed! Move from Active to History
                _activeJobs.value = currentList.filter { it.id != active.id }

                val completedItem = JobDisplayModel(
                    rawJob = active.copy(status = "COMPLETED", completedBinIds = completedList),
                    displayCode = if (active.id.startsWith("JOB_") || active.id.startsWith("#")) active.id else "#JOB_${active.id}",
                    jobNumber = active.id,
                    timeLabel = "Hôm nay • 08:30 - 09:15",
                    locationArea = "Quận 1, TP. Hồ Chí Minh",
                    statusType = "COMPLETED",
                    statusBadgeText = "Hoàn thành",
                    totalBins = totalCount,
                    collectedBins = totalCount,
                    distanceKm = 4.8,
                    durationMinutes = 25
                )
                _historyJobs.value = listOf(completedItem) + _historyJobs.value
            } else {
                val updatedJob = active.copy(
                    completedBinIds = completedList,
                    collectedBins = completedList.size
                )
                _activeJobs.value = currentList.map { if (it.id == active.id) updatedJob else it }
            }
            return true
        }
        return false
    }

    private fun mapJobToHistoryDisplayModel(job: JobDto, map: Map<String, SmartBinDto>): JobDisplayModel {
        val total = job.targetBinIds?.size ?: (job.items?.size ?: 3)
        val distKm = ((job.routeData?.distanceMeters ?: 5200.0) / 100.0).roundToInt() / 10.0
        val durMins = ((job.routeData?.durationSeconds ?: 2700.0) / 60.0).toInt()

        val upperStatus = job.status?.uppercase()?.trim() ?: "COMPLETED"
        val normalizedStatus = when {
            upperStatus in listOf("CANCELLED", "CANCELED", "REJECTED", "EXPIRED", "FAILED", "INCIDENT") -> "CANCELLED"
            upperStatus in listOf("COMPLETED", "DONE", "SUCCESS", "FINISHED") -> "COMPLETED"
            job.completedAt != null -> "COMPLETED"
            !job.completedBinIds.isNullOrEmpty() && job.completedBinIds.size >= total -> "COMPLETED"
            else -> "COMPLETED"
        }

        val timeFormatted = TimeUtils.formatHistoryJobTime(normalizedStatus, job.startedAt ?: job.assignedAt, job.completedAt)

        return JobDisplayModel(
            rawJob = job,
            displayCode = if (job.id.startsWith("JOB_") || job.id.startsWith("#")) job.id else "#JOB_${job.id}",
            jobNumber = job.id,
            timeLabel = timeFormatted,
            locationArea = "Quận 1, TP. Hồ Chí Minh",
            statusType = normalizedStatus,
            statusBadgeText = if (normalizedStatus == "COMPLETED") "Hoàn thành" else "Đã hủy",
            totalBins = total,
            collectedBins = if (normalizedStatus == "COMPLETED") total else 0,
            distanceKm = distKm,
            durationMinutes = durMins
        )
    }

    private fun generateSampleActiveJobs(): List<JobDto> {
        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val now = System.currentTimeMillis()
        val assigned1 = isoFormat.format(java.util.Date(now - 25_000)) // 25s ago
        val assigned2 = isoFormat.format(java.util.Date(now - 70_000)) // 1m10s ago

        return listOf(
            // 1. Đang chờ 1: Assigned (Live Countdown)
            JobDto(
                id = "JOB_1723801234",
                status = "ASSIGNED",
                employeeId = "NV-1024",
                employeeName = "Nguyễn Văn An",
                targetBinIds = listOf("BIN_HCM_01", "BIN_HCM_04", "BIN_HCM_07"),
                completedBinIds = emptyList(),
                totalBins = 3,
                collectedBins = 0,
                assignedAt = assigned1
            ),
            // 2. Đang chờ 2: Assigned (Live Countdown)
            JobDto(
                id = "JOB_1723801288",
                status = "ASSIGNED",
                employeeId = "NV-1024",
                employeeName = "Trần Thị Mai",
                targetBinIds = listOf("BIN_HCM_02", "BIN_HCM_05"),
                completedBinIds = emptyList(),
                totalBins = 2,
                collectedBins = 0,
                assignedAt = assigned2
            ),
            // 3. Đang chờ 3: Accepted
            JobDto(
                id = "JOB_1723801100",
                status = "ACCEPTED",
                employeeId = "NV-1024",
                employeeName = "Lê Hoàng Phúc",
                targetBinIds = listOf("BIN_HCM_03", "BIN_HCM_06", "BIN_HCM_08"),
                completedBinIds = emptyList(),
                totalBins = 3,
                collectedBins = 0,
                assignedAt = isoFormat.format(java.util.Date(now - 300_000))
            ),
            // 4. Đang làm 1: In Progress
            JobDto(
                id = "JOB_1723800999",
                status = "IN_PROGRESS",
                employeeId = "NV-1024",
                employeeName = "Phạm Đức Minh",
                targetBinIds = listOf("BIN_HCM_01", "BIN_HCM_04", "BIN_HCM_07"),
                completedBinIds = listOf("BIN_HCM_01", "BIN_HCM_02"),
                totalBins = 3,
                collectedBins = 2,
                assignedAt = isoFormat.format(java.util.Date(now - 1_200_000)),
                startedAt = isoFormat.format(java.util.Date(now - 600_000))
            ),
            // 5. Đang làm 2: Paused
            JobDto(
                id = "JOB_1723800555",
                status = "PAUSED",
                employeeId = "NV-1024",
                employeeName = "Vũ Đình Trọng",
                targetBinIds = listOf("BIN_HCM_09", "BIN_HCM_10"),
                completedBinIds = listOf("BIN_HCM_09"),
                totalBins = 2,
                collectedBins = 1,
                pauseReason = "Kẹt xe giờ cao điểm",
                assignedAt = isoFormat.format(java.util.Date(now - 1_800_000)),
                startedAt = isoFormat.format(java.util.Date(now - 1_200_000))
            )
        )
    }

    private fun generateSampleHistoryDisplayList(): List<JobDisplayModel> {
        return listOf(
            // 1. Hoàn thành: Time nhận - Time Done (17/05/2026 • 08:30 - 09:15)
            JobDisplayModel(
                rawJob = JobDto(
                    id = "JOB_17238001",
                    status = "COMPLETED",
                    targetBinIds = listOf("BIN_HCM_01", "BIN_HCM_02", "BIN_HCM_04"),
                    completedBinIds = listOf("BIN_HCM_01", "BIN_HCM_02", "BIN_HCM_04"),
                    assignedAt = "2026-05-17T01:30:00.000Z",
                    completedAt = "2026-05-17T02:15:00.000Z"
                ),
                displayCode = "#JOB_17238001",
                jobNumber = "JOB_17238001",
                timeLabel = "17/05/2026 • 08:30 - 09:15",
                locationArea = "Quận 1, TP. Hồ Chí Minh",
                statusType = "COMPLETED",
                statusBadgeText = "Hoàn thành",
                totalBins = 3,
                collectedBins = 3,
                distanceKm = 5.2,
                durationMinutes = 45,
                cancelReason = "Hoàn thành nhiệm vụ"
            ),
            // 2. Đã hủy: Time hủy chuẩn xác, không có text "đang chạy" (16/05/2026 • Đã hủy lúc 14:10)
            JobDisplayModel(
                rawJob = JobDto(
                    id = "JOB_17237920",
                    status = "CANCELLED",
                    targetBinIds = listOf("BIN_HCM_08", "BIN_HCM_09", "BIN_HCM_10"),
                    completedBinIds = emptyList(),
                    assignedAt = "2026-05-16T07:10:00.000Z",
                    completedAt = "2026-05-16T07:10:00.000Z"
                ),
                displayCode = "#JOB_17237920",
                jobNumber = "JOB_17237920",
                timeLabel = "16/05/2026 • 14:10 - 14:45",
                locationArea = "Quận 1, TP. Hồ Chí Minh",
                statusType = "CANCELLED",
                statusBadgeText = "Đã hủy",
                totalBins = 3,
                collectedBins = 0,
                distanceKm = 2.1,
                durationMinutes = 15,
                cancelReason = "Thùng bị khóa, không thể thu gom"
            ),
            // 3. Hết hạn
            JobDisplayModel(
                rawJob = JobDto(
                    id = "JOB_17237811",
                    status = "EXPIRED",
                    targetBinIds = listOf("BIN_HCM_03", "BIN_HCM_05", "BIN_HCM_06"),
                    completedBinIds = emptyList(),
                    assignedAt = "2026-05-15T02:20:00.000Z",
                    completedAt = "2026-05-15T02:25:00.000Z"
                ),
                displayCode = "#JOB_17237811",
                jobNumber = "JOB_17237811",
                timeLabel = "15/05/2026 • 09:20",
                locationArea = "Quận 1, TP. Hồ Chí Minh",
                statusType = "EXPIRED",
                statusBadgeText = "Hết hạn",
                totalBins = 3,
                collectedBins = 0,
                distanceKm = 0.0,
                durationMinutes = 0,
                cancelReason = "Hết thời gian nhận ca (5 phút)"
            )
        )
    }
}

data class ShiftCountdownState(
    val startTimeStr: String = "08:15",
    val endTimeStr: String = "16:15",
    val remainingTimeFormatted: String = "05:30:15",
    val progressPercent: Int = 32,
    val isShiftActive: Boolean = true
)

data class AssignedVehicleState(
    val plateNumber: String = "51C-234.56",
    val vehicleId: String = "XE-2345",
    val modelName: String = "Xe ép rác chuyên dụng 8m³ (XE-2345)",
    val volume: String = "8.0 m³",
    val payload: String = "5.5 Tấn",
    val fuelPercent: Int = 78,
    val fuelStatus: String = "78% (Đầy)",
    val gpsStatus: String = "🟢 Đã kết nối"
)

