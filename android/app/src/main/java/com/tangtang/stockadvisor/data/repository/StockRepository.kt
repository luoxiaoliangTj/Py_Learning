package com.tangtang.stockadvisor.data.repository

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

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
            emit(Result.success(apiClient.getHoldings()))
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
        holdingsJson: String,
        capitalJson: String?
    ): Result<Boolean> {
        return try {
            apiClient.importPortfolio(holdingsJson, capitalJson)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addOrUpdatePosition(
        symbol: String,
        name: String,
        shares: Int,
        costPrice: Double
    ): Result<Boolean> {
        return try {
            apiClient.addOrUpdatePosition(
                PositionUpdateRequest(symbol, name, shares, costPrice)
            )
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePosition(symbol: String): Result<Boolean> {
        return try {
            Result.success(apiClient.deletePosition(symbol))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllPositions(): Result<Int> {
        return try {
            Result.success(apiClient.clearAllPositions())
        } catch (e: Exception) {
            Result.failure(e)
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
        try {
            val request = mapOf("tool_name" to toolName, "params" to params)
            emit(Result.success(apiClient.downloadData(com.tangtang.stockadvisor.data.api.DownloadRequest(toolName))))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
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
