package com.example.app_smart_waste.core.model

import android.graphics.Color
import androidx.annotation.DrawableRes
import com.example.app_smart_waste.R
import java.io.Serializable

enum class NotificationCategoryType : Serializable {
    JOB_ASSIGNED,
    BIN_OVERFULL,
    JOB_CANCELLED,
    BROADCAST_INCIDENT,
    JOB_COMPLETED
}

/**
 * Senior Enterprise Domain Model for In-App Notification Center & Detail View
 */
data class NotificationModel(
    val id: String,
    val type: NotificationCategoryType,
    val title: String,
    val subtitle: String,
    val content: String,
    val timeStr: String,
    val fullDateStr: String,
    val dateGroup: String = "Hôm nay",
    var isUnread: Boolean = true,
    val binId: String? = null,
    val binName: String? = null,
    val jobId: String? = null,
    val location: String? = null,
    val fillPercent: Int? = null,
    val capacityLiters: Int? = null,
    val totalBins: Int? = null,
    val reason: String? = null
) : Serializable {

    @get:DrawableRes
    val iconRes: Int
        get() = when (type) {
            NotificationCategoryType.JOB_ASSIGNED -> R.drawable.ic_banner_clipboard_green
            NotificationCategoryType.BIN_OVERFULL -> R.drawable.ic_banner_alert_red
            NotificationCategoryType.JOB_CANCELLED -> R.drawable.ic_banner_warning_orange
            NotificationCategoryType.BROADCAST_INCIDENT -> R.drawable.ic_banner_speaker_blue
            NotificationCategoryType.JOB_COMPLETED -> R.drawable.ic_banner_clipboard_green
        }

    @get:DrawableRes
    val iconBgRes: Int
        get() = when (type) {
            NotificationCategoryType.JOB_ASSIGNED -> R.drawable.bg_banner_icon_green
            NotificationCategoryType.BIN_OVERFULL -> R.drawable.bg_banner_icon_red
            NotificationCategoryType.JOB_CANCELLED -> R.drawable.bg_banner_icon_orange
            NotificationCategoryType.BROADCAST_INCIDENT -> R.drawable.bg_banner_icon_blue
            NotificationCategoryType.JOB_COMPLETED -> R.drawable.bg_banner_icon_green
        }

    val primaryColor: Int
        get() = when (type) {
            NotificationCategoryType.JOB_ASSIGNED -> Color.parseColor("#16A34A")
            NotificationCategoryType.BIN_OVERFULL -> Color.parseColor("#EF4444")
            NotificationCategoryType.JOB_CANCELLED -> Color.parseColor("#F59E0B")
            NotificationCategoryType.BROADCAST_INCIDENT -> Color.parseColor("#2563EB")
            NotificationCategoryType.JOB_COMPLETED -> Color.parseColor("#16A34A")
        }

    val actionButtonText: String
        get() = when (type) {
            NotificationCategoryType.JOB_ASSIGNED -> "Xem nhiệm vụ"
            NotificationCategoryType.BIN_OVERFULL -> "Mở bản đồ"
            NotificationCategoryType.JOB_CANCELLED -> "Xem danh sách việc"
            NotificationCategoryType.BROADCAST_INCIDENT -> "Đã hiểu"
            NotificationCategoryType.JOB_COMPLETED -> "Xem lịch sử"
        }
}
