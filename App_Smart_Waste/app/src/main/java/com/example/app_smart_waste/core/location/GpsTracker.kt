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
import kotlinx.coroutines.*

class GpsTracker private constructor(private val appContext: Context) {

    private val jobsRepo = JobsRepository(appContext)
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var trackerScope: CoroutineScope? = null
    private var isRunning = false
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

    fun getCurrentLocation(): Location {
        return lastLocation ?: Location("default").apply {
            latitude = com.example.app_smart_waste.core.storage.AppConfig.DEFAULT_MAP_LAT
            longitude = com.example.app_smart_waste.core.storage.AppConfig.DEFAULT_MAP_LNG
        }
    }

    fun startTracking() {
        if (isRunning) return
        isRunning = true

        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Log.w(TAG, "Location permission not granted, GPS tracking postponed.")
            return
        }

        try {
            locationManager?.let { lm ->
                val minDistance = com.example.app_smart_waste.core.storage.AppConfig.DEFAULT_GPS_MIN_DISTANCE_METERS
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        10000L,
                        minDistance,
                        locationListener
                    )
                    lastLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }

                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        10000L,
                        minDistance,
                        locationListener
                    )
                    if (lastLocation == null) {
                        lastLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on location updates: ${e.message}")
        }

        trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        trackerScope?.launch {
            while (isActive && isRunning) {
                sendLocationUpdate()
                val interval = com.example.app_smart_waste.core.storage.AppConfig.getGpsIntervalMs(appContext)
                delay(interval)
            }
        }
    }

    fun stopTracking() {
        isRunning = false
        trackerScope?.cancel()
        trackerScope = null

        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates: ${e.message}")
        }
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
            Log.d(TAG, "GPS updated: (${loc.latitude}, ${loc.longitude})")
        } else {
            Log.w(TAG, "Failed to update GPS: ${result.exceptionOrNull()?.message}")
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
