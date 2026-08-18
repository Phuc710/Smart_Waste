package com.example.app_smart_waste.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.app_smart_waste.R
import com.example.app_smart_waste.ui.main.MainActivity

/**
 * Senior Enterprise Android System Notification Manager
 * Handles System Push/Heads-Up Notifications on Lock Screen, Outside App, and Status Bar
 * with standardized Clean Text, high priority channels, and accurate Intent routing.
 */
class AppNotificationManager private constructor(private val context: Context) {

    companion object {
        const val CHANNEL_ID_JOBS = "channel_smart_waste_jobs"
        const val CHANNEL_ID_ALERTS = "channel_smart_waste_alerts"
        const val CHANNEL_ID_SYSTEM = "channel_smart_waste_system"
        const val CHANNEL_ID_GPS = "channel_smart_waste_gps"

        const val NOTIFICATION_ID_GPS_TRACKING = 9001

        const val EXTRA_OPEN_TAB = "EXTRA_OPEN_TAB"
        const val EXTRA_JOB_ID = "EXTRA_JOB_ID"
        const val EXTRA_BIN_ID = "EXTRA_BIN_ID"

        @Volatile
        private var INSTANCE: AppNotificationManager? = null

        fun getInstance(context: Context): AppNotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppNotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    /**
     * Create High Priority Android Notification Channels (API 26+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // 1. Jobs Channel (High Priority - Heads Up Banner & Sound)
            val jobsChannel = NotificationChannel(
                CHANNEL_ID_JOBS,
                "Nhiệm vụ thu gom",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi có nhiệm vụ mới được giao hoặc thay đổi trạng thái"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                setSound(defaultSound, null)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // 2. Critical Alerts Channel (High Priority - Warning & Alerts)
            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Cảnh báo thùng rác & Sự cố",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Cảnh báo thùng rác đầy vượt ngưỡng (>=85%) và sự cố thu gom khẩn cấp"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(defaultSound, null)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // 3. System Channel (Default Priority)
            val systemChannel = NotificationChannel(
                CHANNEL_ID_SYSTEM,
                "Hệ thống SmartWaste",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo kết nối và cập nhật cấu hình hệ thống"
                setShowBadge(false)
            }

            // 4. GPS Tracking Channel (Low/Silent Priority for sticky foreground service)
            val gpsChannel = NotificationChannel(
                CHANNEL_ID_GPS,
                "Theo dõi vị trí tuyến đường",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị trạng thái theo dõi vị trí GPS khi đang thực hiện tuyến thu gom"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            notificationManager.createNotificationChannels(listOf(jobsChannel, alertsChannel, systemChannel, gpsChannel))
        }
    }

    /**
     * Build Sticky Notification for Location Tracking Foreground Service
     */
    fun buildGpsForegroundNotification(jobId: String? = null, queuedCount: Int = 0, speedKmH: Double? = null): android.app.Notification {
        val displayCode = if (!jobId.isNullOrBlank()) normalizeJobCode(jobId) else "TUYẾN ĐANG CHẠY"
        val title = "🚛 Đang theo dõi vị trí ($displayCode)"
        val message = when {
            queuedCount > 0 -> "⚠️ Mất mạng: Đang lưu ngoại tuyến $queuedCount điểm chờ đồng bộ..."
            speedKmH != null && speedKmH > 1.0 -> "🟢 GPS trực tuyến · Vận tốc: ${String.format(java.util.Locale.US, "%.0f", speedKmH)} km/h"
            else -> "🟢 GPS trực tuyến · Sẵn sàng ghi nhận điểm dừng"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TAB, "JOBS")
            if (!jobId.isNullOrBlank()) putExtra(EXTRA_JOB_ID, jobId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_GPS_TRACKING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_icon)
        } catch (_: Exception) { null }

        return NotificationCompat.Builder(context, CHANNEL_ID_GPS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("SmartWaste GPS")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setColor(Color.parseColor("#2563EB")) // Primary Blue
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun normalizeJobCode(jobId: String): String {
        val raw = jobId.removePrefix("#").trim()
        return if (raw.startsWith("JOB-") || raw.startsWith("JOB_")) {
            raw.replace("_", "-")
        } else {
            "JOB-$raw"
        }
    }

    /**
     * 1. Notify Driver of New Admin-Assigned Job (Heads-Up Banner)
     * Format: "Nhiệm vụ mới #JOB-0258" | "Bạn được giao tuyến thu gom 3 thùng rác." | SubText: "Trượt để xem"
     */
    fun showJobAssignedNotification(jobId: String, routeDesc: String? = null, totalBins: Int = 0) {
        if (!hasNotificationPermission()) return

        val displayCode = normalizeJobCode(jobId)
        val title = "Nhiệm vụ mới #$displayCode"
        val message = if (totalBins > 0) {
            "Bạn được giao tuyến thu gom $totalBins thùng rác."
        } else {
            routeDesc?.takeIf { it.isNotBlank() } ?: "Bạn được giao tuyến thu gom mới."
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TAB, "JOBS")
            putExtra(EXTRA_JOB_ID, jobId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            jobId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_icon)
        } catch (_: Exception) { null }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_JOBS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("Trượt để xem")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setColor(Color.parseColor("#16A34A")) // Green
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(R.drawable.ic_notification, "Xem nhiệm vụ", pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(jobId.hashCode(), notification)
    }

    /**
     * 2. Notify Driver of Job Cancellation / Expiration
     * Format: "Nhiệm vụ #JOB-0245 đã bị hủy" | "Lý do: Điều phối lại tuyến cho tài xế khác." | SubText: "Trượt để xem"
     */
    fun showJobCancelledNotification(jobId: String, reason: String? = null) {
        if (!hasNotificationPermission()) return

        val displayCode = normalizeJobCode(jobId)
        val title = "Nhiệm vụ #$displayCode đã bị hủy"
        val message = "Lý do: ${reason?.takeIf { it.isNotBlank() } ?: "Điều phối lại tuyến cho tài xế khác."}"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TAB, "JOBS")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            ("cancel_$jobId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_icon)
        } catch (_: Exception) { null }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_JOBS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("Trượt để xem")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(Color.parseColor("#F59E0B")) // Amber
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_notification, "Xem chi tiết", pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(("cancel_$jobId").hashCode(), notification)
    }

    /**
     * 3. Notify Driver of Bin Overfill Critical Alert (>= 85%)
     * Format: "Cảnh báo thùng quá tải (90%)" | "Thùng rác Bến Bạch Đằng, Quận 1 cần gom ngay." | SubText: "Trượt để xem"
     */
    fun showBinCriticalAlertNotification(binId: String, binName: String, location: String, levelPercent: Int) {
        if (!hasNotificationPermission()) return

        val title = "Cảnh báo thùng quá tải ($levelPercent%)"
        val message = "$binName, $location cần gom ngay."

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TAB, "MAP")
            putExtra(EXTRA_BIN_ID, binId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            ("bin_alert_$binId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_icon)
        } catch (_: Exception) { null }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("Trượt để xem")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(Color.parseColor("#EF4444")) // Red warning
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_map_route_icon, "Mở bản đồ", pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(("bin_alert_$binId").hashCode(), notification)
    }

    /**
     * 4. General Incident / System Broadcast Notification
     * Format: "Thông báo khẩn" | "Khu vực Quận 4 đang có mưa lớn, đường trơn trượt." | SubText: "Trượt để xem"
     */
    fun showIncidentAlertNotification(title: String, message: String) {
        if (!hasNotificationPermission()) return

        val displayTitle = title.ifBlank { "Thông báo khẩn" }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TAB, "HOME")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            displayTitle.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_icon)
        } catch (_: Exception) { null }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(displayTitle)
            .setContentText(message)
            .setSubText("Trượt để xem")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(Color.parseColor("#2563EB")) // Blue
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_notification, "Xem chi tiết", pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(displayTitle.hashCode(), notification)
    }

    // =========================================================================
    // 🧪 SYSTEM PUSH NOTIFICATION TEST HELPERS
    // =========================================================================

    fun testJobAssignedPush() {
        showJobAssignedNotification("0258", "Tuyến thu gom trung tâm Quận 1", 3)
    }

    fun testBinOverfullPush() {
        showBinCriticalAlertNotification("BIN_01", "Thùng rác Bến Bạch Đằng", "Quận 1", 90)
    }

    fun testJobCancelledPush() {
        showJobCancelledNotification("0245", "Điều phối lại tuyến cho tài xế khác.")
    }

    fun testBroadcastIncidentPush() {
        showIncidentAlertNotification("Thông báo khẩn", "Khu vực Quận 4 đang có mưa lớn, đường trơn trượt.")
    }
}
