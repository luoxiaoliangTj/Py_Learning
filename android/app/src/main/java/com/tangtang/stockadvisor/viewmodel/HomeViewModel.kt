package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.model.StockInfo
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadMarketOverview()
    }

    fun loadMarketOverview() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getMarketOverview().collect { result ->
                result.onSuccess { stocks ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        marketStocks = stocks
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载市场数据失败"
                    )
                }
            }
        }
    }

    fun searchStocks(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch {
            repository.searchStocks(query).collect { result ->
                result.onSuccess { stocks ->
                    _uiState.value = _uiState.value.copy(searchResults = stocks)
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "搜索失败"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
