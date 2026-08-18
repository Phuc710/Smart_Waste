package com.example.app_smart_waste.data.repository

import android.content.Context
import com.example.app_smart_waste.core.model.*
import com.example.app_smart_waste.core.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JobsRepository(private val context: Context) {

    private val api get() = RetrofitClient.getInstance(context).getApi()

    suspend fun getMobileHome(): Result<MobileHomeResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMobileHome()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("Lỗi tải trang chủ (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getActiveJob(): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val response = api.getActiveJob()
            if (response.isSuccessful) {
                Result.success(response.body()?.job)
            } else {
                Result.failure(Exception("Lỗi tải nhiệm vụ (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getJobDetail(jobId: String): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val cleanId = jobId.removePrefix("#")

            // 1. Kiểm tra Active Job trước
            val activeRes = api.getActiveJob()
            if (activeRes.isSuccessful) {
                val activeJob = activeRes.body()?.job
                if (activeJob != null && (activeJob.id == jobId || activeJob.id.removePrefix("#") == cleanId)) {
                    return@withContext Result.success(activeJob)
                }
            }

            // 2. Tra cứu trong History
            val historyRes = api.getHistory(100)
            if (historyRes.isSuccessful) {
                val historyList = historyRes.body().orEmpty()
                val found = historyList.find { it.id == jobId || it.id.removePrefix("#") == cleanId }
                if (found != null) {
                    return@withContext Result.success(found)
                }
                return@withContext Result.success(null)
            }

            // 3. Nếu không tìm thấy trong active job và history API báo lỗi
            if (!historyRes.isSuccessful) {
                return@withContext Result.failure(Exception("Lỗi tải thông tin nhiệm vụ (HTTP ${historyRes.code()})"))
            }

            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun selfPickJob(binIds: List<String>): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val response = api.selfPickJob(SelfPickRequest(binIds))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body.job)
            } else {
                Result.failure(Exception("Không thể nhận danh sách thùng rác (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptJob(jobId: String): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val response = api.acceptJob(jobId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body.job)
            } else {
                Result.failure(Exception("Không thể tiếp nhận nhiệm vụ (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectJob(jobId: String, reason: String? = null): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val payload = if (!reason.isNullOrBlank()) mapOf("reason" to reason) else emptyMap()
            val response = api.rejectJob(jobId, payload)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body.job)
            } else {
                Result.failure(Exception("Không thể từ chối nhiệm vụ (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startJob(jobId: String): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val response = api.startJob(jobId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body.job)
            } else {
                Result.failure(Exception("Không thể bắt đầu tuyến thu gom (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pauseJob(jobId: String, reason: String = "Tạm dừng"): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val response = api.pauseJob(jobId, mapOf("reason" to reason))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body.job)
            } else {
                Result.failure(Exception("Không thể tạm dừng tuyến (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resumeJob(jobId: String): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val response = api.resumeJob(jobId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body.job)
            } else {
                Result.failure(Exception("Không thể tiếp tục tuyến (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun collectBin(
        jobId: String,
        binId: String,
        note: String? = null,
        photoUrl: String? = null
    ): Result<CollectBinResponse> = withContext(Dispatchers.IO) {
        try {
            val req = CollectBinRequest(binId = binId, status = "COLLECTED", note = note, photoUrl = photoUrl)
            val response = api.collectBin(jobId, req)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("Không thể ghi nhận thu gom (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGps(
        latitude: Double,
        longitude: Double,
        speed: Double? = null,
        heading: Double? = null,
        accuracy: Double? = null,
        timestamp: String? = null,
        jobId: String? = null,
        trackingSessionId: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = LocationPayload(
                latitude = latitude,
                longitude = longitude,
                speed = speed,
                heading = heading,
                accuracy = accuracy,
                timestamp = timestamp,
                jobId = jobId,
                trackingSessionId = trackingSessionId
            )
            val response = api.updateLocation(payload)
            if (response.isSuccessful) Result.success(true)
            else Result.failure(Exception("Không thể cập nhật GPS (HTTP ${response.code()})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGpsBatch(
        locations: List<BatchLocationItem>,
        trackingSessionId: String? = null,
        jobId: String? = null
    ): Result<BatchLocationResponse> = withContext(Dispatchers.IO) {
        try {
            val payload = BatchLocationPayload(
                trackingSessionId = trackingSessionId,
                jobId = jobId,
                locations = locations
            )
            val response = api.updateLocationBatch(payload)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("Không thể đồng bộ batch GPS (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistory(limit: Int = 100): Result<List<JobDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getHistory(limit)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("Lỗi tải lịch sử thu gom (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSystemSettings(): Result<SystemSettingsDto?> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSystemSettings()
            if (response.isSuccessful) {
                Result.success(response.body()?.settings)
            } else {
                Result.failure(Exception("Lỗi tải cấu hình hệ thống (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
