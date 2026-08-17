package com.example.app_smart_waste.core.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * =========================================================================
 * ⚙️ SMARTWASTE GLOBAL SYSTEM CONFIGURATION (SINGLE SOURCE OF TRUTH)
 * =========================================================================
 * Tất cả thông số kỹ thuật, địa chỉ IP Server, Cổng Port, Tần suất GPS,
 * Time-out và Cấu hình hệ thống được tập trung duy nhất tại file này.
 *
 * 💡 HƯỚNG DẪN ĐỔI IP:
 * 1. Chạy Máy ảo Android (Emulator) kết nối PC:  Dùng "http://10.0.2.2:3000/"
 * 2. Chạy Điện thoại thật qua Wi-Fi nội bộ (LAN): Dùng "http://192.168.x.x:3000/" (Ví dụ: "http://192.168.1.15:3000/")
 * 3. Chạy Server Production / Cloud Domain:      Dùng "https://api.yourdomain.com/"
 *
 * 👉 Chỉ cần sửa DEFAULT_BASE_URL tại đây hoặc chỉnh sửa trực tiếp trong
 *    màn hình Cài đặt hệ thống (Tab Cá nhân -> Icon Bánh răng ⚙️).
 * =========================================================================
 */
object AppConfig {

    private const val PREFS_NAME = "smartwaste_config"

    // Keys lưu trong SharedPreferences
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_GPS_INTERVAL_MS = "gps_interval_ms"
    private const val KEY_AUTO_GPS = "auto_gps_enabled"
    private const val KEY_OVERLOAD_ALERT = "overload_alert_enabled"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_ASSIGN_TIMEOUT_MINUTES = "assign_timeout_minutes"

    // ─────────────────────────────────────────────────────────────────────
    // 🌐 1. CẤU HÌNH SERVER & MẠNG (NETWORK & API ENDPOINTS)
    // ─────────────────────────────────────────────────────────────────────
    const val DEFAULT_SERVER_HOST = "10.0.2.2"
    const val DEFAULT_SERVER_PORT = 3000

    /**
     * URL Gốc mặc định kết nối tới Backend Node.js
     * Đổi giá trị này khi chuyển đổi giữa Máy ảo và Thiết bị thật.
     */
    const val DEFAULT_BASE_URL = "http://10.0.2.2:3000/"

    // MQTT Broker & Socket.IO URL
    const val DEFAULT_MQTT_HOST = "10.0.2.2"
    const val DEFAULT_MQTT_PORT = 1883
    const val DEFAULT_MQTT_URL = "tcp://10.0.2.2:1883"
    const val DEFAULT_SOCKET_IO_URL = "http://10.0.2.2:3000"

    // Time-out mạng (Seconds)
    const val NETWORK_CONNECT_TIMEOUT_SECONDS = 15L
    const val NETWORK_READ_TIMEOUT_SECONDS = 15L
    const val NETWORK_WRITE_TIMEOUT_SECONDS = 15L

    /**
     * Bật/Tắt in Logcat chi tiết cho các cuộc gọi mạng HTTP (Header, Body, Token).
     * Sẽ tự động tắt ở bản Release để bảo mật thông tin tài xế và mật khẩu.
     */
    const val ENABLE_HTTP_LOGGING = true

    // ─────────────────────────────────────────────────────────────────────
    // 🛰️ 2. CẤU HÌNH GPS & TRACKING TỌA ĐỘ
    // ─────────────────────────────────────────────────────────────────────
    const val DEFAULT_GPS_INTERVAL_MS = 15000L // 15 giây gửi tọa độ 1 lần
    const val DEFAULT_GPS_MIN_DISTANCE_METERS = 5f // Di chuyển 5m mới kích hoạt
    const val DEFAULT_GPS_AUTO_ENABLED = true

    // Tọa độ mặc định trung tâm TP. Hồ Chí Minh (Chợ Bến Thành)
    const val DEFAULT_MAP_LAT = 10.7769
    const val DEFAULT_MAP_LNG = 106.7009
    const val DEFAULT_MAP_ZOOM = 14

    // ─────────────────────────────────────────────────────────────────────
    // 📋 3. CẤU HÌNH NHIỆM VỤ & THU GOM (JOBS & DISPATCH)
    // ─────────────────────────────────────────────────────────────────────
    const val JOB_ASSIGNED_COUNTDOWN_SECONDS = 300L // 5 phút đếm ngược nhận ca
    const val MAX_ATTACHED_PHOTO_SIZE_MB = 5L // Giới hạn ảnh chụp < 5MB
    const val FILL_THRESHOLD_WARNING = 70 // Ngưỡng rác cảnh báo (70%)
    const val FILL_THRESHOLD_CRITICAL = 85 // Ngưỡng rác quá tải / khẩn cấp (85%)

    // ─────────────────────────────────────────────────────────────────────
    // 🔔 4. CẤU HÌNH ÂM BÁO & CẢNH BÁO
    // ─────────────────────────────────────────────────────────────────────
    const val DEFAULT_OVERLOAD_ALERT_ENABLED = true
    const val DEFAULT_SOUND_ALERT_ENABLED = true

    // ─────────────────────────────────────────────────────────────────────
    // 🛠️ GETTERS & SETTERS (PERSISTENT STORAGE)
    // ─────────────────────────────────────────────────────────────────────

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Lấy BASE_URL hiện hành (Ưu tiên cấu hình do người dùng lưu, nếu chưa có thì lấy DEFAULT_BASE_URL)
     */
    fun getBaseUrl(context: Context): String {
        val url = getPrefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        return if (url.endsWith("/")) url else "$url/"
    }

    /**
     * Cập nhật BASE_URL mới và tự động reset Retrofit Client
     */
    fun setBaseUrl(context: Context, url: String) {
        val cleanUrl = url.trim()
        val formatted = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
        getPrefs(context).edit().putString(KEY_BASE_URL, formatted).apply()
        com.example.app_smart_waste.core.network.RetrofitClient.resetClient()
    }

    /**
     * Lấy tần suất cập nhật GPS (Milliseconds)
     */
    fun getGpsIntervalMs(context: Context): Long {
        return getPrefs(context).getLong(KEY_GPS_INTERVAL_MS, DEFAULT_GPS_INTERVAL_MS)
    }

    /**
     * Cập nhật tần suất cập nhật GPS
     */
    fun setGpsIntervalMs(context: Context, intervalMs: Long) {
        getPrefs(context).edit().putLong(KEY_GPS_INTERVAL_MS, intervalMs).apply()
    }

    /**
     * Bật/tắt tự động gửi GPS
     */
    fun isAutoGpsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_GPS, DEFAULT_GPS_AUTO_ENABLED)
    }

    fun setAutoGpsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_GPS, enabled).apply()
    }

    /**
     * Bật/tắt cảnh báo quá tải (>85%)
     */
    fun isOverloadAlertEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_OVERLOAD_ALERT, DEFAULT_OVERLOAD_ALERT_ENABLED)
    }

    fun setOverloadAlertEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OVERLOAD_ALERT, enabled).apply()
    }

    /**
     * Lấy thời gian đếm ngược nhận ca do Admin cấu hình (Phút)
     */
    fun getAssignTimeoutMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_ASSIGN_TIMEOUT_MINUTES, 5)
    }

    fun setAssignTimeoutMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_ASSIGN_TIMEOUT_MINUTES, minutes).apply()
    }

    /**
     * Khôi phục toàn bộ thông số về mặc định gốc
     */
    fun resetToDefaults(context: Context) {
        getPrefs(context).edit().clear().apply()
        com.example.app_smart_waste.core.network.RetrofitClient.resetClient()
    }
}
