package com.tangtang.stockadvisor.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tangtang.stockadvisor.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClient @Inject constructor() {

    companion object {
        const val BASE_URL = "http://10.0.2.2:8000/"
        private const val TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BASE_URL + path)
            .get()
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw Exception("Empty response body")
            body.string()
        }
    }

    private suspend fun post(path: String, body: Any): String = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(BASE_URL + path)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw Exception("Empty response body")
            body.string()
        }
    }

    private inline fun <reified T> parseResponse(json: String): T {
        return gson.fromJson(json, T::class.java)
    }

    private inline fun <reified T> parseResponseList(json: String): List<T> {
        val type = object : TypeToken<List<T>>() {}.type
        return gson.fromJson(json, type)
    }

    // ==================== Stock Data ====================

    suspend fun getStockList(): List<StockInfo> {
        val json = get("api/stock/list")
        val response = parseResponse<StockListResponse>(json)
        if (response.code == 200 && response.data != null) {
            val type = object : TypeToken<List<StockInfo>>() {}.type
            return gson.fromJson(response.data, type)
        }
        throw Exception(response.message)
    }

    suspend fun selectStock(request: SelectStockRequest): StockInfo {
        val json = post("api/stock/select", request)
        val response = parseResponse<StockSelectResponse>(json)
        if (response.code == 200 && response.data != null) {
            return gson.fromJson(response.data, StockInfo::class.java)
        }
        throw Exception(response.message)
    }

    // ==================== Prediction ====================

    suspend fun getDailyPrediction(request: PredictRequest): PredictionResult {
        val json = post("api/predict/daily", request)
        val response = parseResponse<DailyPredictionResponse>(json)
        if (response.code == 200 && response.data != null) {
            return gson.fromJson(response.data, PredictionResult::class.java)
        }
        throw Exception(response.message)
    }

    suspend fun getRealtimePrediction(request: RealtimePredictRequest): OnlinePredictionResult {
        val json = post("api/predict/realtime", request)
        val response = parseResponse<RealtimePredictionResponse>(json)
        if (response.code == 200 && response.data != null) {
            return gson.fromJson(response.data, OnlinePredictionResult::class.java)
        }
        throw Exception(response.message)
    }

    // ==================== Backtest ====================

    suspend fun runBacktest(request: BacktestRequest): BacktestResult {
        val json = post("api/backtest", request)
        val response = parseResponse<BacktestResponse>(json)
        if (response.code == 200 && response.data != null) {
            return gson.fromJson(response.data, BacktestResult::class.java)
        }
        throw Exception(response.message)
    }

    // ==================== Portfolio ====================

    suspend fun getHoldings(): List<PortfolioItem> {
        val json = get("api/portfolio/holdings")
        val response = parseResponse<HoldingsResponse>(json)
        if (response.code == 200 && response.data != null) {
            val type = object : TypeToken<List<PortfolioItem>>() {}.type
            return gson.fromJson(response.data, type)
        }
        throw Exception(response.message)
    }

    suspend fun getCapital(): PortfolioSummary {
        val json = get("api/portfolio/capital")
        val response = parseResponse<CapitalResponse>(json)
        if (response.code == 200 && response.data != null) {
            return gson.fromJson(response.data, PortfolioSummary::class.java)
        }
        throw Exception(response.message)
    }

    suspend fun updateCapital(request: UpdateCapitalRequest): PortfolioSummary {
        val json = post("api/portfolio/capital", request)
        val response = parseResponse<CapitalResponse>(json)
        if (response.code == 200 && response.data != null) {
            return gson.fromJson(response.data, PortfolioSummary::class.java)
        }
        throw Exception(response.message)
    }

    // ==================== Strategy ====================

    suspend fun getStrategyList(): List<StrategyInfo> {
        val json = get("api/strategy/list")
        val response = parseResponse<StrategyListResponse>(json)
        if (response.code == 200 && response.data != null) {
            val type = object : TypeToken<List<StrategyInfo>>() {}.type
            return gson.fromJson(response.data, type)
        }
        throw Exception(response.message)
    }

    suspend fun optimizeStrategy(request: OptimizeRequest): Map<String, Any> {
        val json = post("api/strategy/optimize", request)
        val response = parseResponse<MapResponse>(json)
        if (response.code == 200 && response.data != null) {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            return gson.fromJson(response.data, type)
        }
        throw Exception(response.message)
    }

    // ==================== Tools ====================

    suspend fun downloadData(request: DownloadRequest): Map<String, Any> {
        val json = post("api/tools/download", request)
        val response = parseResponse<MapResponse>(json)
        if (response.code == 200 && response.data != null) {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            return gson.fromJson(response.data, type)
        }
        throw Exception(response.message)
    }
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
