package com.example.app_smart_waste.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.IncidentRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val binsRepo = BinsRepository(application)
    private val jobsRepo = JobsRepository(application)
    private val incidentRepo = IncidentRepository(application)

    // =========================================================================
    // DATA STATE
    // =========================================================================

    private val _allBins = MutableStateFlow<List<SmartBinDto>>(emptyList())
    val allBins: StateFlow<List<SmartBinDto>> = _allBins.asStateFlow()

    private val _activeJob = MutableStateFlow<JobDto?>(null)
    val activeJob: StateFlow<JobDto?> = _activeJob.asStateFlow()

    private val _selectedBin = MutableStateFlow<SmartBinDto?>(null)
    val selectedBin: StateFlow<SmartBinDto?> = _selectedBin.asStateFlow()

    // =========================================================================
    // SEARCH / FILTER
    // =========================================================================

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterLevels = MutableStateFlow(
        setOf("CRITICAL", "WARNING", "NORMAL")
    )
    val filterLevels: StateFlow<Set<String>> = _filterLevels.asStateFlow()

    // =========================================================================
    // DRIVER / RADAR
    // =========================================================================

    private val _driverLocation =
        MutableStateFlow<Pair<Double, Double>?>(null)

    private val _isRadarMode = MutableStateFlow(false)
    val isRadarMode: StateFlow<Boolean> = _isRadarMode.asStateFlow()

    private val _radarRadiusMeters = MutableStateFlow(1000.0)
    val radarRadiusMeters: StateFlow<Double> =
        _radarRadiusMeters.asStateFlow()

    // =========================================================================
    // NAVIGATION
    // =========================================================================

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _navTargetBin = MutableStateFlow<SmartBinDto?>(null)
    val navTargetBin: StateFlow<SmartBinDto?> = _navTargetBin.asStateFlow()

    // Không dùng số demo khi chưa có route thật.
    private val _navDistanceText = MutableStateFlow("--")
    val navDistanceText: StateFlow<String> = _navDistanceText.asStateFlow()

    private val _navEtaText = MutableStateFlow("--")
    val navEtaText: StateFlow<String> = _navEtaText.asStateFlow()

    // =========================================================================
    // MAP / NETWORK
    // =========================================================================

    private val _currentMapLayer = MutableStateFlow("default")
    val currentMapLayer: StateFlow<String> =
        _currentMapLayer.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    val displayedBins: StateFlow<List<SmartBinDto>> = combine(
        _allBins,
        _searchQuery,
        _filterLevels,
        _isRadarMode,
        _driverLocation
    ) { bins, query, levels, isRadar, driverLocation ->

        var result = bins

        if (query.isNotBlank()) {
            val normalized = query.trim().lowercase()

            result = result.filter { bin ->
                bin.name
                    ?.lowercase()
                    ?.contains(normalized) == true ||
                    bin.deviceId
                        .lowercase()
                        .contains(normalized) ||
                    bin.location
                        ?.lowercase()
                        ?.contains(normalized) == true
            }
        }

        if (isRadar) {
            val driver = driverLocation

            result = if (driver == null) {
                emptyList()
            } else {
                result.filter { bin ->
                    val level = bin.levelPercent
                    val lat = bin.latitude
                    val lng = bin.longitude

                    level != null &&
                        level >= 85.0 &&
                        lat != null &&
                        lng != null &&
                        calculateHaversineDistance(
                            driver.first,
                            driver.second,
                            lat,
                            lng
                        ) <= _radarRadiusMeters.value
                }
            }
        }

        result = result.filter { bin ->
            if (bin.isOnline == false) {
                levels.contains("OFFLINE")
            } else {
                val level = bin.levelPercent

                when {
                    level == null -> true
                    level >= 85.0 -> levels.contains("CRITICAL")
                    level >= 70.0 -> levels.contains("WARNING")
                    else -> levels.contains("NORMAL")
                }
            }
        }

        result
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    // =========================================================================
    // ROUTE
    // =========================================================================

    private val _routeCoordinates =
        MutableStateFlow<List<List<Double>>>(emptyList())
    val routeCoordinates: StateFlow<List<List<Double>>> =
        _routeCoordinates.asStateFlow()

    private val _routeWaypoints =
        MutableStateFlow<List<SmartBinDto>>(emptyList())
    val routeWaypoints: StateFlow<List<SmartBinDto>> =
        _routeWaypoints.asStateFlow()

    // =========================================================================
    // UI STATE
    // =========================================================================

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> =
        _toastMessage.asSharedFlow()

    // =========================================================================
    // LOAD DATA
    // =========================================================================

    fun loadMapData(targetJobId: String? = null) {
        if (_isLoading.value) return

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val binsDeferred = async { binsRepo.getBins() }
                val activeJobDeferred = async { jobsRepo.getActiveJob() }

                val binsResult = binsDeferred.await()
                val activeJobResult = activeJobDeferred.await()

                if (binsResult.isSuccess) {
                    _allBins.value =
                        binsResult.getOrDefault(emptyList())
                    _isOffline.value = false
                } else {
                    _isOffline.value = true

                    if (_allBins.value.isEmpty()) {
                        _toastMessage.emit(
                            "Không thể tải dữ liệu thùng rác."
                        )
                    }
                }

                _activeJob.value =
                    if (!targetJobId.isNullOrBlank()) {
                        val detailResult =
                            jobsRepo.getJobDetail(targetJobId)

                        if (detailResult.isFailure) {
                            _toastMessage.emit(
                                "Không thể tải nhiệm vụ $targetJobId."
                            )
                        }

                        // Không thay một job được yêu cầu bằng job khác.
                        detailResult.getOrNull()
                    } else {
                        activeJobResult.getOrNull()
                    }

            } catch (e: Exception) {
                _isOffline.value = true
                _toastMessage.emit(
                    "Không thể cập nhật dữ liệu bản đồ."
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    // =========================================================================
    // SEARCH / FILTER
    // =========================================================================

    fun setSearchQuery(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            _selectedBin.value = null
            return
        }

        val matched = _allBins.value.find { bin ->
            bin.deviceId.contains(
                query,
                ignoreCase = true
            ) ||
                bin.name?.contains(
                    query,
                    ignoreCase = true
                ) == true ||
                bin.location?.contains(
                    query,
                    ignoreCase = true
                ) == true
        }

        _selectedBin.value = matched
    }

    fun applyFilterSettings(levels: Set<String>) {
        _filterLevels.value = levels
    }

    fun resetFilters() {
        _filterLevels.value =
            setOf("CRITICAL", "WARNING", "NORMAL")
        _searchQuery.value = ""
        _selectedBin.value = null
        _isRadarMode.value = false
    }

    // =========================================================================
    // DRIVER / RADAR
    // =========================================================================

    fun updateDriverLocation(
        latitude: Double,
        longitude: Double
    ) {
        _driverLocation.value = latitude to longitude
    }

    fun toggleRadarMode() {
        _isRadarMode.value = !_isRadarMode.value
    }

    // =========================================================================
    // BIN SELECTION / MAP LAYER
    // =========================================================================

    fun selectBin(binId: String) {
        _selectedBin.value =
            _allBins.value.find { it.deviceId == binId }
    }

    fun clearSelectedBin() {
        _selectedBin.value = null
    }

    fun setMapLayer(layer: String) {
        _currentMapLayer.value = layer
    }

    // =========================================================================
    // NAVIGATION
    // =========================================================================

    fun startNavigationToBin(
        bin: SmartBinDto,
        driverLat: Double,
        driverLng: Double
    ) {
        val binLat = bin.latitude
        val binLng = bin.longitude

        if (binLat == null || binLng == null) {
            viewModelScope.launch {
                _toastMessage.emit(
                    "Thùng ${bin.deviceId} chưa có tọa độ hợp lệ."
                )
            }
            return
        }

        _isNavigating.value = true
        _navTargetBin.value = bin
        _navDistanceText.value = "--"
        _navEtaText.value = "--"

        calculateRouteToBin(
            driverLat,
            driverLng,
            binLat,
            binLng
        )
    }

    fun stopNavigation() {
        _isNavigating.value = false
        _navTargetBin.value = null
        _navDistanceText.value = "--"
        _navEtaText.value = "--"
        clearRoute()
    }

    fun calculateRouteToBin(
        driverLat: Double,
        driverLng: Double,
        binLat: Double,
        binLng: Double
    ) {
        viewModelScope.launch {
            val result = binsRepo.calculateRoute(
                listOf(
                    driverLat to driverLng,
                    binLat to binLng
                )
            )

            val route = result.getOrNull()

            if (route == null || route.coordinates.isNullOrEmpty()) {
                _routeCoordinates.value = emptyList()
                _navDistanceText.value = "--"
                _navEtaText.value = "--"
                _isNavigating.value = false
                _navTargetBin.value = null

                _toastMessage.emit(
                    "Không thể tải tuyến đường."
                )
                return@launch
            }

            val leafCoordinates = route.coordinates
                .mapNotNull { coordinate ->
                    if (coordinate.size >= 2) {
                        listOf(
                            coordinate[1],
                            coordinate[0]
                        )
                    } else {
                        null
                    }
                }

            if (leafCoordinates.size < 2) {
                _routeCoordinates.value = emptyList()
                _navDistanceText.value = "--"
                _navEtaText.value = "--"
                _isNavigating.value = false
                _navTargetBin.value = null

                _toastMessage.emit(
                    "Dữ liệu tuyến đường không hợp lệ."
                )
                return@launch
            }

            _routeCoordinates.value = leafCoordinates

            val distanceMeters =
                route.distanceMeters
                    ?: calculatePolylineDistance(
                        leafCoordinates
                    )

            _navDistanceText.value =
                distanceMeters
                    ?.takeIf { it >= 0.0 }
                    ?.let { meters ->
                        String.format(
                            java.util.Locale.US,
                            "%.1f km",
                            meters / 1000.0
                        )
                    }
                    ?: "--"

            _navEtaText.value =
                route.durationSeconds
                    ?.takeIf { it >= 0.0 }
                    ?.let { seconds ->
                        val minutes = max(
                            1,
                            (seconds / 60.0).roundToInt()
                        )
                        "$minutes phút • Dự kiến đến nơi"
                    }
                    ?: "--"
        }
    }

    fun calculateJobRoute(
        job: JobDto,
        driverLat: Double,
        driverLng: Double
    ) {
        viewModelScope.launch {
            val binsById =
                _allBins.value.associateBy { it.deviceId }

            val targetIds =
                job.targetBinIds.orEmpty()

            val points =
                mutableListOf(driverLat to driverLng)

            val waypoints =
                mutableListOf<SmartBinDto>()

            targetIds.forEach { binId ->
                val bin = binsById[binId]
                val lat = bin?.latitude
                val lng = bin?.longitude

                if (bin != null &&
                    lat != null &&
                    lng != null
                ) {
                    points.add(lat to lng)
                    waypoints.add(bin)
                }
            }

            _routeWaypoints.value = waypoints

            if (points.size < 2) {
                _routeCoordinates.value = emptyList()
                return@launch
            }

            val result =
                binsRepo.calculateRoute(points)

            val route = result.getOrNull()

            if (route == null ||
                route.coordinates.isNullOrEmpty()
            ) {
                // Không vẽ đường thẳng giả thay cho route thật.
                _routeCoordinates.value = emptyList()
                _toastMessage.emit(
                    "Không thể tải tuyến đường cho nhiệm vụ."
                )
                return@launch
            }

            _routeCoordinates.value =
                route.coordinates.mapNotNull { coordinate ->
                    if (coordinate.size >= 2) {
                        listOf(
                            coordinate[1],
                            coordinate[0]
                        )
                    } else {
                        null
                    }
                }
        }
    }

    fun clearRoute() {
        _routeCoordinates.value = emptyList()
        _routeWaypoints.value = emptyList()
    }

    // =========================================================================
    // ACTIONS
    // =========================================================================

    fun createSelfPickJob(
        binIds: List<String>,
        onComplete: (Boolean) -> Unit
    ) {
        if (binIds.isEmpty()) {
            viewModelScope.launch {
                _toastMessage.emit(
                    "Không có điểm hợp lệ để tạo ca làm."
                )
            }
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            var shouldReload = false

            try {
                val result =
                    jobsRepo.selfPickJob(binIds)

                if (result.isSuccess) {
                    _toastMessage.emit(
                        "✓ Đã tạo ca làm tự nhận (${binIds.size} điểm) thành công!"
                    )
                    _isRadarMode.value = false
                    shouldReload = true
                    onComplete(true)
                } else {
                    _toastMessage.emit(
                        "Không thể tạo ca làm (${result.exceptionOrNull()?.message ?: "không rõ nguyên nhân"})"
                    )
                    onComplete(false)
                }
            } finally {
                _isLoading.value = false
            }

            if (shouldReload) {
                loadMapData()
            }
        }
    }

    fun reportIncident(
        binId: String,
        issueType: String,
        description: String,
        onComplete: (Boolean) -> Unit
    ) {
        if (binId.isBlank()) {
            viewModelScope.launch {
                _toastMessage.emit(
                    "Không xác định được thùng cần báo sự cố."
                )
            }
            onComplete(false)
            return
        }

        viewModelScope.launch {
            val result =
                incidentRepo.reportIncident(
                    binId,
                    issueType,
                    description
                )

            if (result.isSuccess) {
                _toastMessage.emit(
                    "✓ Đã gửi báo cáo sự cố thành công!"
                )
                onComplete(true)
            } else {
                _toastMessage.emit(
                    "Lỗi gửi sự cố (${result.exceptionOrNull()?.message ?: "không rõ nguyên nhân"})"
                )
                onComplete(false)
            }
        }
    }

    fun remoteOpenLid(
        binId: String,
        onResult: (Boolean) -> Unit
    ) {
        if (binId.isBlank()) {
            viewModelScope.launch {
                _toastMessage.emit(
                    "Không xác định được thùng cần mở nắp."
                )
            }
            onResult(false)
            return
        }

        viewModelScope.launch {
            _toastMessage.emit(
                "📶 Đang gửi lệnh mở nắp thùng $binId..."
            )

            val result = binsRepo.openLid(binId)

            if (result.isSuccess) {
                _toastMessage.emit(
                    "✓ Đã gửi yêu cầu mở nắp cho thiết bị #$binId."
                )
                onResult(true)
            } else {
                _toastMessage.emit(
                    "Không thể mở nắp (${result.exceptionOrNull()?.message ?: "thiết bị không phản hồi"})"
                )
                onResult(false)
            }
        }
    }

    // =========================================================================
    // DISTANCE HELPERS
    // =========================================================================

    private fun calculatePolylineDistance(
        points: List<List<Double>>
    ): Double? {
        if (points.size < 2) return null

        var total = 0.0

        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]

            if (start.size < 2 || end.size < 2) {
                continue
            }

            total += calculateHaversineDistance(
                start[0],
                start[1],
                end[0],
                end[1]
            )
        }

        return total
    }

    private fun calculateHaversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusMeters = 6_371_000.0

        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a =
            sin(deltaPhi / 2.0).pow(2.0) +
                cos(phi1) *
                cos(phi2) *
                sin(deltaLambda / 2.0).pow(2.0)

        val c =
            2.0 * atan2(
                sqrt(a),
                sqrt(1.0 - a)
            )

        return earthRadiusMeters * c
    }
}
