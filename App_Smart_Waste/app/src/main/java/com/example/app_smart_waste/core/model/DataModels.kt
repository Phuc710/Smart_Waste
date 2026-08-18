package com.example.app_smart_waste.core.model

import com.google.gson.annotations.SerializedName

// 1. Auth Models
data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("role") val role: String? = "staff",
    @SerializedName("is_active") val isActive: Boolean? = true
)

data class LoginResponse(
    @SerializedName("token") val token: String? = null,
    @SerializedName("user") val user: UserDto?
)

data class MeResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("isActive") val isActive: Boolean? = null
)

// 2. Smart Bin Models
data class SmartBinDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("latitude") val latitude: Double? = 0.0,
    @SerializedName("longitude") val longitude: Double? = 0.0,
    @SerializedName("level_percent") val levelPercent: Double? = 0.0,
    @SerializedName("lid_state") val lidState: String? = "CLOSED",
    @SerializedName("state") val state: String? = null,
    @SerializedName("control_mode") val controlMode: String? = "AUTO",
    @SerializedName("is_online") val isOnline: Boolean? = true,
    @SerializedName("collection_status") val collectionStatus: String? = "IDLE",
    @SerializedName("last_telemetry") val lastTelemetry: String? = null,
    @SerializedName("last_seen") val rawLastSeen: String? = null
) {
    val fillLevel: Int get() = (levelPercent ?: 0.0).toInt()
    val actualLidState: String get() = lidState ?: state ?: "CLOSED"
    val lidStatus: String get() = if (actualLidState.contains("OPEN", ignoreCase = true)) "🔓 Đang mở" else "🔒 Đã đóng"
    val modeText: String get() = if (controlMode?.equals("MANUAL", ignoreCase = true) == true) "Thủ công" else "Tự động"
    val collectionStatusText: String get() = when (collectionStatus?.uppercase(java.util.Locale.ROOT)) {
        "COLLECTED" -> "Đã thu gom"
        "IN_PROGRESS" -> "Đang thu gom"
        "SKIPPED" -> "Bỏ qua"
        else -> "Chờ thu gom"
    }
    val lastSeen: String?
        get() = lastTelemetry ?: rawLastSeen ?: "Chưa có dữ liệu"
    val status: String get() = collectionStatus ?: "IDLE"
}

data class BinCommandBinDto(
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("lid_state") val lidState: String? = null,
    @SerializedName("level_percent") val levelPercent: Double? = null,
    @SerializedName("control_mode") val controlMode: String? = null,
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("is_online") val isOnline: Boolean? = null,
    @SerializedName("last_seen") val lastSeen: String? = null,
    @SerializedName("command_status") val commandStatus: String? = null
)

data class BinCommandRequest(
    @SerializedName("action") val action: String
)

data class BinCommandResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("bin") val bin: BinCommandBinDto? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null
)

sealed interface BinCommandResult {
    data class Executed(
        val bin: BinCommandBinDto?,
        val message: String
    ) : BinCommandResult

    data class Timeout(val message: String) : BinCommandResult
    data class DeviceOffline(val message: String) : BinCommandResult
    data class Unauthorized(val message: String) : BinCommandResult
    data class NetworkError(val message: String) : BinCommandResult
    data class ServerError(
        val statusCode: Int?,
        val message: String
    ) : BinCommandResult
}

// 3. Collection Job Models
enum class JobStatus {
    ASSIGNED,
    ACCEPTED,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    REJECTED,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): JobStatus = when (value?.uppercase(java.util.Locale.ROOT)?.trim()) {
            "ASSIGNED" -> ASSIGNED
            "ACCEPTED" -> ACCEPTED
            "IN_PROGRESS" -> IN_PROGRESS
            "PAUSED" -> PAUSED
            "COMPLETED" -> COMPLETED
            "CANCELLED" -> CANCELLED
            "EXPIRED" -> EXPIRED
            "REJECTED" -> REJECTED
            else -> UNKNOWN
        }
    }
}

enum class JobStopStatus {
    PENDING,
    COLLECTED,
    SKIPPED,
    INCIDENT,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): JobStopStatus = when (value?.uppercase(java.util.Locale.ROOT)?.trim()) {
            "PENDING" -> PENDING
            "COLLECTED" -> COLLECTED
            "SKIPPED" -> SKIPPED
            "INCIDENT" -> INCIDENT
            else -> UNKNOWN
        }
    }
}

enum class JobActionType {
    ACCEPT,
    REJECT,
    START,
    PAUSE,
    RESUME
}

object JobTransitionPolicy {
    fun canAccept(status: JobStatus): Boolean = status == JobStatus.ASSIGNED
    fun canStart(status: JobStatus): Boolean = status == JobStatus.ACCEPTED
    fun canPause(status: JobStatus): Boolean = status == JobStatus.IN_PROGRESS
    fun canResume(status: JobStatus): Boolean = status == JobStatus.PAUSED

    fun allowedActions(status: JobStatus): Set<JobActionType> {
        return when (status) {
            JobStatus.ASSIGNED -> setOf(JobActionType.ACCEPT, JobActionType.REJECT)
            JobStatus.ACCEPTED -> setOf(JobActionType.START)
            JobStatus.IN_PROGRESS -> setOf(JobActionType.PAUSE)
            JobStatus.PAUSED -> setOf(JobActionType.RESUME)
            else -> emptySet()
        }
    }
}

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    val isValid: Boolean
        get() = com.example.app_smart_waste.ui.map.MapStatePolicy.isValidCoordinate(latitude, longitude)
}

data class JobStopUiModel(
    val binId: String,
    val order: Int,
    val status: JobStopStatus,
    val binName: String? = null,
    val address: String? = null,
    val levelPercent: Double? = null,
    val isOnline: Boolean? = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val collectedAt: String? = null,
    val note: String? = null,
    val photoUrl: String? = null,
    val isNext: Boolean = false,
    val coordinate: GeoCoordinate? = null,
    val bin: SmartBinDto? = null
)

data class RouteStepDto(
    @SerializedName("distanceMeters") val distanceMeters: Double? = 0.0,
    @SerializedName("durationSeconds") val durationSeconds: Double? = 0.0,
    @SerializedName("street") val street: String? = "",
    @SerializedName("maneuverType") val maneuverType: String? = "straight",
    @SerializedName("maneuverModifier") val maneuverModifier: String? = "",
    @SerializedName("location") val location: List<Double>? = null
)

data class JobRouteUiModel(
    val coordinates: List<GeoCoordinate>,
    val stops: List<JobStopUiModel>,
    val distanceMeters: Int?,
    val durationSeconds: Int?,
    val steps: List<RouteStepDto> = emptyList()
)

data class JobItemDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("job_id") val jobId: String? = null,
    @SerializedName("bin_id") val binId: String,
    @SerializedName("sort_order") val sortOrder: Int? = null,
    @SerializedName("status") val status: String? = "PENDING", // PENDING | COLLECTED | SKIPPED | INCIDENT
    @SerializedName("collected_at") val collectedAt: String? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class RouteDataDto(
    @SerializedName("distanceMeters") val distanceMeters: Double? = null,
    @SerializedName("durationSeconds") val durationSeconds: Double? = null,
    @SerializedName("geometry") val geometry: String? = null,
    @SerializedName("coordinates") val coordinates: List<List<Double>>? = null,
    @SerializedName("steps") val steps: List<RouteStepDto>? = emptyList(),
    @SerializedName("optimizedOrder") val optimizedOrder: List<Any>? = null
)

data class JobProgressDto(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("collected") val collected: Int = 0,
    @SerializedName("percent") val percent: Int = 0
)

data class JobDto(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String, // PENDING | ASSIGNED | ACCEPTED | IN_PROGRESS | PAUSED | COMPLETED | CANCELLED | REJECTED | EXPIRED
    @SerializedName("employee_id") val employeeId: String? = null,
    @SerializedName("employee_name") val employeeName: String? = null,
    @SerializedName("target_bin_ids") val targetBinIds: List<String>? = emptyList(),
    @SerializedName("completed_bin_ids") val completedBinIds: List<String>? = emptyList(),
    @SerializedName("items") val items: List<JobItemDto>? = emptyList(),
    @SerializedName("progress") val progress: JobProgressDto? = null,
    @SerializedName("route_data") val routeData: RouteDataDto? = null,
    @SerializedName("total_bins") val totalBins: Int? = 0,
    @SerializedName("collected_bins") val collectedBins: Int? = 0,
    @SerializedName("assigned_at") val assignedAt: String? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("paused_at") val pausedAt: String? = null,
    @SerializedName("pause_reason") val pauseReason: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("version") val version: Int? = 1
)

data class ActiveJobResponse(
    @SerializedName("ok") val ok: Boolean? = true,
    @SerializedName("job") val job: JobDto?
)

data class DailyDriverStatsDto(
    @SerializedName("collectionCount") val collectionCount: Int = 0,
    @SerializedName("distanceMeters") val distanceMeters: Int = 0,
    @SerializedName("estimatedWeightKg") val estimatedWeightKg: Double = 0.0,
    @SerializedName("estimateKgPerCollection") val estimateKgPerCollection: Double = 0.0,
    @SerializedName("day") val day: String? = null,
    @SerializedName("timezone") val timezone: String? = null
)

data class MobileHomeResponse(
    @SerializedName("job") val job: JobDto? = null,
    @SerializedName("stats") val stats: DailyDriverStatsDto = DailyDriverStatsDto()
)

data class CollectBinRequest(
    @SerializedName("binId") val binId: String,
    @SerializedName("status") val status: String = "COLLECTED",
    @SerializedName("note") val note: String? = null,
    @SerializedName("photoUrl") val photoUrl: String? = null
)

data class CollectBinResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("allDone") val allDone: Boolean = false,
    @SerializedName("idempotent") val idempotent: Boolean = false,
    @SerializedName("job") val job: JobDto? = null
)

data class SelfPickRequest(
    @SerializedName("binIds") val binIds: List<String>
)

// 4. GPS & Location Models
data class LocationPayload(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("speed") val speed: Double? = null,
    @SerializedName("heading") val heading: Double? = null,
    @SerializedName("accuracy") val accuracy: Double? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("job_id") val jobId: String? = null,
    @SerializedName("tracking_session_id") val trackingSessionId: String? = null
)

data class BatchLocationItem(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("speed") val speed: Double? = null,
    @SerializedName("heading") val heading: Double? = null,
    @SerializedName("accuracy") val accuracy: Double? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class BatchLocationPayload(
    @SerializedName("trackingSessionId") val trackingSessionId: String? = null,
    @SerializedName("jobId") val jobId: String? = null,
    @SerializedName("locations") val locations: List<BatchLocationItem> = emptyList()
)

data class BatchLocationResponse(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("syncedCount") val syncedCount: Int = 0,
    @SerializedName("serverTime") val serverTime: String? = null,
    @SerializedName("message") val message: String? = null
)

// 5. Incident Models
enum class IncidentReason {
    BROKEN_BIN,
    LID_STUCK,
    SENSOR_FAILURE,
    OVERFLOW,
    OTHER;

    fun toVietnamese(): String = when (this) {
        BROKEN_BIN -> "Thùng hỏng"
        LID_STUCK -> "Nắp kẹt"
        SENSOR_FAILURE -> "Cảm biến lỗi"
        OVERFLOW -> "Rác tràn"
        OTHER -> "Khác"
    }

    companion object {
        fun fromVietnamese(text: String): IncidentReason = when (text.trim()) {
            "Thùng hỏng" -> BROKEN_BIN
            "Nắp kẹt" -> LID_STUCK
            "Cảm biến lỗi" -> SENSOR_FAILURE
            "Rác tràn" -> OVERFLOW
            else -> OTHER
        }
    }
}

sealed interface IncidentAttachmentState {
    data object None : IncidentAttachmentState
    data class Selected(
        val uriString: String,
        val displayName: String? = null,
        val sizeBytes: Long? = null
    ) : IncidentAttachmentState
    data class Compressing(val uriString: String) : IncidentAttachmentState
    data class Uploading(val progressPercent: Int?) : IncidentAttachmentState
    data class Uploaded(val uploadId: String) : IncidentAttachmentState
    data class Failed(val message: String) : IncidentAttachmentState
}

sealed interface IncidentSubmissionState {
    data object Idle : IncidentSubmissionState
    data object Validating : IncidentSubmissionState
    data object Submitting : IncidentSubmissionState
    data class Succeeded(
        val reportId: String? = null,
        val message: String
    ) : IncidentSubmissionState
    data class Failed(
        val message: String,
        val retryable: Boolean = true
    ) : IncidentSubmissionState
}

data class IncidentRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("issue_type") val issueType: String,
    @SerializedName("description") val description: String,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class IncidentUploadRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("issueType") val issueType: String,
    @SerializedName("description") val description: String
)

data class IncidentUploadDto(
    @SerializedName("uploadId") val uploadId: String,
    @SerializedName("uploadUrl") val uploadUrl: String,
    @SerializedName("objectPath") val objectPath: String
)

data class IncidentUploadResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("upload") val upload: IncidentUploadDto?
)

data class IncidentReportDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("bin_name") val binName: String? = null,
    @SerializedName("bin_location") val binLocation: String? = null,
    @SerializedName("reason") val reason: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("status") val status: String = "NEW", // NEW | IN_REVIEW | RESOLVED
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("resolved_at") val resolvedAt: String? = null,
    @SerializedName("has_photo") val hasPhoto: Boolean = false,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("proof_image_url") val proofImageUrl: String? = null
) {
    val displayPhotoUrl: String?
        get() = imageUrl?.takeIf { it.isNotBlank() } ?: proofImageUrl?.takeIf { it.isNotBlank() }
}

data class IncidentsResponse(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("reports") val reports: List<IncidentReportDto> = emptyList()
)

data class ChangePasswordRequest(
    @SerializedName("oldPassword") val oldPassword: String,
    @SerializedName("newPassword") val newPassword: String
)

// 6. Generic Action Response
data class ActionResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("job") val job: JobDto? = null
)

// 7. Notification Models (Sensor Alerts, Task Assignments, Route Updates)
data class NotificationItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("subtitle") val subtitle: String,
    @SerializedName("category_text") val categoryText: String = "Cảnh báo",
    @SerializedName("category_type") val categoryType: String = "ALERT", // ALERT | TASK | SYSTEM
    @SerializedName("time_str") val timeStr: String = "08:00",
    @SerializedName("date_group") val dateGroup: String = "Hôm nay",
    @SerializedName("is_unread") val isUnread: Boolean = false,
    @SerializedName("icon_type") val iconType: String = "TRASH", // TRASH | CALENDAR | ROUTE | CHECK | BELL | TRUCK
    @SerializedName("target_job_id") val targetJobId: String? = null,
    @SerializedName("target_bin_id") val targetBinId: String? = null
)

// 8. Rich UI Display Models for Jobs Screen (Image Mockups 1, 2, 3)
data class JobBinDisplayItem(
    val binId: String,
    val fillPercent: Int,
    val address: String,
    val isCollected: Boolean = false
)

data class JobDisplayModel(
    val rawJob: JobDto,
    val displayCode: String, // e.g. "TUYEN-Q7-01"
    val jobNumber: String,   // e.g. "J250518-001"
    val timeLabel: String,   // e.g. "Hôm nay, 07:30"
    val locationArea: String,// e.g. "Quận 7, TP. Hồ Chí Minh"
    val statusType: String,  // "ASSIGNED" | "IN_PROGRESS" | "COMPLETED"
    val statusBadgeText: String, // "Mới được giao" | "Đang thực hiện" | "Hoàn thành"
    val totalBins: Int,
    val collectedBins: Int,
    val distanceKm: Double,
    val durationMinutes: Int,
    val cancelReason: String? = null,
    val priorityText: String = "Ưu tiên cao",
    val prioritySubtext: String = "> 85% đầy",
    val isHighPriority: Boolean = true,
    val progressPercent: Int = 0,
    val binsList: List<JobBinDisplayItem> = emptyList()
)

// 9. Work Shift & Vehicle Info Models
data class WorkShiftModel(
    val id: String = "SHIFT-01",
    val title: String = "Sáng",
    val startTime: String = "06:00",
    val endTime: String = "14:00",
    val durationText: String = "8 tiếng",
    val routeName: String = "Tuyến Quận 1",
    val totalBins: Int = 14,
    val supervisor: String = "Nguyễn Văn Quản Lý"
)

data class VehicleModel(
    val plate: String = "--",
    val id: String = "XE-2345",
    val type: String = "Xe ép rác",
    val capacity: String = "8 m³",
    val weight: String = "8 tấn",
    val fuelLevel: Int = 100,
    val isGpsConnected: Boolean = true
)

// 10. System Settings Model (Admin Config from Backend CSDL)
data class SystemSettingsDto(
    @SerializedName("assign_timeout_minutes") val assignTimeoutMinutes: Int? = 5,
    @SerializedName("paused_timeout_minutes") val pausedTimeoutMinutes: Int? = 30,
    @SerializedName("bin_offline_timeout_seconds") val binOfflineTimeoutSeconds: Int? = 15,
    @SerializedName("fill_threshold_warning") val fillThresholdWarning: Int? = 70,
    @SerializedName("fill_threshold_critical") val fillThresholdCritical: Int? = 85,
    @SerializedName("auto_assign") val autoAssign: Boolean? = true
)

data class SystemSettingsResponse(
    @SerializedName("ok") val ok: Boolean? = true,
    @SerializedName("settings") val settings: SystemSettingsDto?
)

// 11. Map & Route Models
data class RouteRequest(
    @SerializedName("coordinates") val coordinates: List<List<Double>>
)

data class RouteResponse(
    @SerializedName("provider") val provider: String? = "osrm",
    @SerializedName("distanceMeters") val distanceMeters: Double? = 0.0,
    @SerializedName("durationSeconds") val durationSeconds: Double? = 0.0,
    @SerializedName("coordinates") val coordinates: List<List<Double>>? = emptyList(),
    @SerializedName("steps") val steps: List<RouteStepDto>? = emptyList(),
    @SerializedName("optimizedOrder") val optimizedOrder: List<Int>? = emptyList()
)

// 12. Complete Presentation & UI Models for Jobs Feature
data class ActiveJobUiModel(
    val id: String,
    val status: JobStatus,
    val source: String? = null,
    val employeeName: String? = null,
    val version: Int? = null,
    val stops: List<JobStopUiModel> = emptyList(),
    val completedStops: Int = 0,
    val totalStops: Int = 0,
    val progressPercent: Int = 0,
    val nextStop: JobStopUiModel? = null,
    val distanceMeters: Int? = null,
    val durationSeconds: Int? = null,
    val assignedAt: String? = null,
    val startedAt: String? = null,
    val pausedAt: String? = null,
    val pauseReason: String? = null,
    val completedAt: String? = null,
    val fromArea: String = "Quận 1",
    val toArea: String = "Quận 3",
    val routeDescription: String = "Tuyến thu gom chất thải",
    val overfullBinsCount: Int = 0,
    val rawJob: JobDto? = null
)

data class JobHistoryUiModel(
    val id: String,
    val displayCode: String,
    val status: JobStatus,
    val statusBadgeText: String,
    val dateStr: String,
    val timeRangeStr: String,
    val totalStops: Int,
    val distanceKm: Double,
    val durationMinutes: Int?,
    val routeOrReason: String,
    val rawJob: JobDto
)

sealed interface JobsScreenState {
    data object InitialLoading : JobsScreenState
    data object NoActiveJob : JobsScreenState
    data class Content(val activeJob: ActiveJobUiModel) : JobsScreenState
    data class PartialContent(val activeJob: ActiveJobUiModel, val message: String) : JobsScreenState
    data class Error(val message: String, val canRetry: Boolean = true) : JobsScreenState
}

sealed interface JobOperation {
    data object Accepting : JobOperation
    data object Rejecting : JobOperation
    data object Starting : JobOperation
    data object Pausing : JobOperation
    data object Resuming : JobOperation
    data class Collecting(val binId: String) : JobOperation
}

sealed interface JobsNetworkState {
    data object Online : JobsNetworkState
    data object NoInternet : JobsNetworkState
    data object BackendUnavailable : JobsNetworkState
    data object Reconnecting : JobsNetworkState
}

data class JobsActiveFilter(
    val showAssigned: Boolean = true,
    val showInProgress: Boolean = true,
    val showPaused: Boolean = true
)

data class JobsHistoryFilter(
    val fromDate: String? = null,
    val toDate: String? = null,
    val showCompleted: Boolean = true,
    val showCancelled: Boolean = true,
    val showExpired: Boolean = true,
    val sortOrder: String = "NEWEST" // NEWEST | OLDEST
)

data class JobsUiState(
    val screenState: JobsScreenState = JobsScreenState.InitialLoading,
    val activeTab: Int = 0, // 0 = Đang hoạt động / Cần xử lý, 1 = Lịch sử
    val assignedJob: ActiveJobUiModel? = null,
    val inProgressJob: ActiveJobUiModel? = null,
    val activeQuickFilter: String = "ALL", // ALL | ASSIGNED | IN_PROGRESS | PAUSED
    val historyQuickFilter: String = "ALL", // ALL | COMPLETED | CANCELLED | EXPIRED
    val history: List<JobHistoryUiModel> = emptyList(),
    val rawHistory: List<JobHistoryUiModel> = emptyList(),
    val activeFilter: JobsActiveFilter = JobsActiveFilter(),
    val historyFilter: JobsHistoryFilter = JobsHistoryFilter(),
    val sortOrder: String = "NEWEST", // NEWEST | OLDEST
    val selectedStopId: String? = null,
    val activeOperation: JobOperation? = null,
    val isRefreshing: Boolean = false,
    val networkState: JobsNetworkState = JobsNetworkState.Online,
    val countdownTimestamp: Long = System.currentTimeMillis()
)

