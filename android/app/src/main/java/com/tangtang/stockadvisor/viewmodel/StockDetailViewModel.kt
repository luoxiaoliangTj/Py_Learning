package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.remote.RealtimeDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 股票详情页 ViewModel
 * 管理"当前股票"的实时行情、K线数据加载状态
 */
data class StockDetailUiState(
    val isLoading: Boolean = false,
    val symbol: String = "",
    val stockName: String = "",
    val currentPrice: Double = 0.0,
    val prevClose: Double = 0.0,
    val open: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val change: Double = 0.0,
    val changePercent: Double = 0.0,
    val volume: Long = 0,
    val amount: Double = 0.0,
    val dataSource: String = "",
    val updateTime: String = "",
    val klineLoaded: Boolean = false,
    val klineRecordCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class StockDetailViewModel @Inject constructor(
    private val realtimeDataSource: RealtimeDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockDetailUiState())
    val uiState: StateFlow<StockDetailUiState> = _uiState.asStateFlow()

    fun loadStockDetail(symbol: String) {
        _uiState.value = StockDetailUiState(symbol = symbol, isLoading = true)

        viewModelScope.launch {
            try {
                val data = realtimeDataSource.fetchRealtimeData(symbol)
                if (data.valid) {
                    _uiState.value = StockDetailUiState(
                        isLoading = false,
                        symbol = symbol,
                        stockName = data.name,
                        currentPrice = data.price,
                        prevClose = data.prevClose,
                        open = data.open,
                        high = data.high,
                        low = data.low,
                        change = data.change,
                        changePercent = data.changePct,
                        volume = data.volume,
                        amount = data.amount,
                        dataSource = data.source,
                        updateTime = "${data.date} ${data.time}"
                    )
                } else {
                    _uiState.value = StockDetailUiState(
                        isLoading = false,
                        symbol = symbol,
                        error = "无法获取 ${symbol} 的实时行情，请检查网络或股票代码"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = StockDetailUiState(
                    isLoading = false,
                    symbol = symbol,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    fun refreshPrice(symbol: String) {
        loadStockDetail(symbol)
    }
}
