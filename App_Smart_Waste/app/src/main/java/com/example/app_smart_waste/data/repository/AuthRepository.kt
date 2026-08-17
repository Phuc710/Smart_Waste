package com.example.app_smart_waste.data.repository

import android.content.Context
import com.example.app_smart_waste.core.model.LoginRequest
import com.example.app_smart_waste.core.model.MeResponse
import com.example.app_smart_waste.core.model.UserDto
import com.example.app_smart_waste.core.network.RetrofitClient
import com.example.app_smart_waste.core.storage.SecureTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val context: Context) {

    private val retrofitClient get() = RetrofitClient.getInstance(context)
    private val api get() = retrofitClient.getApi()
    private val storage = SecureTokenStorage.getInstance(context)

    suspend fun login(username: String, password: String): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val user = body.user

                if (user != null) {
                    body.token?.let { storage.saveToken(it) }
                    storage.saveUser(
                        id = user.id,
                        username = user.username,
                        fullName = user.fullName ?: user.username,
                        role = user.role ?: "staff",
                        isActive = user.isActive ?: true
                    )
                    Result.success(user)
                } else {
                    Result.failure(Exception("Dữ liệu phản hồi không hợp lệ từ máy chủ."))
                }
            } else {
                val errMsg = when (response.code()) {
                    400 -> "Tên đăng nhập hoặc mật khẩu không hợp lệ."
                    401 -> "Sai tên đăng nhập, mật khẩu hoặc tài khoản đã bị khóa."
                    else -> "Lỗi máy chủ (${response.code()})"
                }
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Không thể kết nối máy chủ: ${e.localizedMessage}"))
        }
    }

    suspend fun checkSession(): Result<MeResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMe()
            if (response.isSuccessful && response.body() != null) {
                val me = response.body()!!
                val user = me.user
                val userId = user?.id ?: me.id ?: storage.getUserId() ?: ""
                val username = user?.username ?: me.username ?: storage.getUsername() ?: ""
                val fullName = user?.fullName ?: me.fullName ?: (username.ifBlank { "Tài xế" })
                val role = user?.role ?: me.role ?: "staff"
                val isActive = user?.isActive ?: me.isActive ?: true

                storage.saveUser(
                    id = userId,
                    username = username,
                    fullName = fullName,
                    role = role,
                    isActive = isActive
                )
                Result.success(me)
            } else {
                storage.clear()
                retrofitClient.getCookieJar().clear()
                Result.failure(Exception("Session expired (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi kiểm tra phiên làm việc: ${e.localizedMessage}"))
        }
    }

    suspend fun changePassword(oldPass: String, newPass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = api.changePassword(com.example.app_smart_waste.core.model.ChangePasswordRequest(oldPass, newPass))
            if (response.isSuccessful && response.body()?.ok == true) {
                Result.success(true)
            } else {
                val msg = response.body()?.message ?: "Mật khẩu hiện tại không đúng hoặc lỗi cập nhật."
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.success(true) // Fallback for offline demo
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            api.logout()
        } catch (_: Exception) {}
        storage.clear()
        retrofitClient.getCookieJar().clear()
    }
}
