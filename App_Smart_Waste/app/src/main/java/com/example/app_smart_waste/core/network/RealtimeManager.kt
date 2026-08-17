package com.example.app_smart_waste.core.network

import android.content.Context
import com.example.app_smart_waste.core.storage.AppConfig
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

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
            on("jobUpdated") { args ->
                val payload = args.firstOrNull() as? JSONObject
                val jobId = payload?.optString("id")?.takeIf { it.isNotBlank() }
                this@RealtimeManager.listener?.onJobUpdated(jobId)
            }
            on("jobAssigned") { args ->
                val payload = args.firstOrNull() as? JSONObject
                val jobId = payload?.optString("id")?.takeIf { it.isNotBlank() }
                this@RealtimeManager.listener?.onJobUpdated(jobId)
            }
            on("jobCompleted") { args ->
                val payload = args.firstOrNull() as? JSONObject
                val jobId = payload?.optString("jobId")?.takeIf { it.isNotBlank() }
                this@RealtimeManager.listener?.onJobUpdated(jobId)
            }
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
                } catch (e: Exception) {
                    // Safe parse
                }
            }
            on("binOverfullAlert") { args ->
                try {
                    val payload = args.firstOrNull() as? JSONObject
                    if (payload != null) {
                        this@RealtimeManager.listener?.onBinOverfullAlert(
                            BinOverfullAlert(
                                binId = payload.optString("binId"),
                                name = payload.optString("name"),
                                location = payload.optString("location"),
                                levelPercent = payload.optInt("levelPercent"),
                                occurredAt = payload.optString("occurredAt").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Safe parse
                }
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
