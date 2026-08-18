package com.example.app_smart_waste

import com.example.app_smart_waste.core.model.ActiveJobUiModel
import com.example.app_smart_waste.core.model.CollectBinRequest
import com.example.app_smart_waste.core.model.CollectBinResponse
import com.example.app_smart_waste.core.model.GeoCoordinate
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.JobHistoryUiModel
import com.example.app_smart_waste.core.model.JobItemDto
import com.example.app_smart_waste.core.model.JobOperation
import com.example.app_smart_waste.core.model.JobStatus
import com.example.app_smart_waste.core.model.JobStopStatus
import com.example.app_smart_waste.core.model.JobStopUiModel
import com.example.app_smart_waste.core.model.JobTransitionPolicy
import com.example.app_smart_waste.core.model.JobsHistoryFilter
import com.example.app_smart_waste.core.model.JobsNetworkState
import com.example.app_smart_waste.core.model.JobsScreenState
import com.example.app_smart_waste.core.model.JobsUiState
import com.example.app_smart_waste.core.model.RouteDataDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class JobsViewModelTest {

    private val gson = Gson()

    private val sampleBins = listOf(
        SmartBinDto(deviceId = "BIN_HCM_045", name = "Thùng 124 Nguyễn Thái Học", location = "124 Nguyễn Thái Học, Q.1", latitude = 10.7725, longitude = 106.6980, levelPercent = 72.0, lidState = "CLOSED", isOnline = true, collectionStatus = "IDLE"),
        SmartBinDto(deviceId = "BIN_HCM_089", name = "Thùng 45 Võ Văn Tần", location = "45 Võ Văn Tần, Q.3", latitude = 10.7740, longitude = 106.7030, levelPercent = 80.0, lidState = "CLOSED", isOnline = true, collectionStatus = "IDLE"),
        SmartBinDto(deviceId = "BIN_HCM_017", name = "Thùng 78 Cách Mạng Tháng 8", location = "78 Cách Mạng Tháng 8, Q.3", latitude = 10.7760, longitude = 106.7050, levelPercent = 90.0, lidState = "CLOSED", isOnline = true, collectionStatus = "IDLE"),
        SmartBinDto(deviceId = "BIN_HCM_021", name = "Thùng 210 Điện Biên Phủ", location = "210 Điện Biên Phủ, Q.3", latitude = 10.7780, longitude = 106.7070, levelPercent = 65.0, lidState = "CLOSED", isOnline = true, collectionStatus = "IDLE"),
        SmartBinDto(deviceId = "BIN_HCM_033", name = "Thùng 91 Nguyễn Đình Chiểu", location = "91 Nguyễn Đình Chiểu, Q.3", latitude = 10.7800, longitude = 106.7090, levelPercent = 88.0, lidState = "CLOSED", isOnline = false, collectionStatus = "IDLE")
    )

    // =========================================================================
    // 1. Case 01: Default Task List & Grouping Logic
    // =========================================================================

    @Test
    fun testJobStatusMappingAndGrouping() {
        val assignedJobDto = JobDto(
            id = "JOB_1723801234",
            status = "ASSIGNED",
            employeeId = "EMP_001",
            employeeName = "Nguyễn Thái Học",
            targetBinIds = listOf("BIN_HCM_045", "BIN_HCM_089", "BIN_HCM_017", "BIN_HCM_021", "BIN_HCM_033"),
            createdAt = "2025-05-26T08:15:00Z"
        )
        val inProgressJobDto = JobDto(
            id = "JOB_1723801122",
            status = "IN_PROGRESS",
            employeeId = "EMP_001",
            employeeName = "Nguyễn Thái Học",
            targetBinIds = listOf("BIN_HCM_045", "BIN_HCM_089", "BIN_HCM_017"),
            completedBinIds = listOf("BIN_HCM_045", "BIN_HCM_089"),
            startedAt = "2025-05-26T07:30:00Z"
        )

        assertEquals(JobStatus.ASSIGNED, JobStatus.fromString(assignedJobDto.status))
        assertEquals(JobStatus.IN_PROGRESS, JobStatus.fromString(inProgressJobDto.status))
    }

    // =========================================================================
    // 2. Case 02 & Case 03: Progress & Stop Progression
    // =========================================================================

    @Test
    fun testStopProgressionAndNextStopDetermination() {
        val binsMap = sampleBins.associateBy { it.deviceId }
        val targetIds = listOf("BIN_HCM_045", "BIN_HCM_089", "BIN_HCM_017", "BIN_HCM_021", "BIN_HCM_033")
        val completedIds = setOf("BIN_HCM_045", "BIN_HCM_089")

        val stops = targetIds.mapIndexed { index, binId ->
            val isDone = completedIds.contains(binId)
            JobStopUiModel(
                binId = binId,
                order = index + 1,
                status = if (isDone) JobStopStatus.COLLECTED else JobStopStatus.PENDING,
                bin = binsMap[binId]
            )
        }

        val nextStop = stops.firstOrNull { it.status == JobStopStatus.PENDING }
        assertNotNull(nextStop)
        assertEquals("BIN_HCM_017", nextStop?.binId)

        val totalStops = stops.size
        val doneCount = stops.count { it.status == JobStopStatus.COLLECTED }
        val percent = (doneCount * 100 / totalStops)

        assertEquals(5, totalStops)
        assertEquals(2, doneCount)
        assertEquals(40, percent)
    }

    // =========================================================================
    // 3. Case 04: Paused Job State
    // =========================================================================

    @Test
    fun testPausedJobState() {
        val pausedJob = JobDto(
            id = "JOB_1723801122",
            status = "PAUSED",
            pauseReason = "Kẹt xe đường Nam Kỳ Khởi Nghĩa",
            pausedAt = "2025-05-26T09:35:00Z"
        )

        assertEquals(JobStatus.PAUSED, JobStatus.fromString(pausedJob.status))
        assertEquals("Kẹt xe đường Nam Kỳ Khởi Nghĩa", pausedJob.pauseReason)
        assertTrue(JobTransitionPolicy.canResume(JobStatus.PAUSED))
        assertFalse(JobTransitionPolicy.canPause(JobStatus.PAUSED))
    }

    // =========================================================================
    // 4. Case 05: Collect Bin Payload & Idempotency
    // =========================================================================

    @Test
    fun testCollectBinPayloadSerialization() {
        val request = CollectBinRequest(
            binId = "BIN_HCM_017",
            status = "COLLECTED",
            note = "Đã thu gom thành công",
            photoUrl = "https://example.com/evidence.jpg"
        )

        val json = gson.toJson(request)
        assertTrue(json.contains("BIN_HCM_017"))
        assertTrue(json.contains("COLLECTED"))
        assertTrue(json.contains("Đã thu gom thành công"))

        val responseJson = """{"ok":true,"allDone":false,"idempotent":false,"job":{"id":"JOB_01","status":"IN_PROGRESS"}}"""
        val response = gson.fromJson(responseJson, CollectBinResponse::class.java)

        assertTrue(response.ok)
        assertFalse(response.allDone)
        assertFalse(response.idempotent)
        assertEquals("JOB_01", response.job?.id)
    }

    // =========================================================================
    // 5. Case 06: History Filtering & Sorting
    // =========================================================================

    @Test
    fun testHistoryFiltering() {
        val rawJob1 = JobDto(id = "JOB_1723800987", status = "COMPLETED", createdAt = "2025-05-25T07:10:00Z", completedAt = "2025-05-25T08:05:00Z")
        val rawJob2 = JobDto(id = "JOB_1723800765", status = "CANCELLED", pauseReason = "Xe hỏng", createdAt = "2025-05-24T08:30:00Z")
        val rawJob3 = JobDto(id = "JOB_1723800621", status = "EXPIRED", createdAt = "2025-05-23T08:00:00Z")

        val historyList = listOf(
            JobHistoryUiModel(id = rawJob1.id, displayCode = "#JOB_1723800987", status = JobStatus.COMPLETED, statusBadgeText = "Hoàn thành", dateStr = "25/05/2025", timeRangeStr = "07:10 - 08:05", totalStops = 6, distanceKm = 5.6, durationMinutes = 55, routeOrReason = "Tuyến: Quận 2 → Quận 9", rawJob = rawJob1),
            JobHistoryUiModel(id = rawJob2.id, displayCode = "#JOB_1723800765", status = JobStatus.CANCELLED, statusBadgeText = "Đã hủy", dateStr = "24/05/2025", timeRangeStr = "08:30", totalStops = 4, distanceKm = 3.1, durationMinutes = null, routeOrReason = "Lý do: Xe hỏng", rawJob = rawJob2),
            JobHistoryUiModel(id = rawJob3.id, displayCode = "#JOB_1723800621", status = JobStatus.EXPIRED, statusBadgeText = "Hết hạn", dateStr = "23/05/2025", timeRangeStr = "08:00", totalStops = 5, distanceKm = 4.2, durationMinutes = null, routeOrReason = "Lý do: Hết hạn", rawJob = rawJob3)
        )

        // Filter 1: Only completed
        val filterCompletedOnly = JobsHistoryFilter(showCompleted = true, showCancelled = false, showExpired = false)
        val filtered1 = historyList.filter { item ->
            when (item.status) {
                JobStatus.COMPLETED -> filterCompletedOnly.showCompleted
                JobStatus.CANCELLED -> filterCompletedOnly.showCancelled
                JobStatus.EXPIRED -> filterCompletedOnly.showExpired
                else -> true
            }
        }
        assertEquals(1, filtered1.size)
        assertEquals("JOB_1723800987", filtered1[0].id)

        // Filter 2: Completed + Cancelled
        val filterCompAndCanc = JobsHistoryFilter(showCompleted = true, showCancelled = true, showExpired = false)
        val filtered2 = historyList.filter { item ->
            when (item.status) {
                JobStatus.COMPLETED -> filterCompAndCanc.showCompleted
                JobStatus.CANCELLED -> filterCompAndCanc.showCancelled
                JobStatus.EXPIRED -> filterCompAndCanc.showExpired
                else -> true
            }
        }
        assertEquals(2, filtered2.size)
    }

    // =========================================================================
    // 6. Case 07 & Case 08: Screen States
    // =========================================================================

    @Test
    fun testJobsScreenStateHierarchy() {
        val initialLoading: JobsScreenState = JobsScreenState.InitialLoading
        val noActive: JobsScreenState = JobsScreenState.NoActiveJob
        val error: JobsScreenState = JobsScreenState.Error("Network error", canRetry = true)

        assertTrue(initialLoading is JobsScreenState.InitialLoading)
        assertTrue(noActive is JobsScreenState.NoActiveJob)
        assertTrue(error is JobsScreenState.Error)
        assertEquals("Network error", (error as JobsScreenState.Error).message)
    }

    // =========================================================================
    // 7. Transition Policy Machine
    // =========================================================================

    @Test
    fun testJobTransitions() {
        assertTrue(JobTransitionPolicy.canAccept(JobStatus.ASSIGNED))
        assertFalse(JobTransitionPolicy.canAccept(JobStatus.IN_PROGRESS))

        assertTrue(JobTransitionPolicy.canStart(JobStatus.ACCEPTED))
        assertFalse(JobTransitionPolicy.canStart(JobStatus.ASSIGNED))

        assertTrue(JobTransitionPolicy.canPause(JobStatus.IN_PROGRESS))
        assertFalse(JobTransitionPolicy.canPause(JobStatus.PAUSED))

        assertTrue(JobTransitionPolicy.canResume(JobStatus.PAUSED))
        assertFalse(JobTransitionPolicy.canResume(JobStatus.IN_PROGRESS))
    }

    // =========================================================================
    // 8. JSON Deserialization Resilience Tests
    // =========================================================================

    @Test
    fun testJobDtoDeserializationWithStringAndIntOptimizedOrder() {
        val jsonWithStringOrder = """
            {
                "id": "JOB_1786971461509",
                "status": "ACCEPTED",
                "route_data": {
                    "distanceMeters": 4800,
                    "durationSeconds": 1560,
                    "optimizedOrder": ["BIN_HCM_02", "BIN_HCM_04", "BIN_HCM_07"]
                }
            }
        """.trimIndent()

        val jobWithString = gson.fromJson(jsonWithStringOrder, JobDto::class.java)
        assertNotNull(jobWithString)
        assertEquals("JOB_1786971461509", jobWithString.id)
        assertEquals("ACCEPTED", jobWithString.status)
        assertNotNull(jobWithString.routeData?.optimizedOrder)
        assertEquals(3, jobWithString.routeData?.optimizedOrder?.size)

        val jsonWithIntOrder = """
            {
                "id": "JOB_1786971461510",
                "status": "IN_PROGRESS",
                "route_data": {
                    "distanceMeters": 3500,
                    "durationSeconds": 1200,
                    "optimizedOrder": [0, 1, 2]
                }
            }
        """.trimIndent()

        val jobWithInt = gson.fromJson(jsonWithIntOrder, JobDto::class.java)
        assertNotNull(jobWithInt)
        assertEquals("JOB_1786971461510", jobWithInt.id)
        assertEquals(3, jobWithInt.routeData?.optimizedOrder?.size)
    }
}

