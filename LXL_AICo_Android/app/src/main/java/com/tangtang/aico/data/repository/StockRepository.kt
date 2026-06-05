package com.tangtang.aico.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tangtang.aico.data.remote.HistoricalDataDownloader
import com.tangtang.aico.data.remote.RealtimeDataSource
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
    private val realtimeDataSource: RealtimeDataSource,
    private val historicalDataDownloader: HistoricalDataDownloader
) {

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

    // ==================== Portfolio Operations (Local) ===================

    suspend fun importPortfolio(
        context: Context,
        holdingsJson: String,
        capitalJson: String?
    ): Result<Boolean> {
        return try {
            val positionsType = object : TypeToken<Map<String, PositionData>>() {}.type
            val positions: Map<String, PositionData> = gson.fromJson(holdingsJson, positionsType)
                ?: emptyMap()
            savePositionsToLocal(context, positions)
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

    // ==================== Realtime Data (via RealtimeDataSource) ===================

    fun getRealtimeData(symbol: String): Flow<Result<Map<String, Any>>> = flow {
        try {
            val data = realtimeDataSource.fetchRealtimeData(symbol)
            val map = mapOf(
                "name" to data.name,
                "current_price" to data.price,
                "prev_close" to data.prevClose,
                "open" to data.open,
                "high" to data.high,
                "low" to data.low,
                "change" to data.change,
                "change_percent" to data.changePct,
                "volume" to data.volume,
                "amount" to data.amount,
                "date" to data.date,
                "time" to data.time,
                "source" to data.source,
                "valid" to data.valid
            )
            emit(Result.success(map))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun getRealtimeDataForStockList(symbols: List<String>): Flow<Result<Map<String, com.tangtang.aico.data.model.StockInfo>>> = flow {
        try {
            val batchData = realtimeDataSource.fetchRealtimeDataBatch(symbols)
            val stockMap = batchData.mapValues { (_, data) ->
                realtimeDataSource.toStockInfo(data)
            }
            emit(Result.success(stockMap))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // ==================== Historical Data Download (via HistoricalDataDownloader) ===================

    suspend fun downloadData(
        symbol: String,
        years: Int? = null,
        source: String? = null
    ): Result<Map<String, Any>> {
        return try {
            val result = historicalDataDownloader.downloadDailyData(symbol, years ?: 8)
            val map = mapOf(
                "success" to result.success,
                "source" to result.source,
                "record_count" to result.recordCount,
                "file_path" to (result.filePath ?: ""),
                "message" to result.message
            )
            Result.success(map)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDownloadStatus(symbol: String): Flow<Result<Map<String, Any>>> = flow {
        try {
            val check = historicalDataDownloader.checkExistingData(symbol)
            val map = mapOf(
                "exists" to check.exists,
                "message" to check.message,
                "record_count" to check.recordCount,
                "date_range" to check.dateRange
            )
            emit(Result.success(map))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // ==================== Tools (stub) ===================

    fun runTool(toolName: String, params: Map<String, Any>): Flow<Result<Map<String, Any>>> = flow {
        emit(Result.failure(Exception("工具执行功能暂未实现: $toolName")))
    }
}
