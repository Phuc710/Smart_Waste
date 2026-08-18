package com.example.app_smart_waste.data.repository

import android.content.Context
import com.example.app_smart_waste.core.model.IncidentRequest
import com.example.app_smart_waste.core.model.IncidentUploadRequest
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class BinsRepository(private val context: Context) {
    private val api get() = RetrofitClient.getInstance(context).getApi()

    companion object {
        @Volatile
        private var cachedBinsList: List<SmartBinDto> = emptyList()

        fun getCachedBins(): List<SmartBinDto> = cachedBinsList
    }

    suspend fun getBins(): Result<List<SmartBinDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBins()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                cachedBinsList = body
                Result.success(body)
            } else if (cachedBinsList.isNotEmpty()) {
                Result.success(cachedBinsList)
            } else {
                Result.failure(Exception("Lỗi tải danh sách thùng rác (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            if (cachedBinsList.isNotEmpty()) {
                Result.success(cachedBinsList)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getBinDetails(binId: String): Result<SmartBinDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBinDetails(binId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("Lỗi tải thông tin thùng rác (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBinDetail(binId: String): Result<SmartBinDto> = getBinDetails(binId)

    suspend fun sendCommand(binId: String, action: String): com.example.app_smart_waste.core.model.BinCommandResult = withContext(Dispatchers.IO) {
        try {
            val response = api.sendBinCommand(binId, com.example.app_smart_waste.core.model.BinCommandRequest(action = action))
            val body = response.body()
            when {
                response.isSuccessful && body?.ok == true -> {
                    com.example.app_smart_waste.core.model.BinCommandResult.Executed(
                        bin = body.bin,
                        message = body.message ?: "Thiết bị #$binId đã thực thi thành công."
                    )
                }
                response.code() == 504 -> {
                    com.example.app_smart_waste.core.model.BinCommandResult.Timeout("Thiết bị #$binId chưa phản hồi (Gateway Timeout 504). Vui lòng kiểm tra kết nối và thử lại.")
                }
                response.code() == 401 || response.code() == 403 -> {
                    com.example.app_smart_waste.core.model.BinCommandResult.Unauthorized("Phiên làm việc hết hạn hoặc không có quyền gửi lệnh.")
                }
                response.code() == 503 -> {
                    com.example.app_smart_waste.core.model.BinCommandResult.DeviceOffline("Thiết bị #$binId hiện đang ngoại tuyến (Offline), không thể thực thi lệnh.")
                }
                else -> {
                    val errMsg = body?.message ?: body?.error ?: "Lỗi gửi lệnh nắp (HTTP ${response.code()})"
                    com.example.app_smart_waste.core.model.BinCommandResult.ServerError(response.code(), errMsg)
                }
            }
        } catch (e: java.io.IOException) {
            com.example.app_smart_waste.core.model.BinCommandResult.NetworkError("Lỗi kết nối mạng: ${e.localizedMessage ?: "Không thể kết nối máy chủ"}")
        } catch (e: Exception) {
            com.example.app_smart_waste.core.model.BinCommandResult.ServerError(null, e.localizedMessage ?: "Lỗi không xác định")
        }
    }

    suspend fun openLid(binId: String): com.example.app_smart_waste.core.model.BinCommandResult = sendCommand(binId, "OPEN_LID")

    suspend fun calculateRoute(points: List<Pair<Double, Double>>): Result<com.example.app_smart_waste.core.model.RouteResponse?> = withContext(Dispatchers.IO) {
        try {
            val coords = points.map { listOf(it.second, it.first) } // [lng, lat]
            val res = api.calculateRoute(com.example.app_smart_waste.core.model.RouteRequest(coords))
            val body = res.body()
            if (res.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("Lỗi tính toán đường đi (HTTP ${res.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLocation(payload: com.example.app_smart_waste.core.model.LocationPayload): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val res = api.updateLocation(payload)
            if (res.isSuccessful && res.body()?.ok == true) {
                Result.success(true)
            } else {
                Result.failure(Exception("Lỗi cập nhật vị trí (HTTP ${res.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class IncidentRepository(private val context: Context) {
    private val api get() = RetrofitClient.getInstance(context).getApi()

    suspend fun reportIncident(binId: String, issueType: String, description: String, photoUrl: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = IncidentRequest(
                deviceId = binId,
                issueType = issueType,
                description = description,
                photoUrl = photoUrl
            )
            val response = api.reportIncident(request)
            if (response.isSuccessful) Result.success(true)
            else Result.failure(Exception("Lỗi gửi báo cáo sự cố (HTTP ${response.code()})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportIncidentWithPhoto(
        binId: String,
        issueType: String,
        description: String,
        jpegBytes: ByteArray
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (jpegBytes.isEmpty() || jpegBytes.size > 5 * 1024 * 1024) {
                return@withContext Result.failure(Exception("Ảnh JPEG phải nhỏ hơn 5 MB."))
            }
            val prepared = api.prepareIncidentUpload(IncidentUploadRequest(binId, issueType, description))
            val upload = prepared.body()?.upload
            if (!prepared.isSuccessful || upload == null) {
                return@withContext Result.failure(Exception("Không thể tạo phiên tải ảnh (HTTP ${prepared.code()})"))
            }

            val uploadRequest = Request.Builder()
                .url(upload.uploadUrl)
                .put(jpegBytes.toRequestBody("image/jpeg".toMediaType()))
                .build()
            OkHttpClient().newCall(uploadRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Không thể tải ảnh lên (HTTP ${response.code})"))
                }
            }

            val completed = api.completeIncidentUpload(upload.uploadId)
            if (completed.isSuccessful) Result.success(true)
            else Result.failure(Exception("Không thể xác nhận ảnh sự cố (HTTP ${completed.code()})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyIncidents(): Result<List<com.example.app_smart_waste.core.model.IncidentReportDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMyIncidents()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body.reports)
            } else {
                Result.failure(Exception("Lỗi tải lịch sử sự cố (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
