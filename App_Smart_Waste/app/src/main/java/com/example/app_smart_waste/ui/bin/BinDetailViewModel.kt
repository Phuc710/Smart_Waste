package com.example.app_smart_waste.ui.bin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.model.UiState
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.IncidentRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BinDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val binsRepo = BinsRepository(app)
    private val jobsRepo = JobsRepository(app)
    private val incidentRepo = IncidentRepository(app)

    private val _binDetailState = MutableStateFlow<UiState<SmartBinDto>>(UiState.Loading)
    val binDetailState: StateFlow<UiState<SmartBinDto>> = _binDetailState

    private val _actionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val actionState: StateFlow<UiState<String>> = _actionState

    fun loadBinDetail(binId: String) {
        viewModelScope.launch {
            _binDetailState.value = UiState.Loading
            val res = binsRepo.getBinDetail(binId)
            res.fold(
                onSuccess = { bin ->
                    _binDetailState.value = UiState.Success(bin)
                },
                onFailure = { err ->
                    _binDetailState.value = UiState.Error(err.message ?: "Lỗi tải chi tiết thùng rác")
                }
            )
        }
    }

    fun collectBin(jobId: String, binId: String) {
        viewModelScope.launch {
            _actionState.value = UiState.Loading
            val res = jobsRepo.collectBin(jobId, binId, "Đã thu gom thành công")
            res.fold(
                onSuccess = {
                    _actionState.value = UiState.Success("Đã xác nhận thu gom thùng rác thành công!")
                },
                onFailure = { err ->
                    _actionState.value = UiState.Error(err.message ?: "Lỗi xác nhận thu gom")
                }
            )
        }
    }

    fun reportIncident(binId: String, description: String) {
        viewModelScope.launch {
            _actionState.value = UiState.Loading
            val res = incidentRepo.reportIncident(binId, "SENSOR_MALFUNCTION", description, "HIGH")
            res.fold(
                onSuccess = {
                    _actionState.value = UiState.Success("Đã gửi báo cáo sự cố thành công!")
                },
                onFailure = { err ->
                    _actionState.value = UiState.Error(err.message ?: "Lỗi gửi báo cáo sự cố")
                }
            )
        }
    }
}
