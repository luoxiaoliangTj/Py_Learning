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

data class PortfolioUiState(
    val isLoading: Boolean = false,
    val totalMarketValue: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalProfitLoss: Double = 0.0,
    val totalProfitLossPercent: Double = 0.0,
    val items: List<PortfolioItemUi> = emptyList(),
    val error: String? = null
)

data class PortfolioItemUi(
    val code: String,
    val name: String,
    val shares: Int,
    val avgCost: Double,
    val currentPrice: Double,
    val marketValue: Double,
    val profitLoss: Double,
    val profitLossPercent: Double
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    fun loadPortfolio() {
        viewModelScope.launch {
            _uiState.value = PortfolioUiState(isLoading = true)
            repository.getHoldings().collect { result ->
                result.onSuccess { holdings ->
                    val items = holdings.map { h ->
                        PortfolioItemUi(
                            code = h.code,
                            name = h.name,
                            shares = h.shares,
                            avgCost = h.avgCost,
                            currentPrice = h.currentPrice,
                            marketValue = h.marketValue,
                            profitLoss = h.profitLoss,
                            profitLossPercent = h.profitLossPercent
                        )
                    }
                    val totalMV = items.sumOf { it.marketValue }
                    val totalCost = items.sumOf { it.avgCost * it.shares }
                    val totalPL = totalMV - totalCost
                    val totalPLPct = if (totalCost > 0) (totalPL / totalCost) * 100 else 0.0

                    _uiState.value = PortfolioUiState(
                        isLoading = false,
                        items = items,
                        totalMarketValue = totalMV,
                        totalCost = totalCost,
                        totalProfitLoss = totalPL,
                        totalProfitLossPercent = totalPLPct
                    )
                }.onFailure { e ->
                    _uiState.value = PortfolioUiState(
                        isLoading = false,
                        error = e.message ?: "加载持仓失败"
                    )
                }
            }
        }
    }
}
