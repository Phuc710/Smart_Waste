package com.example.app_smart_waste.ui.profile

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.model.IncidentReportDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.storage.SecureTokenStorage
import com.example.app_smart_waste.data.repository.AuthRepository
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.IncidentRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class UserProfileData(
    val id: String = "--",
    val username: String = "",
    val fullName: String = "Người dùng",
    val role: String = "Nhân viên thu gom",
    val department: String = "--",
    val license: String = "--",
    val vehiclePlate: String = "--",
    val isActive: Boolean = true
)

data class ProfileWorkStats(
    val completedTasks: Int = 0,
    val wasteTons: Double = 0.0,
    val distanceKm: Double = 0.0,
    val workHours: Double = 0.0,
    val vehiclePlate: String = "8 tấn • 8 m³",
    val vehicleType: String = "Xe ép rác",
    val shiftName: String = "Sáng",
    val shiftTime: String = "06:00 - 14:00"
)

data class GpsAndSoundConfig(
    val gpsIntervalSeconds: Int = 10,
    val jobSoundEnabled: Boolean = true,
    val binAlertEnabled: Boolean = true
)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val authRepo = AuthRepository(app)
    private val jobsRepo = JobsRepository(app)
    private val binsRepo = BinsRepository(app)
    private val incidentRepo = IncidentRepository(app)
    private val storage = SecureTokenStorage.getInstance(app)
    private val prefs = app.getSharedPreferences("smart_waste_settings", Context.MODE_PRIVATE)

    private val _statsState = MutableStateFlow(ProfileWorkStats())
    val statsState: StateFlow<ProfileWorkStats> = _statsState

    private val _userState = MutableStateFlow(UserProfileData())
    val userState: StateFlow<UserProfileData> = _userState

    private val _incidentsState = MutableStateFlow<List<IncidentReportDto>>(emptyList())
    val incidentsState: StateFlow<List<IncidentReportDto>> = _incidentsState

    private val _binsState = MutableStateFlow<List<SmartBinDto>>(emptyList())
    val binsState: StateFlow<List<SmartBinDto>> = _binsState

    private val _gpsConfigState = MutableStateFlow(loadGpsConfig())
    val gpsConfigState: StateFlow<GpsAndSoundConfig> = _gpsConfigState

    init {
        loadUserProfile()
        loadIncidents()
        loadBins()
    }

    private fun loadGpsConfig(): GpsAndSoundConfig {
        val interval = prefs.getInt("gps_interval_sec", 10)
        val jobSound = prefs.getBoolean("job_sound_enabled", true)
        val binAlert = prefs.getBoolean("bin_alert_enabled", true)
        return GpsAndSoundConfig(interval, jobSound, binAlert)
    }

    fun saveGpsConfig(intervalSec: Int, jobSound: Boolean, binAlert: Boolean) {
        prefs.edit()
            .putInt("gps_interval_sec", intervalSec)
            .putBoolean("job_sound_enabled", jobSound)
            .putBoolean("bin_alert_enabled", binAlert)
            .apply()
        _gpsConfigState.value = GpsAndSoundConfig(intervalSec, jobSound, binAlert)
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            authRepo.checkSession()
            val rawName = storage.getFullName()
            val rawUsername = storage.getUsername() ?: ""
            val rawRole = storage.getRole() ?: "staff"
            val rawId = storage.getUserId()

            val displayName = if (!rawName.isNullOrBlank() && rawName != "test" && rawName != "test12345") {
                rawName
            } else if (rawUsername.isNotBlank()) {
                rawUsername
            } else {
                "Người dùng"
            }

            val roleDisplay = if (rawRole == "admin") "Quản trị viên" else "Nhân viên thu gom"
            val staffCode = if (!rawId.isNullOrBlank() && rawId.length >= 4) "NV-${rawId.takeLast(4).uppercase()}" else if (!rawId.isNullOrBlank()) "NV-$rawId" else "--"

            _userState.value = UserProfileData(
                id = staffCode,
                username = rawUsername,
                fullName = displayName,
                role = roleDisplay,
                department = "Đội xe thu gom thông minh",
                license = "Hạng C (Xe tải chuyên dụng)",
                vehiclePlate = "--",
                isActive = storage.isActive()
            )
        }
    }

    fun loadIncidents() {
        viewModelScope.launch {
            val result = incidentRepo.getMyIncidents()
            _incidentsState.value = result.getOrDefault(emptyList())
        }
    }

    fun loadBins() {
        viewModelScope.launch {
            val result = binsRepo.getBins()
            _binsState.value = result.getOrDefault(emptyList())
        }
    }

    suspend fun submitIncident(binId: String, issueType: String, description: String, photoUrl: String? = null): Result<Boolean> {
        val result = incidentRepo.reportIncident(binId, issueType, description, photoUrl)
        if (result.isSuccess) {
            loadIncidents()
        }
        return result
    }

    fun loadProfileData(filterPeriod: String = "7 ngày qua") {
        viewModelScope.launch {
            val historyRes = jobsRepo.getHistory(100)
            val history = historyRes.getOrNull() ?: emptyList()
            val completedJobs = history.filter { it.status == "COMPLETED" || it.completedAt != null }

            val totalTasks = completedJobs.size
            var totalDistMeters = 0.0
            var totalDurSecs = 0.0

            completedJobs.forEach { job ->
                totalDistMeters += (job.routeData?.distanceMeters ?: 0.0)
                totalDurSecs += (job.routeData?.durationSeconds ?: 0.0)
            }

            val totalKm = ((totalDistMeters / 1000.0) * 10.0).roundToInt() / 10.0
            val totalHours = ((totalDurSecs / 3600.0) * 10.0).roundToInt() / 10.0
            val totalTons = ((totalTasks * 0.45) * 10.0).roundToInt() / 10.0

            _statsState.value = ProfileWorkStats(
                completedTasks = totalTasks,
                wasteTons = totalTons,
                distanceKm = totalKm,
                workHours = totalHours,
                vehiclePlate = "8 tấn • 8 m³",
                vehicleType = "Xe ép rác",
                shiftName = "Sáng",
                shiftTime = "06:00 - 14:00"
            )
        }
    }

    suspend fun changePassword(oldPass: String, newPass: String): Result<Boolean> {
        return authRepo.changePassword(oldPass, newPass)
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
