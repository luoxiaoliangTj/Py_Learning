package com.tangtang.stockadvisor.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.model.StockInfo
import com.tangtang.stockadvisor.data.remote.RealtimeDataSource
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val marketStocks: List<StockInfo> = emptyList(),
    val watchlist: List<StockInfo> = emptyList(),
    val searchResults: List<StockInfo> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StockRepository,
    private val realtimeDataSource: RealtimeDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStockList()
    }

    fun loadStockList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val positions = repository.loadPositionsFromLocal(context)
                if (positions.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        marketStocks = emptyList(),
                        error = "暂无持仓数据，请先导入持仓文件"
                    )
                    return@launch
                }
                // 批量获取实时行情
                val stocks = mutableListOf<StockInfo>()
                for ((code, pos) in positions.entries.take(20)) {
                    try {
                        val realtime = realtimeDataSource.fetchRealtimeData(code)
                        stocks.add(
                            StockInfo(
                                code = code,
                                name = pos.stockName,
                                currentPrice = realtime.price,
                                changePercent = realtime.changePct,
                                changeAmount = realtime.change,
                                volume = realtime.volume,
                                turnover = realtime.amount
                            )
                        )
                    } catch (e: Exception) {
                        // 获取失败就用持仓成本价占位
                        stocks.add(
                            StockInfo(
                                code = code,
                                name = pos.stockName,
                                currentPrice = pos.costPrice,
                                changePercent = 0.0,
                                changeAmount = 0.0,
                                volume = 0,
                                turnover = 0.0
                            )
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    marketStocks = stocks
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    fun searchStocks(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        // 从持仓中搜索
        viewModelScope.launch {
            try {
                val positions = repository.loadPositionsFromLocal(context)
                val filtered = positions.filter { (code, pos) ->
                    code.contains(query) || pos.stockName.contains(query)
                }.map { (code, pos) ->
                    StockInfo(
                        code = code,
                        name = pos.stockName,
                        currentPrice = pos.costPrice,
                        changePercent = 0.0,
                        changeAmount = 0.0,
                        volume = 0,
                        turnover = 0.0
                    )
                }
                _uiState.value = _uiState.value.copy(searchResults = filtered)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(searchResults = emptyList())
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
