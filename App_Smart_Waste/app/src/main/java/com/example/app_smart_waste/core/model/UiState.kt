package com.example.app_smart_waste.core.model

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String, val isUnauthorized: Boolean = false) : UiState<Nothing>
    data object Empty : UiState<Nothing>
}
