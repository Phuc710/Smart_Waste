package com.example.app_smart_waste.data.repository

import android.content.Context
import com.example.app_smart_waste.core.model.IncidentRequest
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BinsRepository(private val context: Context) {
    private val api get() = RetrofitClient.getInstance(context).getApi()

    suspend fun getBins(): Result<List<SmartBinDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBins()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Lỗi tải danh sách thùng rác"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBinDetails(binId: String): Result<SmartBinDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBinDetails(binId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Lỗi tải thông tin thùng rác"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBinDetail(binId: String): Result<SmartBinDto> = getBinDetails(binId)

    suspend fun sendCommand(binId: String, action: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = api.sendBinCommand(binId, mapOf("action" to action))
            if (response.isSuccessful) Result.success(true)
            else Result.failure(Exception("Lỗi gửi lệnh nắp (HTTP ${response.code()})"))
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

    suspend fun getMyIncidents(): Result<List<com.example.app_smart_waste.core.model.IncidentReportDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMyIncidents()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.reports)
            } else {
                Result.failure(Exception("Lỗi tải lịch sử sự cố (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
