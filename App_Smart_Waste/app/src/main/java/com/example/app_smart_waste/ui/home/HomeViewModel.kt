package com.example.app_smart_waste.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.model.DailyDriverStatsDto
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.model.UiState
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.IncidentRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeData(
    val activeJob: JobDto?,
    val stats: DailyDriverStatsDto,
    val currentBin: SmartBinDto?,
    val allBins: List<SmartBinDto> = emptyList(),
    val pendingJobsCount: Int = 0,
    val unresolvedIncidentsCount: Int = 0
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val jobsRepository = JobsRepository(application)
    private val binsRepository = BinsRepository(application)
    private val incidentRepository = IncidentRepository(application)
    private val prefs = application.getSharedPreferences("smart_waste_jobs_prefs", Context.MODE_PRIVATE)

    private val _homeState = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val homeState: StateFlow<UiState<HomeData>> = _homeState.asStateFlow()

    private val _isAvailable = MutableStateFlow(prefs.getBoolean("is_available_self_pick", true))
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    fun updateAvailability(available: Boolean) {
        _isAvailable.value = available
        prefs.edit().putBoolean("is_available_self_pick", available).apply()
        val tracker = com.example.app_smart_waste.core.location.GpsTracker.getInstance(getApplication())
        if (available) {
            prefs.edit().putString("shift_status", "ACTIVE").apply()
            tracker.startTracking()
            viewModelScope.launch {
                tracker.sendImmediateUpdate()
            }
        } else {
            prefs.edit().putString("shift_status", "IDLE").apply()
            tracker.stopTracking()
        }
    }

    fun loadHomeData() {
        _homeState.value = UiState.Loading
        viewModelScope.launch {
            val homeDeferred = async { jobsRepository.getMobileHome() }
            val binsDeferred = async { binsRepository.getBins() }
            val incidentsDeferred = async { incidentRepository.getMyIncidents() }

            val home = homeDeferred.await().getOrElse {
                _homeState.value = UiState.Error(it.message ?: "Không thể tải dữ liệu trang chủ.")
                return@launch
            }
            val bins = binsDeferred.await().getOrElse { emptyList() }
            val incidents = incidentsDeferred.await().getOrElse { emptyList() }

            val currentBin = findCurrentBin(home.job, bins)

            // 1. Pending/Assigned jobs count (Nhiệm vụ cần xác nhận)
            val pendingJobsCount = if (home.job != null && (home.job.status.equals("ASSIGNED", ignoreCase = true) || home.job.status.equals("PENDING", ignoreCase = true))) {
                1
            } else 0

            // 2. Unresolved incidents count (Báo cáo sự cố đang xử lý)
            val unresolvedIncidentsCount = incidents.count { incident ->
                !incident.status.equals("RESOLVED", ignoreCase = true) && !incident.status.equals("DONE", ignoreCase = true)
            }

            _homeState.value = UiState.Success(
                HomeData(
                    activeJob = home.job,
                    stats = home.stats,
                    currentBin = currentBin,
                    allBins = bins,
                    pendingJobsCount = pendingJobsCount,
                    unresolvedIncidentsCount = unresolvedIncidentsCount
                )
            )
        }
    }

    private fun findCurrentBin(job: JobDto?, bins: List<SmartBinDto>): SmartBinDto? {
        val pendingId = job?.targetBinIds?.firstOrNull { id ->
            !job.completedBinIds.orEmpty().contains(id)
        } ?: return null
        return bins.firstOrNull { it.deviceId == pendingId }
    }
}
