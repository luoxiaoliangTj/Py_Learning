package com.tangtang.stockadvisor.data.repository

import com.tangtang.stockadvisor.data.api.StockApiService
import com.tangtang.stockadvisor.data.model.ApiResponse
import com.tangtang.stockadvisor.data.model.BacktestResult
import com.tangtang.stockadvisor.data.model.OnlinePredictionResult
import com.tangtang.stockadvisor.data.model.PortfolioItem
import com.tangtang.stockadvisor.data.model.PortfolioSummary
import com.tangtang.stockadvisor.data.model.PredictionResult
import com.tangtang.stockadvisor.data.model.StockInfo
import com.tangtang.stockadvisor.data.model.StockPrice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepository @Inject constructor(
    private val apiService: StockApiService
) {

    // ==================== Stock Data ====================

    suspend fun getStockInfo(code: String): Result<StockInfo> {
        return try {
            val response = apiService.getStockInfo(code)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStockPrices(
        code: String,
        startDate: String? = null,
        endDate: String? = null,
        period: String = "daily"
    ): Result<List<StockPrice>> {
        return try {
            val response = apiService.getStockPrices(code, startDate, endDate, period)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun searchStocks(keyword: String): Flow<Result<List<StockInfo>>> = flow {
        try {
            val response = apiService.searchStocks(keyword)
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun getMarketOverview(): Flow<Result<List<StockInfo>>> = flow {
        try {
            val response = apiService.getMarketOverview()
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // ==================== Prediction ====================

    fun getPrediction(code: String): Flow<Result<PredictionResult>> = flow {
        try {
            val response = apiService.getPrediction(code)
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun getOnlinePrediction(code: String): Flow<Result<OnlinePredictionResult>> = flow {
        try {
            val response = apiService.getOnlinePrediction(code)
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
        code: String,
        startDate: String,
        endDate: String,
        initialCapital: Double = 100000.0,
        strategy: String = "default"
    ): Flow<Result<BacktestResult>> = flow {
        try {
            val request = com.tangtang.stockadvisor.data.api.BacktestRequest(
                code = code,
                start_date = startDate,
                end_date = endDate,
                initial_capital = initialCapital,
                strategy = strategy
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

    fun getPortfolio(): Flow<Result<PortfolioSummary>> = flow {
        try {
            val response = apiService.getPortfolio()
            if (response.code == 200 && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun addToPortfolio(item: PortfolioItem): Result<PortfolioSummary> {
        return try {
            val response = apiService.addToPortfolio(item)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePortfolioItem(item: PortfolioItem): Result<PortfolioSummary> {
        return try {
            val response = apiService.updatePortfolioItem(item)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFromPortfolio(code: String): Result<PortfolioSummary> {
        return try {
            val response = apiService.removeFromPortfolio(code)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Settings ====================

    suspend fun validateToken(token: String): Result<Boolean> {
        return try {
            val response = apiService.validateToken(token)
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
