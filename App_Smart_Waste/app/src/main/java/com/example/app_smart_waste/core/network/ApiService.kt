package com.example.app_smart_waste.core.network

import com.example.app_smart_waste.core.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // 1. Auth Endpoints
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/auth/me")
    suspend fun getMe(): Response<MeResponse>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ActionResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<ActionResponse>

    // 2. Mobile Jobs Endpoints
    @GET("api/mobile/jobs/active")
    suspend fun getActiveJob(): Response<ActiveJobResponse>

    @POST("api/mobile/jobs/self-pick")
    suspend fun selfPickJob(@Body request: SelfPickRequest): Response<ActiveJobResponse>

    @POST("api/mobile/jobs/{id}/accept")
    suspend fun acceptJob(@Path("id") jobId: String): Response<ActiveJobResponse>

    @POST("api/mobile/jobs/{id}/reject")
    suspend fun rejectJob(@Path("id") jobId: String): Response<ActiveJobResponse>

    @POST("api/mobile/jobs/{id}/start")
    suspend fun startJob(@Path("id") jobId: String): Response<ActiveJobResponse>

    @POST("api/mobile/jobs/{id}/pause")
    suspend fun pauseJob(@Path("id") jobId: String, @Body body: Map<String, String>): Response<ActiveJobResponse>

    @POST("api/mobile/jobs/{id}/resume")
    suspend fun resumeJob(@Path("id") jobId: String): Response<ActiveJobResponse>

    @POST("api/mobile/jobs/{id}/collect-bin")
    suspend fun collectBin(@Path("id") jobId: String, @Body request: CollectBinRequest): Response<CollectBinResponse>

    // 3. Smart Bins & Status
    @GET("api/bins")
    suspend fun getBins(): Response<List<SmartBinDto>>

    @GET("api/bins/{id}")
    suspend fun getBinDetails(@Path("id") binId: String): Response<SmartBinDto>

    @POST("api/bins/{id}/command")
    suspend fun sendBinCommand(@Path("id") binId: String, @Body body: Map<String, String>): Response<ActionResponse>

    // 4. GPS Location Broadcast
    @POST("api/location")
    suspend fun updateLocation(@Body payload: LocationPayload): Response<ActionResponse>

    // 5. Incidents
    @POST("api/incidents")
    suspend fun reportIncident(@Body request: IncidentRequest): Response<ActionResponse>

    @GET("api/incidents/my")
    suspend fun getMyIncidents(): Response<IncidentsResponse>

    // 6. History
    @GET("api/dispatch/history")
    suspend fun getHistory(@Query("limit") limit: Int = 100): Response<List<JobDto>>

    // 7. System Settings & Config (Admin Settings from CSDL)
    @GET("api/settings")
    suspend fun getSystemSettings(): Response<SystemSettingsResponse>
}

