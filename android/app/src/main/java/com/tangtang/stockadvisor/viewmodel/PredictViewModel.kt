package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.model.PredictionResult
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PredictUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val stockCode: String = "",
    val stockName: String = "",
    val currentPrice: Double = 0.0,
    val prediction: PredictionResult? = null,
    val error: String? = null
)

@HiltViewModel
class PredictViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PredictUiState())
    val uiState: StateFlow<PredictUiState> = _uiState.asStateFlow()

    fun loadPrediction(code: String) {
        _uiState.value = _uiState.value.copy(stockCode = code, isLoading = true, error = null)

        // Load daily prediction
        viewModelScope.launch {
            repository.getDailyPrediction(code).collect { result ->
                result.onSuccess { prediction ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        prediction = prediction,
                        stockName = prediction.name,
                        currentPrice = prediction.currentPrice
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "预测加载失败"
                    )
                }
            }
        }
    }

    fun refreshPrediction() {
        val code = _uiState.value.stockCode
        if (code.isBlank()) return

        _uiState.value = _uiState.value.copy(isRefreshing = true)
        viewModelScope.launch {
            repository.getDailyPrediction(code).collect { result ->
                result.onSuccess { prediction ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        prediction = prediction,
                        currentPrice = prediction.currentPrice
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = e.message ?: "刷新失败"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
