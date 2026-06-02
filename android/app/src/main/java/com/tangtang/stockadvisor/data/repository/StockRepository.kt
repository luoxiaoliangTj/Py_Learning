package com.tangtang.stockadvisor.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tangtang.stockadvisor.data.api.ApiClient
import com.tangtang.stockadvisor.data.api.BacktestRequest
import com.tangtang.stockadvisor.data.api.DownloadRequest
import com.tangtang.stockadvisor.data.api.OptimizeRequest
import com.tangtang.stockadvisor.data.api.PredictRequest
import com.tangtang.stockadvisor.data.api.RealtimePredictRequest
import com.tangtang.stockadvisor.data.api.SelectStockRequest
import com.tangtang.stockadvisor.data.api.UpdateCapitalRequest
import com.tangtang.stockadvisor.data.api.PositionUpdateRequest
import com.tangtang.stockadvisor.data.model.BacktestResult
import com.tangtang.stockadvisor.data.model.OnlinePredictionResult
import com.tangtang.stockadvisor.data.model.PortfolioItem
import com.tangtang.stockadvisor.data.model.PortfolioSummary
import com.tangtang.stockadvisor.data.model.PredictionResult
import com.tangtang.stockadvisor.data.model.StockInfo
import com.tangtang.stockadvisor.data.model.StrategyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PositionData(
    val shares: Int,
    val costPrice: Double,
    val stockName: String,
    val lastUpdated: String
)

data class CapitalData(
    val availableCash: Double,
    val totalCapital: Double,
    val lastUpdated: String,
    val note: String = ""
)

@Singleton
class StockRepository @Inject constructor(
    private val apiClient: ApiClient
) {

    fun getStockList(): Flow<Result<List<StockInfo>>> = flow {
        try {
            emit(Result.success(apiClient.getStockList()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun selectStock(
        symbol: String,
        name: String = "",
        indexSymbol: String = "",
        indexName: String = ""
    ): Result<StockInfo> {
        return try {
            Result.success(apiClient.selectStock(
                SelectStockRequest(symbol, name, indexSymbol, indexName)
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDailyPrediction(symbol: String): Flow<Result<PredictionResult>> = flow {
        try {
            emit(Result.success(apiClient.getDailyPrediction(PredictRequest(symbol))))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun getRealtimePrediction(symbol: String, prevClose: Double? = null): Flow<Result<OnlinePredictionResult>> = flow {
        try {
            emit(Result.success(apiClient.getRealtimePrediction(RealtimePredictRequest(symbol, prevClose))))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun runBacktest(
        symbol: String,
        strategyType: String,
        params: Map<String, Any>? = null
    ): Flow<Result<BacktestResult>> = flow {
        try {
            emit(Result.success(apiClient.runBacktest(BacktestRequest(symbol, strategyType, params))))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun getHoldings(): Flow<Result<List<PortfolioItem>>> = flow {
        try {
            // 从本地JSON读取持仓，不再调用后端API
            // 注意：此方法需要 Context 才能读取文件，但 Flow 签名无法直接传入 Context。
            // 调用方应使用 loadPositionsFromLocal(context) 直接获取本地持仓数据。
            // 此处返回空列表作为兼容保留。
            emit(Result.success(emptyList()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun getCapital(): Flow<Result<PortfolioSummary>> = flow {
        try {
            emit(Result.success(apiClient.getCapital()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun updateCapital(
        availableCash: Double? = null,
        totalCapital: Double? = null
    ): Result<PortfolioSummary> {
        return try {
            Result.success(apiClient.updateCapital(UpdateCapitalRequest(availableCash, totalCapital)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getStrategyList(): Flow<Result<List<StrategyInfo>>> = flow {
        try {
            emit(Result.success(apiClient.getStrategyList()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun optimizeStrategy(
        symbol: String,
        strategyType: String
    ): Flow<Result<Map<String, Any>>> = flow {
        try {
            emit(Result.success(apiClient.optimizeStrategy(OptimizeRequest(symbol, strategyType))))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun downloadData(
        symbol: String,
        years: Int? = null,
        source: String? = null
    ): Result<Map<String, Any>> {
        return try {
            Result.success(apiClient.downloadData(DownloadRequest(symbol, years, source)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importPortfolio(
        context: Context,
        holdingsJson: String,
        capitalJson: String?
    ): Result<Boolean> {
        return try {
            // 解析持仓JSON: Map<String, PositionData>
            val positionsType = object : TypeToken<Map<String, PositionData>>() {}.type
            val positions: Map<String, PositionData> = gson.fromJson(holdingsJson, positionsType)
                ?: emptyMap()

            // 保存持仓到本地
            savePositionsToLocal(context, positions)

            // 如果有资金信息，解析并保存
            if (capitalJson != null) {
                val capital: CapitalData = gson.fromJson(capitalJson, CapitalData::class.java)
                saveCapitalToLocal(context, capital)
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addOrUpdatePosition(
        context: Context,
        symbol: String,
        name: String,
        shares: Int,
        costPrice: Double
    ): Result<Boolean> {
        return try {
            val positions = loadPositionsFromLocal(context).toMutableMap()
            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            positions[symbol] = PositionData(
                shares = shares,
                costPrice = costPrice,
                stockName = name,
                lastUpdated = now
            )
            savePositionsToLocal(context, positions)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePosition(context: Context, symbol: String): Result<Boolean> {
        return try {
            val positions = loadPositionsFromLocal(context).toMutableMap()
            positions.remove(symbol)
            savePositionsToLocal(context, positions)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllPositions(context: Context): Result<Int> {
        return try {
            val positions = loadPositionsFromLocal(context)
            val count = positions.size
            savePositionsToLocal(context, emptyMap())
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Local JSON Storage ===================

    private val gson = Gson()

    private fun getDataDir(context: Context): File {
        val dir = File(context.filesDir, "data")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun savePositionsToLocal(context: Context, positions: Map<String, PositionData>) {
        withContext(Dispatchers.IO) {
            val file = File(getDataDir(context), "real_positions.json")
            val jsonString = gson.toJson(positions)
            file.writeText(jsonString)
        }
    }

    suspend fun loadPositionsFromLocal(context: Context): Map<String, PositionData> {
        return withContext(Dispatchers.IO) {
            val file = File(getDataDir(context), "real_positions.json")
            if (!file.exists()) return@withContext emptyMap()
            val jsonString = file.readText()
            val type = object : TypeToken<Map<String, PositionData>>() {}.type
            gson.fromJson(jsonString, type) ?: emptyMap()
        }
    }

    suspend fun saveCapitalToLocal(context: Context, capital: CapitalData) {
        withContext(Dispatchers.IO) {
            val file = File(getDataDir(context), "global_capital.json")
            val jsonString = gson.toJson(capital)
            file.writeText(jsonString)
        }
    }

    suspend fun loadCapitalFromLocal(context: Context): CapitalData {
        return withContext(Dispatchers.IO) {
            val file = File(getDataDir(context), "global_capital.json")
            if (!file.exists()) return@withContext CapitalData(
                availableCash = 100000.0,
                totalCapital = 100000.0,
                lastUpdated = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date()),
                note = "默认资金配置"
            )
            try {
                val jsonString = file.readText()
                gson.fromJson(jsonString, CapitalData::class.java) ?: CapitalData(
                    availableCash = 100000.0,
                    totalCapital = 100000.0,
                    lastUpdated = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date()),
                    note = "默认资金配置（解析失败）"
                )
            } catch (e: Exception) {
                CapitalData(
                    availableCash = 100000.0,
                    totalCapital = 100000.0,
                    lastUpdated = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date()),
                    note = "默认资金配置（读取异常）"
                )
            }
        }
    }

    // ==================== Strategy (extended) ===================

    fun getStrategy(symbol: String): Flow<Result<StrategyInfo>> = flow {
        try {
            emit(Result.success(apiClient.getStrategy(symbol)))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun saveStrategy(symbol: String, strategyType: String): Flow<Result<StrategyInfo>> = flow {
        try {
            emit(Result.success(apiClient.saveStrategy(symbol, com.tangtang.stockadvisor.data.api.StrategySaveRequest(symbol, strategyType))))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun deleteStrategy(symbol: String): Flow<Result<Boolean>> = flow {
        try {
            emit(Result.success(apiClient.deleteStrategy(symbol)))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // ==================== Logs ===================

    fun getLogList(): Flow<Result<Map<String, Map<String, Any>>>> = flow {
        try {
            emit(Result.success(apiClient.getLogList()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun getLog(date: String): Flow<Result<String>> = flow {
        try {
            emit(Result.success(apiClient.getLog(date)))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun deleteLog(date: String): Result<Boolean> {
        return try {
            Result.success(apiClient.deleteLog(date))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Tools (extended) ===================

    fun getToolsList(): Flow<Result<Map<String, Map<String, Any>>>> = flow {
        try {
            emit(Result.success(apiClient.getTools()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun runTool(toolName: String, params: Map<String, Any>): Flow<Result<Map<String, Any>>> = flow {
        // 工具箱通用执行端点（暂未实现，预留接口）
        emit(Result.failure(Exception("工具执行功能暂未实现: $toolName")))
    }

    fun getDownloadStatus(symbol: String): Flow<Result<Map<String, Any>>> = flow {
        try {
            emit(Result.success(apiClient.getDownloadStatus(symbol)))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // ==================== Realtime Data ===================

    fun getRealtimeData(symbol: String): Flow<Result<Map<String, Any>>> = flow {
        try {
            emit(Result.success(apiClient.getRealtimeData(symbol)))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}
