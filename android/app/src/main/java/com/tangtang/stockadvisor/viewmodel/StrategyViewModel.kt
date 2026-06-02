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
import java.io.File
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    private val repository: StockRepository,
    private val application: android.app.Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(StrategyUiState())
    val uiState: StateFlow<StrategyUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private val strategiesFile: File
        get() = File(application.filesDir, "strategies.json")

    fun loadStrategies() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val builtInStrategies = listOf(
                    StrategyInfo(
                        name = "ChannelStrategy",
                        description = "通道策略 - 基于布林带通道突破的交易策略，当价格突破上轨时卖出，跌破下轨时买入",
                        strategyType = "channel"
                    ),
                    StrategyInfo(
                        name = "TrendStrategy",
                        description = "趋势策略 - 基于均线趋势跟踪的交易策略，金叉买入，死叉卖出",
                        strategyType = "trend"
                    )
                )
                val savedStrategies = loadSavedStrategies()
                val allStrategies = builtInStrategies + savedStrategies
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    strategies = allStrategies
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    strategies = emptyList(),
                    error = "加载策略失败: ${e.message}"
                )
            }
        }
    }

    fun loadStrategyDetail(symbol: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            selectedSymbol = symbol
        )
    }

    fun saveStrategy(symbol: String, strategyType: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val newStrategy = StrategyInfo(
                    name = symbol,
                    symbol = symbol,
                    strategyType = strategyType,
                    description = "自定义策略: $symbol - $strategyType"
                )
                val savedStrategies = loadSavedStrategies().toMutableList()
                savedStrategies.add(newStrategy)
                saveStrategiesToFile(savedStrategies)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    saveSuccess = true,
                    strategies = listOf(
                        StrategyInfo(name = "ChannelStrategy", description = "通道策略 - 基于布林带通道突破的交易策略，当价格突破上轨时卖出，跌破下轨时买入", strategyType = "channel"),
                        StrategyInfo(name = "TrendStrategy", description = "趋势策略 - 基于均线趋势跟踪的交易策略，金叉买入，死叉卖出", strategyType = "trend")
                    ) + savedStrategies
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "保存策略失败: ${e.message}"
                )
            }
        }
    }

    fun deleteStrategy(symbol: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val savedStrategies = loadSavedStrategies().toMutableList()
                savedStrategies.removeAll { it.name == symbol || it.symbol == symbol }
                saveStrategiesToFile(savedStrategies)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    deleteSuccess = true,
                    strategies = listOf(
                        StrategyInfo(name = "ChannelStrategy", description = "通道策略 - 基于布林带通道突破的交易策略，当价格突破上轨时卖出，跌破下轨时买入", strategyType = "channel"),
                        StrategyInfo(name = "TrendStrategy", description = "趋势策略 - 基于均线趋势跟踪的交易策略，金叉买入，死叉卖出", strategyType = "trend")
                    ) + savedStrategies
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "删除策略失败: ${e.message}"
                )
            }
        }
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(saveSuccess = false, deleteSuccess = false, error = null)
    }

    private fun loadSavedStrategies(): List<StrategyInfo> {
        return try {
            if (strategiesFile.exists()) {
                val json = strategiesFile.readText()
                val type = object : TypeToken<List<StrategyInfo>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveStrategiesToFile(strategies: List<StrategyInfo>) {
        strategiesFile.writeText(gson.toJson(strategies))
    }
}
