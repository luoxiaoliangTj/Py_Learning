package com.tangtang.stockadvisor.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.tangtang.stockadvisor.data.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
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
    val error: String? = null
)

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

    fun importPortfolioFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 1. 读取文件内容（IO 操作）
                val content = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    reader.readText().also { reader.close() }
                }

                // 2. 解析 .md 文件（CPU 操作）
                val (holdings, capital) = withContext(Dispatchers.Default) {
                    parseMdPortfolio(content)
                }

                // 3. 转换为 JSON
                val gson = Gson()
                val holdingsJson = gson.toJson(holdings)
                val capitalJson = if (capital != null) gson.toJson(capital) else null

                // 4. 调用后端导入（网络操作）
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

    private fun parseMdPortfolio(content: String): Pair<List<Map<String, Any>>, Map<String, Double>?> {
        val holdings = mutableListOf<Map<String, Any>>()
        var totalAssets = 0.0
        var availableCash = 0.0

        // 解析总资产
        val totalAssetsMatch = Regex("总资产\\s*:\\s*([￥$]?\\s*[\\d,]+\\.?\\d*)").find(content)
        if (totalAssetsMatch != null) {
            totalAssets = cleanNumber(totalAssetsMatch.groupValues[1])
        }

        // 解析可用资金
        val availableCashMatch = Regex("可用资金\\s*:\\s*([￥$]?\\s*[\\d,]+\\.?\\d*)").find(content)
        if (availableCashMatch != null) {
            availableCash = cleanNumber(availableCashMatch.groupValues[1])
        }

        // 找到表头行
        val lines = content.split("\n")
        var headerLine: String? = null
        for (line in lines) {
            if (line.contains("股票名称") && line.contains("市值")) {
                headerLine = line
                break
            }
        }

        if (headerLine != null) {
            // 解析表头列索引
            val headers = headerLine.split("|").drop(1).dropLast(1).map { it.trim() }
            val colIndex = mutableMapOf<String, Int>()
            for (i in headers.indices) {
                when {
                    "股票名称" in headers[i] -> colIndex["name"] = i
                    "股票代码" in headers[i] -> colIndex["code"] = i
                    "持仓" in headers[i] && "可用" in headers[i] -> colIndex["shares"] = i
                    "成本价" in headers[i] -> colIndex["cost"] = i
                }
            }

            if ("name" in colIndex && "shares" in colIndex && "cost" in colIndex) {
                for (line in lines) {
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("|") || "---" in trimmed || trimmed == headerLine) continue

                    val cells = trimmed.split("|").drop(1).dropLast(1)
                    if (cells.size <= (colIndex.values.maxOrNull() ?: 0)) continue

                    // 提取股票名称
                    val nameCell = cells[colIndex["name"]!!].trim()
                    val nameMatch = Regex("\\[\\[(.+?)\\]\\]").find(nameCell)
                    if (nameMatch == null) continue
                    val stockName = nameMatch.groupValues[1].trim()

                    // 提取股票代码
                    var symbol: String? = null
                    if ("code" in colIndex) {
                        val codeCell = cells[colIndex["code"]!!].trim()
                        if (codeCell.isNotEmpty() && codeCell != "-" && codeCell != "N/A") {
                            symbol = Regex("\\d+").find(codeCell)?.value
                        }
                    }

                    // 提取持仓股数
                    val sharesCell = cells[colIndex["shares"]!!].trim()
                    val sharesPart = sharesCell.split("/")[0].trim()
                    val shares = cleanNumber(sharesPart).toInt()

                    // 提取成本价
                    val costCell = cells[colIndex["cost"]!!].trim()
                    val costPrice = if (costCell == "-" || costCell.isEmpty() || "特殊" in costCell) {
                        0.0
                    } else {
                        cleanNumber(costCell)
                    }

                    holdings.add(
                        mapOf(
                            "symbol" to (symbol ?: ""),
                            "name" to stockName,
                            "shares" to shares,
                            "cost_price" to costPrice
                        )
                    )
                }
            }
        }

        val capital = if (totalAssets > 0 || availableCash > 0) {
            mapOf(
                "total_capital" to totalAssets,
                "available_cash" to availableCash
            )
        } else null

        return Pair(holdings, capital)
    }

    private fun cleanNumber(text: String): Double {
        val cleaned = text.replace(Regex("[^\\d.-]"), "")
        return if (cleaned.isNotEmpty()) {
            cleaned.toDoubleOrNull() ?: 0.0
        } else {
            0.0
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
}
