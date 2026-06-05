package com.tangtang.aico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.aico.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolInfo(
    val name: String,
    val description: String,
    val category: String = ""
)

data class ToolsUiState(
    val isLoading: Boolean = false,
    val tools: List<ToolInfo> = emptyList(),
    val selectedTool: String = "",
    val toolResult: String = "",
    val downloadStatus: String = "",
    val error: String? = null
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    fun loadTools() {
        val tools = listOf(
            ToolInfo(name = "持仓导入", description = "导入股票持仓数据，支持CSV格式文件导入", category = "数据管理"),
            ToolInfo(name = "数据下载", description = "下载股票历史行情数据，支持指定年份和数据源", category = "数据管理"),
            ToolInfo(name = "数据检查", description = "检查股票数据的完整性和最新状态", category = "数据管理")
        )
        _uiState.value = ToolsUiState(tools = tools)
    }

    fun runTool(toolName: String, params: Map<String, Any> = emptyMap()) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                selectedTool = toolName,
                toolResult = ""
            )
            repository.runTool(toolName, params).collect { result ->
                result.onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        toolResult = response.toString()
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "执行工具失败"
                    )
                }
            }
        }
    }

    fun downloadData(symbol: String, years: Int? = null, source: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, downloadStatus = "下载中...")
            val result = repository.downloadData(symbol, years, source)
            result.onSuccess { response ->
                val success = response["success"] as? Boolean ?: false
                val message = response["message"] as? String ?: ""
                val recordCount = response["record_count"] as? Int ?: 0
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    downloadStatus = if (success) "下载完成: ${recordCount}条记录 - $message" else "下载失败: $message"
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "下载失败",
                    downloadStatus = "失败"
                )
            }
        }
    }

    fun checkDownloadStatus(symbol: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getDownloadStatus(symbol).collect { result ->
                result.onSuccess { status ->
                    val exists = status["exists"] as? Boolean ?: false
                    val message = status["message"] as? String ?: ""
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        downloadStatus = if (exists) "数据已存在: $message" else message
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "检查状态失败"
                    )
                }
            }
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(toolResult = "", downloadStatus = "", error = null)
    }
}
