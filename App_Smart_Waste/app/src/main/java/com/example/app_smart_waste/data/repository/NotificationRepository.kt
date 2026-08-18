package com.example.app_smart_waste.data.repository

import com.example.app_smart_waste.core.model.NotificationCategoryType
import com.example.app_smart_waste.core.model.NotificationModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Senior Enterprise Notification Repository
 * Single Source of Truth for Notification History, Realtime Updates, and Unread Count.
 */
class NotificationRepository private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: NotificationRepository? = null

        fun getInstance(): NotificationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationRepository().also { INSTANCE = it }
            }
        }
    }

    private val _notifications = MutableStateFlow<List<NotificationModel>>(getInitialSeedData())
    val notifications: StateFlow<List<NotificationModel>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(calculateUnread(_notifications.value))
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private fun calculateUnread(list: List<NotificationModel>): Int {
        return list.count { it.isUnread }
    }

    /**
     * Mark all notifications as read
     */
    fun markAllAsRead() {
        val updated = _notifications.value.map { it.copy(isUnread = false) }
        _notifications.value = updated
        _unreadCount.value = 0
    }

    /**
     * Mark single notification as read by ID
     */
    fun markAsRead(notificationId: String) {
        val updated = _notifications.value.map {
            if (it.id == notificationId) it.copy(isUnread = false) else it
        }
        _notifications.value = updated
        _unreadCount.value = calculateUnread(updated)
    }

    /**
     * Add new realtime notification to the top of the list
     */
    fun addNotification(notification: NotificationModel) {
        val current = _notifications.value.toMutableList()
        current.add(0, notification)
        _notifications.value = current
        _unreadCount.value = calculateUnread(current)
    }

    /**
     * Prepend a realtime event from Socket.IO into Notification History
     */
    fun addNewRealtimeJob(jobId: String, totalBins: Int, routeDesc: String?) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val fullFormat = SimpleDateFormat("HH:mm • dd/MM/yyyy", Locale.getDefault())
        val now = Date()

        val cleanId = if (jobId.startsWith("JOB-") || jobId.startsWith("JOB_")) {
            jobId.replace("_", "-")
        } else {
            "JOB-$jobId"
        }

        val notif = NotificationModel(
            id = "rt_job_${System.currentTimeMillis()}",
            type = NotificationCategoryType.JOB_ASSIGNED,
            title = "Nhiệm vụ mới #$cleanId",
            subtitle = if (totalBins > 0) "Bạn được giao tuyến thu gom $totalBins thùng rác." else (routeDesc ?: "Bạn được giao tuyến thu gom mới."),
            content = "Admin vừa điều phối tuyến thu gom #$cleanId với $totalBins điểm thùng rác cần xử lý. Vui lòng nhận nhiệm vụ và bắt đầu lộ trình sớm.",
            timeStr = timeFormat.format(now),
            fullDateStr = fullFormat.format(now),
            dateGroup = "Hôm nay",
            isUnread = true,
            jobId = cleanId,
            totalBins = totalBins,
            location = routeDesc ?: "Khu vực trung tâm"
        )
        addNotification(notif)
    }

    fun addNewRealtimeOverfullBin(binId: String, binName: String, location: String, fillPercent: Int) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val fullFormat = SimpleDateFormat("HH:mm • dd/MM/yyyy", Locale.getDefault())
        val now = Date()

        val notif = NotificationModel(
            id = "rt_bin_${System.currentTimeMillis()}",
            type = NotificationCategoryType.BIN_OVERFULL,
            title = "Cảnh báo thùng quá tải ($fillPercent%)",
            subtitle = "$binName, $location cần thu gom ngay.",
            content = "$binName tại $location đang vượt mức cho phép ($fillPercent%). Vui lòng đến thu gom trong thời gian sớm nhất.",
            timeStr = timeFormat.format(now),
            fullDateStr = fullFormat.format(now),
            dateGroup = "Hôm nay",
            isUnread = true,
            binId = binId,
            binName = binName,
            location = location,
            fillPercent = fillPercent,
            capacityLiters = 240
        )
        addNotification(notif)
    }

    fun addNewRealtimeJobCancelled(jobId: String, reason: String?) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val fullFormat = SimpleDateFormat("HH:mm • dd/MM/yyyy", Locale.getDefault())
        val now = Date()

        val cleanId = if (jobId.startsWith("JOB-") || jobId.startsWith("JOB_")) {
            jobId.replace("_", "-")
        } else {
            "JOB-$jobId"
        }

        val notif = NotificationModel(
            id = "rt_cancel_${System.currentTimeMillis()}",
            type = NotificationCategoryType.JOB_CANCELLED,
            title = "Nhiệm vụ #$cleanId bị hủy",
            subtitle = "Lý do: ${reason ?: "Điều phối lại tuyến."}",
            content = "Nhiệm vụ #$cleanId đã bị hủy bởi Quản trị viên hệ thống. Lý do: ${reason ?: "Điều phối lại tuyến cho tài xế khác."}",
            timeStr = timeFormat.format(now),
            fullDateStr = fullFormat.format(now),
            dateGroup = "Hôm nay",
            isUnread = true,
            jobId = cleanId,
            reason = reason ?: "Điều phối lại tuyến cho tài xế khác."
        )
        addNotification(notif)
    }

    fun addNewRealtimeIncident(title: String, message: String) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val fullFormat = SimpleDateFormat("HH:mm • dd/MM/yyyy", Locale.getDefault())
        val now = Date()

        val notif = NotificationModel(
            id = "rt_incident_${System.currentTimeMillis()}",
            type = NotificationCategoryType.BROADCAST_INCIDENT,
            title = title.ifBlank { "Thông báo khẩn" },
            subtitle = message,
            content = message,
            timeStr = timeFormat.format(now),
            fullDateStr = fullFormat.format(now),
            dateGroup = "Hôm nay",
            isUnread = true
        )
        addNotification(notif)
    }

    private fun getInitialSeedData(): List<NotificationModel> {
        return listOf(
            // 1. Hôm nay: Nhiệm vụ mới #JOB-0258
            NotificationModel(
                id = "notif_01",
                type = NotificationCategoryType.JOB_ASSIGNED,
                title = "Nhiệm vụ mới #JOB-0258",
                subtitle = "Bạn được giao tuyến thu gom 3 thùng rác.",
                content = "Bạn vừa được điều phối ca thu gom mới mã #JOB-0258 bao gồm 3 điểm thùng rác tại khu vực Bến Bạch Đằng & Quận 1. Vui lòng nhận nhiệm vụ và thực hiện đúng thời gian quy định.",
                timeStr = "10:30",
                fullDateStr = "10:30 • 20/05/2025",
                dateGroup = "Hôm nay",
                isUnread = true,
                jobId = "JOB-0258",
                totalBins = 3,
                location = "Quận 1, TP.HCM"
            ),
            // 2. Hôm nay: Cảnh báo thùng quá tải (90%)
            NotificationModel(
                id = "notif_02",
                type = NotificationCategoryType.BIN_OVERFULL,
                title = "Cảnh báo thùng quá tải (90%)",
                subtitle = "Thùng rác Bến Bạch Đằng, Q.1 cần thu gom ngay.",
                content = "Thùng rác Bến Bạch Đằng, Quận 1 đang vượt mức cho phép (90%). Vui lòng đến thu gom trong thời gian sớm nhất.",
                timeStr = "10:31",
                fullDateStr = "10:31 • 20/05/2025",
                dateGroup = "Hôm nay",
                isUnread = true,
                binId = "BIN-015",
                binName = "Thùng rác Bến Bạch Đằng",
                location = "Bến Bạch Đằng, Quận 1",
                fillPercent = 90,
                capacityLiters = 240
            ),
            // 3. Hôm nay: Nhiệm vụ #JOB-0245 bị hủy
            NotificationModel(
                id = "notif_03",
                type = NotificationCategoryType.JOB_CANCELLED,
                title = "Nhiệm vụ #JOB-0245 bị hủy",
                subtitle = "Lý do: Điều phối lại tuyến.",
                content = "Nhiệm vụ #JOB-0245 đã bị hủy bởi Quản trị viên hệ thống. Lý do: Điều phối lại tuyến cho tài xế khác. Danh sách nhiệm vụ của bạn đã được cập nhật.",
                timeStr = "10:32",
                fullDateStr = "10:32 • 20/05/2025",
                dateGroup = "Hôm nay",
                isUnread = true,
                jobId = "JOB-0245",
                reason = "Điều phối lại tuyến cho tài xế khác."
            ),
            // 4. Hôm nay: Thông báo khẩn
            NotificationModel(
                id = "notif_04",
                type = NotificationCategoryType.BROADCAST_INCIDENT,
                title = "Thông báo khẩn",
                subtitle = "Khu vực Quận 4 đang có mưa lớn, đường trơn trượt.",
                content = "Ban điều hành thông báo khẩn: Khu vực Quận 4 và lân cận đang có mưa lớn cục bộ, một số tuyến đường trơn trượt và ngập nhẹ. Các tài xế chú ý giảm tốc độ và tuân thủ an toàn lao động.",
                timeStr = "10:33",
                fullDateStr = "10:33 • 20/05/2025",
                dateGroup = "Hôm nay",
                isUnread = true,
                location = "Quận 4, TP.HCM"
            ),
            // 5. Hôm qua: Nhiệm vụ hoàn thành
            NotificationModel(
                id = "notif_05",
                type = NotificationCategoryType.JOB_COMPLETED,
                title = "Nhiệm vụ hoàn thành",
                subtitle = "Bạn đã hoàn thành nhiệm vụ #JOB-0238.",
                content = "Chúc mừng bạn đã hoàn thành xuất sắc tuyến thu gom #JOB-0238. Dữ liệu các điểm thùng rác đã được lưu và đồng bộ lên máy chủ thành công.",
                timeStr = "18:45",
                fullDateStr = "18:45 • 19/05/2025",
                dateGroup = "Hôm qua",
                isUnread = false,
                jobId = "JOB-0238",
                totalBins = 4,
                location = "Quận 7, TP.HCM"
            )
        )
    }
}
