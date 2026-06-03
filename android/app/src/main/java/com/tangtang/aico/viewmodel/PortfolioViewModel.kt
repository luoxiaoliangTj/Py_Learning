package com.tangtang.aico.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.aico.data.repository.CapitalData
import com.tangtang.aico.data.repository.PositionData
import com.tangtang.aico.data.repository.StockRepository
import com.tangtang.aico.data.remote.StockCodeMapper
import com.tangtang.aico.util.MdFileParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val scanStatus: ScanStatus = ScanStatus.IDLE,
    val importMessage: String? = null
)

enum class ScanStatus {
    IDLE, SUCCESS, ERROR
}

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: StockRepository,
    private val codeMapper: StockCodeMapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    /**
     * 从本地JSON加载持仓（App启动时自动调用）
     */
    fun loadPortfolio(context: Context) {
        viewModelScope.launch {
            _uiState.value = PortfolioUiState(isLoading = true)
            try {
                val positions = withContext(Dispatchers.IO) {
                    repository.loadPositionsFromLocal(context)
                }

                val items = positions.filter { it.value.shares > 0 }.map { (symbol, pos) ->
                    val currentPrice = pos.costPrice // 暂用成本价
                    val marketValue = pos.shares * currentPrice
                    val cost = pos.shares * pos.costPrice
                    val profitLoss = marketValue - cost
                    val profitLossPercent = if (cost > 0) (profitLoss / cost) * 100 else 0.0

                    PortfolioItemUi(
                        code = symbol,
                        name = pos.stockName,
                        shares = pos.shares,
                        avgCost = pos.costPrice,
                        currentPrice = currentPrice,
                        marketValue = marketValue,
                        profitLoss = profitLoss,
                        profitLossPercent = profitLossPercent
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
            } catch (e: Exception) {
                _uiState.value = PortfolioUiState(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    /**
     * 通过 SAF 文件选择器导入 .md 文件
     * 流程：读文件 → 解析 → 5级代码匹配 → 写本地JSON → 重新加载
     */
    fun importPortfolioFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, importMessage = null, error = null)
            try {
                // 1. 读取文件内容
                val content = withContext(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                            ?: throw Exception("无法打开文件，请重新选择")
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        reader.readText().also { reader.close() }
                    } catch (e: Exception) {
                        throw Exception("文件读取失败: ${e.message}")
                    }
                }

                Log.d("PortfolioImport", "文件内容长度: ${content.length}")
                Log.d("PortfolioImport", "文件前200字符: ${content.take(200)}")

                // 2. 解析 .md 文件
                val parseResult = withContext(Dispatchers.Default) {
                    MdFileParser.parseContent(content)
                }

                Log.d("PortfolioImport", "解析结果: holdings=${parseResult.holdings.size}, error=${parseResult.errorMessage}")

                if (parseResult.holdings.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = parseResult.errorMessage ?: "未解析到任何持仓数据"
                    )
                    return@launch
                }

                // 3. 转换为 RawHolding 并执行5级代码匹配
                val rawHoldings = parseResult.holdings.map { h ->
                    RawHolding(
                        symbol = (h["symbol"] as? String)?.takeIf { it.isNotEmpty() },
                        name = h["name"] as? String ?: "",
                        shares = (h["shares"] as? Number)?.toInt() ?: 0,
                        costPrice = (h["cost_price"] as? Number)?.toDouble() ?: 0.0
                    )
                }

                val existingPositions = repository.loadPositionsFromLocal(context)
                // 确保 StockCodeMapper 已初始化
                try { codeMapper.init(context) } catch (e: Exception) { Log.w("Portfolio", "CodeMapper init: ${e.message}") }
                val matchedPositions = performCodeMatching(rawHoldings, existingPositions, codeMapper)

                // 4. 写本地JSON
                repository.savePositionsToLocal(context, matchedPositions)
                if (parseResult.capital != null) {
                    val capital = CapitalData(
                        availableCash = parseResult.capital["available_cash"] ?: 0.0,
                        totalCapital = parseResult.capital["total_capital"] ?: 0.0,
                        lastUpdated = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        note = "从持仓文件同步"
                    )
                    repository.saveCapitalToLocal(context, capital)
                }

                // 5. 重新加载显示
                loadPortfolio(context)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scanStatus = ScanStatus.SUCCESS,
                    importMessage = "成功导入 ${matchedPositions.size} 支持仓"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scanStatus = ScanStatus.ERROR,
                    error = e.message ?: "导入失败"
                )
            }
        }
    }

    /**
     * 5级代码匹配（对齐 position_manager_tool.py）
     * 第1级：文件中的股票代码列
     * 第2级：现有持仓精确匹配名称
     * 第3级：现有持仓模糊匹配
     * 第4级：本地映射表（StockCodeMapper）
     * 第5级：跳过
     */
    private fun performCodeMatching(
        rawHoldings: List<RawHolding>,
        existingPositions: Map<String, PositionData>,
        codeMapper: StockCodeMapper?
    ): Map<String, PositionData> {
        val result = mutableMapOf<String, PositionData>()
        val fileNames = rawHoldings.map { it.name }.toSet()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        for (raw in rawHoldings) {
            var finalSymbol: String? = null

            // 第1级：文件中的股票代码列
            if (!raw.symbol.isNullOrEmpty() && raw.symbol != "0") {
                finalSymbol = raw.symbol
            }

            // 第2级：现有持仓精确匹配名称
            if (finalSymbol == null) {
                for ((sym, info) in existingPositions) {
                    if (info.stockName == raw.name) {
                        finalSymbol = sym
                        break
                    }
                }
            }

            // 第3级：现有持仓模糊匹配
            if (finalSymbol == null) {
                finalSymbol = fuzzyMatchName(raw.name, existingPositions)
            }

            // 第4级：本地映射表（StockCodeMapper）
            if (finalSymbol == null && codeMapper != null) {
                finalSymbol = codeMapper.getCodeByName(raw.name)
                if (finalSymbol != null) {
                    Log.d("Portfolio", "第4级匹配: '${raw.name}' -> $finalSymbol")
                }
            }

            // 第5级：跳过
            if (finalSymbol == null) {
                Log.w("Portfolio", "跳过 '${raw.name}'：无代码且无法匹配")
                continue
            }

            result[finalSymbol] = PositionData(
                shares = raw.shares,
                costPrice = raw.costPrice,
                stockName = raw.name,
                lastUpdated = sdf.format(Date())
            )
        }

        // 清仓处理：文件中没有但本地有的股票 → shares=0
        for ((sym, info) in existingPositions) {
            if (info.stockName !in fileNames && info.shares > 0) {
                result[sym] = info.copy(shares = 0)
            }
        }

        return result
    }

    private fun fuzzyMatchName(name: String, positions: Map<String, PositionData>): String? {
        fun preprocess(n: String): String {
            return n.replace(Regex("[\\(（\\[【].*?[\\)）\\]】]"), "")
                .replace(Regex("\\s+"), "")
                .lowercase()
        }

        val target = preprocess(name)
        if (target.isEmpty()) return null

        var bestMatch: String? = null
        var bestScore = 0.7

        for ((sym, info) in positions) {
            val dbName = preprocess(info.stockName)
            if (dbName.isEmpty()) continue

            if (target == dbName) return sym

            if (target in dbName || dbName in target) {
                val ratio = minOf(target.length, dbName.length).toDouble() / maxOf(target.length, dbName.length)
                if (ratio > bestScore) {
                    bestScore = ratio
                    bestMatch = sym
                }
            }
        }
        return bestMatch
    }

    fun addOrUpdatePosition(symbol: String, name: String, shares: Int, costPrice: Double, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.addOrUpdatePosition(context, symbol, name, shares, costPrice)
                result.onSuccess { loadPortfolio(context) }
                    .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "操作失败") }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "操作失败")
            }
        }
    }

    fun deletePosition(symbol: String, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.deletePosition(context, symbol)
                result.onSuccess { loadPortfolio(context) }
                    .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "删除失败") }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "删除失败")
            }
        }
    }

    fun clearAllPositions(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.clearAllPositions(context)
                result.onSuccess { loadPortfolio(context) }
                    .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "清空失败") }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "清空失败")
            }
        }
    }
}

data class RawHolding(
    val symbol: String?,
    val name: String,
    val shares: Int,
    val costPrice: Double
)
