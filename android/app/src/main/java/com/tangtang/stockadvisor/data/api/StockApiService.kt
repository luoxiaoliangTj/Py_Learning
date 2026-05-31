package com.tangtang.stockadvisor.data.api

import com.tangtang.stockadvisor.data.model.ApiResponse
import com.tangtang.stockadvisor.data.model.BacktestResult
import com.tangtang.stockadvisor.data.model.OnlinePredictionResult
import com.tangtang.stockadvisor.data.model.PortfolioItem
import com.tangtang.stockadvisor.data.model.PortfolioSummary
import com.tangtang.stockadvisor.data.model.PredictionResult
import com.tangtang.stockadvisor.data.model.StockInfo
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface StockApiService {

    // ==================== Stock Data ====================

    @GET("api/stock/list")
    suspend fun getStockList(): ApiResponse<List<StockInfo>>

    @POST("api/stock/select")
    suspend fun selectStock(
        @Body request: SelectStockRequest
    ): ApiResponse<StockInfo>

    // ==================== Prediction ====================

    @POST("api/predict/daily")
    suspend fun getDailyPrediction(
        @Body request: PredictRequest
    ): ApiResponse<PredictionResult>

    @POST("api/predict/realtime")
    suspend fun getRealtimePrediction(
        @Body request: RealtimePredictRequest
    ): ApiResponse<OnlinePredictionResult>

    // ==================== Backtest ====================

    @POST("api/backtest")
    suspend fun runBacktest(
        @Body request: BacktestRequest
    ): ApiResponse<BacktestResult>

    // ==================== Portfolio ====================

    @GET("api/portfolio/holdings")
    suspend fun getHoldings(): ApiResponse<List<PortfolioItem>>

    @GET("api/portfolio/capital")
    suspend fun getCapital(): ApiResponse<PortfolioSummary>

    @POST("api/portfolio/capital")
    suspend fun updateCapital(
        @Body request: UpdateCapitalRequest
    ): ApiResponse<PortfolioSummary>

    // ==================== Strategy ====================

    @GET("api/strategy/list")
    suspend fun getStrategyList(): ApiResponse<List<StrategyInfo>>

    @POST("api/strategy/optimize")
    suspend fun optimizeStrategy(
        @Body request: OptimizeRequest
    ): ApiResponse<Map<String, Any>>

    // ==================== Tools ====================

    @POST("api/tools/download")
    suspend fun downloadData(
        @Body request: DownloadRequest
    ): ApiResponse<Map<String, Any>>
}

// ==================== Request Data Classes ====================

data class SelectStockRequest(
    val symbol: String,
    val name: String = "",
    val index_symbol: String = "",
    val index_name: String = ""
)

data class PredictRequest(
    val symbol: String
)

data class RealtimePredictRequest(
    val symbol: String,
    val prev_close: Double? = null
)

data class BacktestRequest(
    val symbol: String,
    val strategy_type: String,
    val params: Map<String, Any>? = null
)

data class UpdateCapitalRequest(
    val available_cash: Double? = null,
    val total_capital: Double? = null
)

data class StrategyInfo(
    val name: String = "",
    val description: String = ""
)

data class OptimizeRequest(
    val symbol: String,
    val strategy_type: String
)

data class DownloadRequest(
    val symbol: String,
    val years: Int? = null,
    val source: String? = null
)
