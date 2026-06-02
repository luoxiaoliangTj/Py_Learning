package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class LogEntry(
    val date: String,
    val filename: String,
    val size: Long = 0
)

data class LogUiState(
    val isLoading: Boolean = false,
    val logs: List<LogEntry> = emptyList(),
    val selectedLog: String = "",
    val logContent: String = "",
    val error: String? = null,
    val deleteSuccess: Boolean = false
)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    fun loadLogs() {
        _uiState.value = LogUiState(
            error = "日志管理功能需要后端支持，当前不可用"
        )
    }

    fun loadLog(date: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            selectedLog = date,
            error = "日志查看功能需要后端支持，当前不可用"
        )
    }

    fun deleteLog(date: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = "日志删除功能需要后端支持，当前不可用"
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedLog = "", logContent = "", deleteSuccess = false, error = null)
    }
}
