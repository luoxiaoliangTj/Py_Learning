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

data class PredictUiState(
    val isLoading: Boolean = false,
    val symbol: String = "",
    val stockName: String = "",
    val currentPrice: Double = 0.0,
    val predictedHigh: Double = 0.0,
    val predictedLow: Double = 0.0,
    val predictedClose: Double = 0.0,
    val confidence: Double = 0.0,
    val recommendation: String = "",
    val signals: List<SignalInfo> = emptyList(),
    val error: String? = null
)

data class SignalInfo(
    val name: String,
    val signal: String,
    val weight: Double
)

@HiltViewModel
class PredictViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PredictUiState())
    val uiState: StateFlow<PredictUiState> = _uiState.asStateFlow()

    fun loadPrediction(symbol: String) {
        _uiState.value = PredictUiState(symbol = symbol, isLoading = true)

        viewModelScope.launch {
            repository.getDailyPrediction(symbol).collect { result ->
                result.onSuccess { prediction ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        symbol = symbol,
                        stockName = prediction.name,
                        currentPrice = prediction.currentPrice,
                        predictedHigh = prediction.predictedHigh,
                        predictedLow = prediction.predictedLow,
                        predictedClose = prediction.predictedClose,
                        confidence = prediction.confidence,
                        recommendation = determineRecommendation(prediction.confidence, prediction.predictedClose, prediction.currentPrice),
                        signals = prediction.strategies.map { SignalInfo(it.name, it.signal, it.weight) },
                        error = null
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

    private fun determineRecommendation(confidence: Double, predictedClose: Double, currentPrice: Double): String {
        val changePct = (predictedClose - currentPrice) / currentPrice
        return when {
            changePct > 0.03 && confidence > 0.6 -> "BUY"
            changePct < -0.03 && confidence > 0.6 -> "SELL"
            changePct > 0.01 -> "HOLD_BUY"
            changePct < -0.01 -> "HOLD_SELL"
            else -> "HOLD"
        }
    }
}
