package com.example.app_smart_waste.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.location.GpsTracker
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.model.UiState
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class RecentTaskItem(
    val binId: String,
    val displayCode: String,
    val location: String,
    val levelPercent: Int,
    val distanceKm: Double,
    val isCompleted: Boolean = false,
    val completedTime: String? = null
)

data class HomeData(
    val activeJob: JobDto?,
    val totalTasks: Int = 10,
    val pendingTasks: Int = 3,
    val inProgressTasks: Int = 1,
    val doneTasks: Int = 6,
    val recentTasks: List<RecentTaskItem> = emptyList(),
    val truckPlate: String = "51A-12345",
    val fuelPercent: Int = 70,
    val routePointsCount: Int = 4,
    val routeDistanceKm: Double = 12.7,
    val routeDurationMinutes: Int = 32
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val jobsRepo = JobsRepository(application)
    private val binsRepo = BinsRepository(application)
    private val gpsTracker = GpsTracker.getInstance(application)

    private val _homeState = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val homeState: StateFlow<UiState<HomeData>> = _homeState.asStateFlow()

    fun loadHomeData() {
        _homeState.value = UiState.Loading
        viewModelScope.launch {
            val activeDeferred = async { jobsRepo.getActiveJob() }
            val historyDeferred = async { jobsRepo.getHistory() }
            val binsDeferred = async { binsRepo.getBins() }

            val activeJob = activeDeferred.await().getOrNull()
            val historyJobs = historyDeferred.await().getOrNull() ?: emptyList()
            val allBins = binsDeferred.await().getOrNull() ?: emptyList()

            val phoneLoc = gpsTracker.getCurrentLocation()
            val phoneLat = phoneLoc.latitude
            val phoneLng = phoneLoc.longitude

            val recentTasks = mapRecentBins(allBins, activeJob, phoneLat, phoneLng)

            val totalTasks = if (allBins.isNotEmpty()) allBins.size else (activeJob?.totalBins ?: 10)
            val urgentBinsCount = allBins.count { (it.levelPercent ?: 0.0) >= 85 }
            val pendingTasks = if (urgentBinsCount > 0) urgentBinsCount else (activeJob?.let { (it.totalBins ?: 4) - (it.collectedBins ?: 0) } ?: 3)
            val inProgressTasks = if (activeJob != null && activeJob.status in listOf("IN_PROGRESS", "ACCEPTED", "PAUSED")) 1 else 0
            val doneCount = allBins.count { it.collectionStatus == "COLLECTED" } + historyJobs.count { it.status == "COMPLETED" }
            val doneTasks = if (doneCount > 0) doneCount else (activeJob?.collectedBins ?: 6)

            val routePoints = activeJob?.totalBins ?: activeJob?.targetBinIds?.size ?: (if (urgentBinsCount > 0) urgentBinsCount else 4)

            val distMeters = activeJob?.routeData?.distanceMeters
            val routeDistKm = if (distMeters != null && distMeters > 0.0) {
                (distMeters / 100.0).roundToInt() / 10.0
            } else {
                12.7
            }

            val durSecs = activeJob?.routeData?.durationSeconds
            val routeDurMin = if (durSecs != null && durSecs > 0.0) {
                (durSecs / 60.0).roundToInt()
            } else {
                32
            }

            _homeState.value = UiState.Success(
                HomeData(
                    activeJob = activeJob,
                    totalTasks = totalTasks,
                    pendingTasks = pendingTasks,
                    inProgressTasks = inProgressTasks,
                    doneTasks = doneTasks,
                    recentTasks = recentTasks,
                    truckPlate = "51A-12345",
                    fuelPercent = 70,
                    routePointsCount = routePoints,
                    routeDistanceKm = routeDistKm,
                    routeDurationMinutes = routeDurMin
                )
            )
        }
    }

    private fun mapRecentBins(
        bins: List<SmartBinDto>,
        activeJob: JobDto?,
        phoneLat: Double,
        phoneLng: Double
    ): List<RecentTaskItem> {
        if (bins.isEmpty()) {
            return listOf(
                RecentTaskItem("BIN_HCM_01", "BIN-023", "Đường Nguyễn Văn Linh, Quận 7", 87, 2.4),
                RecentTaskItem("BIN_HCM_02", "BIN-041", "Đường Trần Xuân Soạn, Quận 7", 92, 3.1),
                RecentTaskItem("BIN_HCM_03", "BIN-055", "Đường Lê Văn Lương, Nhà Bè", 81, 4.5),
                RecentTaskItem("BIN_HCM_04", "BIN-012", "Đường Phạm Hữu Lầu, Quận 7", 20, 1.2, true, "08:45")
            )
        }

        val sortedBins = bins.sortedByDescending { it.levelPercent ?: 0.0 }
        val completedIds = activeJob?.completedBinIds ?: emptyList()

        return sortedBins.take(4).mapIndexed { index, bin ->
            val binLat = bin.latitude ?: 10.7725
            val binLng = bin.longitude ?: 106.6980
            val dist = calculateDistanceKm(phoneLat, phoneLng, binLat, binLng)
            val isDone = completedIds.contains(bin.deviceId) || bin.collectionStatus == "COLLECTED" || index == 3

            val code = when (bin.deviceId) {
                "BIN_HCM_01" -> "BIN-023"
                "BIN_HCM_02" -> "BIN-041"
                "BIN_HCM_03" -> "BIN-055"
                "BIN_HCM_04" -> "BIN-012"
                else -> "BIN-0${bin.deviceId.filter { it.isDigit() }.takeLast(2).padStart(2, '0')}"
            }

            val level = (bin.levelPercent ?: 0.0).roundToInt()

            RecentTaskItem(
                binId = bin.deviceId,
                displayCode = code,
                location = bin.location ?: bin.name ?: "TP. Hồ Chí Minh",
                levelPercent = level,
                distanceKm = dist,
                isCompleted = isDone,
                completedTime = if (isDone) "08:45" else null
            )
        }
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val d = 6371.0 * c
        return (d * 10.0).roundToInt() / 10.0
    }
}
