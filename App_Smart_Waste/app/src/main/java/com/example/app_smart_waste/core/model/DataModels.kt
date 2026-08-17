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
    @SerializedName("is_online") val isOnline: Boolean? = true,
    @SerializedName("collection_status") val collectionStatus: String? = "IDLE",
    @SerializedName("last_telemetry") val lastTelemetry: String? = null
)

// 3. Collection Job Models
data class JobItemDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("job_id") val jobId: String? = null,
    @SerializedName("bin_id") val binId: String,
    @SerializedName("status") val status: String? = "PENDING", // PENDING | COLLECTED | SKIPPED | INCIDENT
    @SerializedName("collected_at") val collectedAt: String? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class RouteDataDto(
    @SerializedName("distanceMeters") val distanceMeters: Double? = null,
    @SerializedName("durationSeconds") val durationSeconds: Double? = null,
    @SerializedName("geometry") val geometry: String? = null
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
    @SerializedName("completed_at") val completedAt: String? = null
)

data class ActiveJobResponse(
    @SerializedName("ok") val ok: Boolean? = true,
    @SerializedName("job") val job: JobDto?
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
    @SerializedName("accuracy") val accuracy: Double? = null
)

// 5. Incident Models
data class IncidentRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("issue_type") val issueType: String,
    @SerializedName("description") val description: String,
    @SerializedName("photo_url") val photoUrl: String? = null
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
    @SerializedName("image_url") val imageUrl: String? = null
)

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
    val priorityText: String = "Ưu tiên cao",
    val prioritySubtext: String = "> 85% đầy",
    val isHighPriority: Boolean = true,
    val progressPercent: Int = 0,
    val binsList: List<JobBinDisplayItem> = emptyList()
)
