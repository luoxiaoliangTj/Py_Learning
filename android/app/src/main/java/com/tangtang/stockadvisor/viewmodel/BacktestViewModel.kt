package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class BacktestUiState(
    val isLoading: Boolean = false,
    val symbol: String = "",
    val stockName: String = "",
    val strategyType: String = "channel",
    val totalReturn: Double = 0.0,
    val annualReturn: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val sharpeRatio: Double = 0.0,
    val winRate: Double = 0.0,
    val totalTrades: Int = 0,
    val finalCapital: Double = 0.0,
    val initialCapital: Double = 100000.0,
    val error: String? = null
)

@HiltViewModel
class BacktestViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BacktestUiState())
    val uiState: StateFlow<BacktestUiState> = _uiState.asStateFlow()

    fun runBacktest(symbol: String, strategyType: String = "channel") {
        _uiState.value = BacktestUiState(
            symbol = symbol,
            strategyType = strategyType,
            isLoading = false,
            error = "回测功能需要后端支持，当前不可用"
        )
    }
}
