package com.tangtang.stockadvisor.data.api

import com.tangtang.stockadvisor.data.model.ApiResponse
import com.tangtang.stockadvisor.data.model.BacktestResult
import com.tangtang.stockadvisor.data.model.OnlinePredictionResult
import com.tangtang.stockadvisor.data.model.PortfolioItem
import com.tangtang.stockadvisor.data.model.PortfolioSummary
import com.tangtang.stockadvisor.data.model.PredictionResult
import com.tangtang.stockadvisor.data.model.StockInfo
import com.tangtang.stockadvisor.data.model.StockPrice
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface StockApiService {

    // ==================== Stock Data ====================

    @GET("api/stock/{code}")
    suspend fun getStockInfo(
        @Path("code") code: String
    ): ApiResponse<StockInfo>

    @GET("api/stock/{code}/prices")
    suspend fun getStockPrices(
        @Path("code") code: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("period") period: String = "daily"
    ): ApiResponse<List<StockPrice>>

    @GET("api/stocks/search")
    suspend fun searchStocks(
        @Query("keyword") keyword: String
    ): ApiResponse<List<StockInfo>>

    @GET("api/stocks/market")
    suspend fun getMarketOverview(): ApiResponse<List<StockInfo>>

    // ==================== Prediction ====================

    @GET("api/predict/{code}")
    suspend fun getPrediction(
        @Path("code") code: String
    ): ApiResponse<PredictionResult>

    @GET("api/predict/{code}/online")
    suspend fun getOnlinePrediction(
        @Path("code") code: String
    ): ApiResponse<OnlinePredictionResult>

    // ==================== Backtest ====================

    @POST("api/backtest")
    suspend fun runBacktest(
        @Body request: BacktestRequest
    ): ApiResponse<BacktestResult>

    // ==================== Portfolio ====================

    @GET("api/portfolio")
    suspend fun getPortfolio(): ApiResponse<PortfolioSummary>

    @POST("api/portfolio/add")
    suspend fun addToPortfolio(
        @Body item: PortfolioItem
    ): ApiResponse<PortfolioSummary>

    @PUT("api/portfolio/update")
    suspend fun updatePortfolioItem(
        @Body item: PortfolioItem
    ): ApiResponse<PortfolioSummary>

    @DELETE("api/portfolio/{code}")
    suspend fun removeFromPortfolio(
        @Path("code") code: String
    ): ApiResponse<PortfolioSummary>

    // ==================== Settings ====================

    @GET("api/settings/token")
    suspend fun validateToken(
        @Query("token") token: String
    ): ApiResponse<Boolean>
}

data class BacktestRequest(
    val code: String,
    val start_date: String,
    val end_date: String,
    val initial_capital: Double = 100000.0,
    val strategy: String = "default"
)
