package com.example.app_smart_waste.ui.route

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.UiState
import com.example.app_smart_waste.data.repository.JobsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RouteDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val jobsRepo = JobsRepository(app)

    private val _jobDetailState = MutableStateFlow<UiState<JobDto>>(UiState.Loading)
    val jobDetailState: StateFlow<UiState<JobDto>> = _jobDetailState

    fun loadRouteDetail(jobId: String?) {
        viewModelScope.launch {
            _jobDetailState.value = UiState.Loading
            val res = if (jobId != null) {
                jobsRepo.getJobDetail(jobId)
            } else {
                jobsRepo.getActiveJob()
            }
            res.fold(
                onSuccess = { job ->
                    if (job != null) {
                        _jobDetailState.value = UiState.Success(job)
                    } else {
                        _jobDetailState.value = UiState.Error("Không tìm thấy tuyến thu gom")
                    }
                },
                onFailure = { err ->
                    _jobDetailState.value = UiState.Error(err.message ?: "Lỗi tải tuyến thu gom")
                }
            )
        }
    }
}
