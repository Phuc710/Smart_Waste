package com.example.app_smart_waste.core.utils

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {

    private val vnTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val isoSimpleFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val vnDisplayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).apply {
        timeZone = vnTimeZone
    }

    private val vnDisplayTimeFormat = SimpleDateFormat("HH:mm", Locale("vi", "VN")).apply {
        timeZone = vnTimeZone
    }

    private val vnDisplayFullTimeFormat = SimpleDateFormat("HH:mm:ss", Locale("vi", "VN")).apply {
        timeZone = vnTimeZone
    }

    private val vnDisplayDateTimeFormat = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale("vi", "VN")).apply {
        timeZone = vnTimeZone
    }

    private val vnFullStampFormat = SimpleDateFormat("HH:mm:ss • dd/MM/yyyy", Locale("vi", "VN")).apply {
        timeZone = vnTimeZone
    }

    /**
     * Parses an ISO 8601 UTC string to a Date object.
     */
    fun parseIsoDate(isoString: String?): Date? {
        if (isoString.isNullOrBlank()) return null
        return try {
            isoFormat.parse(isoString)
        } catch (_: Exception) {
            try {
                isoSimpleFormat.parse(isoString.substringBefore("."))
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Formats a Date or ISO string to VN time range: "17/05/2026 • 08:30 - 09:15"
     */
    fun formatJobTimeRange(startIso: String?, endIso: String?, defaultDate: String = "17/05/2026"): String {
        return formatHistoryJobTime(null, startIso, endIso, defaultDate)
    }

    /**
     * Formats time for completed vs cancelled jobs:
     * - Completed: "17/05/2026 • 08:30 - 09:15" (Time nhận - Time Done)
     * - Cancelled: "16/05/2026 • Đã hủy lúc 14:10" (No "Đang chạy" text!)
     */
    fun formatHistoryJobTime(status: String?, startIso: String?, endIso: String?, defaultDate: String = "17/05/2026"): String {
        val startDate = parseIsoDate(startIso)
        val endDate = parseIsoDate(endIso)

        if (status == "CANCELLED" || status == "REJECTED") {
            val cancelDate = endDate ?: startDate ?: Date()
            val dateStr = vnDisplayDateFormat.format(cancelDate)
            val timeStr = vnDisplayTimeFormat.format(cancelDate)
            return "$dateStr • Đã hủy lúc $timeStr"
        }

        if (startDate != null && endDate != null) {
            val dateStr = vnDisplayDateFormat.format(startDate)
            val startStr = vnDisplayTimeFormat.format(startDate)
            val endStr = vnDisplayTimeFormat.format(endDate)
            return "$dateStr • $startStr - $endStr"
        } else if (startDate != null) {
            val dateStr = vnDisplayDateFormat.format(startDate)
            val startStr = vnDisplayTimeFormat.format(startDate)
            return "$dateStr • $startStr"
        }

        return "$defaultDate • 08:30 - 09:15"
    }

    /**
     * Formats single timestamp for a collected bin: "08:42:15 • 17/05/2026"
     */
    fun formatCollectedTimestamp(isoString: String?, defaultFallback: String = "08:42:15 • 17/05/2026"): String {
        val date = parseIsoDate(isoString)
        return if (date != null) {
            vnFullStampFormat.format(date)
        } else {
            defaultFallback
        }
    }

    /**
     * Formats current time in Vietnam Timezone: "17/05/2026 • 08:30"
     */
    fun getCurrentVnDateTime(): String {
        return vnDisplayDateTimeFormat.format(Date())
    }

    /**
     * Formats ISO timestamp to Vietnam display format: "18/05/2026 • 09:39"
     */
    fun formatDisplayDateTime(isoString: String?): String {
        val date = parseIsoDate(isoString) ?: return "Chưa có dữ liệu"
        return vnDisplayDateTimeFormat.format(date)
    }

    /**
     * Formats current time in Vietnam Timezone: "08:30:00"
     */
    fun getCurrentVnFullTime(): String {
        return vnDisplayFullTimeFormat.format(Date())
    }

    /**
     * Formats duration in minutes to readable VN text (e.g. 45 -> "45 phút", 75 -> "1h 15p")
     */
    fun formatDurationMinutes(minutes: Int): String {
        return if (minutes >= 60) {
            val h = minutes / 60
            val m = minutes % 60
            if (m > 0) "${h}h ${m}p" else "$h giờ"
        } else {
            "$minutes phút"
        }
    }

    /**
     * Calculates remaining seconds for an assigned job based on assigned_at and timeout minutes.
     */
    fun calculateJobCountdownSeconds(assignedAtIso: String?, timeoutMinutes: Int = 5): Long {
        val assignedDate = parseIsoDate(assignedAtIso) ?: return (timeoutMinutes * 60L)
        val elapsedMs = System.currentTimeMillis() - assignedDate.time
        val totalMs = timeoutMinutes * 60 * 1000L
        val remainingMs = totalMs - elapsedMs
        return maxOf(0L, remainingMs / 1000L)
    }

    /**
     * Formats remaining seconds as "Còn MM:SS để xác nhận" or "Đã hết hạn nhận ca" (no duplicate emoji icon)
     */
    fun formatJobCountdownText(remainingSeconds: Long): String {
        if (remainingSeconds <= 0) {
            return "Đã hết hạn nhận ca"
        }
        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60
        return String.format(Locale.US, "Còn %02d:%02d để xác nhận", mins, secs)
    }
}
