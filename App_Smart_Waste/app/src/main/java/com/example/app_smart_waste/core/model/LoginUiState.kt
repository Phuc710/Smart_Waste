package com.example.app_smart_waste.core.model

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val user: UserDto) : LoginUiState
    data class Error(val message: String, val isInactive: Boolean = false) : LoginUiState
}
