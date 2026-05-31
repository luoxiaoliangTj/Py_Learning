package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.model.PortfolioItem
import com.tangtang.stockadvisor.data.model.PortfolioSummary
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PortfolioUiState(
    val isLoading: Boolean = false,
    val summary: PortfolioSummary? = null,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val newStockCode: String = "",
    val newStockName: String = "",
    val newShares: String = "",
    val newAvgCost: String = ""
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    init {
        loadPortfolio()
    }

    fun loadPortfolio() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getPortfolio().collect { result ->
                result.onSuccess { summary ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        summary = summary
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载持仓失败"
                    )
                }
            }
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = false,
            newStockCode = "",
            newStockName = "",
            newShares = "",
            newAvgCost = ""
        )
    }

    fun updateNewStockCode(code: String) {
        _uiState.value = _uiState.value.copy(newStockCode = code)
    }

    fun updateNewStockName(name: String) {
        _uiState.value = _uiState.value.copy(newStockName = name)
    }

    fun updateNewShares(shares: String) {
        _uiState.value = _uiState.value.copy(newShares = shares)
    }

    fun updateNewAvgCost(cost: String) {
        _uiState.value = _uiState.value.copy(newAvgCost = cost)
    }

    fun addStock() {
        val state = _uiState.value
        val shares = state.newShares.toIntOrNull()
        val avgCost = state.newAvgCost.toDoubleOrNull()

        if (state.newStockCode.isBlank()) {
            _uiState.value = state.copy(error = "请输入股票代码")
            return
        }
        if (shares == null || shares <= 0) {
            _uiState.value = state.copy(error = "请输入有效的持仓数量")
            return
        }
        if (avgCost == null || avgCost <= 0) {
            _uiState.value = state.copy(error = "请输入有效的成本价")
            return
        }

        viewModelScope.launch {
            val item = PortfolioItem(
                code = state.newStockCode,
                name = state.newStockName.ifBlank { state.newStockCode },
                shares = shares,
                avgCost = avgCost
            )
            val result = repository.addToPortfolio(item)
            result.onSuccess { summary ->
                _uiState.value = _uiState.value.copy(
                    showAddDialog = false,
                    summary = summary,
                    newStockCode = "",
                    newStockName = "",
                    newShares = "",
                    newAvgCost = ""
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "添加失败"
                )
            }
        }
    }

    fun removeStock(code: String) {
        viewModelScope.launch {
            val result = repository.removeFromPortfolio(code)
            result.onSuccess { summary ->
                _uiState.value = _uiState.value.copy(summary = summary)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "删除失败"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
