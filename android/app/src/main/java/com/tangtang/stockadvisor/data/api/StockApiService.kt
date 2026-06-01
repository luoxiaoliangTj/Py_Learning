package com.tangtang.stockadvisor.data.api

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
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface StockApiService {

    // ==================== Stock Data ====================

    @GET("api/stock/list")
    suspend fun getStockList(): StockListResponse

    @POST("api/stock/select")
    suspend fun selectStock(
        @Body request: SelectStockRequest
    ): StockSelectResponse

    // ==================== Prediction ====================

    @POST("api/predict/daily")
    suspend fun getDailyPrediction(
        @Body request: PredictRequest
    ): DailyPredictionResponse

    @POST("api/predict/realtime")
    suspend fun getRealtimePrediction(
        @Body request: RealtimePredictRequest
    ): RealtimePredictionResponse

    // ==================== Backtest ====================

    @POST("api/backtest")
    suspend fun runBacktest(
        @Body request: BacktestRequest
    ): BacktestResponse

    // ==================== Portfolio ====================

    @GET("api/portfolio/holdings")
    suspend fun getHoldings(): HoldingsResponse

    @GET("api/portfolio/capital")
    suspend fun getCapital(): CapitalResponse

    @POST("api/portfolio/capital")
    suspend fun updateCapital(
        @Body request: UpdateCapitalRequest
    ): CapitalResponse

    // ==================== Strategy ====================

    @GET("api/strategy/list")
    suspend fun getStrategyList(): StrategyListResponse

    @POST("api/strategy/optimize")
    suspend fun optimizeStrategy(
        @Body request: OptimizeRequest
    ): MapResponse

    // ==================== Tools ====================

    @POST("api/tools/download")
    suspend fun downloadData(
        @Body request: DownloadRequest
    ): MapResponse
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

data class OptimizeRequest(
    val symbol: String,
    val strategy_type: String
)

data class DownloadRequest(
    val symbol: String,
    val years: Int? = null,
    val source: String? = null
)
