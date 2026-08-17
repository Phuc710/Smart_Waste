package com.example.app_smart_waste.ui.map

import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.SmartBinDto
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// =============================================================================
// 1. DATA MODELS & ENUMS
// =============================================================================

data class BinThresholds(
    val warning: Double,
    val critical: Double
) {
    init {
        require(warning in 0.0..100.0) { "Warning threshold ($warning) must be in range [0.0, 100.0]" }
        require(critical in 0.0..100.0) { "Critical threshold ($critical) must be in range [0.0, 100.0]" }
        require(warning < critical) { "Warning threshold ($warning) must be strictly less than critical threshold ($critical)" }
    }

    companion object {
        val FALLBACK = BinThresholds(
            warning = 70.0,
            critical = 85.0
        )

        fun createSafe(warning: Double?, critical: Double?): BinThresholds {
            return try {
                if (warning != null && critical != null) {
                    BinThresholds(warning, critical)
                } else {
                    FALLBACK
                }
            } catch (e: IllegalArgumentException) {
                FALLBACK
            }
        }
    }
}

enum class BinLevel {
    CRITICAL,
    WARNING,
    NORMAL;

    fun toCategoryString(): String = name
}

enum class ConnectivityFilter {
    ALL,
    ONLINE_ONLY,
    OFFLINE_ONLY
}

enum class MapFilterChipId {
    SEARCH_QUERY,
    CRITICAL_ONLY,
    WARNING_ONLY,
    NORMAL_ONLY,
    OFFLINE_ONLY,
    ONLINE_ONLY
}

data class ActiveFilterChip(
    val id: MapFilterChipId,
    val label: String
)

data class MapFilters(
    val showCritical: Boolean = true,
    val showWarning: Boolean = true,
    val showNormal: Boolean = true,
    val connectivity: ConnectivityFilter = ConnectivityFilter.ALL
) {
    val isDefault: Boolean
        get() = showCritical && showWarning && showNormal && connectivity == ConnectivityFilter.ALL

    val activeCount: Int
        get() = (if (showCritical) 1 else 0) +
            (if (showWarning) 1 else 0) +
            (if (showNormal) 1 else 0) +
            (if (connectivity != ConnectivityFilter.ALL) 1 else 0)
}

// =============================================================================
// 2. PRODUCTION MAP STATE POLICY (CENTRALIZED DOMAIN LOGIC)
// =============================================================================

internal object MapStatePolicy {

    fun classifyBin(
        levelPercent: Double,
        thresholds: BinThresholds
    ): BinLevel = when {
        levelPercent >= thresholds.critical -> BinLevel.CRITICAL
        levelPercent >= thresholds.warning -> BinLevel.WARNING
        else -> BinLevel.NORMAL
    }

    fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
    }

    fun filterBins(
        bins: List<SmartBinDto>,
        query: String,
        filters: MapFilters,
        radarState: RadarState = RadarState.Disabled,
        driverLocation: MapCoordinate? = null,
        thresholds: BinThresholds = BinThresholds.FALLBACK
    ): List<SmartBinDto> {
        var result = bins

        // 1. Text Search Filter (on appliedSearchQuery)
        if (query.isNotBlank()) {
            val normalized = query.trim().lowercase(java.util.Locale.ROOT)
            result = result.filter { bin ->
                bin.name?.lowercase(java.util.Locale.ROOT)?.contains(normalized) == true ||
                    bin.deviceId.lowercase(java.util.Locale.ROOT).contains(normalized) ||
                    bin.location?.lowercase(java.util.Locale.ROOT)?.contains(normalized) == true
            }
        }

        // 2. Radar Spatial Filter
        if (radarState is RadarState.Active) {
            val driver = driverLocation
            result = if (driver == null || !driver.isValid) {
                emptyList()
            } else {
                result.filter { bin ->
                    val lat = bin.latitude
                    val lng = bin.longitude
                    val level = bin.levelPercent ?: 0.0
                    lat != null && lng != null &&
                        isValidCoordinate(lat, lng) &&
                        level >= thresholds.warning &&
                        calculateHaversineDistance(driver.latitude, driver.longitude, lat, lng) <= radarState.radiusMeters
                }
            }
        }

        // 3. Independent Two-Dimensional Filter: (Connectivity Condition AND Fill-Level Condition)
        result = result.filter { bin ->
            // A. Connectivity Check
            val isOnline = bin.isOnline ?: true
            val matchesConnectivity = when (filters.connectivity) {
                ConnectivityFilter.ALL -> true
                ConnectivityFilter.ONLINE_ONLY -> isOnline
                ConnectivityFilter.OFFLINE_ONLY -> !isOnline
            }

            if (!matchesConnectivity) return@filter false

            // B. Fill-Level Check
            val level = bin.levelPercent ?: 0.0
            val levelCategory = classifyBin(level, thresholds)
            val matchesFillLevel = when (levelCategory) {
                BinLevel.CRITICAL -> filters.showCritical
                BinLevel.WARNING -> filters.showWarning
                BinLevel.NORMAL -> filters.showNormal
            }

            matchesFillLevel
        }

        return result
    }

    fun resolveOperationalMode(
        state: MapUiState,
        displayedBins: List<SmartBinDto>
    ): MapMode {
        return when {
            state.navigationState is NavigationState.Active -> MapMode.NAVIGATION
            state.radarState is RadarState.Active -> MapMode.RADAR
            state.selectedBin != null -> MapMode.BIN_SELECTED
            state.activeJob != null && state.activeJob.status in listOf("ASSIGNED", "ACCEPTED", "IN_PROGRESS", "PAUSED") -> MapMode.ACTIVE_JOB
            state.loadingState == MapLoadingState.Idle &&
                displayedBins.isEmpty() &&
                (state.appliedSearchQuery.isNotBlank() || !state.filters.isDefault) -> MapMode.EMPTY_RESULT
            state.networkState is NetworkState.NoInternet && state.allBins.isEmpty() -> MapMode.OFFLINE
            state.gpsState is GpsState.Disabled || state.gpsState is GpsState.PermissionDenied || state.gpsState is GpsState.PermanentlyDenied -> MapMode.GPS_UNAVAILABLE
            else -> MapMode.IDLE
        }
    }

    fun deriveActiveChips(
        state: MapUiState,
        thresholds: BinThresholds
    ): List<ActiveFilterChip> {
        val chips = mutableListOf<ActiveFilterChip>()

        // 1. Search Query Chip
        if (state.appliedSearchQuery.isNotBlank()) {
            chips.add(ActiveFilterChip(MapFilterChipId.SEARCH_QUERY, "Tìm kiếm: \"${state.appliedSearchQuery}\""))
        }

        // 2. Connectivity Chips
        when (state.filters.connectivity) {
            ConnectivityFilter.ONLINE_ONLY -> {
                chips.add(ActiveFilterChip(MapFilterChipId.ONLINE_ONLY, "Trạng thái: Online"))
            }
            ConnectivityFilter.OFFLINE_ONLY -> {
                chips.add(ActiveFilterChip(MapFilterChipId.OFFLINE_ONLY, "Trạng thái: Offline"))
            }
            ConnectivityFilter.ALL -> Unit
        }

        // 3. Fill-Level Chips (Accurate mathematical boundary labels)
        val critText = thresholds.critical.roundToInt()
        val warnText = thresholds.warning.roundToInt()

        if (state.filters.showCritical && !state.filters.showWarning && !state.filters.showNormal) {
            chips.add(ActiveFilterChip(MapFilterChipId.CRITICAL_ONLY, "Mức đầy: ≥ $critText%"))
        } else if (state.filters.showWarning && !state.filters.showCritical && !state.filters.showNormal) {
            chips.add(ActiveFilterChip(MapFilterChipId.WARNING_ONLY, "Mức đầy: $warnText%–<$critText%"))
        } else if (state.filters.showNormal && !state.filters.showCritical && !state.filters.showWarning) {
            chips.add(ActiveFilterChip(MapFilterChipId.NORMAL_ONLY, "Mức đầy: < $warnText%"))
        }

        return chips
    }

    fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val a = sin(deltaPhi / 2.0).pow(2.0) + cos(phi1) * cos(phi2) * sin(deltaLambda / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    fun parseJobRoute(job: JobDto?, allBins: List<SmartBinDto>): com.example.app_smart_waste.core.model.JobRouteUiModel {
        if (job == null) {
            return com.example.app_smart_waste.core.model.JobRouteUiModel(
                coordinates = emptyList(),
                stops = emptyList(),
                distanceMeters = null,
                durationSeconds = null
            )
        }

        val binMap = allBins.associateBy { it.deviceId }

        // 1. Resolve stops from items or target_bin_ids sorted by sortOrder
        val rawStops = if (!job.items.isNullOrEmpty()) {
            job.items.sortedBy { it.sortOrder ?: Int.MAX_VALUE }
        } else {
            job.targetBinIds.orEmpty().mapIndexed { index, binId ->
                val isCollected = job.completedBinIds?.contains(binId) == true
                com.example.app_smart_waste.core.model.JobItemDto(
                    binId = binId,
                    sortOrder = index + 1,
                    status = if (isCollected) "COLLECTED" else "PENDING"
                )
            }
        }

        var foundNext = false
        val stops = rawStops.mapIndexed { idx, item ->
            val bin = binMap[item.binId]
            val lat = bin?.latitude
            val lng = bin?.longitude
            val coord = if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                com.example.app_smart_waste.core.model.GeoCoordinate(lat, lng)
            } else null
            val status = com.example.app_smart_waste.core.model.JobStopStatus.fromString(item.status)
            val isNext = if (!foundNext && status != com.example.app_smart_waste.core.model.JobStopStatus.COLLECTED) {
                foundNext = true
                true
            } else false

            com.example.app_smart_waste.core.model.JobStopUiModel(
                binId = item.binId,
                order = item.sortOrder ?: (idx + 1),
                coordinate = coord,
                status = status,
                isNext = isNext,
                bin = bin
            )
        }

        // 2. Parse coordinates from route_data: GeoJSON [lng, lat] -> GeoCoordinate(lat, lng)
        val rawCoords = job.routeData?.coordinates
        val coordinates = rawCoords?.mapNotNull { pt ->
            if (pt.size >= 2 && isValidCoordinate(pt[1], pt[0])) {
                com.example.app_smart_waste.core.model.GeoCoordinate(latitude = pt[1], longitude = pt[0])
            } else null
        } ?: emptyList()

        return com.example.app_smart_waste.core.model.JobRouteUiModel(
            coordinates = coordinates,
            stops = stops,
            distanceMeters = job.routeData?.distanceMeters?.toInt(),
            durationSeconds = job.routeData?.durationSeconds?.toInt()
        )
    }

    fun filterEligibleRadarBins(
        allBins: List<SmartBinDto>,
        driverLocation: MapCoordinate?,
        radiusMeters: Double = 500.0,
        thresholds: BinThresholds = BinThresholds.FALLBACK
    ): List<SmartBinDto> {
        if (driverLocation == null || !driverLocation.isValid) return emptyList()

        return allBins.filter { bin ->
            val lat = bin.latitude
            val lng = bin.longitude
            val level = bin.levelPercent ?: 0.0
            val isOnline = bin.isOnline ?: true
            val isCollected = bin.collectionStatus == "COLLECTED"

            lat != null && lng != null &&
                isValidCoordinate(lat, lng) &&
                isOnline &&
                !isCollected &&
                level >= thresholds.warning &&
                calculateHaversineDistance(driverLocation.latitude, driverLocation.longitude, lat, lng) <= radiusMeters
        }
    }
}
