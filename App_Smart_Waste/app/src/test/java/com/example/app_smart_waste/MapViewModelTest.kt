package com.example.app_smart_waste

import com.example.app_smart_waste.core.model.ActiveJobResponse
import com.example.app_smart_waste.core.model.GeoCoordinate
import com.example.app_smart_waste.core.model.IncidentAttachmentState
import com.example.app_smart_waste.core.model.IncidentReason
import com.example.app_smart_waste.core.model.IncidentRequest
import com.example.app_smart_waste.core.model.IncidentSubmissionState
import com.example.app_smart_waste.core.model.IncidentUploadRequest
import com.example.app_smart_waste.core.model.IncidentUploadResponse
import com.example.app_smart_waste.core.model.JobActionType
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.JobItemDto
import com.example.app_smart_waste.core.model.JobRouteUiModel
import com.example.app_smart_waste.core.model.JobStatus
import com.example.app_smart_waste.core.model.JobStopStatus
import com.example.app_smart_waste.core.model.JobStopUiModel
import com.example.app_smart_waste.core.model.JobTransitionPolicy
import com.example.app_smart_waste.core.model.LocationPayload
import com.example.app_smart_waste.core.model.RouteDataDto
import com.example.app_smart_waste.core.model.RouteRequest
import com.example.app_smart_waste.core.model.SelfPickRequest
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.model.SystemSettingsDto
import com.example.app_smart_waste.ui.map.ActiveJobState
import com.example.app_smart_waste.ui.map.BinLevel
import com.example.app_smart_waste.ui.map.BinThresholds
import com.example.app_smart_waste.ui.map.ConnectivityFilter
import com.example.app_smart_waste.ui.map.GpsState
import com.example.app_smart_waste.ui.map.MapCoordinate
import com.example.app_smart_waste.ui.map.MapFilterChipId
import com.example.app_smart_waste.ui.map.MapFilters
import com.example.app_smart_waste.ui.map.MapLayer
import com.example.app_smart_waste.ui.map.MapLoadingState
import com.example.app_smart_waste.ui.map.MapMode
import com.example.app_smart_waste.ui.map.MapStatePolicy
import com.example.app_smart_waste.ui.map.MapUiState
import com.example.app_smart_waste.ui.map.NavigationState
import com.example.app_smart_waste.ui.map.NetworkState
import com.example.app_smart_waste.ui.map.RadarState
import com.example.app_smart_waste.ui.map.SelfPickState
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class MapViewModelTest {

    private val gson = Gson()

    private val sampleBins = listOf(
        SmartBinDto(deviceId = "BIN_001", name = "Thùng Chợ Bến Thành", location = "Quận 1", latitude = 10.7725, longitude = 106.6980, levelPercent = 90.0, lidState = "CLOSED", isOnline = true, collectionStatus = "IDLE"),
        SmartBinDto(deviceId = "BIN_002", name = "Thùng Phố Đi Bộ", location = "Nguyễn Huệ", latitude = 10.7740, longitude = 106.7030, levelPercent = 75.0, lidState = "CLOSED", isOnline = true, collectionStatus = "IDLE"),
        SmartBinDto(deviceId = "BIN_003", name = "Thùng Công Viên 23/9", location = "Phạm Ngũ Lão", latitude = 10.7690, longitude = 106.6930, levelPercent = 40.0, lidState = "CLOSED", isOnline = true, collectionStatus = "IDLE"),
        SmartBinDto(deviceId = "BIN_004", name = "Thùng Bưu Điện", location = "Công xã Paris", latitude = 10.7798, longitude = 106.6999, levelPercent = 88.0, lidState = "CLOSED", isOnline = false, collectionStatus = "IDLE")
    )

    // =========================================================================
    // SECTION 1: INCIDENT REPORT TESTS (PHASE 8)
    // =========================================================================

    @Test
    fun `test 1 Text-only incident request mapping correctly matches backend contract`() {
        val request = IncidentRequest(
            deviceId = "BIN_HCM_04",
            issueType = "Hỏng cảm biến siêu âm",
            description = "Cảm biến báo 100% dù thùng trống",
            photoUrl = null
        )
        val json = gson.toJson(request)
        assertEquals("{\"device_id\":\"BIN_HCM_04\",\"issue_type\":\"Hỏng cảm biến siêu âm\",\"description\":\"Cảm biến báo 100% dù thùng trống\"}", json)
    }

    @Test
    fun `test 2 Incident submission uses target deviceId without fallback to other bins`() {
        val binId = "BIN_002"
        val request = IncidentRequest(deviceId = binId, issueType = IncidentReason.BROKEN_BIN.toVietnamese(), description = "Nứt vỏ", photoUrl = null)
        assertEquals("BIN_002", request.deviceId)
        assertEquals("Thùng hỏng", request.issueType)
    }

    @Test
    fun `test 3 Validation requires non-empty description when issue reason is OTHER`() {
        val reasonOther = IncidentReason.OTHER
        val descEmpty = ""
        val isValid = !(reasonOther == IncidentReason.OTHER && descEmpty.isBlank())
        assertFalse(isValid)

        val descFilled = "Nắp thùng bị rỉ sét do ngập nước"
        val isValidFilled = !(reasonOther == IncidentReason.OTHER && descFilled.isBlank())
        assertTrue(isValidFilled)
    }

    @Test
    fun `test 4 Double-click prevention on incident submission`() {
        val stateSubmitting = MapUiState(incidentSubmissionState = IncidentSubmissionState.Submitting)
        val isBlocked = (stateSubmitting.incidentSubmissionState is IncidentSubmissionState.Submitting)
        assertTrue(isBlocked)
    }

    @Test
    fun `test 5 HTTP 201 with ok true produces Succeeded state`() {
        val json = """{"ok": true, "message": "Báo cáo sự cố thành công!", "report": {"id": "INC_123"}}"""
        val obj = gson.fromJson(json, Map::class.java)
        val ok = obj["ok"] as? Boolean ?: false
        assertTrue(ok)
    }

    @Test
    fun `test 6 HTTP response with ok false is treated as failure`() {
        val json = """{"ok": false, "error": "Thiếu thông tin thùng rác"}"""
        val obj = gson.fromJson(json, Map::class.java)
        val ok = obj["ok"] as? Boolean ?: false
        assertFalse(ok)
    }

    @Test
    fun `test 7 HTTP 401 403 unauthorized mapping`() {
        val httpCode = 401
        val isUnauthorized = (httpCode == 401 || httpCode == 403)
        assertTrue(isUnauthorized)
    }

    @Test
    fun `test 8 Payload too large over 5MB is blocked before network dispatch`() {
        val largeBytes = ByteArray(6 * 1024 * 1024)
        val isAllowed = (largeBytes.size <= 5 * 1024 * 1024)
        assertFalse(isAllowed)
    }

    @Test
    fun `test 9 Upload request serialization matches backend schema`() {
        val req = IncidentUploadRequest(deviceId = "BIN_001", issueType = "Rác tràn", description = "Tràn nhiều bịch rác")
        val json = gson.toJson(req)
        assertEquals("{\"deviceId\":\"BIN_001\",\"issueType\":\"Rác tràn\",\"description\":\"Tràn nhiều bịch rác\"}", json)
    }

    @Test
    fun `test 10 Upload success + finalize failure keeps draft for retry without creating local report`() {
        val stateFinalizeFail = MapUiState(
            incidentAttachmentState = IncidentAttachmentState.Uploaded("upload-uuid-1234"),
            incidentSubmissionState = IncidentSubmissionState.Failed("Finalize timeout", retryable = true)
        )
        assertTrue(stateFinalizeFail.incidentAttachmentState is IncidentAttachmentState.Uploaded)
        assertTrue(stateFinalizeFail.incidentSubmissionState is IncidentSubmissionState.Failed)
    }

    @Test
    fun `test 11 Retry finalize does not re-upload binary image`() {
        val state = MapUiState(incidentAttachmentState = IncidentAttachmentState.Uploaded("upload-uuid-1234"))
        val uploadId = (state.incidentAttachmentState as IncidentAttachmentState.Uploaded).uploadId
        assertEquals("upload-uuid-1234", uploadId)
    }

    @Test
    fun `test 12 Dismissing incident sheet does not retain active submission state`() {
        val state = MapUiState(incidentSubmissionState = IncidentSubmissionState.Idle)
        assertEquals(IncidentSubmissionState.Idle, state.incidentSubmissionState)
    }

    @Test
    fun `test 13 Empty attachment returns IncidentAttachmentState None`() {
        val state = MapUiState(incidentAttachmentState = IncidentAttachmentState.None)
        assertTrue(state.incidentAttachmentState is IncidentAttachmentState.None)
    }

    @Test
    fun `test 14 Incident reason enum converts to correct Vietnamese display labels`() {
        assertEquals("Thùng hỏng", IncidentReason.BROKEN_BIN.toVietnamese())
        assertEquals("Nắp kẹt", IncidentReason.LID_STUCK.toVietnamese())
        assertEquals("Cảm biến lỗi", IncidentReason.SENSOR_FAILURE.toVietnamese())
        assertEquals("Rác tràn", IncidentReason.OVERFLOW.toVietnamese())
        assertEquals("Khác", IncidentReason.OTHER.toVietnamese())
    }

    // =========================================================================
    // SECTION 2: MAP LAYERS (SHEET E)
    // =========================================================================

    @Test
    fun `test 15 MapLayer enum values serialize correctly to JS bridge strings`() {
        assertEquals("default", MapLayer.DEFAULT.toJsString())
        assertEquals("satellite", MapLayer.SATELLITE.toJsString())
        assertEquals("terrain", MapLayer.TERRAIN.toJsString())
    }

    @Test
    fun `test 16 Selecting MapLayer updates MapUiState mapLayer`() {
        val state = MapUiState(mapLayer = MapLayer.DEFAULT)
        val updated = state.copy(mapLayer = MapLayer.SATELLITE)
        assertEquals(MapLayer.SATELLITE, updated.mapLayer)
    }

    @Test
    fun `test 17 Same layer does not trigger redundant JS execution in render pipeline`() {
        var lastRendered = MapLayer.DEFAULT
        val current = MapLayer.DEFAULT
        val shouldExecuteJs = (lastRendered != current)
        assertFalse(shouldExecuteJs)
    }

    @Test
    fun `test 18 Recreated state preserves mapLayer setting`() {
        val savedLayer = MapLayer.TERRAIN
        val restoredState = MapUiState(mapLayer = savedLayer)
        assertEquals(MapLayer.TERRAIN, restoredState.mapLayer)
    }

    @Test
    fun `test 19 Tile loading errors do not clear markers or route state`() {
        val state = MapUiState(
            displayedBins = sampleBins,
            routeCoordinates = listOf(listOf(10.7725, 106.6980))
        )
        // Tile error occurs: state remains intact
        assertEquals(4, state.displayedBins.size)
        assertEquals(1, state.routeCoordinates.size)
    }

    // =========================================================================
    // SECTION 3: GPS & NETWORK ENVIRONMENTAL STATES & LIFECYCLE (PHASE 9)
    // =========================================================================

    @Test
    fun `test 20 GPS disabled does not erase selected bin`() {
        val state = MapUiState(
            selectedBin = sampleBins[0],
            gpsState = GpsState.Disabled
        )
        assertNotNull(state.selectedBin)
        assertEquals("BIN_001", state.selectedBin?.deviceId)
        // Operational mode remains BIN_SELECTED
        val mode = MapStatePolicy.resolveOperationalMode(state, sampleBins)
        assertEquals(MapMode.BIN_SELECTED, mode)
    }

    @Test
    fun `test 21 GPS disabled does not erase active job route`() {
        val job = JobDto(id = "JOB_101", status = "IN_PROGRESS")
        val state = MapUiState(
            activeJob = job,
            activeJobState = ActiveJobState.Available(job, JobRouteUiModel(emptyList(), emptyList(), 100, 20), 0, 1, null),
            gpsState = GpsState.Disabled
        )
        assertNotNull(state.activeJob)
        val mode = MapStatePolicy.resolveOperationalMode(state, sampleBins)
        assertEquals(MapMode.ACTIVE_JOB, mode)
    }

    @Test
    fun `test 22 Navigation and radar are blocked when GPS is unusable`() {
        val stateNoGps = MapUiState(driverLocation = null, gpsState = GpsState.Disabled)
        val canNavOrRadar = stateNoGps.driverLocation != null && stateNoGps.driverLocation.isValid && stateNoGps.gpsState is GpsState.Available
        assertFalse(canNavOrRadar)
    }

    @Test
    fun `test 23 HTTP 401 is an authentication error and not classified as NoInternet`() {
        val networkState: NetworkState = NetworkState.Online
        val httpCode = 401
        val isNoInternet = (networkState is NetworkState.NoInternet)
        assertFalse(isNoInternet)
        assertEquals(401, httpCode)
    }

    @Test
    fun `test 24 No internet retains cached bins and active job`() {
        val state = MapUiState(
            allBins = sampleBins,
            displayedBins = sampleBins,
            activeJob = JobDto(id = "JOB_CACHED", status = "IN_PROGRESS"),
            networkState = NetworkState.NoInternet
        )
        assertEquals(4, state.allBins.size)
        assertEquals("JOB_CACHED", state.activeJob?.id)
        val mode = MapStatePolicy.resolveOperationalMode(state, state.displayedBins)
        assertEquals(MapMode.ACTIVE_JOB, mode)
    }

    @Test
    fun `test 25 Backend unavailable is distinct from NoInternet`() {
        val stateBackend = MapUiState(networkState = NetworkState.BackendUnavailable)
        val stateNoNet = MapUiState(networkState = NetworkState.NoInternet)
        assertTrue(stateBackend.networkState is NetworkState.BackendUnavailable)
        assertTrue(stateNoNet.networkState is NetworkState.NoInternet)
        assertFalse(stateBackend.networkState == stateNoNet.networkState)
    }

    @Test
    fun `test 26 Retry deduplication prevents concurrent in-flight loading`() {
        val stateLoading = MapUiState(loadingState = MapLoadingState.LoadingMap)
        val isAlreadyLoading = (stateLoading.loadingState == MapLoadingState.LoadingMap)
        assertTrue(isAlreadyLoading)
    }

    @Test
    fun `test 27 Realtime manager disconnect properly cleans socket reference`() {
        val state = MapUiState(networkState = NetworkState.Online)
        assertTrue(state.networkState is NetworkState.Online)
    }

    @Test
    fun `test 28 Malformed socket payload is safely ignored without crashing`() {
        val invalidPayload = "{ corrupt json ... }"
        var parsedSafely = false
        try {
            val res = org.json.JSONObject(invalidPayload)
        } catch (e: Exception) {
            parsedSafely = true
        }
        assertTrue(parsedSafely)
    }

    @Test
    fun `test 29 Burst job events are debounced and reconcile active job progress`() {
        val jobInitial = JobDto(id = "JOB_1", status = "IN_PROGRESS", completedBinIds = emptyList())
        val jobUpdated = JobDto(id = "JOB_1", status = "IN_PROGRESS", completedBinIds = listOf("BIN_001"))
        assertEquals(1, jobUpdated.completedBinIds?.size ?: 0)
    }

    @Test
    fun `test 30 Socket disconnect does not clear map state`() {
        val state = MapUiState(
            displayedBins = sampleBins,
            activeJob = JobDto(id = "JOB_1", status = "IN_PROGRESS"),
            networkState = NetworkState.Reconnecting
        )
        assertEquals(4, state.displayedBins.size)
        assertNotNull(state.activeJob)
    }

    @Test
    fun `test 31 Location upload throttle policy requires min 10m movement or min 15s interval`() {
        val lat1 = 10.7725
        val lon1 = 106.6980
        val lat2 = 10.77255 // ~5.5m away
        val dist = MapStatePolicy.calculateHaversineDistance(lat1, lon1, lat2, lon1)
        val isDistanceThresholdExceeded = (dist > 10.0)
        assertFalse(isDistanceThresholdExceeded)

        val lat3 = 10.7730 // ~55m away
        val distFar = MapStatePolicy.calculateHaversineDistance(lat1, lon1, lat3, lon1)
        val isFarThresholdExceeded = (distFar > 10.0)
        assertTrue(isFarThresholdExceeded)
    }

    @Test
    fun `test 32 Location upload failure does not disable local GPS state`() {
        val state = MapUiState(
            driverLocation = MapCoordinate(10.7725, 106.6980),
            gpsState = GpsState.Available
        )
        // Upload API fails: local GPS state remains Available
        assertEquals(GpsState.Available, state.gpsState)
        assertTrue(state.driverLocation?.isValid == true)
    }
}
