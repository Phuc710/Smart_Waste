package com.example.app_smart_waste.core.network

import android.content.Context
import com.example.app_smart_waste.core.storage.AppConfig
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

class RealtimeManager(context: Context) {

    interface Listener {
        fun onJobUpdated()
        fun onBinOverfullAlert(alert: BinOverfullAlert)
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
        }
        socket = IO.socket(baseUrl, options).apply {
            on("jobUpdated") { this@RealtimeManager.listener?.onJobUpdated() }
            on("binOverfullAlert") { args ->
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
