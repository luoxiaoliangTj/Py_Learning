package com.tangtang.stockadvisor.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.repository.CapitalData
import com.tangtang.stockadvisor.data.repository.PositionData
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
    val scanStatus: ScanStatus = ScanStatus.IDLE
)

enum class ScanStatus {
    IDLE, SCANNING, SUCCESS, ERROR
}

data class RawHolding(
    val symbol: String?,
    val name: String,
    val shares: Int,
    val costPrice: Double
)

data class RawCapital(
    val totalAssets: Double,
    val availableCash: Double
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    fun loadPortfolio(context: Context) {
        viewModelScope.launch {
            _uiState.value = PortfolioUiState(isLoading = true)
            try {
                val positions = withContext(Dispatchers.IO) {
                    repository.loadPositionsFromLocal(context)
                }

                val items = positions.filter { it.value.shares > 0 }.map { (symbol, pos) ->
                    // 暂时用成本价作为当前价（后续可从实时数据获取）
                    val currentPrice = pos.costPrice
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
     * 从 URI 导入持仓（通过 SAF 文件选择器选择的 .md 文件）
     * 1. 读取文件内容（IO）
     * 2. 解析 .md 文件（Default）
     * 3. 5级代码匹配（Default）
     * 4. 写本地JSON（IO）
     * 5. 重新加载显示
     */
    fun importPortfolioFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 1. 读取文件内容
                val content = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    reader.readText().also { reader.close() }
                }

                // 2. 解析.md文件
                val (rawHoldings, capitalInfo) = withContext(Dispatchers.Default) {
                    parseMdPortfolio(content)
                }

                if (rawHoldings.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "未解析到任何持仓数据"
                    )
                    return@launch
                }

                // 3. 加载现有持仓 + 5级代码匹配
                val existingPositions = repository.loadPositionsFromLocal(context)
                val matchedPositions = performCodeMatching(rawHoldings, existingPositions)

                // 4. 写本地JSON
                repository.savePositionsToLocal(context, matchedPositions)
                if (capitalInfo != null) {
                    val capital = CapitalData(
                        availableCash = capitalInfo.availableCash,
                        totalCapital = capitalInfo.totalAssets,
                        lastUpdated = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        note = "从持仓文件同步"
                    )
                    repository.saveCapitalToLocal(context, capital)
                }

                // 5. 重新加载显示
                loadPortfolio(context)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "导入失败"
                )
            }
        }
    }

    /**
     * 从存储卡目录自动扫描并导入持仓（原有方式，保留兼容）
     * 1. 扫描 /storage/emulated/0/Documents/mindmaps/炒股/ 目录（IO）
     * 2. 解析 .md 文件（Default）
     * 3. 5级代码匹配（Default）
     * 4. 写本地JSON（IO）
     * 5. 重新加载显示
     */
    fun importFromMdFile(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, scanStatus = ScanStatus.SCANNING)

            try {
                // 1. 扫描目录 + 读取文件（IO 操作）
                val scanResult = withContext(Dispatchers.IO) {
                    com.tangtang.stockadvisor.util.MdFileScanner.scanAndParse()
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

                // 2. 转换为 RawHolding 列表
                val rawHoldings = withContext(Dispatchers.Default) {
                    scanResult.holdings.map { h ->
                        RawHolding(
                            symbol = (h["symbol"] as? String)?.takeIf { it.isNotEmpty() },
                            name = h["name"] as? String ?: "",
                            shares = (h["shares"] as? Number)?.toInt() ?: 0,
                            costPrice = (h["cost_price"] as? Number)?.toDouble() ?: 0.0
                        )
                    }
                }

                val capitalInfo = scanResult.capital?.let { cap ->
                    RawCapital(
                        totalAssets = (cap["total_capital"] as? Number)?.toDouble() ?: 0.0,
                        availableCash = (cap["available_cash"] as? Number)?.toDouble() ?: 0.0
                    )
                }

                // 3. 加载现有持仓 + 5级代码匹配
                val existingPositions = repository.loadPositionsFromLocal(context)
                val matchedPositions = performCodeMatching(rawHoldings, existingPositions)

                // 4. 写本地JSON
                repository.savePositionsToLocal(context, matchedPositions)
                if (capitalInfo != null) {
                    val capital = CapitalData(
                        availableCash = capitalInfo.availableCash,
                        totalCapital = capitalInfo.totalAssets,
                        lastUpdated = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        note = "从持仓文件同步"
                    )
                    repository.saveCapitalToLocal(context, capital)
                }

                // 5. 重新加载显示
                loadPortfolio(context)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scanStatus = ScanStatus.SUCCESS
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
     * 5级代码匹配
     * 对齐 position_manager_tool.py 的 import_from_latest_md() 方法
     * existingPositions 由调用方在协程中加载后传入
     */
    private fun performCodeMatching(
        rawHoldings: List<RawHolding>,
        existingPositions: Map<String, PositionData>
    ): Map<String, PositionData> {
        val result = mutableMapOf<String, PositionData>()
        val fileNames = rawHoldings.map { it.name }.toSet()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        for (raw in rawHoldings) {
            var finalSymbol: String? = null
            var matchMethod = "未匹配"

            // 第1级：文件中的股票代码列
            if (!raw.symbol.isNullOrEmpty() && raw.symbol != "0") {
                finalSymbol = raw.symbol
                matchMethod = "文件代码列"
            }

            // 第2级：现有持仓精确匹配名称
            if (finalSymbol == null) {
                for ((sym, info) in existingPositions) {
                    if (info.stockName == raw.name) {
                        finalSymbol = sym
                        matchMethod = "名称精确匹配"
                        break
                    }
                }
            }

            // 第3级：现有持仓模糊匹配
            if (finalSymbol == null) {
                finalSymbol = fuzzyMatchName(raw.name, existingPositions)
                if (finalSymbol != null) matchMethod = "名称模糊匹配"
            }

            // 第4级：跳过（App端无本地缓存）
            // 第5级：跳过
            if (finalSymbol == null) {
                Log.w("Portfolio", "跳过股票 '${raw.name}'：无代码且无法匹配")
                continue
            }

            result[finalSymbol] = PositionData(
                shares = raw.shares,
                costPrice = raw.costPrice,
                stockName = raw.name,
                lastUpdated = sdf.format(Date())
            )
            Log.d("Portfolio", "匹配成功: $finalSymbol - ${raw.name} ($matchMethod)")
        }

        // 清仓处理：数据库有但文件没有的股票 → shares=0
        for ((sym, info) in existingPositions) {
            if (info.stockName !in fileNames && info.shares > 0) {
                result[sym] = info.copy(shares = 0)
                Log.d("Portfolio", "标记清仓: $sym - ${info.stockName}")
            }
        }

        return result
    }

    /**
     * 模糊匹配名称
     * 对齐 position_manager_tool.py 的 _fuzzy_match_name() 方法
     */
    private fun fuzzyMatchName(name: String, positions: Map<String, PositionData>): String? {
        // 预处理：移除括号内容、空格，转小写
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

    /**
     * 解析 .md 持仓文件内容
     * 返回 (持仓列表, 资金信息)
     */
    private fun parseMdPortfolio(content: String): Pair<List<RawHolding>, RawCapital?> {
        val holdings = mutableListOf<RawHolding>()
        var totalAssets = 0.0
        var availableCash = 0.0

        // 解析总资产
        val totalAssetsMatch = Regex("总资产\\s*:\\s*([￥$]?\\s*[\\d,]+\\.?\\d*)").find(content)
        if (totalAssetsMatch != null) {
            totalAssets = cleanNumberStr(totalAssetsMatch.groupValues[1])
        }

        // 解析可用资金
        val availableCashMatch = Regex("可用资金\\s*:\\s*([￥$]?\\s*[\\d,]+\\.?\\d*)").find(content)
        if (availableCashMatch != null) {
            availableCash = cleanNumberStr(availableCashMatch.groupValues[1])
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
                    val shares = cleanNumberStr(sharesPart).toInt()

                    // 提取成本价
                    val costCell = cells[colIndex["cost"]!!].trim()
                    val costPrice = if (costCell == "-" || costCell.isEmpty() || "特殊" in costCell) {
                        0.0
                    } else {
                        cleanNumberStr(costCell)
                    }

                    holdings.add(
                        RawHolding(
                            symbol = symbol,
                            name = stockName,
                            shares = shares,
                            costPrice = costPrice
                        )
                    )
                }
            }
        }

        val capital = if (totalAssets > 0 || availableCash > 0) {
            RawCapital(totalAssets = totalAssets, availableCash = availableCash)
        } else null

        return Pair(holdings, capital)
    }

    /**
     * 清洗数字字符串，移除货币符号、千分位逗号等
     */
    private fun cleanNumberStr(text: String): Double {
        val cleaned = text.replace(Regex("[^\\d.-]"), "")
        return if (cleaned.isNotEmpty()) {
            cleaned.toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
    }

    fun importPortfolio(holdingsJson: String, capitalJson: String?) {
        // 此方法需要 Context，但签名中没有。保留兼容，实际应使用 importPortfolioFromUri 或 importFromMdFile
        Log.w("Portfolio", "importPortfolio(holdingsJson, capitalJson) 已废弃，请使用 importPortfolioFromUri 或 importFromMdFile")
    }

    fun addOrUpdatePosition(symbol: String, name: String, shares: Int, costPrice: Double, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.addOrUpdatePosition(context, symbol, name, shares, costPrice)
                result.onSuccess {
                    loadPortfolio(context)
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

    fun deletePosition(symbol: String, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.deletePosition(context, symbol)
                result.onSuccess {
                    loadPortfolio(context)
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

    fun clearAllPositions(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.clearAllPositions(context)
                result.onSuccess {
                    loadPortfolio(context)
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
