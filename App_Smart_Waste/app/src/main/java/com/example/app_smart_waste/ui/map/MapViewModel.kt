package com.example.app_smart_waste.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.model.BinCommandResult
import com.example.app_smart_waste.core.model.GeoCoordinate
import com.example.app_smart_waste.core.model.IncidentAttachmentState
import com.example.app_smart_waste.core.model.IncidentReason
import com.example.app_smart_waste.core.model.IncidentSubmissionState
import com.example.app_smart_waste.core.model.JobActionType
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.JobRouteUiModel
import com.example.app_smart_waste.core.model.JobStatus
import com.example.app_smart_waste.core.model.JobStopStatus
import com.example.app_smart_waste.core.model.JobStopUiModel
import com.example.app_smart_waste.core.model.JobTransitionPolicy
import com.example.app_smart_waste.core.model.LocationPayload
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.model.SystemSettingsDto
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.IncidentRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

// =============================================================================
// 1. DATA MODELS & ENUMS
// =============================================================================

enum class MapMode {
    IDLE,              // Case 01: Default map view, browsing bins
    BIN_SELECTED,      // Case 02: Bin focused with preview card
    ACTIVE_JOB,        // Case 03: Live collection job route & stops
    NAVIGATION,        // Case 04: Turn-by-turn navigation mode
    RADAR,             // Case 05: 500m radar self-pick mode
    EMPTY_RESULT,      // Case 06: Zero bins matching filter/search
    GPS_UNAVAILABLE,   // Case 07A: GPS permission denied or disabled (only when Idle)
    OFFLINE            // Case 07B: Network offline / backend disconnected (only when Idle)
}

enum class MapLayer {
    DEFAULT,
    SATELLITE,
    TERRAIN;

    fun toJsString(): String = when (this) {
        DEFAULT -> "default"
        SATELLITE -> "satellite"
        TERRAIN -> "terrain"
    }

    companion object {
        fun fromString(value: String): MapLayer = when (value.lowercase(java.util.Locale.ROOT)) {
            "satellite" -> SATELLITE
            "terrain" -> TERRAIN
            else -> DEFAULT
        }
    }
}

data class MapCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    val isValid: Boolean
        get() = MapStatePolicy.isValidCoordinate(latitude, longitude)
}

sealed interface GpsState {
    data object Available : GpsState
    data object Disabled : GpsState
    data object PermissionDenied : GpsState
    data object PermanentlyDenied : GpsState
    data object Unavailable : GpsState
}

sealed interface NetworkState {
    data object Online : NetworkState
    data object NoInternet : NetworkState
    data object BackendUnavailable : NetworkState
    data object Reconnecting : NetworkState
}

sealed interface ActiveJobState {
    data object None : ActiveJobState
    data object Loading : ActiveJobState
    data class Available(
        val job: JobDto,
        val route: JobRouteUiModel,
        val completedStops: Int,
        val totalStops: Int,
        val nextStop: JobStopUiModel?
    ) : ActiveJobState
    data class Error(
        val message: String,
        val cached: Available? = null
    ) : ActiveJobState
}

sealed interface NavigationState {
    data object Inactive : NavigationState
    data class Preparing(val targetBinId: String) : NavigationState
    data class Confirming(
        val targetBinId: String,
        val targetBin: SmartBinDto,
        val distanceMeters: Int?,
        val durationSeconds: Int?,
        val distanceText: String = "--",
        val etaText: String = "--",
        val isGpsAvailable: Boolean = true,
        val route: JobRouteUiModel? = null
    ) : NavigationState
    data class Active(
        val targetBinId: String,
        val targetBin: SmartBinDto,
        val route: JobRouteUiModel,
        val remainingDistanceMeters: Int?,
        val estimatedDurationSeconds: Int?,
        val distanceText: String = "--",
        val etaText: String = "--",
        val nextTurnInstruction: String = "Đi theo tuyến đường đã định",
        val nextTurnDistanceMeters: Int = 0,
        val nextTurnDistanceText: String = "120 m",
        val isMuted: Boolean = false,
        val isAutoFollow: Boolean = true
    ) : NavigationState
    data class Arrived(
        val targetBinId: String,
        val targetBin: SmartBinDto,
        val distanceText: String = "~20 m"
    ) : NavigationState
    data class Failed(
        val targetBinId: String?,
        val message: String
    ) : NavigationState
}

sealed interface RadarState {
    data object Disabled : RadarState
    data class Active(
        val radiusMeters: Double = 500.0,
        val eligibleBins: List<SmartBinDto> = emptyList(),
        val criticalBinsCount: Int = 0
    ) : RadarState
}

sealed interface SelfPickState {
    data object Idle : SelfPickState
    data class Confirming(
        val binIds: List<String>,
        val eligibleBins: List<SmartBinDto>,
        val estimatedDistanceMeters: Int? = null,
        val estimatedDurationSeconds: Int? = null
    ) : SelfPickState
    data class Submitting(val binIds: List<String>) : SelfPickState
    data class Failed(val binIds: List<String>, val message: String) : SelfPickState
}

sealed interface MapLoadingState {
    data object Idle : MapLoadingState
    data object LoadingMap : MapLoadingState
    data object LoadingRoute : MapLoadingState
    data object SubmittingSelfPick : MapLoadingState
    data object ExecutingLidCommand : MapLoadingState
    data object SubmittingIncident : MapLoadingState
    data object ExecutingJobTransition : MapLoadingState
}

sealed interface BinDetailState {
    data object Closed : BinDetailState
    data class Loading(val binId: String) : BinDetailState
    data class Content(
        val binId: String,
        val bin: SmartBinDto,
        val isRefreshing: Boolean = false
    ) : BinDetailState
    data class Error(
        val binId: String,
        val message: String,
        val cachedBin: SmartBinDto? = null
    ) : BinDetailState
}

enum class LidCommandFailure {
    TIMEOUT,
    DEVICE_OFFLINE,
    UNAUTHORIZED,
    NETWORK_ERROR,
    SERVER_ERROR
}

sealed interface LidCommandState {
    data object Idle : LidCommandState
    data class Executing(val binId: String) : LidCommandState
    data class Succeeded(
        val binId: String,
        val confirmedStatus: String?,
        val message: String
    ) : LidCommandState
    data class Failed(
        val binId: String,
        val failureType: LidCommandFailure,
        val message: String
    ) : LidCommandState
}

/**
 * Unified Immutable Map UI State (Single Source of Truth)
 */
data class MapUiState(
    val mode: MapMode = MapMode.IDLE,
    val allBins: List<SmartBinDto> = emptyList(),
    val displayedBins: List<SmartBinDto> = emptyList(),
    val selectedBin: SmartBinDto? = null,
    val binDetailState: BinDetailState = BinDetailState.Closed,
    val lidCommandState: LidCommandState = LidCommandState.Idle,
    val activeJobState: ActiveJobState = ActiveJobState.None,
    val activeJob: JobDto? = null,
    val activeJobRoute: JobRouteUiModel? = null,
    val navigationState: NavigationState = NavigationState.Inactive,
    val radarState: RadarState = RadarState.Disabled,
    val selfPickState: SelfPickState = SelfPickState.Idle,
    val driverLocation: MapCoordinate? = null,
    val driverHeading: Float = 0f,
    val driverSpeed: Double = 0.0,
    val searchInput: String = "",
    val appliedSearchQuery: String = "",
    val filters: MapFilters = MapFilters(),
    val thresholds: BinThresholds = BinThresholds.FALLBACK,
    val routeCoordinates: List<List<Double>> = emptyList(),
    val routeWaypoints: List<SmartBinDto> = emptyList(),
    val routeWaypointModels: List<JobStopUiModel> = emptyList(),
    val mapLayer: MapLayer = MapLayer.DEFAULT,
    val networkState: NetworkState = NetworkState.Online,
    val gpsState: GpsState = GpsState.Available,
    val loadingState: MapLoadingState = MapLoadingState.Idle,
    val incidentReason: IncidentReason = IncidentReason.BROKEN_BIN,
    val incidentDescription: String = "",
    val incidentAttachmentState: IncidentAttachmentState = IncidentAttachmentState.None,
    val incidentSubmissionState: IncidentSubmissionState = IncidentSubmissionState.Idle
) {
    val activeChips: List<ActiveFilterChip>
        get() = MapStatePolicy.deriveActiveChips(this, thresholds)
}

// =============================================================================
// 2. USER INTENTS / MAP ACTIONS (PURE DATA ONLY)
// =============================================================================

sealed interface MapAction {
    data class LoadData(val targetJobId: String? = null) : MapAction
    data object RefreshActiveJob : MapAction
    data object RetryMapData : MapAction
    data object RetryRealtime : MapAction
    data class SearchInputChanged(val input: String) : MapAction
    data object ClearSearch : MapAction
    data class ApplyFilter(val filters: MapFilters) : MapAction
    data object ResetFilter : MapAction
    data class RemoveFilterChip(val chipId: MapFilterChipId) : MapAction
    data class SelectBin(val binId: String) : MapAction
    data object ClearSelection : MapAction
    data class SelectJobStop(val binId: String) : MapAction
    data class OpenBinDetail(val binId: String) : MapAction
    data object CloseBinDetail : MapAction
    data class RefreshBinDetail(val binId: String) : MapAction
    data class ConfirmOpenLid(val binId: String) : MapAction
    data object DismissLidCommandState : MapAction
    data class OpenIncidentSheet(val binId: String) : MapAction
    data object CloseIncidentSheet : MapAction
    data class SelectIncidentReason(val reason: IncidentReason) : MapAction
    data class ChangeIncidentDescription(val value: String) : MapAction
    data class SelectIncidentAttachment(val uriString: String, val displayName: String? = null, val sizeBytes: Long? = null) : MapAction
    data object RemoveIncidentAttachment : MapAction
    data class SubmitIncident(val binId: String, val jpegBytes: ByteArray? = null) : MapAction
    data object RetryIncidentSubmission : MapAction
    data class OpenIncidentForBin(val binId: String) : MapAction
    data class StartNavigation(val binId: String) : MapAction
    data class StartNavigationToBin(val binId: String) : MapAction
    data class StartNavigationToNextJobStop(val jobId: String) : MapAction
    data object ConfirmStartNavigation : MapAction
    data object CancelNavigationPreview : MapAction
    data object RetryGpsForNavigation : MapAction
    data object ResumeAutoFollow : MapAction
    data object PauseAutoFollow : MapAction
    data object StartCollectionAtDestination : MapAction
    data object StopNavigation : MapAction
    data class EnableRadar(val radiusMeters: Double = 500.0) : MapAction
    data object DisableRadar : MapAction
    data object ToggleRadar : MapAction
    data class OpenSelfPickConfirmation(val binIds: List<String>) : MapAction
    data object CloseSelfPickConfirmation : MapAction
    data class ConfirmSelfPick(val binIds: List<String>) : MapAction
    data class CreateSelfPickJob(val binIds: List<String>) : MapAction
    data class AcceptJob(val jobId: String) : MapAction
    data class StartJob(val jobId: String) : MapAction
    data class PauseJob(val jobId: String, val reason: String = "Tạm dừng") : MapAction
    data class ResumeJob(val jobId: String) : MapAction
    data class UpdateDriverLocation(val lat: Double, val lng: Double, val heading: Float = 0f, val accuracy: Double? = null, val speed: Double? = null) : MapAction
    data class SetGpsState(val state: GpsState) : MapAction
    data class SetNetworkState(val state: NetworkState) : MapAction
    data class SetMapLayer(val layer: MapLayer) : MapAction
    data class UpdateThresholds(val thresholds: BinThresholds) : MapAction
}

// =============================================================================
// 3. ONE-SHOT MAP EFFECTS
// =============================================================================

sealed interface MapEffect {
    data class ShowToast(val message: String) : MapEffect
    data class NavigateToIncident(val binId: String) : MapEffect
    data class IncidentSubmissionSuccess(val binId: String, val message: String) : MapEffect
    data class SelfPickSuccess(val jobId: String?, val message: String) : MapEffect
    data class LidCommandResultEffect(val binId: String, val isSuccess: Boolean, val message: String) : MapEffect
    data class OperationFailed(val operation: String, val message: String) : MapEffect
}

// =============================================================================
// 4. MAP VIEW MODEL
// =============================================================================

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val binsRepo = BinsRepository(application)
    private val jobsRepo = JobsRepository(application)
    private val incidentRepo = IncidentRepository(application)

    // Single Source of Truth
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Side Effects Channel
    private val _effectChannel = Channel<MapEffect>(Channel.BUFFERED)
    val effects: Flow<MapEffect> = _effectChannel.receiveAsFlow()

    // Cancellable Search Debounce Job
    private var searchDebounceJob: Job? = null

    // In-flight command tracking
    private var inFlightCommandBinId: String? = null
    private var isSubmittingSelfPick: Boolean = false
    private var isJobTransitionInFlight: Boolean = false
    private var isSubmittingIncident: Boolean = false

    // Realtime update debouncer
    private var realtimeRefreshJob: Job? = null

    // GPS location upload throttle tracking
    private var lastUploadedLat: Double = 0.0
    private var lastUploadedLng: Double = 0.0
    private var lastLocationUploadTimestamp: Long = 0L

    // Navigation & Off-route auto-reroute debouncer
    private var consecutiveOffRouteCount: Int = 0
    private var lastRerouteTimestamp: Long = 0L

    init {
        val cached = BinsRepository.getCachedBins()
        if (cached.isNotEmpty()) {
            updateState { it.copy(allBins = cached) }
        }
        executeLoadData(null)
    }

    // =========================================================================
    // 5. REDUCER / ACTION DISPATCHER
    // =========================================================================

    fun handleAction(action: MapAction) {
        when (action) {
            is MapAction.LoadData,
            is MapAction.RetryMapData -> executeLoadData(if (action is MapAction.LoadData) action.targetJobId else null)

            is MapAction.RetryRealtime -> {
                sendEffect(MapEffect.ShowToast("Đang kết nối lại dịch vụ thời gian thực..."))
                executeLoadData(null)
            }

            is MapAction.RefreshActiveJob -> {
                realtimeRefreshJob?.cancel()
                realtimeRefreshJob = viewModelScope.launch {
                    delay(250) // Debounce realtime refresh storm
                    executeLoadData(null)
                }
            }

            is MapAction.SearchInputChanged -> {
                val input = action.input
                _uiState.update { it.copy(searchInput = input) }
                searchDebounceJob?.cancel()
                searchDebounceJob = viewModelScope.launch {
                    delay(300)
                    val trimmed = input.trim()
                    updateState { it.copy(appliedSearchQuery = trimmed) }
                }
            }

            is MapAction.ClearSearch -> {
                searchDebounceJob?.cancel()
                updateState {
                    it.copy(
                        searchInput = "",
                        appliedSearchQuery = "",
                        selectedBin = null
                    )
                }
            }

            is MapAction.ApplyFilter -> {
                updateState { it.copy(filters = action.filters) }
            }

            is MapAction.ResetFilter -> {
                searchDebounceJob?.cancel()
                updateState {
                    it.copy(
                        filters = MapFilters(),
                        searchInput = "",
                        appliedSearchQuery = "",
                        selectedBin = null,
                        radarState = RadarState.Disabled
                    )
                }
            }

            is MapAction.RemoveFilterChip -> {
                when (action.chipId) {
                    MapFilterChipId.SEARCH_QUERY -> {
                        searchDebounceJob?.cancel()
                        updateState { it.copy(searchInput = "", appliedSearchQuery = "") }
                    }
                    MapFilterChipId.OFFLINE_ONLY,
                    MapFilterChipId.ONLINE_ONLY -> {
                        updateState { it.copy(filters = it.filters.copy(connectivity = ConnectivityFilter.ALL)) }
                    }
                    MapFilterChipId.CRITICAL_ONLY,
                    MapFilterChipId.WARNING_ONLY,
                    MapFilterChipId.NORMAL_ONLY -> {
                        updateState {
                            it.copy(
                                filters = it.filters.copy(
                                    showCritical = true,
                                    showWarning = true,
                                    showNormal = true
                                )
                            )
                        }
                    }
                }
            }

            is MapAction.SelectBin -> {
                val found = _uiState.value.allBins.find { it.deviceId == action.binId }
                updateState { it.copy(selectedBin = found) }
            }

            is MapAction.SelectJobStop -> {
                val found = _uiState.value.allBins.find { it.deviceId == action.binId }
                updateState { it.copy(selectedBin = found) }
            }

            is MapAction.ClearSelection -> {
                updateState { it.copy(selectedBin = null) }
            }

            is MapAction.OpenBinDetail -> {
                val found = _uiState.value.allBins.find { it.deviceId == action.binId }
                updateState {
                    it.copy(
                        selectedBin = found ?: it.selectedBin,
                        binDetailState = if (found != null) {
                            BinDetailState.Content(action.binId, found, isRefreshing = false)
                        } else {
                            BinDetailState.Loading(action.binId)
                        }
                    )
                }
                executeRefreshBinDetail(action.binId)
            }

            is MapAction.CloseBinDetail -> {
                updateState {
                    it.copy(
                        binDetailState = BinDetailState.Closed,
                        lidCommandState = LidCommandState.Idle
                    )
                }
            }

            is MapAction.RefreshBinDetail -> {
                executeRefreshBinDetail(action.binId)
            }

            is MapAction.ConfirmOpenLid -> {
                executeConfirmOpenLid(action.binId)
            }

            is MapAction.DismissLidCommandState -> {
                updateState { it.copy(lidCommandState = LidCommandState.Idle) }
            }

            // Incident Flow
            is MapAction.OpenIncidentSheet -> {
                val found = _uiState.value.allBins.find { it.deviceId == action.binId }
                updateState {
                    it.copy(
                        selectedBin = found ?: it.selectedBin,
                        incidentReason = IncidentReason.BROKEN_BIN,
                        incidentDescription = "",
                        incidentAttachmentState = IncidentAttachmentState.None,
                        incidentSubmissionState = IncidentSubmissionState.Idle
                    )
                }
            }

            is MapAction.CloseIncidentSheet -> {
                updateState {
                    it.copy(incidentSubmissionState = IncidentSubmissionState.Idle)
                }
            }

            is MapAction.SelectIncidentReason -> {
                updateState { it.copy(incidentReason = action.reason) }
            }

            is MapAction.ChangeIncidentDescription -> {
                updateState { it.copy(incidentDescription = action.value) }
            }

            is MapAction.SelectIncidentAttachment -> {
                updateState {
                    it.copy(
                        incidentAttachmentState = IncidentAttachmentState.Selected(
                            uriString = action.uriString,
                            displayName = action.displayName,
                            sizeBytes = action.sizeBytes
                        )
                    )
                }
            }

            is MapAction.RemoveIncidentAttachment -> {
                updateState { it.copy(incidentAttachmentState = IncidentAttachmentState.None) }
            }

            is MapAction.SubmitIncident -> {
                executeSubmitIncident(action.binId, action.jpegBytes)
            }

            is MapAction.RetryIncidentSubmission -> {
                val binId = _uiState.value.selectedBin?.deviceId.orEmpty()
                if (binId.isNotBlank()) {
                    executeSubmitIncident(binId, null)
                }
            }

            is MapAction.OpenIncidentForBin -> {
                if (action.binId.isNotBlank()) {
                    sendEffect(MapEffect.NavigateToIncident(action.binId))
                } else {
                    sendEffect(MapEffect.ShowToast("Không xác định được mã thùng rác để báo sự cố."))
                }
            }

            is MapAction.StartNavigation,
            is MapAction.StartNavigationToBin -> {
                val binId = if (action is MapAction.StartNavigation) action.binId else (action as MapAction.StartNavigationToBin).binId
                executePreviewNavigation(binId)
            }

            is MapAction.StartNavigationToNextJobStop -> {
                val nextStop = (_uiState.value.activeJobState as? ActiveJobState.Available)?.nextStop
                if (nextStop != null) {
                    executePreviewNavigation(nextStop.binId)
                } else {
                    sendEffect(MapEffect.ShowToast("Tất cả các điểm trong ca đã được thu gom hoàn tất."))
                }
            }

            is MapAction.ConfirmStartNavigation -> {
                executeConfirmStartNavigation()
            }

            is MapAction.CancelNavigationPreview -> {
                executeCancelNavigationPreview()
            }

            is MapAction.RetryGpsForNavigation -> {
                val confirming = _uiState.value.navigationState as? NavigationState.Confirming
                if (confirming != null) {
                    executePreviewNavigation(confirming.targetBinId)
                }
            }

            is MapAction.ResumeAutoFollow -> {
                val nav = _uiState.value.navigationState as? NavigationState.Active
                if (nav != null) {
                    updateState { it.copy(navigationState = nav.copy(isAutoFollow = true)) }
                }
            }

            is MapAction.PauseAutoFollow -> {
                val nav = _uiState.value.navigationState as? NavigationState.Active
                if (nav != null) {
                    updateState { it.copy(navigationState = nav.copy(isAutoFollow = false)) }
                }
            }

            is MapAction.StartCollectionAtDestination -> {
                executeStopNavigation()
            }

            is MapAction.StopNavigation -> {
                executeStopNavigation()
            }

            is MapAction.EnableRadar -> {
                executeEnableRadar(action.radiusMeters)
            }

            is MapAction.DisableRadar -> {
                updateState { it.copy(radarState = RadarState.Disabled) }
            }

            is MapAction.ToggleRadar -> {
                if (_uiState.value.radarState is RadarState.Active) {
                    updateState { it.copy(radarState = RadarState.Disabled) }
                } else {
                    executeEnableRadar(500.0)
                }
            }

            is MapAction.OpenSelfPickConfirmation -> {
                executeOpenSelfPickConfirmation(action.binIds)
            }

            is MapAction.CloseSelfPickConfirmation -> {
                updateState { it.copy(selfPickState = SelfPickState.Idle) }
            }

            is MapAction.ConfirmSelfPick,
            is MapAction.CreateSelfPickJob -> {
                val binIds = if (action is MapAction.ConfirmSelfPick) action.binIds else (action as MapAction.CreateSelfPickJob).binIds
                executeCreateSelfPickJob(binIds)
            }

            is MapAction.AcceptJob -> executeJobTransition(action.jobId, JobActionType.ACCEPT)
            is MapAction.StartJob -> executeJobTransition(action.jobId, JobActionType.START)
            is MapAction.PauseJob -> executeJobTransition(action.jobId, JobActionType.PAUSE, action.reason)
            is MapAction.ResumeJob -> executeJobTransition(action.jobId, JobActionType.RESUME)

            is MapAction.UpdateDriverLocation -> {
                val coord = MapCoordinate(action.lat, action.lng)
                if (coord.isValid) {
                    updateState { current ->
                        val updated = current.copy(
                            driverLocation = coord,
                            driverHeading = action.heading,
                            driverSpeed = action.speed ?: 0.0
                        )
                        // If radar is active, update eligible bins based on new driver location
                        if (updated.radarState is RadarState.Active) {
                            val eligible = MapStatePolicy.filterEligibleRadarBins(
                                allBins = updated.allBins,
                                driverLocation = coord,
                                radiusMeters = updated.radarState.radiusMeters,
                                thresholds = updated.thresholds
                            )
                            val critCount = eligible.count { (it.levelPercent ?: 0.0) >= updated.thresholds.critical }
                            updated.copy(
                                radarState = RadarState.Active(
                                    radiusMeters = updated.radarState.radiusMeters,
                                    eligibleBins = eligible,
                                    criticalBinsCount = critCount
                                )
                            )
                        } else if (updated.navigationState is NavigationState.Active) {
                            val nav = updated.navigationState as NavigationState.Active
                            val binLat = nav.targetBin.latitude
                            val binLng = nav.targetBin.longitude
                            if (binLat != null && binLng != null && coord.isValid && coord.latitude in 8.0..24.0) {
                                val remDistM = MapStatePolicy.calculateHaversineDistance(
                                    coord.latitude, coord.longitude,
                                    binLat, binLng
                                ).toInt()

                                if (remDistM <= 25) {
                                    updated.copy(
                                        navigationState = NavigationState.Arrived(
                                            targetBinId = nav.targetBinId,
                                            targetBin = nav.targetBin,
                                            distanceText = "~$remDistM m"
                                        )
                                    )
                                } else {
                                    val distText = if (remDistM < 1000) {
                                        "$remDistM m"
                                    } else {
                                        String.format(java.util.Locale.GERMAN, "%.1f km", remDistM / 1000.0).replace('.', ',')
                                    }
                                    val durationSec = ((remDistM / 30000.0) * 3600.0).roundToInt()
                                    val minutes = max(1, (durationSec / 60.0).roundToInt())
                                    val etaText = if (minutes < 60) "$minutes phút" else "${minutes / 60} giờ ${minutes % 60} phút"

                                    // Dynamic maneuver derivation from OSRM steps
                                    val (instruction, nextTurnM) = MapStatePolicy.deriveNextManeuverInstruction(
                                        coord.latitude, coord.longitude, remDistM, nav.route.steps
                                    )
                                    val nextTurnText = if (nextTurnM < 1000) "$nextTurnM m" else String.format(java.util.Locale.GERMAN, "%.1f km", nextTurnM / 1000.0).replace('.', ',')

                                    updated.copy(
                                        navigationState = nav.copy(
                                            remainingDistanceMeters = remDistM,
                                            estimatedDurationSeconds = durationSec,
                                            distanceText = distText,
                                            etaText = etaText,
                                            nextTurnInstruction = instruction,
                                            nextTurnDistanceMeters = nextTurnM,
                                            nextTurnDistanceText = nextTurnText
                                        )
                                    )
                                }
                            } else {
                                updated
                            }
                        } else {
                            updated
                        }
                    }

                    // Off-Route Reroute Detection (distance > 65m, accuracy <= 25m, sustained 3 consecutive samples)
                    val activeNav = _uiState.value.navigationState as? NavigationState.Active
                    if (activeNav != null) {
                        val routeCoords = _uiState.value.routeCoordinates
                        val binLat = activeNav.targetBin.latitude
                        val binLng = activeNav.targetBin.longitude
                        if (routeCoords.isNotEmpty() && binLat != null && binLng != null && action.accuracy != null && action.accuracy <= 25.0) {
                            val distToRoute = MapStatePolicy.calculateDistanceToPolyline(action.lat, action.lng, routeCoords)
                            if (distToRoute > 65.0) {
                                consecutiveOffRouteCount++
                                val now = System.currentTimeMillis()
                                if (consecutiveOffRouteCount >= 3 && (now - lastRerouteTimestamp > 10000L)) {
                                    lastRerouteTimestamp = now
                                    consecutiveOffRouteCount = 0
                                    executeAutoReroute(action.lat, action.lng, binLat, binLng, activeNav)
                                }
                            } else if (distToRoute <= 40.0) {
                                consecutiveOffRouteCount = 0
                            }
                        }
                    }

                    // Background GPS upload throttle (min 10 meters or min 15 seconds)
                    val now = System.currentTimeMillis()
                    val distMoved = MapStatePolicy.calculateHaversineDistance(lastUploadedLat, lastUploadedLng, action.lat, action.lng)
                    if (distMoved > 10.0 || (now - lastLocationUploadTimestamp > 15000L)) {
                        lastUploadedLat = action.lat
                        lastUploadedLng = action.lng
                        lastLocationUploadTimestamp = now
                        viewModelScope.launch {
                            try {
                                binsRepo.updateLocation(
                                    LocationPayload(
                                        latitude = action.lat,
                                        longitude = action.lng,
                                        heading = action.heading.toDouble(),
                                        speed = action.speed,
                                        accuracy = action.accuracy
                                    )
                                )
                            } catch (e: Exception) {
                                // Upload failure is non-fatal: does not block local driver marker or change GPS state
                            }
                        }
                    }
                }
            }

            is MapAction.SetGpsState -> {
                updateState { it.copy(gpsState = action.state) }
            }

            is MapAction.SetNetworkState -> {
                updateState { it.copy(networkState = action.state) }
            }

            is MapAction.SetMapLayer -> {
                updateState { it.copy(mapLayer = action.layer) }
            }

            is MapAction.UpdateThresholds -> {
                updateState { it.copy(thresholds = action.thresholds) }
            }
        }
    }

    /**
     * Centralized Atomic State Mutation with Pure Derived State Resolution
     */
    private fun updateState(transform: (MapUiState) -> MapUiState) {
        _uiState.update { currentState ->
            val intermediate = transform(currentState)
            val computedBins = MapStatePolicy.filterBins(
                bins = intermediate.allBins,
                query = intermediate.appliedSearchQuery,
                filters = intermediate.filters,
                radarState = intermediate.radarState,
                driverLocation = intermediate.driverLocation,
                thresholds = intermediate.thresholds
            )
            val resolvedMode = MapStatePolicy.resolveOperationalMode(intermediate, computedBins)
            intermediate.copy(
                displayedBins = computedBins,
                mode = resolvedMode
            )
        }
    }

    // =========================================================================
    // 6. ASYNC REPOSITORY OPERATIONS
    // =========================================================================

    private fun executeLoadData(targetJobId: String?) {
        if (_uiState.value.loadingState == MapLoadingState.LoadingMap) return

        if (_uiState.value.allBins.isEmpty()) {
            updateState { it.copy(loadingState = MapLoadingState.LoadingMap) }
        }

        viewModelScope.launch {
            try {
                val binsDeferred = async { binsRepo.getBins() }
                val activeJobDeferred = async { jobsRepo.getActiveJob() }
                val settingsDeferred = async { jobsRepo.getSystemSettings() }

                val binsResult = binsDeferred.await()
                val activeJobResult = activeJobDeferred.await()
                val settingsResult = settingsDeferred.await()

                // 1. Process System Settings & Dynamic Thresholds
                val dynamicThresholds = if (settingsResult.isSuccess) {
                    val settings = settingsResult.getOrNull()
                    BinThresholds.createSafe(
                        warning = settings?.fillThresholdWarning?.toDouble(),
                        critical = settings?.fillThresholdCritical?.toDouble()
                    )
                } else {
                    _uiState.value.thresholds
                }

                // 2. Process Bins
                val freshBins = if (binsResult.isSuccess) {
                    binsResult.getOrDefault(emptyList())
                } else {
                    _uiState.value.allBins
                }

                if (binsResult.isFailure && _uiState.value.allBins.isEmpty()) {
                    updateState { it.copy(networkState = NetworkState.BackendUnavailable) }
                    sendEffect(MapEffect.ShowToast("Không thể tải dữ liệu thùng rác từ máy chủ."))
                } else if (binsResult.isSuccess) {
                    updateState { it.copy(networkState = NetworkState.Online) }
                }

                // 3. Process Active Job
                val rawJob = if (!targetJobId.isNullOrBlank()) {
                    val detailResult = jobsRepo.getJobDetail(targetJobId)
                    if (detailResult.isFailure) {
                        sendEffect(MapEffect.ShowToast("Không thể tải chi tiết nhiệm vụ $targetJobId."))
                    }
                    detailResult.getOrNull()
                } else {
                    activeJobResult.getOrNull()
                }

                val jobRoute = rawJob?.let { MapStatePolicy.parseJobRoute(it, freshBins) }
                val totalStops = jobRoute?.stops?.size ?: (rawJob?.targetBinIds?.size ?: 0)
                val completedStops = jobRoute?.stops?.count { it.status == JobStopStatus.COLLECTED } ?: (rawJob?.completedBinIds?.size ?: 0)
                val nextStop = jobRoute?.stops?.firstOrNull { it.isNext }

                val activeJobState = if (rawJob != null && rawJob.status in listOf("ASSIGNED", "ACCEPTED", "IN_PROGRESS", "PAUSED")) {
                    ActiveJobState.Available(
                        job = rawJob,
                        route = jobRoute ?: JobRouteUiModel(emptyList(), emptyList(), null, null),
                        completedStops = completedStops,
                        totalStops = totalStops,
                        nextStop = nextStop
                    )
                } else if (activeJobResult.isFailure && _uiState.value.activeJobState is ActiveJobState.Available) {
                    ActiveJobState.Error(
                        message = activeJobResult.exceptionOrNull()?.message ?: "Lỗi làm mới nhiệm vụ",
                        cached = _uiState.value.activeJobState as ActiveJobState.Available
                    )
                } else {
                    ActiveJobState.None
                }

                // 4. Atomic Unified State Update
                updateState { current ->
                    val reconciledSelected = current.selectedBin?.let { sel ->
                        freshBins.find { it.deviceId == sel.deviceId }
                    }
                    val reconciledDetail = when (val detail = current.binDetailState) {
                        is BinDetailState.Content -> {
                            val fresh = freshBins.find { it.deviceId == detail.binId }
                            if (fresh != null) BinDetailState.Content(detail.binId, fresh, false) else detail
                        }
                        is BinDetailState.Loading -> {
                            val fresh = freshBins.find { it.deviceId == detail.binId }
                            if (fresh != null) BinDetailState.Content(detail.binId, fresh, false) else detail
                        }
                        else -> detail
                    }

                    // Determine route coordinates and waypoints based on current navigation vs active job
                    val (routeCoords, routeWps, routeWpModels) = if (current.navigationState is NavigationState.Active) {
                        Triple(current.routeCoordinates, current.routeWaypoints, current.routeWaypointModels)
                    } else if (jobRoute != null && jobRoute.coordinates.isNotEmpty()) {
                        val coords = jobRoute.coordinates.map { listOf(it.latitude, it.longitude) }
                        val wps = jobRoute.stops.mapNotNull { it.bin }
                        Triple(coords, wps, jobRoute.stops)
                    } else {
                        Triple(emptyList<List<Double>>(), emptyList<SmartBinDto>(), emptyList<JobStopUiModel>())
                    }

                    current.copy(
                        allBins = freshBins,
                        thresholds = dynamicThresholds,
                        selectedBin = reconciledSelected,
                        binDetailState = reconciledDetail,
                        activeJob = rawJob,
                        activeJobRoute = jobRoute,
                        activeJobState = activeJobState,
                        routeCoordinates = routeCoords,
                        routeWaypoints = routeWps,
                        routeWaypointModels = routeWpModels
                    )
                }

            } catch (e: Exception) {
                updateState { it.copy(networkState = NetworkState.BackendUnavailable) }
                sendEffect(MapEffect.ShowToast("Lỗi nạp dữ liệu: ${e.message ?: "Không rõ"}"))
            } finally {
                updateState { it.copy(loadingState = MapLoadingState.Idle) }
            }
        }
    }

    private fun executeRefreshBinDetail(binId: String) {
        viewModelScope.launch {
            try {
                updateState { current ->
                    if (current.binDetailState is BinDetailState.Content && current.binDetailState.binId == binId) {
                        current.copy(binDetailState = current.binDetailState.copy(isRefreshing = true))
                    } else {
                        current
                    }
                }

                val result = binsRepo.getBinDetail(binId)
                if (result.isSuccess) {
                    val freshBin = result.getOrNull()
                    if (freshBin != null) {
                        updateState { current ->
                            val updatedAllBins = current.allBins.map { if (it.deviceId == binId) freshBin else it }
                            val updatedSelected = if (current.selectedBin?.deviceId == binId) freshBin else current.selectedBin
                            current.copy(
                                allBins = updatedAllBins,
                                selectedBin = updatedSelected,
                                binDetailState = BinDetailState.Content(binId, freshBin, isRefreshing = false)
                            )
                        }
                    }
                } else {
                    updateState { current ->
                        val cached = (current.binDetailState as? BinDetailState.Content)?.bin ?: current.allBins.find { it.deviceId == binId }
                        current.copy(
                            binDetailState = BinDetailState.Error(
                                binId = binId,
                                message = result.exceptionOrNull()?.message ?: "Lỗi tải thông tin chi tiết",
                                cachedBin = cached
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                updateState { current ->
                    val cached = (current.binDetailState as? BinDetailState.Content)?.bin ?: current.allBins.find { it.deviceId == binId }
                    current.copy(
                        binDetailState = BinDetailState.Error(
                            binId = binId,
                            message = e.message ?: "Lỗi kết nối",
                            cachedBin = cached
                        )
                    )
                }
            }
        }
    }

    private fun executeSubmitIncident(binId: String, jpegBytes: ByteArray?) {
        val targetId = binId.trim()
        if (targetId.isBlank()) {
            sendEffect(MapEffect.ShowToast("Vui lòng chọn thùng rác cần báo sự cố."))
            return
        }

        if (isSubmittingIncident || _uiState.value.incidentSubmissionState is IncidentSubmissionState.Submitting) {
            return
        }

        if (_uiState.value.networkState is NetworkState.NoInternet) {
            updateState {
                it.copy(
                    incidentSubmissionState = IncidentSubmissionState.Failed("Đang ngoại tuyến. Vui lòng kết nối Internet để gửi báo cáo.", retryable = true)
                )
            }
            sendEffect(MapEffect.ShowToast("Đang ngoại tuyến. Đã lưu bản nháp để gửi lại."))
            return
        }

        val reason = _uiState.value.incidentReason
        val description = _uiState.value.incidentDescription.trim()

        if (reason == IncidentReason.OTHER && description.isBlank()) {
            sendEffect(MapEffect.ShowToast("Vui lòng nhập mô tả chi tiết cho loại sự cố 'Khác'."))
            return
        }

        isSubmittingIncident = true
        updateState {
            it.copy(
                incidentSubmissionState = IncidentSubmissionState.Submitting,
                loadingState = MapLoadingState.SubmittingIncident
            )
        }

        viewModelScope.launch {
            try {
                val result = if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                    incidentRepo.reportIncidentWithPhoto(
                        binId = targetId,
                        issueType = reason.toVietnamese(),
                        description = description,
                        jpegBytes = jpegBytes
                    )
                } else {
                    incidentRepo.reportIncident(
                        binId = targetId,
                        issueType = reason.toVietnamese(),
                        description = description,
                        photoUrl = null
                    )
                }

                if (result.isSuccess) {
                    updateState {
                        it.copy(
                            incidentSubmissionState = IncidentSubmissionState.Succeeded(null, "Báo cáo sự cố #${targetId} đã được gửi thành công!"),
                            incidentDescription = "",
                            incidentAttachmentState = IncidentAttachmentState.None,
                            loadingState = MapLoadingState.Idle
                        )
                    }
                    sendEffect(MapEffect.IncidentSubmissionSuccess(targetId, "✓ Báo cáo sự cố #${targetId} đã được gửi thành công!"))
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Lỗi gửi báo cáo sự cố"
                    updateState {
                        it.copy(
                            incidentSubmissionState = IncidentSubmissionState.Failed(errMsg, retryable = true),
                            loadingState = MapLoadingState.Idle
                        )
                    }
                    sendEffect(MapEffect.OperationFailed("Incident", errMsg))
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: "Lỗi kết nối máy chủ"
                updateState {
                    it.copy(
                        incidentSubmissionState = IncidentSubmissionState.Failed(errMsg, retryable = true),
                        loadingState = MapLoadingState.Idle
                    )
                }
                sendEffect(MapEffect.OperationFailed("Incident", errMsg))
            } finally {
                isSubmittingIncident = false
            }
        }
    }

    private fun executeConfirmOpenLid(binId: String) {
        if (binId.isBlank()) {
            sendEffect(MapEffect.ShowToast("Không xác định được mã thùng rác."))
            return
        }

        if (_uiState.value.lidCommandState is LidCommandState.Executing || inFlightCommandBinId != null) {
            return
        }

        val targetBin = _uiState.value.allBins.find { it.deviceId == binId }
        if (targetBin?.isOnline == false) {
            updateState {
                it.copy(
                    lidCommandState = LidCommandState.Failed(
                        binId = binId,
                        failureType = LidCommandFailure.DEVICE_OFFLINE,
                        message = "Thùng #$binId đang ngoại tuyến (Offline), không thể mở nắp từ xa."
                    )
                )
            }
            sendEffect(MapEffect.LidCommandResultEffect(binId, false, "Thùng #$binId đang ngoại tuyến."))
            return
        }

        inFlightCommandBinId = binId
        updateState {
            it.copy(
                lidCommandState = LidCommandState.Executing(binId),
                loadingState = MapLoadingState.ExecutingLidCommand
            )
        }

        viewModelScope.launch {
            try {
                when (val result = binsRepo.openLid(binId)) {
                    is BinCommandResult.Executed -> {
                        val ackBin = result.bin
                        val confirmedLid = ackBin?.lidState ?: ackBin?.state ?: "OPEN"
                        val updatedFill = ackBin?.levelPercent
                        val updatedOnline = ackBin?.isOnline ?: true

                        updateState { current ->
                            val updatedAll = current.allBins.map { b ->
                                if (b.deviceId == binId) {
                                    b.copy(
                                        lidState = confirmedLid,
                                        levelPercent = updatedFill ?: b.levelPercent,
                                        isOnline = updatedOnline
                                    )
                                } else b
                            }
                            val updatedSel = if (current.selectedBin?.deviceId == binId) {
                                current.selectedBin.copy(
                                    lidState = confirmedLid,
                                    levelPercent = updatedFill ?: current.selectedBin.levelPercent,
                                    isOnline = updatedOnline
                                )
                            } else current.selectedBin

                            val updatedDetail = if (current.binDetailState is BinDetailState.Content && current.binDetailState.binId == binId) {
                                BinDetailState.Content(
                                    binId = binId,
                                    bin = updatedSel ?: current.binDetailState.bin,
                                    isRefreshing = false
                                )
                            } else current.binDetailState

                            current.copy(
                                allBins = updatedAll,
                                selectedBin = updatedSel,
                                binDetailState = updatedDetail,
                                lidCommandState = LidCommandState.Succeeded(
                                    binId = binId,
                                    confirmedStatus = confirmedLid,
                                    message = result.message
                                ),
                                loadingState = MapLoadingState.Idle
                            )
                        }
                        sendEffect(MapEffect.LidCommandResultEffect(binId, true, result.message))
                    }

                    is BinCommandResult.Timeout -> {
                        updateState {
                            it.copy(
                                lidCommandState = LidCommandState.Failed(
                                    binId = binId,
                                    failureType = LidCommandFailure.TIMEOUT,
                                    message = result.message
                                ),
                                loadingState = MapLoadingState.Idle
                            )
                        }
                        sendEffect(MapEffect.LidCommandResultEffect(binId, false, result.message))
                    }

                    is BinCommandResult.DeviceOffline -> {
                        updateState {
                            it.copy(
                                lidCommandState = LidCommandState.Failed(
                                    binId = binId,
                                    failureType = LidCommandFailure.DEVICE_OFFLINE,
                                    message = result.message
                                ),
                                loadingState = MapLoadingState.Idle
                            )
                        }
                        sendEffect(MapEffect.LidCommandResultEffect(binId, false, result.message))
                    }

                    is BinCommandResult.Unauthorized -> {
                        updateState {
                            it.copy(
                                lidCommandState = LidCommandState.Failed(
                                    binId = binId,
                                    failureType = LidCommandFailure.UNAUTHORIZED,
                                    message = result.message
                                ),
                                loadingState = MapLoadingState.Idle
                            )
                        }
                        sendEffect(MapEffect.LidCommandResultEffect(binId, false, result.message))
                    }

                    is BinCommandResult.NetworkError -> {
                        updateState {
                            it.copy(
                                lidCommandState = LidCommandState.Failed(
                                    binId = binId,
                                    failureType = LidCommandFailure.NETWORK_ERROR,
                                    message = result.message
                                ),
                                loadingState = MapLoadingState.Idle
                            )
                        }
                        sendEffect(MapEffect.LidCommandResultEffect(binId, false, result.message))
                    }

                    is BinCommandResult.ServerError -> {
                        updateState {
                            it.copy(
                                lidCommandState = LidCommandState.Failed(
                                    binId = binId,
                                    failureType = LidCommandFailure.SERVER_ERROR,
                                    message = result.message
                                ),
                                loadingState = MapLoadingState.Idle
                            )
                        }
                        sendEffect(MapEffect.LidCommandResultEffect(binId, false, result.message))
                    }
                }
            } catch (e: Exception) {
                updateState {
                    it.copy(
                        lidCommandState = LidCommandState.Failed(
                            binId = binId,
                            failureType = LidCommandFailure.SERVER_ERROR,
                            message = "Lỗi thực thi lệnh: ${e.message}"
                        ),
                        loadingState = MapLoadingState.Idle
                    )
                }
                sendEffect(MapEffect.LidCommandResultEffect(binId, false, "Lỗi thực thi: ${e.message}"))
            } finally {
                inFlightCommandBinId = null
            }
        }
    }

    private fun executePreviewNavigation(binId: String) {
        val bin = _uiState.value.allBins.find { it.deviceId == binId }
        if (bin == null) {
            sendEffect(MapEffect.ShowToast("Không tìm thấy dữ liệu thùng #$binId."))
            return
        }

        val binLat = bin.latitude
        val binLng = bin.longitude
        if (binLat == null || binLng == null || !MapStatePolicy.isValidCoordinate(binLat, binLng)) {
            sendEffect(MapEffect.ShowToast("Thùng #${bin.deviceId} chưa có tọa độ hợp lệ."))
            return
        }

        val driver = _uiState.value.driverLocation
        val isGpsAvailable = driver != null && driver.isValid && _uiState.value.gpsState !is GpsState.Disabled && _uiState.value.gpsState !is GpsState.PermissionDenied

        // Mutual exclusivity: preparing navigation disables radar
        updateState {
            it.copy(
                loadingState = MapLoadingState.LoadingRoute,
                radarState = RadarState.Disabled,
                navigationState = NavigationState.Preparing(binId)
            )
        }

        viewModelScope.launch {
            try {
                val isDriverFar = driver == null || !driver.isValid || driver.latitude !in 8.0..24.0 || driver.longitude !in 102.0..110.0 ||
                        MapStatePolicy.calculateHaversineDistance(driver.latitude, driver.longitude, binLat, binLng) > 60_000.0

                val startLat = if (isDriverFar) com.example.app_smart_waste.core.storage.AppConfig.DEFAULT_MAP_LAT else driver.latitude
                val startLng = if (isDriverFar) com.example.app_smart_waste.core.storage.AppConfig.DEFAULT_MAP_LNG else driver.longitude

                // Backend OSRM request uses [lng, lat]
                val result = binsRepo.calculateRoute(listOf(startLat to startLng, binLat to binLng))
                val route = result.getOrNull()

                if (route == null || route.coordinates.isNullOrEmpty()) {
                    updateState {
                        it.copy(
                            navigationState = NavigationState.Failed(binId, "Không thể tải tuyến đường dẫn đường."),
                            loadingState = MapLoadingState.Idle
                        )
                    }
                    sendEffect(MapEffect.ShowToast("Không thể tải tuyến đường dẫn đường."))
                    return@launch
                }

                // GeoJSON [lng, lat] converted to [lat, lng] for Leaflet
                val leafCoords = route.coordinates.mapNotNull { pt ->
                    if (pt.size >= 2 && MapStatePolicy.isValidCoordinate(pt[1], pt[0])) listOf(pt[1], pt[0]) else null
                }
                val geoCoords = leafCoords.map { GeoCoordinate(it[0], it[1]) }

                val distanceMeters = route.distanceMeters?.toInt()
                val distText = distanceMeters?.let { meters ->
                    if (meters < 1000) {
                        "$meters m"
                    } else {
                        String.format(java.util.Locale.GERMAN, "%.1f km", meters / 1000.0).replace('.', ',')
                    }
                } ?: "2,4 km"

                val durationSeconds = route.durationSeconds?.toInt()
                val etaText = durationSeconds?.let { seconds ->
                    val minutes = max(1, (seconds / 60.0).roundToInt())
                    if (minutes < 60) {
                        "$minutes phút"
                    } else {
                        val hrs = minutes / 60
                        val mins = minutes % 60
                        if (mins > 0) "$hrs giờ $mins phút" else "$hrs giờ"
                    }
                } ?: "8 phút"

                val stopUiModel = JobStopUiModel(
                    binId = bin.deviceId,
                    order = 1,
                    coordinate = GeoCoordinate(binLat, binLng),
                    status = JobStopStatus.PENDING,
                    isNext = true,
                    bin = bin
                )

                val steps = route.steps ?: emptyList()
                val jobRouteUi = JobRouteUiModel(
                    coordinates = geoCoords,
                    stops = listOf(stopUiModel),
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSeconds,
                    steps = steps
                )

                updateState { current ->
                    current.copy(
                        driverLocation = MapCoordinate(startLat, startLng),
                        routeCoordinates = leafCoords,
                        routeWaypoints = listOf(bin),
                        routeWaypointModels = listOf(stopUiModel),
                        navigationState = NavigationState.Confirming(
                            targetBinId = binId,
                            targetBin = bin,
                            distanceMeters = distanceMeters,
                            durationSeconds = durationSeconds,
                            distanceText = distText,
                            etaText = etaText,
                            isGpsAvailable = isGpsAvailable,
                            route = jobRouteUi
                        ),
                        loadingState = MapLoadingState.Idle
                    )
                }
            } catch (e: Exception) {
                updateState {
                    it.copy(
                        navigationState = NavigationState.Failed(binId, e.message ?: "Lỗi định tuyến"),
                        loadingState = MapLoadingState.Idle
                    )
                }
                sendEffect(MapEffect.ShowToast("Lỗi tính tuyến đường: ${e.message}"))
            }
        }
    }

    private fun executeConfirmStartNavigation() {
        val confirming = _uiState.value.navigationState as? NavigationState.Confirming ?: return
        if (!confirming.isGpsAvailable && (_uiState.value.gpsState is GpsState.Disabled || _uiState.value.gpsState is GpsState.PermissionDenied)) {
            sendEffect(MapEffect.ShowToast("Vui lòng bật GPS để bắt đầu dẫn đường."))
            return
        }

        val bin = confirming.targetBin
        val route = confirming.route
        val leafCoords = _uiState.value.routeCoordinates

        val (initialInstruction, nextTurnM) = if (route != null && route.steps.isNotEmpty()) {
            MapStatePolicy.deriveNextManeuverInstruction(
                _uiState.value.driverLocation?.latitude ?: 0.0,
                _uiState.value.driverLocation?.longitude ?: 0.0,
                confirming.distanceMeters ?: 120,
                route.steps
            )
        } else {
            val inst = if (!bin.location.isNullOrBlank()) {
                "Đi theo tuyến đường đến\n${bin.location}"
            } else {
                "Đi theo tuyến đường đã định\nđến điểm #${bin.deviceId}"
            }
            val dist = if (confirming.distanceMeters != null && confirming.distanceMeters <= 250) confirming.distanceMeters else 120
            Pair(inst, dist)
        }
        val nextTurnDistText = if (nextTurnM < 1000) "$nextTurnM m" else String.format(java.util.Locale.GERMAN, "%.1f km", nextTurnM / 1000.0).replace('.', ',')

        val effectiveHeading = if (leafCoords.size >= 2) {
            val p1 = leafCoords[0]
            val p2 = leafCoords[1]
            val y = Math.sin(Math.toRadians(p2[1] - p1[1])) * Math.cos(Math.toRadians(p2[0]))
            val x = Math.cos(Math.toRadians(p1[0])) * Math.sin(Math.toRadians(p2[0])) -
                    Math.sin(Math.toRadians(p1[0])) * Math.cos(Math.toRadians(p2[0])) * Math.cos(Math.toRadians(p2[1] - p1[1]))
            ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
        } else _uiState.value.driverHeading

        updateState { current ->
            current.copy(
                driverHeading = effectiveHeading,
                navigationState = NavigationState.Active(
                    targetBinId = confirming.targetBinId,
                    targetBin = bin,
                    route = route ?: JobRouteUiModel(emptyList(), emptyList(), 0, 0),
                    remainingDistanceMeters = confirming.distanceMeters,
                    estimatedDurationSeconds = confirming.durationSeconds,
                    distanceText = confirming.distanceText,
                    etaText = confirming.etaText,
                    nextTurnInstruction = initialInstruction,
                    nextTurnDistanceMeters = nextTurnM,
                    nextTurnDistanceText = nextTurnDistText,
                    isAutoFollow = true
                ),
                loadingState = MapLoadingState.Idle
            )
        }
    }

    private fun executeAutoReroute(
        driverLat: Double,
        driverLng: Double,
        binLat: Double,
        binLng: Double,
        currentNav: NavigationState.Active
    ) {
        viewModelScope.launch {
            try {
                val result = binsRepo.calculateRoute(listOf(driverLat to driverLng, binLat to binLng))
                val route = result.getOrNull() ?: return@launch
                if (route.coordinates.isNullOrEmpty()) return@launch

                val leafCoords = route.coordinates.mapNotNull { pt ->
                    if (pt.size >= 2 && MapStatePolicy.isValidCoordinate(pt[1], pt[0])) listOf(pt[1], pt[0]) else null
                }
                if (leafCoords.isEmpty()) return@launch
                val geoCoords = leafCoords.map { GeoCoordinate(it[0], it[1]) }
                val distanceMeters = route.distanceMeters?.toInt()
                val durationSeconds = route.durationSeconds?.toInt()
                val steps = route.steps ?: emptyList()

                val updatedRoute = currentNav.route.copy(
                    coordinates = geoCoords,
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSeconds,
                    steps = steps
                )

                updateState { current ->
                    if (current.navigationState is NavigationState.Active) {
                        current.copy(
                            routeCoordinates = leafCoords,
                            navigationState = currentNav.copy(
                                route = updatedRoute,
                                remainingDistanceMeters = distanceMeters,
                                estimatedDurationSeconds = durationSeconds
                            )
                        )
                    } else {
                        current
                    }
                }
            } catch (e: Exception) {
                // Non-fatal: continue existing navigation
            }
        }
    }

    private fun executeCancelNavigationPreview() {
        executeStopNavigation()
    }

    private fun executeStopNavigation() {
        updateState { current ->
            // If active job route exists, restore it; else clear route
            val activeRoute = current.activeJobRoute
            if (activeRoute != null && activeRoute.coordinates.isNotEmpty()) {
                val coords = activeRoute.coordinates.map { listOf(it.latitude, it.longitude) }
                val wps = activeRoute.stops.mapNotNull { it.bin }
                current.copy(
                    navigationState = NavigationState.Inactive,
                    routeCoordinates = coords,
                    routeWaypoints = wps,
                    routeWaypointModels = activeRoute.stops
                )
            } else {
                current.copy(
                    navigationState = NavigationState.Inactive,
                    routeCoordinates = emptyList(),
                    routeWaypoints = emptyList(),
                    routeWaypointModels = emptyList()
                )
            }
        }
    }

    private fun executeEnableRadar(radiusMeters: Double) {
        val driver = _uiState.value.driverLocation
        if (driver == null || !driver.isValid || _uiState.value.gpsState is GpsState.Disabled || _uiState.value.gpsState is GpsState.PermissionDenied) {
            sendEffect(MapEffect.ShowToast("Cần vị trí GPS để quét radar ${radiusMeters.roundToInt()}m."))
            return
        }

        // Mutual exclusivity: radar disables navigation
        updateState { current ->
            val eligible = MapStatePolicy.filterEligibleRadarBins(
                allBins = current.allBins,
                driverLocation = driver,
                radiusMeters = radiusMeters,
                thresholds = current.thresholds
            )
            val critCount = eligible.count { (it.levelPercent ?: 0.0) >= current.thresholds.critical }
            current.copy(
                radarState = RadarState.Active(
                    radiusMeters = radiusMeters,
                    eligibleBins = eligible,
                    criticalBinsCount = critCount
                ),
                navigationState = NavigationState.Inactive,
                selectedBin = null
            )
        }
    }

    private fun executeOpenSelfPickConfirmation(binIds: List<String>) {
        val uniqueIds = binIds.distinct().filter { it.isNotBlank() }
        if (uniqueIds.isEmpty()) {
            sendEffect(MapEffect.ShowToast("Không có điểm hợp lệ để tạo ca làm."))
            return
        }

        val eligible = _uiState.value.allBins.filter { it.deviceId in uniqueIds }
        updateState {
            it.copy(
                selfPickState = SelfPickState.Confirming(
                    binIds = uniqueIds,
                    eligibleBins = eligible
                )
            )
        }
    }

    private fun executeCreateSelfPickJob(binIds: List<String>) {
        val uniqueIds = binIds.distinct().filter { it.isNotBlank() }
        if (uniqueIds.isEmpty()) {
            sendEffect(MapEffect.ShowToast("Không có điểm hợp lệ để tạo ca làm."))
            return
        }

        // Double-click prevention
        if (isSubmittingSelfPick || _uiState.value.selfPickState is SelfPickState.Submitting) {
            return
        }

        isSubmittingSelfPick = true
        updateState {
            it.copy(
                selfPickState = SelfPickState.Submitting(uniqueIds),
                loadingState = MapLoadingState.SubmittingSelfPick
            )
        }

        viewModelScope.launch {
            try {
                val result = jobsRepo.selfPickJob(uniqueIds)
                if (result.isSuccess) {
                    val rawJob = result.getOrNull()
                    val freshBins = _uiState.value.allBins
                    val jobRoute = rawJob?.let { MapStatePolicy.parseJobRoute(it, freshBins) }
                    val totalStops = jobRoute?.stops?.size ?: (rawJob?.targetBinIds?.size ?: 0)
                    val completedStops = jobRoute?.stops?.count { it.status == JobStopStatus.COLLECTED } ?: 0
                    val nextStop = jobRoute?.stops?.firstOrNull { it.isNext }

                    val activeState = if (rawJob != null) {
                        ActiveJobState.Available(
                            job = rawJob,
                            route = jobRoute ?: JobRouteUiModel(emptyList(), emptyList(), null, null),
                            completedStops = completedStops,
                            totalStops = totalStops,
                            nextStop = nextStop
                        )
                    } else ActiveJobState.None

                    val (routeCoords, routeWps, routeWpModels) = if (jobRoute != null && jobRoute.coordinates.isNotEmpty()) {
                        val coords = jobRoute.coordinates.map { listOf(it.latitude, it.longitude) }
                        val wps = jobRoute.stops.mapNotNull { it.bin }
                        Triple(coords, wps, jobRoute.stops)
                    } else {
                        Triple(emptyList<List<Double>>(), emptyList<SmartBinDto>(), emptyList<JobStopUiModel>())
                    }

                    updateState { current ->
                        current.copy(
                            radarState = RadarState.Disabled,
                            selfPickState = SelfPickState.Idle,
                            activeJob = rawJob,
                            activeJobRoute = jobRoute,
                            activeJobState = activeState,
                            routeCoordinates = routeCoords,
                            routeWaypoints = routeWps,
                            routeWaypointModels = routeWpModels,
                            loadingState = MapLoadingState.Idle
                        )
                    }

                    sendEffect(MapEffect.SelfPickSuccess(rawJob?.id, "✓ Đã tạo ca làm tự nhận (${uniqueIds.size} điểm) thành công!"))
                    executeLoadData(rawJob?.id)
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "không rõ nguyên nhân"
                    updateState {
                        it.copy(
                            selfPickState = SelfPickState.Failed(uniqueIds, errMsg),
                            loadingState = MapLoadingState.Idle
                        )
                    }
                    sendEffect(MapEffect.OperationFailed("SelfPick", "Không thể tạo ca làm: $errMsg"))
                }
            } catch (e: Exception) {
                updateState {
                    it.copy(
                        selfPickState = SelfPickState.Failed(uniqueIds, e.message ?: "Lỗi kết nối"),
                        loadingState = MapLoadingState.Idle
                    )
                }
                sendEffect(MapEffect.OperationFailed("SelfPick", "Lỗi tạo ca: ${e.message}"))
            } finally {
                isSubmittingSelfPick = false
            }
        }
    }

    private fun executeJobTransition(jobId: String, actionType: JobActionType, reason: String = "") {
        if (isJobTransitionInFlight || jobId.isBlank()) return

        val currentJob = _uiState.value.activeJob
        val currentStatus = JobStatus.fromString(currentJob?.status)
        val allowed = JobTransitionPolicy.allowedActions(currentStatus)

        if (!allowed.contains(actionType)) {
            sendEffect(MapEffect.ShowToast("Không thể thực hiện hành động này ở trạng thái ${currentJob?.status}."))
            return
        }

        isJobTransitionInFlight = true
        updateState { it.copy(loadingState = MapLoadingState.ExecutingJobTransition) }

        viewModelScope.launch {
            try {
                val result = when (actionType) {
                    JobActionType.ACCEPT -> jobsRepo.acceptJob(jobId)
                    JobActionType.REJECT -> jobsRepo.rejectJob(jobId, reason)
                    JobActionType.START -> jobsRepo.startJob(jobId)
                    JobActionType.PAUSE -> jobsRepo.pauseJob(jobId, reason)
                    JobActionType.RESUME -> jobsRepo.resumeJob(jobId)
                }

                if (result.isSuccess) {
                    val updated = result.getOrNull()
                    sendEffect(MapEffect.ShowToast("✓ Cập nhật trạng thái nhiệm vụ thành công!"))
                    executeLoadData(updated?.id ?: jobId)
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Lỗi cập nhật trạng thái"
                    sendEffect(MapEffect.OperationFailed("JobTransition", errMsg))
                    executeLoadData(jobId) // reconcile on conflict
                }
            } catch (e: Exception) {
                sendEffect(MapEffect.OperationFailed("JobTransition", e.message ?: "Lỗi kết nối"))
            } finally {
                isJobTransitionInFlight = false
                updateState { it.copy(loadingState = MapLoadingState.Idle) }
            }
        }
    }

    private fun sendEffect(effect: MapEffect) {
        viewModelScope.launch { _effectChannel.send(effect) }
    }

    // =========================================================================
    // 7. COMPATIBILITY GETTERS & DELEGATES
    // =========================================================================

    val allBins: StateFlow<List<SmartBinDto>> = _uiState.map { it.allBins }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val displayedBins: StateFlow<List<SmartBinDto>> = _uiState.map { it.displayedBins }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activeJob: StateFlow<JobDto?> = _uiState.map { it.activeJob }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selectedBin: StateFlow<SmartBinDto?> = _uiState.map { it.selectedBin }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val searchQuery: StateFlow<String> = _uiState.map { it.searchInput }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val filters: StateFlow<MapFilters> = _uiState.map { it.filters }.stateIn(viewModelScope, SharingStarted.Eagerly, MapFilters())
    val isRadarMode: StateFlow<Boolean> = _uiState.map { it.radarState is RadarState.Active }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val radarRadiusMeters: StateFlow<Double> = _uiState.map { (it.radarState as? RadarState.Active)?.radiusMeters ?: 500.0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 500.0)
    val isNavigating: StateFlow<Boolean> = _uiState.map { it.navigationState is NavigationState.Active }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val navTargetBin: StateFlow<SmartBinDto?> = _uiState.map { (it.navigationState as? NavigationState.Active)?.targetBin }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val navDistanceText: StateFlow<String> = _uiState.map { (it.navigationState as? NavigationState.Active)?.distanceText ?: "--" }.stateIn(viewModelScope, SharingStarted.Eagerly, "--")
    val navEtaText: StateFlow<String> = _uiState.map { (it.navigationState as? NavigationState.Active)?.etaText ?: "--" }.stateIn(viewModelScope, SharingStarted.Eagerly, "--")
    val currentMapLayer: StateFlow<String> = _uiState.map { it.mapLayer.toJsString() }.stateIn(viewModelScope, SharingStarted.Eagerly, "default")
    val isOffline: StateFlow<Boolean> = _uiState.map { it.networkState !is NetworkState.Online }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val routeCoordinates: StateFlow<List<List<Double>>> = _uiState.map { it.routeCoordinates }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val routeWaypoints: StateFlow<List<SmartBinDto>> = _uiState.map { it.routeWaypoints }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val isLoading: StateFlow<Boolean> = _uiState.map { it.loadingState != MapLoadingState.Idle }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Compatibility method delegations
    fun loadMapData(targetJobId: String? = null) = handleAction(MapAction.LoadData(targetJobId))
    fun setSearchQuery(query: String) = handleAction(MapAction.SearchInputChanged(query))
    fun applyFilters(filters: MapFilters) = handleAction(MapAction.ApplyFilter(filters))
    fun resetFilters() = handleAction(MapAction.ResetFilter)
    fun updateDriverLocation(lat: Double, lng: Double) = handleAction(MapAction.UpdateDriverLocation(lat, lng))
    fun toggleRadarMode() = handleAction(MapAction.ToggleRadar)
    fun selectBin(binId: String) = handleAction(MapAction.SelectBin(binId))
    fun clearSelectedBin() = handleAction(MapAction.ClearSelection)
    fun setMapLayer(layerStr: String) = handleAction(MapAction.SetMapLayer(MapLayer.fromString(layerStr)))
    fun startNavigationToBin(bin: SmartBinDto, driverLat: Double, driverLng: Double) = handleAction(MapAction.StartNavigation(bin.deviceId))
    fun stopNavigation() = handleAction(MapAction.StopNavigation)

    fun createSelfPickJob(binIds: List<String>, onComplete: ((Boolean) -> Unit)? = null) {
        handleAction(MapAction.CreateSelfPickJob(binIds))
    }

    fun remoteOpenLid(binId: String, onResult: ((Boolean) -> Unit)? = null) {
        handleAction(MapAction.ConfirmOpenLid(binId))
    }

    fun clearRoute() {
        updateState {
            it.copy(
                routeCoordinates = emptyList(),
                routeWaypoints = emptyList(),
                routeWaypointModels = emptyList()
            )
        }
    }
}
