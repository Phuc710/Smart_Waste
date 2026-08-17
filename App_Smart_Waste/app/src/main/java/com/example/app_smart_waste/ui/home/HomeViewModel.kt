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
    val allBins: List<SmartBinDto> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val jobsRepository = JobsRepository(application)
    private val binsRepository = BinsRepository(application)
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

            val home = homeDeferred.await().getOrElse {
                _homeState.value = UiState.Error(it.message ?: "Không thể tải dữ liệu trang chủ.")
                return@launch
            }
            val bins = binsDeferred.await().getOrElse { emptyList() }
            val currentBin = findCurrentBin(home.job, bins)

            _homeState.value = UiState.Success(HomeData(home.job, home.stats, currentBin, bins))
        }
    }

    private fun findCurrentBin(job: JobDto?, bins: List<SmartBinDto>): SmartBinDto? {
        val pendingId = job?.targetBinIds?.firstOrNull { id ->
            !job.completedBinIds.orEmpty().contains(id)
        } ?: return null
        return bins.firstOrNull { it.deviceId == pendingId }
    }
}
