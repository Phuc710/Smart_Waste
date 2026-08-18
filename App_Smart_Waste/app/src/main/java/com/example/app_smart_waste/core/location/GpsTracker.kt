package com.example.app_smart_waste.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.app_smart_waste.data.repository.JobsRepository
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*

/**
 * Senior Enterprise GPS Tracker Facade
 * Coordinates between UI layer, background Heartbeat, and LocationTrackingService (Foreground Service).
 */
class GpsTracker private constructor(private val appContext: Context) {

    private val jobsRepo = JobsRepository(appContext)
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val fusedLocationClient = try {
        LocationServices.getFusedLocationProviderClient(appContext)
    } catch (_: Exception) { null }

    private var trackerScope: CoroutineScope? = null
    private var isHeartbeatRunning = false
    private var lastLocation: Location? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /**
     * Get current best location (from Foreground Service, Fused Location, or Fallback)
     */
    fun getCurrentLocation(): Location {
        LocationTrackingService.lastTrackedLocation?.let { return it }

        lastLocation?.let { return it }

        // Query last known from providers
        try {
            locationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                        lastLocation = it
                        return it
                    }
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
                        lastLocation = it
                        return it
                    }
                }
            }
        } catch (_: SecurityException) {}

        return Location("default").apply {
            latitude = com.example.app_smart_waste.core.storage.AppConfig.DEFAULT_MAP_LAT
            longitude = com.example.app_smart_waste.core.storage.AppConfig.DEFAULT_MAP_LNG
        }
    }

    fun getLastKnownLocation(): Location? = LocationTrackingService.lastTrackedLocation ?: lastLocation

    /**
     * Start Full-Scale High-Accuracy Route Tracking (Foreground Service)
     * To be called when Driver accepts or starts an active job (IN_PROGRESS).
     */
    fun startRouteTracking(jobId: String? = null, trackingSessionId: String? = null) {
        stopHeartbeat()
        LocationTrackingService.startService(appContext, jobId, trackingSessionId)
        Log.i(TAG, "Started Foreground Route Tracking for Job #$jobId")
    }

    /**
     * Stop High-Accuracy Route Tracking
     */
    fun stopRouteTracking() {
        LocationTrackingService.stopService(appContext)
        Log.i(TAG, "Stopped Foreground Route Tracking.")
    }

    /**
     * Start Low-Frequency In-App Heartbeat when driver is idle/browsing app
     */
    fun startTracking() {
        if (LocationTrackingService.isRunning) {
            Log.d(TAG, "Foreground tracking is already running; skipping heartbeat.")
            return
        }
        if (isHeartbeatRunning) return
        isHeartbeatRunning = true

        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            try {
                fusedLocationClient?.lastLocation?.addOnSuccessListener { loc ->
                    if (loc != null) lastLocation = loc
                }

                locationManager?.let { lm ->
                    val minDistance = com.example.app_smart_waste.core.storage.AppConfig.DEFAULT_GPS_MIN_DISTANCE_METERS
                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, minDistance, locationListener)
                    }
                    if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, minDistance, locationListener)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException on heartbeat updates: ${e.message}")
            }
        }

        trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        trackerScope?.launch {
            while (isActive && isHeartbeatRunning && !LocationTrackingService.isRunning) {
                sendLocationUpdate()
                val interval = com.example.app_smart_waste.core.storage.AppConfig.getGpsIntervalMs(appContext)
                delay(interval)
            }
        }
    }

    suspend fun sendImmediateUpdate() {
        sendLocationUpdate()
    }

    private fun stopHeartbeat() {
        isHeartbeatRunning = false
        trackerScope?.cancel()
        trackerScope = null
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}
    }

    fun stopTracking() {
        stopHeartbeat()
        stopRouteTracking()
    }

    private suspend fun sendLocationUpdate() {
        val loc = getCurrentLocation()
        val result = jobsRepo.updateGps(
            latitude = loc.latitude,
            longitude = loc.longitude,
            speed = if (loc.hasSpeed()) loc.speed.toDouble() else null,
            heading = if (loc.hasBearing()) loc.bearing.toDouble() else null,
            accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null
        )
        if (result.isSuccess) {
            Log.d(TAG, "Heartbeat GPS updated: (${loc.latitude}, ${loc.longitude})")
        } else {
            Log.w(TAG, "Heartbeat GPS failed: ${result.exceptionOrNull()?.message}")
        }
    }

    companion object {
        private const val TAG = "GpsTracker"

        @Volatile
        private var INSTANCE: GpsTracker? = null

        fun getInstance(context: Context): GpsTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GpsTracker(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
