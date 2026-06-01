package com.tangtang.stockadvisor.data.repository

import com.tangtang.stockadvisor.data.api.BacktestRequest
import com.tangtang.stockadvisor.data.api.DownloadRequest
import com.tangtang.stockadvisor.data.api.OptimizeRequest
import com.tangtang.stockadvisor.data.api.PredictRequest
import com.tangtang.stockadvisor.data.api.RealtimePredictRequest
import com.tangtang.stockadvisor.data.api.SelectStockRequest
import com.tangtang.stockadvisor.data.api.StockApiService
import com.tangtang.stockadvisor.data.api.UpdateCapitalRequest
import com.tangtang.stockadvisor.data.model.BacktestResponse
import com.tangtang.stockadvisor.data.model.CapitalResponse
import com.tangtang.stockadvisor.data.model.DailyPredictionResponse
import com.tangtang.stockadvisor.data.model.HoldingsResponse
import com.tangtang.stockadvisor.data.model.MapResponse
import com.tangtang.stockadvisor.data.model.RealtimePredictionResponse
import com.tangtang.stockadvisor.data.model.StockListResponse
import com.tangtang.stockadvisor.data.model.StockSelectResponse
import com.tangtang.stockadvisor.data.model.StrategyInfo
import com.tangtang.stockadvisor.data.model.StrategyListResponse
import com.tangtang.stockadvisor.data.model.BacktestResult
import com.tangtang.stockadvisor.data.model.OnlinePredictionResult
import com.tangtang.stockadvisor.data.model.PortfolioItem
import com.tangtang.stockadvisor.data.model.PortfolioSummary
import com.tangtang.stockadvisor.data.model.PredictionResult
import com.tangtang.stockadvisor.data.model.StockInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepository @Inject constructor(
    private val apiService: StockApiService
) {

    // ==================== Stock Data ====================

    fun getStockList(): Flow<Result<List<StockInfo>>> = flow {
        try {
            val response = apiService.getStockList()
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
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
            val response = apiService.selectStock(
                SelectStockRequest(
                    symbol = symbol,
                    name = name,
                    index_symbol = indexSymbol,
                    index_name = indexName
                )
            )
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Prediction ====================

    fun getDailyPrediction(symbol: String): Flow<Result<PredictionResult>> = flow {
        try {
            val response = apiService.getDailyPrediction(PredictRequest(symbol = symbol))
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun getRealtimePrediction(symbol: String, prevClose: Double? = null): Flow<Result<OnlinePredictionResult>> = flow {
        try {
            val response = apiService.getRealtimePrediction(
                RealtimePredictRequest(symbol = symbol, prev_close = prevClose)
            )
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // ==================== Backtest ====================

    fun runBacktest(
        symbol: String,
        strategyType: String,
        params: Map<String, Any>? = null
    ): Flow<Result<BacktestResult>> = flow {
        try {
            val request = BacktestRequest(
                symbol = symbol,
                strategy_type = strategyType,
                params = params
            )
            val response = apiService.runBacktest(request)
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // ==================== Portfolio ====================

    fun getHoldings(): Flow<Result<List<PortfolioItem>>> = flow {
        try {
            val response = apiService.getHoldings()
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun getCapital(): Flow<Result<PortfolioSummary>> = flow {
        try {
            val response = apiService.getCapital()
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun updateCapital(
        availableCash: Double? = null,
        totalCapital: Double? = null
    ): Result<PortfolioSummary> {
        return try {
            val response = apiService.updateCapital(
                UpdateCapitalRequest(
                    available_cash = availableCash,
                    total_capital = totalCapital
                )
            )
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Strategy ====================

    fun getStrategyList(): Flow<Result<List<StrategyInfo>>> = flow {
        try {
            val response = apiService.getStrategyList()
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun optimizeStrategy(
        symbol: String,
        strategyType: String
    ): Flow<Result<Map<String, Any>>> = flow {
        try {
            val response = apiService.optimizeStrategy(
                OptimizeRequest(symbol = symbol, strategy_type = strategyType)
            )
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // ==================== Tools ====================

    suspend fun downloadData(
        symbol: String,
        years: Int? = null,
        source: String? = null
    ): Result<Map<String, Any>> {
        return try {
            val response = apiService.downloadData(
                DownloadRequest(symbol = symbol, years = years, source = source)
            )
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
