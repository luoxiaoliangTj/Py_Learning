package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.model.StrategyInfo
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getStrategyList().collect { result ->
                result.onSuccess { strategies ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        strategies = strategies
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载策略列表失败"
                    )
                }
            }
        }
    }

    fun loadStrategyDetail(symbol: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, selectedSymbol = symbol)
            repository.getStrategy(symbol).collect { result ->
                result.onSuccess { strategy ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        selectedStrategy = strategy
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载策略详情失败"
                    )
                }
            }
        }
    }

    fun saveStrategy(symbol: String, strategyType: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, saveSuccess = false)
            repository.saveStrategy(symbol, strategyType).collect { result ->
                result.onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        saveSuccess = true
                    )
                    loadStrategies()
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "保存策略失败"
                    )
                }
            }
        }
    }

    fun deleteStrategy(symbol: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, deleteSuccess = false)
            repository.deleteStrategy(symbol).collect { result ->
                result.onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        deleteSuccess = true
                    )
                    loadStrategies()
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "删除策略失败"
                    )
                }
            }
        }
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(saveSuccess = false, deleteSuccess = false, error = null)
    }
}
