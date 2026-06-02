package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import com.tangtang.stockadvisor.data.model.StrategyInfo
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class StrategyUiState(
    val isLoading: Boolean = false,
    val strategies: List<StrategyInfo> = emptyList(),
    val selectedSymbol: String = "",
    val selectedStrategy: StrategyInfo? = null,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val deleteSuccess: Boolean = false
)

@HiltViewModel
class StrategyViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StrategyUiState())
    val uiState: StateFlow<StrategyUiState> = _uiState.asStateFlow()

    fun loadStrategies() {
        _uiState.value = StrategyUiState(
            error = "策略管理功能需要后端支持，当前不可用"
        )
    }

    fun loadStrategyDetail(symbol: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            selectedSymbol = symbol,
            error = "策略详情功能需要后端支持，当前不可用"
        )
    }

    fun saveStrategy(symbol: String, strategyType: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = "策略保存功能需要后端支持，当前不可用"
        )
    }

    fun deleteStrategy(symbol: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = "策略删除功能需要后端支持，当前不可用"
        )
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(saveSuccess = false, deleteSuccess = false, error = null)
    }
}
