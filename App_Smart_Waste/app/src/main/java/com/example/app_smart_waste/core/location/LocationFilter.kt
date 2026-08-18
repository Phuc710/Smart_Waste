package com.example.app_smart_waste.core.location

import android.location.Location
import android.os.SystemClock

/**
 * Senior Enterprise Location Filter
 * Filters out GPS noise, stationary jitter, stale coordinates, and abnormal speed outliers.
 */
class LocationFilter(
    private val maxAccuracyMeters: Float = 50.0f,
    private val minDisplacementMeters: Float = 8.0f,
    private val maxStationaryTimeMs: Long = 20_000L,
    private val maxRealisticSpeedMps: Float = 38.0f // ~136 km/h
) {

    sealed interface FilterResult {
        data class Accepted(val location: Location, val reason: String) : FilterResult
        data class Rejected(val reason: String) : FilterResult
    }

    private var lastAcceptedLocation: Location? = null
    private var lastAcceptedTimeMs: Long = 0L

    @Synchronized
    fun filter(location: Location): FilterResult {
        // 1. Check coordinates validity
        if (!isValidCoordinate(location.latitude, location.longitude)) {
            return FilterResult.Rejected("Tọa độ ngoài phạm vi hợp lệ (${location.latitude}, ${location.longitude})")
        }

        // 2. Check Accuracy (Discard low accuracy readings)
        if (location.hasAccuracy() && location.accuracy > maxAccuracyMeters) {
            return FilterResult.Rejected("Độ chính xác kém (${location.accuracy}m > ${maxAccuracyMeters}m)")
        }

        // 3. Check Freshness (Discard old cached points > 35s)
        val nowMs = System.currentTimeMillis()
        val locTimeMs = if (location.time > 0) location.time else nowMs
        val ageMs = Math.abs(nowMs - locTimeMs)
        if (ageMs > 35_000L) {
            return FilterResult.Rejected("Tọa độ cũ trong bộ nhớ đệm (${ageMs / 1000}s trước)")
        }

        // 4. Check Speed Outlier
        if (location.hasSpeed() && location.speed > maxRealisticSpeedMps) {
            return FilterResult.Rejected("Vận tốc không thực tế (${location.speed * 3.6} km/h)")
        }

        val prev = lastAcceptedLocation
        if (prev == null) {
            lastAcceptedLocation = location
            lastAcceptedTimeMs = System.currentTimeMillis()
            return FilterResult.Accepted(location, "Vị trí ban đầu")
        }

        // 5. Check Distance & Time Delta from last accepted location
        val distanceMeters = prev.distanceTo(location)
        val timeDeltaMs = System.currentTimeMillis() - lastAcceptedTimeMs

        // Check implied velocity between 2 consecutive readings
        if (timeDeltaMs > 500) {
            val impliedSpeedMps = distanceMeters / (timeDeltaMs / 1000.0f)
            if (impliedSpeedMps > (maxRealisticSpeedMps * 1.3f)) {
                return FilterResult.Rejected("Bước nhảy GPS bất thường (Khoảng cách ${distanceMeters}m trong ${timeDeltaMs}ms)")
            }
        }

        // 6. Stationary Jitter Filter (If displacement < minDisplacement and time < maxStationaryTime, ignore noise)
        if (distanceMeters < minDisplacementMeters && timeDeltaMs < maxStationaryTimeMs) {
            return FilterResult.Rejected("Xe đang đứng yên/dừng đèn đỏ (Dịch chuyển ${distanceMeters}m < ${minDisplacementMeters}m)")
        }

        lastAcceptedLocation = location
        lastAcceptedTimeMs = System.currentTimeMillis()
        return FilterResult.Accepted(location, "Dịch chuyển hợp lệ (${distanceMeters.toInt()}m)")
    }

    @Synchronized
    fun reset() {
        lastAcceptedLocation = null
        lastAcceptedTimeMs = 0L
    }

    companion object {
        fun isValidCoordinate(lat: Double, lng: Double): Boolean {
            return lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)
        }
    }
}
