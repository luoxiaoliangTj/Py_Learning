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
    val holdings: List<PortfolioItem> = emptyList(),
    val capital: PortfolioSummary? = null,
    val error: String? = null
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
        loadHoldings()
        loadCapital()
    }

    private fun loadHoldings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getHoldings().collect { result ->
                result.onSuccess { holdings ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        holdings = holdings
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

    private fun loadCapital() {
        viewModelScope.launch {
            repository.getCapital().collect { result ->
                result.onSuccess { summary ->
                    _uiState.value = _uiState.value.copy(capital = summary)
                }.onFailure { e ->
                    // Don't override main error with capital load failure
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
