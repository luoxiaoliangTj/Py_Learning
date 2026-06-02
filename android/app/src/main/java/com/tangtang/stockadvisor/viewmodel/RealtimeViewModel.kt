package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RealtimeUiState(
    val isLoading: Boolean = false,
    val symbol: String = "",
    val stockName: String = "",
    val currentPrice: Double = 0.0,
    val predictedPrice: Double = 0.0,
    val confidence: Double = 0.0,
    val changePercent: Double = 0.0,
    val volume: Long = 0,
    val signals: List<SignalInfo> = emptyList(),
    val updateTime: String = "",
    val isAutoRefresh: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class RealtimeViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RealtimeUiState())
    val uiState: StateFlow<RealtimeUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    fun startRealtime(symbol: String) {
        _uiState.value = RealtimeUiState(symbol = symbol, isAutoRefresh = true)
        loadRealtimeData(symbol)
        startAutoRefresh(symbol)
    }

    fun loadRealtimeData(symbol: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getRealtimeData(symbol).collect { result ->
                result.onSuccess { data ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        symbol = symbol,
                        stockName = data["name"] as? String ?: "",
                        currentPrice = (data["current_price"] as? Number)?.toDouble() ?: 0.0,
                        predictedPrice = (data["predicted_price"] as? Number)?.toDouble() ?: 0.0,
                        confidence = (data["confidence"] as? Number)?.toDouble() ?: 0.0,
                        changePercent = (data["change_percent"] as? Number)?.toDouble() ?: 0.0,
                        volume = (data["volume"] as? Number)?.toLong() ?: 0L,
                        updateTime = (data["update_time"] as? String) ?: "",
                        signals = parseSignals(data["signals"]),
                        error = null
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载实时数据失败"
                    )
                }
            }
        }
    }

    private fun parseSignals(signalsData: Any?): List<SignalInfo> {
        if (signalsData == null) return emptyList()
        val list = signalsData as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            SignalInfo(
                name = map["name"] as? String ?: "",
                signal = map["signal"] as? String ?: "",
                weight = (map["weight"] as? Number)?.toDouble() ?: 0.0
            )
        }
    }

    fun toggleAutoRefresh(symbol: String) {
        val current = _uiState.value.isAutoRefresh
        _uiState.value = _uiState.value.copy(isAutoRefresh = !current)
        if (!current) {
            startAutoRefresh(symbol)
        } else {
            stopAutoRefresh()
        }
    }

    private fun startAutoRefresh(symbol: String) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive && _uiState.value.isAutoRefresh) {
                delay(30_000L) // 30 seconds
                if (_uiState.value.isAutoRefresh) {
                    loadRealtimeData(symbol)
                }
            }
        }
    }

    fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }
}
