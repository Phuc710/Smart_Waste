package com.example.app_smart_waste.core.network

import android.content.Context
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.notification.AppNotificationManager
import com.example.app_smart_waste.core.notification.InAppNotificationManager
import com.example.app_smart_waste.core.notification.InAppNotificationType
import com.example.app_smart_waste.core.storage.AppConfig
import com.example.app_smart_waste.core.storage.SecureTokenStorage
import com.example.app_smart_waste.core.utils.ActivityLifecycleTracker
import com.example.app_smart_waste.data.repository.NotificationRepository
import com.example.app_smart_waste.ui.main.MainActivity
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/**
 * Senior Enterprise Realtime Manager (Socket.IO)
 * Listens to Realtime dispatching events, bin telemetries, job lifecycle updates, and broadcast alerts.
 * Dispatches both System Push Notifications and In-App Top Dropdown Banners seamlessly.
 */
class RealtimeManager(context: Context) {

    interface Listener {
        fun onJobUpdated() {}
        fun onJobUpdated(jobId: String?) {
            onJobUpdated()
        }
        fun onBinUpdated(binId: String, levelPercent: Double? = null, isOnline: Boolean? = null) {}
        fun onBinOverfullAlert(alert: BinOverfullAlert) {}
        fun onConnectionStateChanged(connected: Boolean) {}
    }

    data class BinOverfullAlert(
        val binId: String,
        val name: String,
        val location: String,
        val levelPercent: Int,
        val occurredAt: String?
    )

    private val appContext = context.applicationContext
    private var socket: Socket? = null
    private var listener: Listener? = null

    fun connect(listener: Listener) {
        this.listener = listener
        if (socket?.connected() == true) return

        val baseUrl = AppConfig.getBaseUrl(appContext).removeSuffix("/")
        val cookieHeader = RetrofitClient.getInstance(appContext).getCookieJar()
            .getCookieHeader(baseUrl.toHttpUrlOrNull() ?: return)
            ?: return

        val options = IO.Options().apply {
            extraHeaders = mapOf("Cookie" to listOf(cookieHeader))
            transports = arrayOf("websocket", "polling")
            reconnection = true
            reconnectionAttempts = 10
            reconnectionDelay = 1000
            reconnectionDelayMax = 5000
            timeout = 10000
        }

        disconnect() // Clean previous socket if any

        socket = IO.socket(baseUrl, options).apply {
            on(Socket.EVENT_CONNECT) {
                this@RealtimeManager.listener?.onConnectionStateChanged(true)
            }
            on(Socket.EVENT_DISCONNECT) {
                this@RealtimeManager.listener?.onConnectionStateChanged(false)
            }
            on(Socket.EVENT_CONNECT_ERROR) {
                this@RealtimeManager.listener?.onConnectionStateChanged(false)
            }

            // 1. Job Updated Event
            on("jobUpdated") { args ->
                try {
                    val payload = args.firstOrNull() as? JSONObject
                    if (payload != null) {
                        val jobId = payload.optString("id").takeIf { it.isNotBlank() } ?: payload.optString("job_id")
                        val status = payload.optString("status").uppercase()
                        val employeeId = payload.optString("employee_id").ifBlank { payload.optString("employeeId") }
                        val currentUserId = SecureTokenStorage.getInstance(appContext).getUserId()

                        if (!currentUserId.isNullOrBlank() && (employeeId.isBlank() || employeeId == currentUserId)) {
                            val cleanJobId = jobId ?: "NEW"
                            if (status == "ASSIGNED") {
                                val targetBins = payload.optJSONArray("target_bin_ids")?.length() ?: 0

                                // 0. Save to in-app Notification Center history
                                NotificationRepository.getInstance().addNewRealtimeJob(
                                    jobId = cleanJobId,
                                    totalBins = targetBins,
                                    routeDesc = "Admin vừa phân công ca thu gom mới"
                                )

                                // 1. System Push Notification (outside app / lockscreen)
                                AppNotificationManager.getInstance(appContext).showJobAssignedNotification(
                                    jobId = cleanJobId,
                                    routeDesc = "Admin vừa phân công ca thu gom mới",
                                    totalBins = targetBins
                                )

                                // 2. In-App Dropdown Top Banner (if inside app)
                                if (ActivityLifecycleTracker.isAppInForeground) {
                                    InAppNotificationManager.show(
                                        notification = InAppNotificationType.JobAssigned(
                                            jobId = cleanJobId,
                                            totalBins = targetBins,
                                            routeDesc = "Admin vừa phân công ca thu gom mới"
                                        )
                                    ) {
                                        (ActivityLifecycleTracker.currentActivity as? MainActivity)?.selectTab(R.id.navItemJobs)
                                    }
                                }
                            } else if (status in listOf("CANCELLED", "EXPIRED", "REJECTED")) {
                                val reason = payload.optString("pause_reason").ifBlank { payload.optString("reason") }
                                    .ifBlank { "Điều phối lại tuyến cho tài xế khác." }

                                // 0. Save to in-app Notification Center history
                                NotificationRepository.getInstance().addNewRealtimeJobCancelled(
                                    jobId = cleanJobId,
                                    reason = reason
                                )

                                // 1. System Push
                                AppNotificationManager.getInstance(appContext).showJobCancelledNotification(
                                    jobId = cleanJobId,
                                    reason = reason
                                )

                                // 2. In-App Banner
                                if (ActivityLifecycleTracker.isAppInForeground) {
                                    InAppNotificationManager.show(
                                        notification = InAppNotificationType.JobCancelled(
                                            jobId = cleanJobId,
                                            reason = reason
                                        )
                                    ) {
                                        (ActivityLifecycleTracker.currentActivity as? MainActivity)?.selectTab(R.id.navItemJobs)
                                    }
                                }
                            }
                        }

                        this@RealtimeManager.listener?.onJobUpdated(jobId)
                    }
                } catch (_: Exception) {}
            }

            // 2. Job Assigned Direct Event
            on("jobAssigned") { args ->
                try {
                    val payload = args.firstOrNull() as? JSONObject
                    val jobId = payload?.optString("id")?.takeIf { it.isNotBlank() } ?: payload?.optString("job_id")
                    if (jobId != null) {
                        val targetBins = payload?.optJSONArray("target_bin_ids")?.length() ?: 0

                        // 0. Save to in-app Notification Center history
                        NotificationRepository.getInstance().addNewRealtimeJob(
                            jobId = jobId,
                            totalBins = targetBins,
                            routeDesc = "Admin vừa phân công ca thu gom mới"
                        )

                        // 1. System Push Notification
                        AppNotificationManager.getInstance(appContext).showJobAssignedNotification(
                            jobId = jobId,
                            routeDesc = "Admin vừa phân công ca thu gom mới",
                            totalBins = targetBins
                        )

                        // 2. In-App Banner
                        if (ActivityLifecycleTracker.isAppInForeground) {
                            InAppNotificationManager.show(
                                notification = InAppNotificationType.JobAssigned(
                                    jobId = jobId,
                                    totalBins = targetBins,
                                    routeDesc = "Admin vừa phân công ca thu gom mới"
                                )
                            ) {
                                (ActivityLifecycleTracker.currentActivity as? MainActivity)?.selectTab(R.id.navItemJobs)
                            }
                        }
                    }
                    this@RealtimeManager.listener?.onJobUpdated(jobId)
                } catch (_: Exception) {}
            }

            // 3. Job Completed Event
            on("jobCompleted") { args ->
                val payload = args.firstOrNull() as? JSONObject
                val jobId = payload?.optString("jobId")?.takeIf { it.isNotBlank() }
                this@RealtimeManager.listener?.onJobUpdated(jobId)
            }

            // 4. Bin Telemetry Data Event
            on("binData") { args ->
                try {
                    val payload = args.firstOrNull() as? JSONObject
                    if (payload != null) {
                        val binId = payload.optString("binId")
                        val dataObj = payload.optJSONObject("data")
                        val level = if (dataObj != null && dataObj.has("levelPercent")) dataObj.optDouble("levelPercent") else null
                        val isOnline = if (dataObj != null && dataObj.has("isOnline")) dataObj.optBoolean("isOnline") else null
                        if (binId.isNotBlank()) {
                            this@RealtimeManager.listener?.onBinUpdated(binId, level, isOnline)
                        }
                    }
                } catch (_: Exception) {}
            }

            // 5. Bin Overfull Critical Alert Event (>= 85%)
            on("binOverfullAlert") { args ->
                try {
                    val payload = args.firstOrNull() as? JSONObject
                    if (payload != null) {
                        val binId = payload.optString("binId")
                        val name = payload.optString("name").ifBlank { "Thùng $binId" }
                        val location = payload.optString("location").ifBlank { "Quận 1" }
                        val levelPercent = payload.optInt("levelPercent")
                        val occurredAt = payload.optString("occurredAt").takeIf { it.isNotBlank() }

                        if (levelPercent >= 85 && binId.isNotBlank()) {
                            // 0. Save to in-app Notification Center history
                            NotificationRepository.getInstance().addNewRealtimeOverfullBin(
                                binId = binId,
                                binName = name,
                                location = location,
                                fillPercent = levelPercent
                            )

                            // 1. System Push
                            AppNotificationManager.getInstance(appContext).showBinCriticalAlertNotification(
                                binId = binId,
                                binName = name,
                                location = location,
                                levelPercent = levelPercent
                            )

                            // 2. In-App Banner
                            if (ActivityLifecycleTracker.isAppInForeground) {
                                InAppNotificationManager.show(
                                    notification = InAppNotificationType.BinOverfull(
                                        binId = binId,
                                        binName = name,
                                        location = location,
                                        levelPercent = levelPercent
                                    )
                                ) {
                                    (ActivityLifecycleTracker.currentActivity as? MainActivity)?.selectTab(R.id.navItemMap)
                                }
                            }
                        }

                        this@RealtimeManager.listener?.onBinOverfullAlert(
                            BinOverfullAlert(
                                binId = binId,
                                name = name,
                                location = location,
                                levelPercent = levelPercent,
                                occurredAt = occurredAt
                            )
                        )
                    }
                } catch (_: Exception) {}
            }

            // 6. Broadcast Event / Emergency Incident
            on("newEvent") { args ->
                try {
                    val payload = args.firstOrNull() as? JSONObject
                    if (payload != null) {
                        val title = payload.optString("title").ifBlank { "Thông báo khẩn" }
                        val desc = payload.optString("description").ifBlank { payload.optString("message") }
                        if (desc.isNotBlank()) {
                            // 0. Save to in-app Notification Center history
                            NotificationRepository.getInstance().addNewRealtimeIncident(title, desc)

                            // 1. System Push
                            AppNotificationManager.getInstance(appContext).showIncidentAlertNotification(title, desc)

                            // 2. In-App Banner
                            if (ActivityLifecycleTracker.isAppInForeground) {
                                InAppNotificationManager.show(
                                    notification = InAppNotificationType.BroadcastIncident(
                                        customTitle = title,
                                        message = desc
                                    )
                                ) {
                                    (ActivityLifecycleTracker.currentActivity as? MainActivity)?.selectTab(R.id.navItemHome)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            connect()
        }
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
        listener = null
    }
}
