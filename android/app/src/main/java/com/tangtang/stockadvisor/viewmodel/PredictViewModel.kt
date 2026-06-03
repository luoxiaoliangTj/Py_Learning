package com.tangtang.stockadvisor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.stockadvisor.data.model.StockPrice
import com.tangtang.stockadvisor.data.remote.HistoricalDataDownloader
import com.tangtang.stockadvisor.data.remote.RealtimeDataSource
import com.tangtang.stockadvisor.engine.TradingAdvisor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

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
    private val historicalDataDownloader: HistoricalDataDownloader,
    private val realtimeDataSource: RealtimeDataSource
) : ViewModel() {

    companion object {
        private const val TAG = "PredictViewModel"
    }

    private val _uiState = MutableStateFlow(PredictUiState())
    val uiState: StateFlow<PredictUiState> = _uiState.asStateFlow()

    private data class Prediction(
        val high: Double,
        val low: Double,
        val close: Double,
        val confidence: Double
    )

    fun loadPrediction(symbol: String) {
        _uiState.value = PredictUiState(
            symbol = symbol,
            isLoading = true
        )

        viewModelScope.launch {
            try {
                // Step 1: Fetch realtime price
                val realtimeData = realtimeDataSource.fetchRealtimeData(symbol)
                if (!realtimeData.valid || realtimeData.price <= 0) {
                    _uiState.value = PredictUiState(
                        symbol = symbol,
                        isLoading = false,
                        error = "无法获取实时价格"
                    )
                    return@launch
                }

                // Step 2: Download K-line data (5 years)
                val downloadResult = historicalDataDownloader.downloadDailyData(symbol, 5)
                Log.i(TAG, "K-line download: success=${downloadResult.success}, " +
                        "records=${downloadResult.recordCount}, source=${downloadResult.source}")

                // Step 3: Read CSV records and convert to StockPrice list
                val klines = if (downloadResult.success && downloadResult.filePath != null) {
                    readCsvStockPrices(downloadResult.filePath)
                } else {
                    emptyList()
                }
                Log.i(TAG, "Loaded ${klines.size} K-line records")

                // Step 4: Compute predictions from K-line data
                val prediction = computePredictions(klines, realtimeData.price)

                // Step 5: Use TradingAdvisor for trading advice
                val trendStrength = TradingAdvisor.analyzeTrendStrength(klines)
                val volatility = if (klines.size >= 10) {
                    computeVolatility(klines)
                } else {
                    0.02
                }

                val advice = TradingAdvisor.generateTradingAdvice(
                    position = null, // No position info in prediction mode
                    currentPrice = realtimeData.price,
                    predictedLow = prediction.low,
                    predictedHigh = prediction.high,
                    volatility = volatility,
                    trendStrength = trendStrength
                )

                // Step 6: Build signal list from advice
                val signals = mutableListOf(
                    SignalInfo(
                        name = "交易建议",
                        signal = advice.action.labelCn,
                        weight = advice.confidence
                    ),
                    SignalInfo(
                        name = "趋势分析",
                        signal = when {
                            trendStrength > 0.01 -> "上升趋势"
                            trendStrength < -0.01 -> "下降趋势"
                            else -> "震荡"
                        },
                        weight = abs(trendStrength) * 10.0
                    ),
                    SignalInfo(
                        name = "波动率",
                        signal = "${String.format("%.2f", volatility * 100)}%",
                        weight = volatility * 5.0
                    )
                )

                if (advice.profitThresholds != null) {
                    val t = advice.profitThresholds
                    signals.add(
                        SignalInfo(
                            name = "止盈阈值",
                            signal = "${String.format("%.1f", t.profitThreshold * 100)}%",
                            weight = 0.5
                        )
                    )
                    signals.add(
                        SignalInfo(
                            name = "止损阈值",
                            signal = "${String.format("%.1f", t.lossThreshold * 100)}%",
                            weight = 0.5
                        )
                    )
                }

                // Step 7: Fill PredictUiState
                _uiState.value = PredictUiState(
                    isLoading = false,
                    symbol = symbol,
                    stockName = realtimeData.name,
                    currentPrice = realtimeData.price,
                    predictedHigh = prediction.high,
                    predictedLow = prediction.low,
                    predictedClose = prediction.close,
                    confidence = prediction.confidence,
                    recommendation = "${advice.action.labelCn}: ${advice.reason}",
                    signals = signals,
                    error = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Prediction failed: ${e.message}", e)
                _uiState.value = PredictUiState(
                    symbol = symbol,
                    isLoading = false,
                    error = "预测失败: ${e.message}"
                )
            }
        }
    }

    /**
     * Read CSV file and convert to StockPrice list
     */
    private fun readCsvStockPrices(filePath: String): List<StockPrice> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()

        val prices = mutableListOf<StockPrice>()
        try {
            BufferedReader(java.io.InputStreamReader(java.io.FileInputStream(file), Charsets.UTF_8)).use { reader ->
                var line = reader.readLine() ?: return emptyList()
                // Skip BOM
                if (line.startsWith("\uFEFF")) line = line.substring(1)

                // Parse header
                val headers = line.split(",").map { it.trim() }
                val dateIdx = headers.indexOf("日期")
                val openIdx = headers.indexOf("开盘")
                val highIdx = headers.indexOf("最高")
                val lowIdx = headers.indexOf("最低")
                val closeIdx = headers.indexOf("收盘")
                val volumeIdx = headers.indexOf("成交量")

                if (dateIdx < 0 || closeIdx < 0) return emptyList()

                while (true) {
                    line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val cols = line.split(",").map { it.trim() }
                    if (cols.size <= maxOf(dateIdx, openIdx, highIdx, lowIdx, closeIdx)) continue

                    val date = cols[dateIdx]
                    val open = cols[openIdx].toDoubleOrNull() ?: continue
                    val high = cols[highIdx].toDoubleOrNull() ?: continue
                    val low = cols[lowIdx].toDoubleOrNull() ?: continue
                    val close = cols[closeIdx].toDoubleOrNull() ?: continue
                    val volume = if (volumeIdx >= 0 && volumeIdx < cols.size) {
                        cols[volumeIdx].toLongOrNull() ?: 0L
                    } else 0L

                    prices.add(StockPrice(date, open, high, low, close, volume))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CSV read error: ${e.message}", e)
        }
        return prices.sortedBy { it.date }
    }

    /**
     * Compute predictions based on historical K-line data
     */
    private fun computePredictions(klines: List<StockPrice>, currentPrice: Double): Prediction {
        if (klines.size < 5 || currentPrice <= 0) {
            // Not enough data, use simple estimation
            return Prediction(
                high = currentPrice * 1.03,
                low = currentPrice * 0.97,
                close = currentPrice,
                confidence = 0.3
            )
        }

        val recentCloses = klines.takeLast(20).map { it.close }

        // Volatility (standard deviation of daily returns)
        val returns = recentCloses.zipWithNext { a, b -> (b - a) / a }
        val avgReturn = returns.average()
        val variance = returns.map { (it - avgReturn) * (it - avgReturn) }.average()
        val volatility = sqrt(variance)

        // Trend strength via TradingAdvisor
        val trendStrength = TradingAdvisor.analyzeTrendStrength(klines)

        // Predict based on trend and volatility
        val predictedClose = currentPrice * (1 + avgReturn * 2 + trendStrength * 10)
        val range = currentPrice * volatility * 2.0
        val predictedHigh = currentPrice + range * 1.5
        val predictedLow = currentPrice - range * 1.2

        // Confidence based on data quality
        val dataConfidence = when {
            klines.size >= 100 -> 0.7
            klines.size >= 50 -> 0.5
            else -> 0.3
        }
        val confidence = dataConfidence * (1.0 - volatility * 5.0).coerceIn(0.1, 1.0)

        return Prediction(
            high = String.format("%.2f", predictedHigh).toDouble(),
            low = String.format("%.2f", predictedLow).toDouble(),
            close = String.format("%.2f", predictedClose).toDouble(),
            confidence = String.format("%.2f", confidence).toDouble()
        )
    }

    /**
     * Compute volatility from recent K-line data
     */
    private fun computeVolatility(klines: List<StockPrice>): Double {
        if (klines.size < 10) return 0.02
        val recentCloses = klines.takeLast(20).map { it.close }
        val returns = recentCloses.zipWithNext { a, b -> (b - a) / a }
        val avgReturn = returns.average()
        val variance = returns.map { (it - avgReturn) * (it - avgReturn) }.average()
        return sqrt(variance).coerceIn(0.005, 0.1)
    }
}
