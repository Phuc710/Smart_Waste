package com.example.app_smart_waste.data.repository

import android.content.Context
import com.example.app_smart_waste.core.model.*
import com.example.app_smart_waste.core.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JobsRepository(private val context: Context) {

    private val api get() = RetrofitClient.getInstance(context).getApi()

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
            val activeRes = api.getActiveJob()
            if (activeRes.isSuccessful && activeRes.body()?.job?.id == jobId) {
                return@withContext Result.success(activeRes.body()?.job)
            }
            val historyRes = api.getHistory(100)
            if (historyRes.isSuccessful && historyRes.body() != null) {
                val found = historyRes.body()?.find { it.id == jobId }
                if (found != null) return@withContext Result.success(found)
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun selfPickJob(binIds: List<String>): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val response = api.selfPickJob(SelfPickRequest(binIds))
            if (response.isSuccessful) {
                Result.success(response.body()?.job)
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
            if (response.isSuccessful) {
                Result.success(response.body()?.job)
            } else {
                Result.failure(Exception("Không thể tiếp nhận nhiệm vụ (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectJob(jobId: String): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val response = api.rejectJob(jobId)
            if (response.isSuccessful) {
                Result.success(response.body()?.job)
            } else {
                Result.failure(Exception("Không thể hủy nhiệm vụ (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startJob(jobId: String): Result<JobDto?> = withContext(Dispatchers.IO) {
        try {
            val response = api.startJob(jobId)
            if (response.isSuccessful) {
                Result.success(response.body()?.job)
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
            if (response.isSuccessful) {
                Result.success(response.body()?.job)
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
            if (response.isSuccessful) {
                Result.success(response.body()?.job)
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
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
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
        accuracy: Double? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = LocationPayload(latitude, longitude, speed, heading, accuracy)
            val response = api.updateLocation(payload)
            if (response.isSuccessful) Result.success(true)
            else Result.failure(Exception("Không thể cập nhật GPS (HTTP ${response.code()})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistory(limit: Int = 100): Result<List<JobDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getHistory(limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
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
