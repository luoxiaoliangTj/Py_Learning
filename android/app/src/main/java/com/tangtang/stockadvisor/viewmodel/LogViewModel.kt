package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getLogList().collect { result ->
                result.onSuccess { logList ->
                    val entries = logList.map { (date, info) ->
                        LogEntry(
                            date = date,
                            filename = info["filename"] as? String ?: "$date.log",
                            size = (info["size"] as? Number)?.toLong() ?: 0L
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logs = entries
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载日志列表失败"
                    )
                }
            }
        }
    }

    fun loadLog(date: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, selectedLog = date)
            repository.getLog(date).collect { result ->
                result.onSuccess { content ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logContent = content
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载日志内容失败"
                    )
                }
            }
        }
    }

    fun deleteLog(date: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, deleteSuccess = false)
            repository.deleteLog(date).collect { result ->
                result.onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        deleteSuccess = true,
                        selectedLog = "",
                        logContent = ""
                    )
                    loadLogs()
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "删除日志失败"
                    )
                }
            }
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedLog = "", logContent = "", deleteSuccess = false, error = null)
    }
}
