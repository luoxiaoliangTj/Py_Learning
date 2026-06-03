package com.tangtang.stockadvisor.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * 历史K线数据下载模块
 * 支持新浪/搜狐/Tushare三级降级下载，直接调用API获取数据并保存为CSV
 */
@Singleton
class HistoricalDataDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HistDataDownloader"
        private const val SINA_KLINE_URL = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData"
        private const val SOHU_HIS_URL = "https://q.stock.sohu.com/hisHq"
        private const val TUSHARE_DAILY_URL = "https://api.tushare.pro"

        // 标准CSV列
        val STANDARD_COLUMNS = listOf("日期", "开盘", "最高", "最低", "收盘", "成交量", "成交额")

        private val USER_AGENTS = arrayOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
    }

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    // ==================== 公开接口 ====================

    /**
     * 下载历史日线数据
     * 三级降级: 新浪 → 搜狐 → Tushare
     */
    suspend fun downloadDailyData(
        symbol: String,
        years: Int = 8
    ): DownloadResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "开始下载 $symbol 的 ${years}年 日线数据...")
        val errors = mutableListOf<String>()

        // 1. 尝试新浪
        try {
            val sinaResult = downloadFromSina(symbol, years)
            if (sinaResult.isNotEmpty()) {
                val cleaned = cleanData(sinaResult)
                val validated = validateData(cleaned, symbol)
                if (validated.isValid) {
                    val file = saveCsv(symbol, cleaned)
                    if (file != null) {
                        Log.i(TAG, "新浪下载成功: ${cleaned.size}条记录 → ${file.absolutePath}")
                        return@withContext DownloadResult(
                            success = true,
                            source = "sina",
                            recordCount = cleaned.size,
                            filePath = file.absolutePath,
                            message = "新浪财经下载成功，${cleaned.size}条记录"
                        )
                    } else {
                        errors.add("新浪: CSV保存失败")
                    }
                } else {
                    errors.add("新浪: 数据验证失败(${validated.details})")
                    Log.w(TAG, "新浪数据验证失败: ${validated.details}")
                }
            } else {
                errors.add("新浪: 返回空数据")
            }
        } catch (e: Exception) {
            errors.add("新浪: ${e.message}")
            Log.w(TAG, "新浪下载失败: ${e.message}")
        }

        delay(500) // 请求间隔

        // 2. 尝试搜狐
        try {
            val sohuResult = downloadFromSohu(symbol, years)
            if (sohuResult.isNotEmpty()) {
                val cleaned = cleanData(sohuResult)
                val validated = validateData(cleaned, symbol)
                if (validated.isValid) {
                    val file = saveCsv(symbol, cleaned)
                    if (file != null) {
                        Log.i(TAG, "搜狐下载成功: ${cleaned.size}条记录 → ${file.absolutePath}")
                        return@withContext DownloadResult(
                            success = true,
                            source = "sohu",
                            recordCount = cleaned.size,
                            filePath = file.absolutePath,
                            message = "搜狐财经下载成功，${cleaned.size}条记录"
                        )
                    } else {
                        errors.add("搜狐: CSV保存失败")
                    }
                } else {
                    errors.add("搜狐: 数据验证失败(${validated.details})")
                    Log.w(TAG, "搜狐数据验证失败: ${validated.details}")
                }
            } else {
                errors.add("搜狐: 返回空数据")
            }
        } catch (e: Exception) {
            errors.add("搜狐: ${e.message}")
            Log.w(TAG, "搜狐下载失败: ${e.message}")
        }

        delay(500)

        // 3. 尝试Tushare（需要token）
        try {
            val tushareResult = downloadFromTushare(symbol, years)
            if (tushareResult.isNotEmpty()) {
                val cleaned = cleanData(tushareResult)
                val validated = validateData(cleaned, symbol)
                if (validated.isValid) {
                    val file = saveCsv(symbol, cleaned)
                    if (file != null) {
                        Log.i(TAG, "Tushare下载成功: ${cleaned.size}条记录 → ${file.absolutePath}")
                        return@withContext DownloadResult(
                            success = true,
                            source = "tushare",
                            recordCount = cleaned.size,
                            filePath = file.absolutePath,
                            message = "Tushare下载成功，${cleaned.size}条记录"
                        )
                    } else {
                        errors.add("Tushare: CSV保存失败")
                    }
                } else {
                    errors.add("Tushare: 数据验证失败(${validated.details})")
                    Log.w(TAG, "Tushare数据验证失败: ${validated.details}")
                }
            } else {
                errors.add("Tushare: 返回空数据(可能未配置Token)")
            }
        } catch (e: Exception) {
            errors.add("Tushare: ${e.message}")
            Log.w(TAG, "Tushare下载失败: ${e.message}")
        }

        Log.e(TAG, "所有数据源均失败: $symbol, 原因: ${errors.joinToString("; ")}")
        return@withContext DownloadResult(
            success = false,
            source = "none",
            recordCount = 0,
            filePath = null,
            message = "所有数据源均下载失败:\n${errors.joinToString("\n")}\n\n建议：\n1. 检查网络连接\n2. 确认股票代码正确($symbol)\n3. 稍后再试"
        )
    }

    /**
     * 检查是否已有数据
     */
    fun checkExistingData(symbol: String): DataCheckResult {
        val file = getCsvFile(symbol)

        if (!file.exists()) {
            Log.w(TAG, "数据文件不存在: ${file.absolutePath}")
            return DataCheckResult(
                exists = false,
                message = "数据文件不存在",
                recordCount = 0,
                dateRange = "",
                details = null
            )
        }

        return try {
            val records = readCsv(file)
            Log.i(TAG, "读取CSV: ${file.absolutePath}, ${records.size}条记录")
            if (records.isEmpty()) {
                return DataCheckResult(
                    exists = false,
                    message = "数据文件为空",
                    recordCount = 0,
                    dateRange = "",
                    details = null
                )
            }

            val dates = records.mapNotNull { it["日期"] }.sorted()
            val firstDate = dates.firstOrNull() ?: ""
            val lastDate = dates.lastOrNull() ?: ""
            val dateRange = if (firstDate.isNotEmpty() && lastDate.isNotEmpty()) {
                "$firstDate 至 $lastDate"
            } else {
                ""
            }

            // 计算涨跌幅
            val priceInfo = if (records.size >= 2) {
                val firstClose = records.first()["收盘"]?.toDoubleOrNull() ?: 0.0
                val lastClose = records.last()["收盘"]?.toDoubleOrNull() ?: 0.0
                if (firstClose > 0) {
                    val changePct = (lastClose / firstClose - 1) * 100
                    ", 涨跌幅: ${String.format("%+.1f", changePct)}%"
                } else ""
            } else ""

            DataCheckResult(
                exists = true,
                message = "数据量: ${records.size}条, 时间范围: $dateRange$priceInfo",
                recordCount = records.size,
                dateRange = dateRange,
                details = records
            )
        } catch (e: Exception) {
            Log.e(TAG, "读取CSV失败: ${file.absolutePath}, ${e.message}", e)
            DataCheckResult(
                exists = false,
                message = "数据文件读取失败: ${e.message}",
                recordCount = 0,
                dateRange = "",
                details = null
            )
        }
    }

    // ==================== 新浪数据源 ====================

    private suspend fun downloadFromSina(symbol: String, years: Int): List<Map<String, String>> {
        val sinaSymbol = if (symbol.startsWith("6")) "sh$symbol" else "sz$symbol"
        val datalen = years * 250 // 每年约250个交易日

        val url = "$SINA_KLINE_URL?symbol=$sinaSymbol&scale=240&ma=no&datalen=$datalen"

        // 重试最多2次
        var lastException: Exception? = null
        for (attempt in 0..2) {
            try {
                if (attempt > 0) {
                    Log.w(TAG, "新浪下载重试第${attempt}次: $symbol")
                    delay(1000L * attempt)
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", getRandomUserAgent())
                    .addHeader("Referer", "https://finance.sina.com.cn")
                    .addHeader("Accept", "*/*")
                    .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (!it.isSuccessful) {
                        Log.w(TAG, "新浪HTTP ${it.code}: $symbol (attempt ${attempt + 1})")
                        if (it.code == 403 || it.code == 429) {
                            lastException = Exception("新浪API限流(HTTP ${it.code})")
                            continue
                        }
                        throw Exception("HTTP ${it.code}")
                    }

                    val body = it.body?.string() ?: throw Exception("空响应体")

                    // 检查是否是空数组
                    if (body.trim() == "[]" || body.trim().isEmpty()) {
                        Log.w(TAG, "新浪返回空数据: $symbol")
                        return emptyList()
                    }

                    val jsonArray = JsonParser.parseString(body).asJsonArray
                    if (jsonArray.size() == 0) return emptyList()

                    val records = mutableListOf<Map<String, String>>()
                    for (element in jsonArray) {
                        val obj = element.asJsonObject
                        val day = obj.get("day")?.asString ?: continue
                        val open = obj.get("open")?.asString ?: continue
                        val high = obj.get("high")?.asString ?: continue
                        val low = obj.get("low")?.asString ?: continue
                        val close = obj.get("close")?.asString ?: continue
                        val volume = obj.get("volume")?.asString ?: "0"
                        val amount = obj.get("amount")?.asString ?: "0"

                        // 新浪K线API返回的volume已经是股数，不需要乘以100
                        val volumeInShares = volume.toDoubleOrNull() ?: 0.0
                        val amountVal = amount.toDoubleOrNull() ?: 0.0
                        // 如果没有成交额字段，估算成交额
                        val turnover = if (amountVal > 0) amountVal else (close.toDoubleOrNull() ?: 0.0) * volumeInShares

                        records.add(mapOf(
                            "日期" to formatDate(day),
                            "开盘" to open,
                            "最高" to high,
                            "最低" to low,
                            "收盘" to close,
                            "成交量" to volumeInShares.toLong().toString(),
                            "成交额" to String.format("%.2f", turnover)
                        ))
                    }

                    Log.i(TAG, "新浪下载: ${records.size}条记录 (attempt ${attempt + 1})")
                    return records
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "新浪下载异常 (attempt ${attempt + 1}): ${e.message}")
            }
        }

        // 所有重试都失败
        throw lastException ?: Exception("新浪下载失败，已重试3次")
    }

    // ==================== 搜狐数据源 ====================

    private suspend fun downloadFromSohu(symbol: String, years: Int): List<Map<String, String>> {
        val cal = Calendar.getInstance()
        val endDate = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(cal.time)
        cal.add(Calendar.YEAR, -years)
        val startDate = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(cal.time)

        val code = if (symbol in listOf("000001", "399001")) "zs_$symbol" else "cn_$symbol"

        val url = "$SOHU_HIS_URL?code=$code&start=$startDate&end=$endDate&stat=1&order=D&period=d"

        var lastException: Exception? = null
        for (attempt in 0..2) {
            try {
                if (attempt > 0) {
                    Log.w(TAG, "搜狐下载重试第${attempt}次: $symbol")
                    delay(1000L * attempt)
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", getRandomUserAgent())
                    .addHeader("Referer", "https://q.stock.sohu.com/")
                    .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (!it.isSuccessful) {
                        Log.w(TAG, "搜狐HTTP ${it.code}: $symbol (attempt ${attempt + 1})")
                        throw Exception("HTTP ${it.code}")
                    }

                    val body = it.body?.string() ?: throw Exception("空响应体")
                    val jsonArray = JsonParser.parseString(body).asJsonArray

                    if (jsonArray.size() == 0 || !jsonArray[0].asJsonObject.has("hq")) {
                        Log.w(TAG, "搜狐返回空数据: $symbol")
                        return emptyList()
                    }

                    val hqArray = jsonArray[0].asJsonObject.getAsJsonArray("hq")
                    val records = mutableListOf<Map<String, String>>()

                    for (element in hqArray) {
                        val item = element.asJsonArray
                        // 字段顺序: [0]日期 [1]开盘 [2]收盘 [3]涨跌额 [4]涨跌幅 [5]最低 [6]最高 [7]成交量 [8]成交额
                        if (item.size() < 9) continue

                        val dateStr = item[0].asString
                        val open = item[1].asString
                        val close = item[2].asString
                        val low = item[5].asString
                        val high = item[6].asString
                        val volume = item[7].asString
                        val amount = item[8].asString

                        records.add(mapOf(
                            "日期" to formatDate(dateStr),
                            "开盘" to open,
                            "最高" to high,
                            "最低" to low,
                            "收盘" to close,
                            "成交量" to volume,
                            "成交额" to amount
                        ))
                    }

                    Log.i(TAG, "搜狐下载: ${records.size}条记录 (attempt ${attempt + 1})")
                    return records
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "搜狐下载异常 (attempt ${attempt + 1}): ${e.message}")
            }
        }

        throw lastException ?: Exception("搜狐下载失败，已重试3次")
    }

    // ==================== Tushare数据源 ====================

    private suspend fun downloadFromTushare(symbol: String, years: Int): List<Map<String, String>> {
        val tushareToken = getTushareToken()
        if (tushareToken.isEmpty()) {
            Log.w(TAG, "未配置Tushare Token，跳过Tushare")
            return emptyList()
        }

        val cal = Calendar.getInstance()
        val endDate = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(cal.time)
        cal.add(Calendar.YEAR, -years)
        val startDate = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(cal.time)

        val tsCode = if (symbol.startsWith("6")) "$symbol.SH" else "$symbol.SZ"

        val requestBody = mapOf(
            "api_name" to "daily",
            "token" to tushareToken,
            "params" to mapOf(
                "ts_code" to tsCode,
                "start_date" to startDate,
                "end_date" to endDate
            ),
            "fields" to "trade_date,open,high,low,close,vol,amount"
        )

        val jsonBody = gson.toJson(requestBody)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(TUSHARE_DAILY_URL)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) throw Exception("HTTP ${it.code}")

            val respBody = it.body?.string() ?: throw Exception("空响应体")
            val respJson = JsonParser.parseString(respBody).asJsonObject

            if (respJson.has("code") && respJson.get("code").asInt != 0) {
                throw Exception(respJson.get("msg")?.asString ?: "Tushare API错误")
            }

            val data = respJson.getAsJsonObject("data")
            val items = data?.getAsJsonArray("items") ?: return emptyList()
            val fields = data?.getAsJsonArray("fields") ?: return emptyList()

            // 建立字段索引映射
            val fieldMap = mutableMapOf<String, Int>()
            for (i in 0 until fields.size()) {
                fieldMap[fields[i].asString] = i
            }

            val records = mutableListOf<Map<String, String>>()
            for (itemArray in items) {
                val item = itemArray.asJsonArray
                val tradeDate = item[fieldMap["trade_date"] ?: continue].asString
                val open = item[fieldMap["open"] ?: continue].asString
                val high = item[fieldMap["high"] ?: continue].asString
                val low = item[fieldMap["low"] ?: continue].asString
                val close = item[fieldMap["close"] ?: continue].asString
                val vol = item[fieldMap["vol"] ?: continue].asString
                val amount = item[fieldMap["amount"] ?: continue].asString

                records.add(mapOf(
                    "日期" to formatTushareDate(tradeDate),
                    "开盘" to open,
                    "最高" to high,
                    "最低" to low,
                    "收盘" to close,
                    "成交量" to vol,
                    "成交额" to amount
                ))
            }

            // Tushare返回按日期倒序，需要正序
            Log.i(TAG, "Tushare下载: ${records.size}条记录")
            return records.sortedBy { it["日期"] }
        }
    }

    // ==================== 数据清洗 ====================

    /**
     * 3σ处理价格异常，IQR处理成交量异常
     */
    private fun cleanData(records: List<Map<String, String>>): List<Map<String, String>> {
        if (records.size < 10) return records // 数据量太少，不做清洗

        // 按日期排序
        val sorted = records.sortedBy { it["日期"] }

        // --- 3σ 处理价格异常 ---
        val priceColumns = listOf("开盘", "最高", "最低", "收盘")
        val cleaned = sorted.toMutableList()

        for (col in priceColumns) {
            val values = sorted.mapNotNull { it[col]?.toDoubleOrNull() }
            if (values.isEmpty()) continue

            val mean = values.average()
            val std = sqrt(values.map { (it - mean) * (it - mean) }.average())
            if (std <= 0) continue

            val lowerBound = mean - 3 * std
            val upperBound = mean + 3 * std

            for (i in cleaned.indices) {
                val v = cleaned[i][col]?.toDoubleOrNull() ?: continue
                if (v < lowerBound || v > upperBound) {
                    val patched = cleaned[i].toMutableMap()
                    patched[col] = String.format("%.2f", mean)
                    cleaned[i] = patched
                }
            }
        }

        // --- IQR 处理成交量异常 ---
        val volumes = sorted.mapNotNull { it["成交量"]?.toDoubleOrNull() }.sorted()
        if (volumes.size >= 4) {
            val q1 = volumes[volumes.size / 4]
            val q3 = volumes[volumes.size * 3 / 4]
            val iqr = q3 - q1
            if (iqr > 0) {
                val lowerBound = q1 - 1.5 * iqr
                val upperBound = q3 + 1.5 * iqr
                val median = volumes[volumes.size / 2]

                for (i in cleaned.indices) {
                    val v = cleaned[i]["成交量"]?.toDoubleOrNull() ?: continue
                    if (v < lowerBound || v > upperBound) {
                        val patched = cleaned[i].toMutableMap()
                        patched["成交量"] = median.toLong().toString()
                        cleaned[i] = patched
                    }
                }
            }
        }

        return cleaned
    }

    // ==================== 数据验证 ====================

    /**
     * 数据验证: 列完整性, 日期连续性, 价格合理性
     */
    private fun validateData(records: List<Map<String, String>>, symbol: String): ValidationResult {
        val details = mutableListOf<String>()
        var allValid = true

        if (records.isEmpty()) {
            return ValidationResult(false, "数据为空")
        }

        // 1. 列完整性
        val missingColumns = STANDARD_COLUMNS.filter { col ->
            records.any { it[col] == null || it[col].isNullOrEmpty() }
        }
        if (missingColumns.isNotEmpty()) {
            allValid = false
            details.add("列完整性: 失败 - 缺少列: ${missingColumns.joinToString(", ")}")
        } else {
            details.add("列完整性: 通过")
        }

        // 2. 日期连续性
        val dates = records.mapNotNull { it["日期"] }.sorted()
        if (dates.size > 1) {
            var gaps = 0
            for (i in 1 until dates.size) {
                try {
                    val prev = dateFormat.parse(dates[i - 1])
                    val curr = dateFormat.parse(dates[i])
                    if (prev != null && curr != null) {
                        val dayDiff = ((curr.time - prev.time) / (1000 * 60 * 60 * 24)).toInt()
                        if (dayDiff > 5) gaps++ // 超过5天的间隔算异常（排除节假日）
                    }
                } catch (_: Exception) { }
            }
            val gapRatio = gaps.toDouble() / dates.size
            if (gapRatio > 0.5) {
                allValid = false
                details.add("日期连续性: 失败 - 发现 $gaps 个较大日期间隔 (占比 ${String.format("%.1f", gapRatio * 100)}%)")
            } else {
                details.add("日期连续性: 通过 (间隔数: $gaps)")
            }
        }

        // 3. 价格合理性
        var priceOk = true
        for (col in listOf("开盘", "最高", "最低", "收盘")) {
            val hasInvalid = records.any {
                val v = it[col]?.toDoubleOrNull() ?: -1.0
                v <= 0
            }
            if (hasInvalid) {
                priceOk = false
                allValid = false
                details.add("价格合理性($col): 失败 - 存在无效价格")
            }
        }
        if (priceOk) {
            details.add("价格合理性: 通过")
        }

        return ValidationResult(allValid, details.joinToString("; "))
    }

    // ==================== CSV保存/读取 ====================

    private fun getCsvFile(symbol: String): File {
        val dataDir = File(context.filesDir, "data")
        if (!dataDir.exists()) {
            val created = dataDir.mkdirs()
            Log.i(TAG, "数据目录创建: ${dataDir.absolutePath}, 结果: $created")
        }
        return File(dataDir, "ccb_${symbol}_daily.csv")
    }

    /**
     * 保存为CSV (UTF-8, 日期YYYY-MM-DD)
     */
    private fun saveCsv(symbol: String, records: List<Map<String, String>>): File? {
        return try {
            val file = getCsvFile(symbol)
            java.io.OutputStreamWriter(java.io.FileOutputStream(file), Charsets.UTF_8).use { writer ->
                // 写BOM头（方便Excel打开）
                writer.write("\uFEFF")
                // 写表头
                writer.write(STANDARD_COLUMNS.joinToString(","))
                writer.write("\n")

                // 按日期排序后写入
                val sorted = records.sortedBy { it["日期"] }
                for (record in sorted) {
                    val line = STANDARD_COLUMNS.joinToString(",") { col ->
                        record[col] ?: ""
                    }
                    writer.write(line)
                    writer.write("\n")
                }
            }
            Log.i(TAG, "CSV已保存: ${file.absolutePath}, ${records.size}条记录")
            file
        } catch (e: Exception) {
            Log.e(TAG, "保存CSV失败: ${e.message}")
            null
        }
    }

    /**
     * 读取CSV文件
     */
    private fun readCsv(file: File): List<Map<String, String>> {
        val records = mutableListOf<Map<String, String>>()
        BufferedReader(java.io.InputStreamReader(java.io.FileInputStream(file), Charsets.UTF_8)).use { reader ->
            var line = reader.readLine() ?: return records

            // 跳过BOM
            if (line.isNotEmpty() && line[0] == '\uFEFF') {
                line = line.substring(1)
            }

            val headers = line.split(",").map { it.trim() }

            while (reader.readLine().also { line = it } != null) {
                if (line.isBlank()) continue
                val values = line.split(",")
                if (values.size >= headers.size) {
                    val record = mutableMapOf<String, String>()
                    for (i in headers.indices) {
                        record[headers[i]] = values[i].trim()
                    }
                    records.add(record)
                }
            }
        }
        return records
    }

    // ==================== 工具方法 ====================

    private fun getRandomUserAgent(): String {
        return USER_AGENTS[(Math.random() * USER_AGENTS.size).toInt()]
    }

    /**
     * 格式化日期为 YYYY-MM-DD
     */
    private fun formatDate(dateStr: String): String {
        return try {
            // 可能是 YYYY-MM-DD 或 YYYY/MM/DD 或 YYYYMMDD 格式
            val cleaned = dateStr.replace("/", "-")
            if (cleaned.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                cleaned
            } else if (cleaned.matches(Regex("\\d{8}"))) {
                "${cleaned.substring(0, 4)}-${cleaned.substring(4, 6)}-${cleaned.substring(6, 8)}"
            } else {
                // 尝试解析
                val formats = arrayOf("yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "yyyy-MM-dd HH:mm:ss")
                for (fmt in formats) {
                    try {
                        val sdf = SimpleDateFormat(fmt, Locale.CHINA)
                        val d = sdf.parse(cleaned)
                        if (d != null) return dateFormat.format(d)
                    } catch (_: Exception) { }
                }
                cleaned // 无法解析则原样返回
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    /**
     * 格式化Tushare日期 (YYYYMMDD → YYYY-MM-DD)
     */
    private fun formatTushareDate(dateStr: String): String {
        return try {
            if (dateStr.length == 8) {
                "${dateStr.substring(0, 4)}-${dateStr.substring(4, 6)}-${dateStr.substring(6, 8)}"
            } else {
                formatDate(dateStr)
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    /**
     * 获取Tushare Token（从SharedPreferences读取）
     */
    private fun getTushareToken(): String {
        val prefs = context.getSharedPreferences("stock_advisor_settings", Context.MODE_PRIVATE)
        return prefs.getString("tushare_token", "") ?: ""
    }

    // ==================== 数据类 ====================

    data class DownloadResult(
        val success: Boolean,
        val source: String,
        val recordCount: Int,
        val filePath: String?,
        val message: String
    )

    data class DataCheckResult(
        val exists: Boolean,
        val message: String,
        val recordCount: Int,
        val dateRange: String,
        val details: List<Map<String, String>>?
    )

    data class ValidationResult(
        val isValid: Boolean,
        val details: String
    )
}
