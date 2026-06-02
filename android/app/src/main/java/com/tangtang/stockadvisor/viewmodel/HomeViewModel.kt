package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import com.tangtang.stockadvisor.data.model.StockInfo
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        _uiState.value = HomeUiState(
            error = "股票列表功能需要后端支持，当前不可用"
        )
    }

    fun loadStockList() {
        // 股票列表功能需要后端支持，当前不可用
        _uiState.value = _uiState.value.copy(
            error = "股票列表功能需要后端支持，当前不可用"
        )
    }

    fun searchStocks(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        // 搜索功能需要后端支持，当前不可用
        _uiState.value = _uiState.value.copy(
            searchResults = emptyList(),
            error = "搜索功能需要后端支持，当前不可用"
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
