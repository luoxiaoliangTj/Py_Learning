package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.model.BacktestResult
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BacktestUiState(
    val isLoading: Boolean = false,
    val stockCode: String = "",
    val stockName: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val initialCapital: Double = 100000.0,
    val strategy: String = "default",
    val result: BacktestResult? = null,
    val error: String? = null,
    val availableStrategies: List<String> = listOf(
        "default", "kdj", "macd", "rsi", "bollinger", "atr_channel"
    )
)

@HiltViewModel
class BacktestViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BacktestUiState())
    val uiState: StateFlow<BacktestUiState> = _uiState.asStateFlow()

    fun setStockCode(code: String, name: String = "") {
        _uiState.value = _uiState.value.copy(stockCode = code, stockName = name)
    }

    fun setDateRange(startDate: String, endDate: String) {
        _uiState.value = _uiState.value.copy(startDate = startDate, endDate = endDate)
    }

    fun setInitialCapital(capital: Double) {
        _uiState.value = _uiState.value.copy(initialCapital = capital)
    }

    fun setStrategy(strategy: String) {
        _uiState.value = _uiState.value.copy(strategy = strategy)
    }

    fun runBacktest() {
        val state = _uiState.value
        if (state.stockCode.isBlank()) {
            _uiState.value = state.copy(error = "请输入股票代码")
            return
        }
        if (state.startDate.isBlank() || state.endDate.isBlank()) {
            _uiState.value = state.copy(error = "请选择回测时间范围")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.runBacktest(
                code = state.stockCode,
                startDate = state.startDate,
                endDate = state.endDate,
                initialCapital = state.initialCapital,
                strategy = state.strategy
            ).collect { result ->
                result.onSuccess { backtestResult ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        result = backtestResult
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "回测执行失败"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
