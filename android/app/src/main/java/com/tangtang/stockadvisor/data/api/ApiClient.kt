package com.tangtang.stockadvisor.data.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tangtang.stockadvisor.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
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
class ApiClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"
        private const val PREFS_NAME = "stock_advisor_settings"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val baseUrl: String
        get() {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_BACKEND_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + path)
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
            .url(baseUrl + path)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw Exception("Empty response body")
            body.string()
        }
    }

    private suspend fun delete(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + path)
            .delete()
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

    suspend fun importPortfolio(holdingsJson: String, capitalJson: String?): Boolean {
        val body = mapOf(
            "holdings" to gson.fromJson(holdingsJson, Any::class.java),
            "capital" to if (capitalJson != null) gson.fromJson(capitalJson, Any::class.java) else null
        )
        val json = post("api/portfolio/import", body)
        val response = parseResponse<ImportResponse>(json)
        if (response.code == 200) {
            return true
        }
        throw Exception(response.message)
    }

    suspend fun addOrUpdatePosition(request: PositionUpdateRequest): PositionUpdateResponse {
        val json = post("api/portfolio/position", request)
        val response = parseResponse<PositionUpdateResponse>(json)
        if (response.code == 200) {
            return response
        }
        throw Exception(response.message)
    }

    suspend fun deletePosition(symbol: String): Boolean {
        val json = delete("api/portfolio/position/$symbol")
        val response = parseResponse<MapResponse>(json)
        if (response.code == 200) {
            return true
        }
        throw Exception(response.message)
    }

    suspend fun clearAllPositions(): Int {
        val json = delete("api/portfolio/positions")
        val response = parseResponse<MapResponse>(json)
        if (response.code == 200) {
            return response.data?.asInt ?: 0
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

    // ==================== Strategy (extended) ====================

    suspend fun getStrategy(symbol: String): StrategyInfo {
        val json = get("api/strategy/$symbol")
        val response = parseResponse<StrategyResponse>(json)
        if (response.code == 200 && response.data != null) {
            return gson.fromJson(response.data, StrategyInfo::class.java)
        }
        throw Exception(response.message)
    }

    suspend fun saveStrategy(symbol: String, request: StrategySaveRequest): StrategyInfo {
        val json = post("api/strategy/$symbol", request)
        val response = parseResponse<StrategyResponse>(json)
        if (response.code == 200 && response.data != null) {
            return gson.fromJson(response.data, StrategyInfo::class.java)
        }
        throw Exception(response.message)
    }

    suspend fun deleteStrategy(symbol: String): Boolean {
        val json = delete("api/strategy/$symbol")
        val response = parseResponse<MapResponse>(json)
        if (response.code == 200) {
            return true
        }
        throw Exception(response.message)
    }

    // ==================== Logs ====================

    suspend fun getLogList(): Map<String, Map<String, Any>> {
        val json = get("api/logs/list")
        val response = parseResponse<LogListResponse>(json)
        if (response.code == 200 && response.data != null) {
            val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            return gson.fromJson(response.data, type)
        }
        throw Exception(response.message)
    }

    suspend fun getLog(date: String): String {
        val json = get("api/logs/$date")
        val response = parseResponse<LogResponse>(json)
        if (response.code == 200 && response.data != null) {
            return response.data.asString
        }
        throw Exception(response.message)
    }

    suspend fun deleteLog(date: String): Boolean {
        val json = delete("api/logs/$date")
        val response = parseResponse<MapResponse>(json)
        if (response.code == 200) {
            return true
        }
        throw Exception(response.message)
    }

    // ==================== Tools (extended) ====================

    suspend fun getTools(): Map<String, Map<String, Any>> {
        val json = get("api/tools")
        val response = parseResponse<ToolsResponse>(json)
        if (response.code == 200 && response.data != null) {
            val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            return gson.fromJson(response.data, type)
        }
        throw Exception(response.message)
    }

    suspend fun getDownloadStatus(symbol: String): Map<String, Any> {
        val json = get("api/tools/download/status/$symbol")
        val response = parseResponse<MapResponse>(json)
        if (response.code == 200 && response.data != null) {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            return gson.fromJson(response.data, type)
        }
        throw Exception(response.message)
    }

    // ==================== Realtime Data ====================

    suspend fun getRealtimeData(symbol: String): Map<String, Any> {
        val json = get("api/realtime/$symbol")
        val response = parseResponse<RealtimeResponse>(json)
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

data class PositionUpdateRequest(
    val symbol: String,
    val name: String = "",
    val shares: Int,
    val cost_price: Double
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

data class StrategySaveRequest(
    val symbol: String,
    val strategy_type: String,
    val params: Map<String, Any>? = null
)
