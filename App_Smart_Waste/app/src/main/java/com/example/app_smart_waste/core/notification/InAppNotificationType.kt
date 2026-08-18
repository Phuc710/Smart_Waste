package com.example.app_smart_waste.core.notification

import android.graphics.Color
import androidx.annotation.DrawableRes
import com.example.app_smart_waste.R

/**
 * Senior Enterprise Sealed Model for System & In-App Notifications
 * Defines the 4 standard notification categories with clean text, colors, and UI tokens.
 */
sealed class InAppNotificationType {

    abstract val title: String
    abstract val subtitle: String
    abstract val actionText: String
    @get:DrawableRes abstract val iconRes: Int
    @get:DrawableRes abstract val iconBgRes: Int
    @get:DrawableRes abstract val buttonBgRes: Int
    @get:DrawableRes abstract val dotBgRes: Int
    abstract val buttonTextColor: Int

    /**
     * 1. Giao việc mới (Admin Assign)
     * Format: "Nhiệm vụ mới #JOB-0258" | "Bạn được giao tuyến thu gom 3 thùng rác." | [ Xem ngay ]
     */
    data class JobAssigned(
        val jobId: String,
        val totalBins: Int = 0,
        val routeDesc: String? = null
    ) : InAppNotificationType() {
        private val cleanId: String
            get() {
                val raw = jobId.removePrefix("#").trim()
                return if (raw.startsWith("JOB-") || raw.startsWith("JOB_")) {
                    raw.replace("_", "-")
                } else {
                    "JOB-$raw"
                }
            }

        override val title: String
            get() = "Nhiệm vụ mới #$cleanId"

        override val subtitle: String
            get() = if (totalBins > 0) {
                "Bạn được giao tuyến thu gom $totalBins thùng rác."
            } else {
                routeDesc?.takeIf { it.isNotBlank() } ?: "Bạn được giao tuyến thu gom mới."
            }

        override val actionText: String = "Xem ngay"
        override val iconRes: Int = R.drawable.ic_banner_clipboard_green
        override val iconBgRes: Int = R.drawable.bg_banner_icon_green
        override val buttonBgRes: Int = R.drawable.bg_btn_banner_action_green
        override val dotBgRes: Int = R.drawable.bg_dot_active_green
        override val buttonTextColor: Int = Color.parseColor("#16A34A")
    }

    /**
     * 2. Thùng rác khẩn cấp (Overfull Alert >= 85%)
     * Format: "Cảnh báo thùng quá tải (90%)" | "Thùng rác Bến Bạch Đằng, Quận 1 cần gom ngay." | [ Mở bản đồ ]
     */
    data class BinOverfull(
        val binId: String,
        val binName: String,
        val location: String,
        val levelPercent: Int
    ) : InAppNotificationType() {
        override val title: String
            get() = "Cảnh báo thùng quá tải ($levelPercent%)"

        override val subtitle: String
            get() = "$binName, $location cần gom ngay."

        override val actionText: String = "Mở bản đồ"
        override val iconRes: Int = R.drawable.ic_banner_alert_red
        override val iconBgRes: Int = R.drawable.bg_banner_icon_red
        override val buttonBgRes: Int = R.drawable.bg_btn_banner_action_red
        override val dotBgRes: Int = R.drawable.bg_badge_circle_red_solid
        override val buttonTextColor: Int = Color.parseColor("#EF4444")
    }

    /**
     * 3. Ca bị hủy / Hết hạn (Cancelled / Expired)
     * Format: "Nhiệm vụ #JOB-0245 đã bị hủy" | "Lý do: Điều phối lại tuyến cho tài xế khác." | [ Xem chi tiết ]
     */
    data class JobCancelled(
        val jobId: String,
        val reason: String? = null
    ) : InAppNotificationType() {
        private val cleanId: String
            get() {
                val raw = jobId.removePrefix("#").trim()
                return if (raw.startsWith("JOB-") || raw.startsWith("JOB_")) {
                    raw.replace("_", "-")
                } else {
                    "JOB-$raw"
                }
            }

        override val title: String
            get() = "Nhiệm vụ #$cleanId đã bị hủy"

        override val subtitle: String
            get() = "Lý do: ${reason?.takeIf { it.isNotBlank() } ?: "Điều phối lại tuyến cho tài xế khác."}"

        override val actionText: String = "Xem chi tiết"
        override val iconRes: Int = R.drawable.ic_banner_warning_orange
        override val iconBgRes: Int = R.drawable.bg_banner_icon_orange
        override val buttonBgRes: Int = R.drawable.bg_btn_banner_action_orange
        override val dotBgRes: Int = R.drawable.bg_circle_notif_yellow
        override val buttonTextColor: Int = Color.parseColor("#F59E0B")
    }

    /**
     * 4. Tin tức / Sự cố phát thanh (Broadcast / Incident)
     * Format: "Thông báo khẩn" | "Khu vực Quận 4 đang có mưa lớn, đường trơn trượt." | [ Xem chi tiết ]
     */
    data class BroadcastIncident(
        val customTitle: String? = null,
        val message: String
    ) : InAppNotificationType() {
        override val title: String
            get() = customTitle?.takeIf { it.isNotBlank() } ?: "Thông báo khẩn"

        override val subtitle: String
            get() = message

        override val actionText: String = "Xem chi tiết"
        override val iconRes: Int = R.drawable.ic_banner_speaker_blue
        override val iconBgRes: Int = R.drawable.bg_banner_icon_blue
        override val buttonBgRes: Int = R.drawable.bg_btn_banner_action_blue
        override val dotBgRes: Int = R.drawable.bg_circle_notif_blue
        override val buttonTextColor: Int = Color.parseColor("#2563EB")
    }
}
