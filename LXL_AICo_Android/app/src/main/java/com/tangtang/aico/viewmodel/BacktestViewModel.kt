package com.tangtang.aico.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.aico.data.model.StockPrice
import com.tangtang.aico.data.remote.HistoricalDataDownloader
import com.tangtang.aico.data.repository.StockRepository
import com.tangtang.aico.engine.BacktestEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BacktestUiState(
    val isLoading: Boolean = true,
    val symbol: String = "",
    val stockName: String = "",
    val strategyType: String = "channel",
    val totalReturn: Double = 0.0,
    val annualReturn: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val sharpeRatio: Double = 0.0,
    val winRate: Double = 0.0,
    val totalTrades: Int = 0,
    val finalCapital: Double = 0.0,
    val initialCapital: Double = 100000.0,
    val error: String? = null,
    val equityCurve: List<Pair<String, Double>> = emptyList(),
    // 数据下载配置
    val dataSource: String = "sina",
    val dataYears: Int = 8,
    // 策略详情
    val strategyDetail: String = "",
    val pluginName: String = "atr_channel",
    val downloadMessage: String? = null,
    val dataCount: Int = 0,
    val dateRange: String = ""
)

@HiltViewModel
class BacktestViewModel @Inject constructor(
    private val repository: StockRepository,
    private val historicalDataDownloader: HistoricalDataDownloader
) : ViewModel() {

    companion object {
        private const val TAG = "BacktestVM"
    }

    private val _uiState = MutableStateFlow(BacktestUiState())
    val uiState: StateFlow<BacktestUiState> = _uiState.asStateFlow()

    fun runBacktest(
        symbol: String,
        strategyType: String = "channel",
        initialCapital: Double = 100000.0,
        dataSource: String = "sina",
        dataYears: Int = 8,
        pluginName: String = "atr_channel"
    ) {
        // 防止重复调用
        if (_uiState.value.isLoading && _uiState.value.symbol == symbol) {
            Log.w(TAG, "回测进行中，忽略重复请求: $symbol/$strategyType")
            return
        }

        viewModelScope.launch {
            _uiState.value = BacktestUiState(
                symbol = symbol,
                strategyType = strategyType,
                isLoading = true,
                initialCapital = initialCapital,
                dataSource = dataSource,
                dataYears = dataYears,
                pluginName = pluginName
            )

            try {
                // Step 1: Download K-line data
                Log.i(TAG, "开始回测 $symbol，策略: $strategyType, 数据源: $dataSource, 年数: $dataYears")

                val existingCheck = withContext(Dispatchers.IO) {
                    historicalDataDownloader.checkExistingData(symbol)
                }

                val records: List<Map<String, String>> = if (existingCheck.exists && existingCheck.recordCount >= 25) {
                    Log.i(TAG, "使用已有数据: ${existingCheck.recordCount}条")
                    existingCheck.details ?: emptyList()
                } else {
                    Log.i(TAG, "下载历史数据... (本地数据: exists=${existingCheck.exists}, count=${existingCheck.recordCount})")
                    val downloadResult = historicalDataDownloader.downloadDailyData(symbol, dataYears, dataSource)
                    if (!downloadResult.success) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "数据下载失败: ${downloadResult.message}\n\n建议：\n1. 检查网络连接\n2. 切换数据源后重试\n3. 稍后再试"
                        )
                        return@launch
                    }
                    Log.i(TAG, "下载成功: ${downloadResult.recordCount}条，来源: ${downloadResult.source}")

                    if (downloadResult.records != null && downloadResult.records.isNotEmpty()) {
                        Log.i(TAG, "使用下载时已有的数据: ${downloadResult.records.size}条")
                        downloadResult.records
                    } else {
                        Log.i(TAG, "从CSV文件读取数据: ${downloadResult.filePath}")
                        val freshCheck = withContext(Dispatchers.IO) {
                            historicalDataDownloader.checkExistingData(symbol)
                        }
                        Log.i(TAG, "读取下载数据: exists=${freshCheck.exists}, count=${freshCheck.recordCount}")
                        if (!freshCheck.exists || freshCheck.details == null) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "数据下载成功但读取失败\n文件路径: ${downloadResult.filePath}\n\n建议：重启App后重试"
                            )
                            return@launch
                        }
                        freshCheck.details
                    }
                }

                if (records.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "未获取到有效K线数据\n\n建议：\n1. 检查股票代码是否正确 ($symbol)\n2. 切换数据源后重试"
                    )
                    return@launch
                }

                // Step 2: Convert CSV records to StockPrice list
                val klines = convertRecordsToStockPrice(records)
                Log.i(TAG, "转换完成: ${klines.size}条K线数据")

                if (klines.size < 25) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "数据不足: 需要至少25根K线，当前仅${klines.size}根\n\n建议：增加下载年数或切换数据源"
                    )
                    return@launch
                }

                // Step 3: Map strategy type
                val engineStrategyType = when (strategyType.lowercase()) {
                    "trend" -> BacktestEngine.StrategyType.TREND
                    else -> BacktestEngine.StrategyType.CHANNEL
                }

                val params = when (engineStrategyType) {
                    BacktestEngine.StrategyType.CHANNEL -> mapOf("plugin" to pluginName, "k" to 2.0)
                    BacktestEngine.StrategyType.TREND -> mapOf("fastPeriod" to 10, "slowPeriod" to 30)
                }

                // Step 4: Run backtest
                Log.i(TAG, "执行回测: ${klines.size}条K线, 策略=$strategyType, 插件=$pluginName, 初始资金=$initialCapital")
                val result = BacktestEngine.runBacktest(
                    klines = klines,
                    strategyType = engineStrategyType,
                    params = params,
                    initialCash = initialCapital
                )

                Log.i(TAG, "回测完成: 总收益=${result.totalReturn}, 年化=${result.annualReturn}, 最大回撤=${result.maxDrawdown}, 交易次数=${result.totalTrades}")

                // Step 5: Build strategy detail string
                val strategyDetail = buildString {
                    append("策略: ${if (strategyType == "channel") "通道策略" else "趋势策略"}")
                    if (strategyType == "channel") {
                        append(" | 插件: $pluginName")
                    } else {
                        append(" | MA(10/30)")
                    }
                    append(" | 数据源: ${dataSourceLabel(dataSource)}")
                    append(" | ${dataYears}年 | ${klines.size}条K线")
                }

                // Step 6: Display results
                _uiState.value = BacktestUiState(
                    symbol = symbol,
                    stockName = symbol,
                    strategyType = strategyType,
                    isLoading = false,
                    totalReturn = result.totalReturn * 100.0,
                    annualReturn = result.annualReturn * 100.0,
                    maxDrawdown = result.maxDrawdown,
                    sharpeRatio = result.sharpeRatio,
                    winRate = result.winRate,
                    totalTrades = result.totalTrades,
                    finalCapital = result.finalCapital,
                    initialCapital = result.initialCapital,
                    error = null,
                    equityCurve = try {
                        result.equityCurve.map { it.date to it.value }
                    } catch (e: Exception) {
                        Log.w(TAG, "权益曲线转换失败: ${e.message}")
                        emptyList()
                    },
                    dataSource = dataSource,
                    dataYears = dataYears,
                    pluginName = pluginName,
                    strategyDetail = strategyDetail,
                    dataCount = klines.size,
                    dateRange = if (klines.size >= 2) {
                        "${klines.first().date} 至 ${klines.last().date}"
                    } else ""
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // 不吞掉 CancellationException
            } catch (e: Exception) {
                Log.e(TAG, "回测异常: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "回测失败: ${e.message}"
                )
            }
        }
    }

    private fun dataSourceLabel(source: String): String = when (source) {
        "sina" -> "新浪财经"
        "sohu" -> "搜狐财经"
        "tushare" -> "Tushare"
        "all" -> "自动选择"
        else -> source
    }

    /**
     * Convert CSV records (with Chinese keys from HistoricalDataDownloader) to StockPrice list
     */
    private fun convertRecordsToStockPrice(records: List<Map<String, String>>): List<StockPrice> {
        return records.mapNotNull { record ->
            try {
                val date = record["日期"] ?: record["date"] ?: return@mapNotNull null
                val open = record["开盘"]?.toDoubleOrNull() ?: record["open"]?.toDoubleOrNull() ?: return@mapNotNull null
                val high = record["最高"]?.toDoubleOrNull() ?: record["high"]?.toDoubleOrNull() ?: return@mapNotNull null
                val low = record["最低"]?.toDoubleOrNull() ?: record["low"]?.toDoubleOrNull() ?: return@mapNotNull null
                val close = record["收盘"]?.toDoubleOrNull() ?: record["close"]?.toDoubleOrNull() ?: return@mapNotNull null
                val volume = record["成交量"]?.toLongOrNull() ?: record["volume"]?.toLongOrNull() ?: 0L

                StockPrice(
                    date = date,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume
                )
            } catch (e: Exception) {
                Log.w(TAG, "跳过无效记录: ${e.message}")
                null
            }
        }.sortedBy { it.date }
    }
}
