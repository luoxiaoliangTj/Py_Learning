package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        _uiState.value = PredictUiState(
            symbol = symbol,
            isLoading = false,
            error = "预测功能需要后端支持，当前不可用"
        )
    }
}
