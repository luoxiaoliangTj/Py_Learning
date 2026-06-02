package com.tangtang.stockadvisor.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.tangtang.stockadvisor.data.repository.StockRepository
import com.tangtang.stockadvisor.util.MdFileScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PortfolioItemUi(
    val code: String,
    val name: String,
    val shares: Int,
    val avgCost: Double,
    val currentPrice: Double,
    val marketValue: Double,
    val profitLoss: Double,
    val profitLossPercent: Double
)

data class PortfolioUiState(
    val isLoading: Boolean = false,
    val totalMarketValue: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalProfitLoss: Double = 0.0,
    val totalProfitLossPercent: Double = 0.0,
    val items: List<PortfolioItemUi> = emptyList(),
    val error: String? = null,
    val scanStatus: ScanStatus = ScanStatus.IDLE
)

enum class ScanStatus {
    IDLE, SCANNING, SUCCESS, ERROR
}

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    fun loadPortfolio() {
        viewModelScope.launch {
            _uiState.value = PortfolioUiState(isLoading = true)
            repository.getHoldings().collect { result ->
                result.onSuccess { holdings ->
                    val items = holdings.map { h ->
                        PortfolioItemUi(
                            code = h.code,
                            name = h.name,
                            shares = h.shares,
                            avgCost = h.avgCost,
                            currentPrice = h.currentPrice,
                            marketValue = h.marketValue,
                            profitLoss = h.profitLoss,
                            profitLossPercent = h.profitLossPercent
                        )
                    }
                    val totalMV = items.sumOf { it.marketValue }
                    val totalCost = items.sumOf { it.avgCost * it.shares }
                    val totalPL = totalMV - totalCost
                    val totalPLPct = if (totalCost > 0) (totalPL / totalCost) * 100 else 0.0

                    _uiState.value = PortfolioUiState(
                        isLoading = false,
                        items = items,
                        totalMarketValue = totalMV,
                        totalCost = totalCost,
                        totalProfitLoss = totalPL,
                        totalProfitLossPercent = totalPLPct
                    )
                }.onFailure { e ->
                    _uiState.value = PortfolioUiState(
                        isLoading = false,
                        error = e.message ?: "加载持仓失败"
                    )
                }
            }
        }
    }

    /**
     * 从存储卡目录自动扫描并导入持仓
     * 1. 扫描 /storage/emulated/0/Documents/mindmaps/炒股/ 目录（IO）
     * 2. 解析 .md 文件（Default）
     * 3. 发送到后端（IO）
     */
    fun importFromMdFile(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, scanStatus = ScanStatus.SCANNING)

            try {
                // 1. 扫描目录 + 读取文件（IO 操作）
                val scanResult = withContext(Dispatchers.IO) {
                    MdFileScanner.scanAndParse()
                }

                if (scanResult.errorMessage != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        scanStatus = ScanStatus.ERROR,
                        error = scanResult.errorMessage
                    )
                    return@launch
                }

                if (scanResult.holdings.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        scanStatus = ScanStatus.ERROR,
                        error = "未解析到任何持仓数据"
                    )
                    return@launch
                }

                // 2. 转换为 JSON（CPU 操作）
                val gson = Gson()
                val holdingsJson = withContext(Dispatchers.Default) {
                    gson.toJson(scanResult.holdings)
                }
                val capitalJson = withContext(Dispatchers.Default) {
                    scanResult.capital?.let { gson.toJson(it) }
                }

                // 3. 调用后端导入（网络操作）
                val result = withContext(Dispatchers.IO) {
                    repository.importPortfolio(holdingsJson, capitalJson)
                }

                result.onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        scanStatus = ScanStatus.SUCCESS
                    )
                    // 导入成功后重新加载
                    loadPortfolio()
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        scanStatus = ScanStatus.ERROR,
                        error = e.message ?: "导入失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scanStatus = ScanStatus.ERROR,
                    error = e.message ?: "导入失败"
                )
            }
        }
    }

    fun importPortfolio(holdingsJson: String, capitalJson: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.importPortfolio(holdingsJson, capitalJson)
                result.onSuccess {
                    // 导入成功后重新加载
                    loadPortfolio()
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "导入失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "导入失败"
                )
            }
        }
    }

    fun addOrUpdatePosition(symbol: String, name: String, shares: Int, costPrice: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.addOrUpdatePosition(symbol, name, shares, costPrice)
                result.onSuccess {
                    loadPortfolio()
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "操作失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "操作失败"
                )
            }
        }
    }

    fun deletePosition(symbol: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.deletePosition(symbol)
                result.onSuccess {
                    loadPortfolio()
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "删除失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "删除失败"
                )
            }
        }
    }

    fun clearAllPositions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.clearAllPositions()
                result.onSuccess {
                    loadPortfolio()
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "清空失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "清空失败"
                )
            }
        }
    }
}
