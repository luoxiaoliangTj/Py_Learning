package com.tangtang.aico.data.remote

import android.util.Log
import com.tangtang.aico.data.model.StockInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 实时数据获取模块 — 直接调用新浪/网易/搜狐API，不依赖后端
 *
 * 三级降级策略：新浪 → 网易 → 搜狐（复用网易接口）
 */

// ==================== 数据类 ====================

data class RealtimeData(
    val symbol: String,
    val name: String,
    val price: Double,
    val prevClose: Double,
    val open: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val change: Double,
    val changePct: Double,
    val volume: Long,
    val amount: Double = 0.0,
    val date: String,
    val time: String,
    val source: String,
    val valid: Boolean
)

// ==================== 数据源实现 ====================

@Singleton
class RealtimeDataSource @Inject constructor() {

    companion object {
        private const val TAG = "RealtimeDataSource"

        // 新浪API
        private const val SINA_BASE_URL = "http://hq.sinajs.cn/list="

        // 网易API
        private const val NETEASE_BASE_URL = "http://api.money.126.net/data/feed/"

        // 请求超时
        private const val TIMEOUT_SECONDS = 15L

        // 新浪数据字段索引
        private const val FIELD_NAME = 0
        private const val FIELD_OPEN = 1
        private const val FIELD_PREV_CLOSE = 2
        private const val FIELD_CURRENT = 3
        private const val FIELD_HIGH = 4
        private const val FIELD_LOW = 5
        private const val FIELD_VOLUME = 8
        private const val FIELD_AMOUNT = 9
        private const val FIELD_DATE = 30
        private const val FIELD_TIME = 31
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val nowStr: Pair<String, String>
        get() {
            val df = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val tf = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
            val now = Date()
            return df.format(now) to tf.format(now)
        }

    // ==================== 股票代码转换 ====================

    /**
     * 转换为新浪格式：6开头=sh前缀，0/3开头=sz前缀
     */
    private fun toSinaSymbol(symbol: String): String? {
        return when {
            symbol.startsWith("6") -> "sh$symbol"
            symbol.startsWith("0") || symbol.startsWith("3") -> "sz$symbol"
            else -> null
        }
    }

    /**
     * 转换为网易格式：6开头=0前缀，0/3开头=1前缀
     */
    private fun toNeteaseSymbol(symbol: String): String? {
        return when {
            symbol.startsWith("6") -> "0$symbol"
            symbol.startsWith("0") || symbol.startsWith("3") -> "1$symbol"
            else -> null
        }
    }

    // ==================== 新浪数据源 ====================

    /**
     * 获取新浪实时数据
     * @param symbol 纯数字股票代码，如 "600036"
     * @return RealtimeData? 失败返回null
     */
    suspend fun fetchRealtimeSina(symbol: String): RealtimeData? {
        return withContext(Dispatchers.IO) {
            try {
                val sinaSymbol = toSinaSymbol(symbol)
                if (sinaSymbol == null) {
                    Log.w(TAG, "[新浪] 不支持的股票代码: $symbol")
                    return@withContext null
                }

                val url = "$SINA_BASE_URL$sinaSymbol"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Referer", "http://finance.sina.com.cn/")
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (body.isEmpty() || !body.contains("=\"")) {
                    Log.w(TAG, "[新浪] 响应数据为空或格式异常: $body")
                    return@withContext null
                }

                return@withContext parseSinaData(body, symbol)
            } catch (e: Exception) {
                Log.e(TAG, "[新浪] 获取失败: ${e.message}", e)
                null
            }
        }
    }

    /**
     * 解析新浪数据格式
     * 原始格式: var hq_str_sh600036="招商银行,32.10,32.05,32.38,..."
     */
    private fun parseSinaData(raw: String, symbol: String): RealtimeData? {
        return try {
            val content = raw.split("=\"").getOrNull(1)
                ?.trimEnd('"', ';')
                ?: return null

            val fields = content.split(",")
            if (fields.size < 32) {
                Log.w(TAG, "[新浪] 字段不足: ${fields.size}个")
                return null
            }

            val name = fields[FIELD_NAME]
            val open = fields[FIELD_OPEN].toDoubleOrNull() ?: 0.0
            val prevClose = fields[FIELD_PREV_CLOSE].toDoubleOrNull() ?: 0.0
            val price = fields[FIELD_CURRENT].toDoubleOrNull() ?: 0.0
            val high = fields[FIELD_HIGH].toDoubleOrNull() ?: 0.0
            val low = fields[FIELD_LOW].toDoubleOrNull() ?: 0.0
            val volume = fields[FIELD_VOLUME].toLongOrNull() ?: 0L
            val amount = fields[FIELD_AMOUNT].toDoubleOrNull() ?: 0.0
            val date = fields[FIELD_DATE].trim()
            val time = fields[FIELD_TIME].trim()

            val change = if (prevClose > 0) price - prevClose else 0.0
            val changePct = if (prevClose > 0) (change / prevClose) * 100 else 0.0

            RealtimeData(
                symbol = symbol,
                name = name,
                price = price,
                prevClose = prevClose,
                open = open,
                high = high,
                low = low,
                change = change,
                changePct = changePct,
                volume = volume,
                amount = amount,
                date = date,
                time = time,
                source = "新浪",
                valid = price > 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "[新浪] 解析失败: ${e.message}", e)
            null
        }
    }

    // ==================== 网易数据源 ====================

    /**
     * 获取网易实时数据
     * @param symbol 纯数字股票代码，如 "600036"
     * @return RealtimeData? 失败返回null
     */
    suspend fun fetchRealtimeNetease(symbol: String): RealtimeData? {
        return withContext(Dispatchers.IO) {
            try {
                val neteaseSymbol = toNeteaseSymbol(symbol)
                if (neteaseSymbol == null) {
                    Log.w(TAG, "[网易] 不支持的股票代码: $symbol")
                    return@withContext null
                }

                val url = "$NETEASE_BASE_URL$neteaseSymbol"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Referer", "http://quotes.money.163.com/")
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (body.isEmpty() || !body.contains("_ntes_quote_callback")) {
                    Log.w(TAG, "[网易] 响应数据为空或格式异常: ${body.take(100)}")
                    return@withContext null
                }

                return@withContext parseNeteaseData(body, symbol, neteaseSymbol)
            } catch (e: Exception) {
                Log.e(TAG, "[网易] 获取失败: ${e.message}", e)
                null
            }
        }
    }

    /**
     * 解析网易数据格式
     * 原始格式: _ntes_quote_callback({"0600036":{"price":"32.38","yestclose":"32.05",...}});
     */
    private fun parseNeteaseData(raw: String, symbol: String, neteaseSymbol: String): RealtimeData? {
        return try {
            // 去除 JSONP 包装: _ntes_quote_callback(...);\n
            var jsonStr = raw.trim()
            if (jsonStr.startsWith("_ntes_quote_callback(")) {
                jsonStr = jsonStr.removePrefix("_ntes_quote_callback(")
            }
            if (jsonStr.endsWith(");")) {
                jsonStr = jsonStr.removeSuffix(");")
            }
            if (jsonStr.endsWith(")")) {
                jsonStr = jsonStr.removeSuffix(")")
            }

            // 手动解析JSON（避免引入额外依赖）
            // 格式: {"0600036":{"price":"32.38",...}}
            val stockData = extractStockMap(jsonStr, neteaseSymbol)
            if (stockData.isEmpty()) {
                Log.w(TAG, "[网易] 未找到股票数据: $neteaseSymbol")
                return null
            }

            val price = stockData["price"]?.toDoubleOrNull() ?: 0.0
            val prevClose = stockData["yestclose"]?.toDoubleOrNull() ?: price
            val open = stockData["open"]?.toDoubleOrNull() ?: 0.0
            val high = stockData["high"]?.toDoubleOrNull() ?: 0.0
            val low = stockData["low"]?.toDoubleOrNull() ?: 0.0
            val volume = stockData["volume"]?.toLongOrNull() ?: 0L
            val amount = stockData["turnover"]?.toDoubleOrNull() ?: 0.0
            val name = stockData["name"] ?: ""
            val date = stockData["date"] ?: ""
            val time = stockData["time"] ?: ""

            val change = if (prevClose > 0) price - prevClose else 0.0
            val changePct = if (prevClose > 0) (change / prevClose) * 100 else 0.0

            RealtimeData(
                symbol = symbol,
                name = name,
                price = price,
                prevClose = prevClose,
                open = open,
                high = high,
                low = low,
                change = change,
                changePct = changePct,
                volume = volume,
                amount = amount,
                date = date,
                time = time,
                source = "网易",
                valid = price > 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "[网易] 解析失败: ${e.message}", e)
            null
        }
    }

    /**
     * 简易JSON提取: 从 {"key":{...}} 格式中提取指定key的value map
     * 不依赖Gson/JSONObject，直接用字符串解析（兼容性好）
     */
    private fun extractStockMap(jsonStr: String, key: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // 定位目标key: "key":{
        val keyPattern = "\"$key\":"
        val keyIdx = jsonStr.indexOf(keyPattern)
        if (keyIdx < 0) return result

        // 找到值的起始 {
        val braceStart = jsonStr.indexOf("{", keyIdx + keyPattern.length)
        if (braceStart < 0) return result

        // 找到匹配的 }
        var depth = 0
        var braceEnd = -1
        for (i in braceStart until jsonStr.length) {
            when (jsonStr[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        braceEnd = i
                        break
                    }
                }
            }
        }
        if (braceEnd < 0) return result

        val inner = jsonStr.substring(braceStart + 1, braceEnd)

        // 解析 "key":"value" 对
        var pos = 0
        while (pos < inner.length) {
            val keyStart = inner.indexOf("\"", pos)
            if (keyStart < 0) break

            val keyEnd = inner.indexOf("\"", keyStart + 1)
            if (keyEnd < 0) break

            val k = inner.substring(keyStart + 1, keyEnd)

            // 跳过冒号
            var colonIdx = inner.indexOf(":", keyEnd + 1)
            if (colonIdx < 0) break

            val valStart = inner.indexOf("\"", colonIdx + 1)
            if (valStart < 0) {
                // 数值类型
                val valEnd = inner.indexOfAny(charArrayOf(',', '}'), colonIdx + 1)
                val value = if (valEnd > colonIdx + 1)
                    inner.substring(colonIdx + 1, valEnd).trim()
                else
                    inner.substring(colonIdx + 1).trim()
                result[k] = value
                pos = if (valEnd > colonIdx + 1) valEnd else inner.length
                continue
            }

            val valEnd = inner.indexOf("\"", valStart + 1)
            if (valEnd < 0) break

            val v = inner.substring(valStart + 1, valEnd)
            result[k] = v
            pos = valEnd + 1
        }

        return result
    }

    // ==================== 搜狐数据源（复用网易接口） ====================

    /**
     * 获取搜狐实时数据（实际调用网易API，与原始Python实现一致）
     * @param symbol 纯数字股票代码
     * @return RealtimeData? 失败返回null
     */
    suspend fun fetchRealtimeSohu(symbol: String): RealtimeData? {
        // 搜狐实时接口复用网易接口（与原始Python实现一致）
        val data = fetchRealtimeNetease(symbol)
        return data?.copy(source = "搜狐")
    }

    // ==================== 三级降级获取 ====================

    /**
     * 三级降级获取实时数据：新浪 → 网易 → 搜狐
     * @param symbol 纯数字股票代码，如 "600036"
     * @return RealtimeData 至少返回一个数据源的结果；全部失败时返回 valid=false 的占位数据
     */
    suspend fun fetchRealtimeData(symbol: String): RealtimeData {
        // 第一级：新浪
        val sinaData = fetchRealtimeSina(symbol)
        if (sinaData != null && sinaData.valid) {
            Log.d(TAG, "✅ 新浪数据获取成功: ${sinaData.name} ${sinaData.price}")
            return sinaData
        }
        Log.w(TAG, "⚠️ 新浪数据获取失败或无效，尝试网易")

        // 第二级：网易
        val neteaseData = fetchRealtimeNetease(symbol)
        if (neteaseData != null && neteaseData.valid) {
            Log.d(TAG, "✅ 网易数据获取成功: ${neteaseData.name} ${neteaseData.price}")
            return neteaseData
        }
        Log.w(TAG, "⚠️ 网易数据获取失败或无效，尝试搜狐")

        // 第三级：搜狐（复用网易接口）
        val sohuData = fetchRealtimeSohu(symbol)
        if (sohuData != null && sohuData.valid) {
            Log.d(TAG, "✅ 搜狐数据获取成功: ${sohuData.name} ${sohuData.price}")
            return sohuData
        }
        Log.e(TAG, "❌ 所有数据源均失败: $symbol")

        // 全部失败返回无效数据
        val (date, time) = nowStr
        return RealtimeData(
            symbol = symbol,
            name = "",
            price = 0.0,
            prevClose = 0.0,
            change = 0.0,
            changePct = 0.0,
            volume = 0,
            date = date,
            time = time,
            source = "无",
            valid = false
        )
    }

    // ==================== 批量获取 ====================

    /**
     * 批量获取多只股票实时数据
     * @param symbols 股票代码列表
     * @return Map<symbol, RealtimeData>
     */
    suspend fun fetchRealtimeDataBatch(symbols: List<String>): Map<String, RealtimeData> {
        return symbols.associateWith { symbol ->
            fetchRealtimeData(symbol)
        }
    }

    /**
     * 转换 RealtimeData 为 StockInfo（兼容现有UI层数据模型）
     */
    fun toStockInfo(data: RealtimeData): StockInfo {
        return StockInfo(
            code = data.symbol,
            name = data.name,
            currentPrice = data.price,
            changePercent = data.changePct,
            changeAmount = data.change,
            volume = data.volume,
            turnover = data.amount,
            high = data.high,
            low = data.low,
            open = data.open,
            prevClose = data.prevClose
        )
    }
}
