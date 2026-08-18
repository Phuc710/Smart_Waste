package com.example.app_smart_waste.core.location

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.app_smart_waste.core.model.BatchLocationItem
import com.example.app_smart_waste.core.notification.AppNotificationManager
import com.example.app_smart_waste.data.repository.JobsRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Senior Enterprise Foreground Service for Continuous Realtime Route GPS Tracking.
 * Complies with Android 14+ Foreground Service policies (foregroundServiceType="location").
 * 
 * Flow: FusedLocationProvider (or LocationManager Fallback)
 *       -> LocationFilter (Accuracy, Noise, Jitter)
 *       -> Online Upload (POST /api/location) OR Offline Queue (FIFO Buffer)
 *       -> Batch Replay (POST /api/location/batch) when connectivity restores.
 */
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"

        const val ACTION_START_TRACKING = "com.example.app_smart_waste.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.app_smart_waste.ACTION_STOP_TRACKING"
        const val ACTION_FLUSH_QUEUE = "com.example.app_smart_waste.ACTION_FLUSH_QUEUE"

        const val EXTRA_JOB_ID = "EXTRA_JOB_ID"
        const val EXTRA_TRACKING_SESSION_ID = "EXTRA_TRACKING_SESSION_ID"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastTrackedLocation: Location? = null
            private set

        fun startService(context: Context, jobId: String? = null, trackingSessionId: String? = null) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_JOB_ID, jobId)
                putExtra(EXTRA_TRACKING_SESSION_ID, trackingSessionId ?: UUID.randomUUID().toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val locationFilter = LocationFilter()
    private lateinit var offlineQueue: OfflineLocationQueue
    private lateinit var notifManager: AppNotificationManager
    private lateinit var jobsRepo: JobsRepository

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var fallbackLocationManager: LocationManager? = null

    private var currentJobId: String? = null
    private var currentSessionId: String? = null

    private fun formatUtcIso(timeMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(if (timeMs > 0) timeMs else System.currentTimeMillis()))
    }

    override fun onCreate() {
        super.onCreate()
        offlineQueue = OfflineLocationQueue.getInstance(this)
        notifManager = AppNotificationManager.getInstance(this)
        jobsRepo = JobsRepository(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fallbackLocationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        Log.i(TAG, "LocationTrackingService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_TRACKING

        when (action) {
            ACTION_STOP_TRACKING -> {
                Log.i(TAG, "Received STOP tracking command.")
                stopTrackingInternal()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_FLUSH_QUEUE -> {
                serviceScope.launch { flushOfflineQueue() }
                return START_STICKY
            }
            ACTION_START_TRACKING -> {
                currentJobId = intent?.getStringExtra(EXTRA_JOB_ID) ?: currentJobId
                currentSessionId = intent?.getStringExtra(EXTRA_TRACKING_SESSION_ID) ?: currentSessionId ?: UUID.randomUUID().toString()

                val notification = notifManager.buildGpsForegroundNotification(
                    jobId = currentJobId,
                    queuedCount = offlineQueue.size()
                )
                startForeground(AppNotificationManager.NOTIFICATION_ID_GPS_TRACKING, notification)
                isRunning = true

                startLocationUpdates()
                serviceScope.launch { flushOfflineQueue() }
            }
        }

        return START_STICKY
    }

    private fun startLocationUpdates() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Log.e(TAG, "Location permissions not granted for foreground tracking.")
            return
        }

        try {
            // 1. Try Google Play Services Fused Location Provider
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 6000L)
                .setMinUpdateIntervalMillis(4000L)
                .setMinUpdateDistanceMeters(8.0f)
                .setWaitForAccurateLocation(false)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    for (loc in locationResult.locations) {
                        handleIncomingLocation(loc)
                    }
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )?.addOnFailureListener { e ->
                Log.w(TAG, "FusedLocationProvider failed (${e.message}), falling back to Android LocationManager.")
                startFallbackLocationUpdates()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception initializing FusedLocationProvider (${e.message}), using fallback.")
            startFallbackLocationUpdates()
        }
    }

    private val fallbackListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleIncomingLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun startFallbackLocationUpdates() {
        try {
            val lm = fallbackLocationManager ?: return
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    6000L,
                    8.0f,
                    fallbackListener,
                    Looper.getMainLooper()
                )
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    6000L,
                    8.0f,
                    fallbackListener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on fallback LocationManager: ${e.message}")
        }
    }

    private fun handleIncomingLocation(location: Location) {
        lastTrackedLocation = location

        val filterResult = locationFilter.filter(location)
        when (filterResult) {
            is LocationFilter.FilterResult.Rejected -> {
                Log.v(TAG, "Location rejected: ${filterResult.reason}")
            }
            is LocationFilter.FilterResult.Accepted -> {
                Log.d(TAG, "Location accepted: (${location.latitude}, ${location.longitude}) — ${filterResult.reason}")
                dispatchLocation(filterResult.location)
            }
        }
    }

    private fun dispatchLocation(location: Location) {
        val timestampStr = formatUtcIso(location.time)
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6 else null

        serviceScope.launch {
            val updateResult = jobsRepo.updateGps(
                latitude = location.latitude,
                longitude = location.longitude,
                speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                heading = if (location.hasBearing()) location.bearing.toDouble() else null,
                accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                timestamp = timestampStr,
                jobId = currentJobId,
                trackingSessionId = currentSessionId
            )

            if (updateResult.isSuccess) {
                // Realtime upload succeeded -> Try to flush any offline buffer
                if (!offlineQueue.isEmpty()) {
                    flushOfflineQueue()
                } else {
                    updateNotificationStatus(speedKmh, queuedCount = 0)
                }
            } else {
                // Upload failed (offline/timeout) -> Save into FIFO offline queue
                val offlineItem = BatchLocationItem(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                    heading = if (location.hasBearing()) location.bearing.toDouble() else null,
                    accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                    timestamp = timestampStr
                )
                offlineQueue.enqueue(offlineItem)
                updateNotificationStatus(speedKmh, queuedCount = offlineQueue.size())
            }
        }
    }

    private suspend fun flushOfflineQueue() {
        if (offlineQueue.isEmpty()) return

        val batch = offlineQueue.peekBatch(25)
        if (batch.isEmpty()) return

        Log.i(TAG, "Attempting to flush offline batch of ${batch.size} points...")

        val result = jobsRepo.updateGpsBatch(
            locations = batch,
            trackingSessionId = currentSessionId,
            jobId = currentJobId
        )

        if (result.isSuccess) {
            offlineQueue.removeBatch(batch.size)
            Log.i(TAG, "Successfully synced ${batch.size} offline points. Remaining: ${offlineQueue.size()}")

            updateNotificationStatus(null, queuedCount = offlineQueue.size())

            // If more items remain, continue flushing
            if (!offlineQueue.isEmpty()) {
                flushOfflineQueue()
            }
        } else {
            Log.w(TAG, "Offline batch sync failed: ${result.exceptionOrNull()?.message}")
            updateNotificationStatus(null, queuedCount = offlineQueue.size())
        }
    }

    private fun updateNotificationStatus(speedKmh: Double?, queuedCount: Int) {
        val notification = notifManager.buildGpsForegroundNotification(
            jobId = currentJobId,
            queuedCount = queuedCount,
            speedKmH = speedKmh
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.notify(AppNotificationManager.NOTIFICATION_ID_GPS_TRACKING, notification)
    }

    private fun stopTrackingInternal() {
        isRunning = false
        try {
            locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
            fallbackLocationManager?.removeUpdates(fallbackListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates: ${e.message}")
        }
        locationFilter.reset()
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTrackingInternal()
        Log.i(TAG, "LocationTrackingService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
