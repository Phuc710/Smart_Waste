package com.example.app_smart_waste.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.model.LoginUiState
import com.example.app_smart_waste.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = AuthRepository(application)

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    fun login(username: String, password: String) {
        val trimmedUser = username.trim()
        val trimmedPass = password.trim()

        if (trimmedUser.isBlank() || trimmedPass.isBlank()) {
            _loginState.value = LoginUiState.Error("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.")
            return
        }

        _loginState.value = LoginUiState.Loading
        viewModelScope.launch {
            val result = authRepo.login(trimmedUser, trimmedPass)
            if (result.isSuccess) {
                val user = result.getOrNull()!!
                if (user.isActive == false) {
                    authRepo.logout()
                    _loginState.value = LoginUiState.Error(
                        message = "Tài khoản đã bị khóa bởi Quản trị viên.",
                        isInactive = true
                    )
                } else {
                    _loginState.value = LoginUiState.Success(user)
                }
            } else {
                val exception = result.exceptionOrNull()
                val rawMsg = exception?.message ?: ""
                val userMsg = when {
                    rawMsg.contains("Sai tên đăng nhập", ignoreCase = true) || rawMsg.contains("401") ->
                        "Tên đăng nhập hoặc mật khẩu không chính xác."
                    rawMsg.contains("Không thể kết nối", ignoreCase = true) || rawMsg.contains("ConnectException", ignoreCase = true) || rawMsg.contains("SocketTimeout", ignoreCase = true) ->
                        "Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng."
                    else ->
                        rawMsg.ifBlank { "Tên đăng nhập hoặc mật khẩu không chính xác." }
                }
                _loginState.value = LoginUiState.Error(userMsg)
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginUiState.Idle
    }
}
