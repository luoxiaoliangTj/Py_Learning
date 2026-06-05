package com.tangtang.tjartvolunteer.data.network

import com.tangtang.tjartvolunteer.data.model.AdmissionScoreEntity
import com.tangtang.tjartvolunteer.data.model.DataSourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 网络数据源：从掌上高考等抓取天津美术类录取分数
 *
 * 2025年天津综合分公式：文化×50% + 专业×2.5×50%
 * 2024年及以前：文化×40% + 专业×2.5×60%
 */
class DataFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val dataSources = listOf(
        DataSourceConfig(
            name = "掌上高考",
            baseUrl = "https://api.eol.cn/gkcx/api",
            type = "zhangshang",
            enabled = true
        )
    )

    /**
     * 从掌上高考获取某院校在天津的美术类录取分数
     */
    suspend fun fetchFromZhangshang(
        universityName: String,
        provinceId: Int = 12, // 天津
        year: Int = 2025
    ): FetchResult = withContext(Dispatchers.IO) {
        try {
            val searchUrl = "https://api.eol.cn/gkcx/api" +
                "?access_token=" +
                "&keyword=${java.net.URLEncoder.encode(universityName, "UTF-8")}" +
                "&province_id=$provinceId" +
                "&type=1" +
                "&uri=api_other_scores" +
                "&page=1&size=50"

            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .addHeader("Referer", "https://daxue.cn/")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext FetchResult.error("空响应")

            parseZhangshangResponse(body, universityName, year)
        } catch (e: Exception) {
            FetchResult.error("网络错误: ${e.message}")
        }
    }

    private fun parseZhangshangResponse(
        json: String,
        universityName: String,
        targetYear: Int
    ): FetchResult {
        return try {
            val root = JSONObject(json)
            val code = root.optString("code", "-1")
            if (code != "0000") {
                return FetchResult.error("API错误: code=$code")
            }

            val data = root.optJSONObject("data") ?: return FetchResult.error("无数据")
            val items = data.optJSONArray("item") ?: return FetchResult.error("无记录")

            val scores = mutableListOf<AdmissionScoreEntity>()
            val now = System.currentTimeMillis()

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val year = item.optString("year", "").toIntOrNull() ?: continue

                if (year != targetYear && year != targetYear - 1) continue

                val minScore = item.optString("min_score", "").toDoubleOrNull()
                val minRank = item.optString("min_section", "").toIntOrNull()

                if (minScore != null && minScore > 0) {
                    val formula = if (year >= 2025) "50/50" else "40/60"
                    scores.add(
                        AdmissionScoreEntity(
                            universityName = universityName,
                            year = year,
                            minScore = minScore,
                            minRank = minRank,
                            formula = formula,
                            source = "掌上高考",
                            fetchedAt = now,
                            isConfirmed = false
                        )
                    )
                }
            }

            if (scores.isNotEmpty()) {
                FetchResult.success(scores)
            } else {
                FetchResult.error("未找到${targetYear}年数据")
            }
        } catch (e: Exception) {
            FetchResult.error("解析错误: ${e.message}")
        }
    }

    /**
     * 备用方案：通过网页搜索抓取
     */
    suspend fun fetchViaSearch(
        universityName: String,
        year: Int = 2025
    ): FetchResult = withContext(Dispatchers.IO) {
        try {
            val query = "${universityName} ${year}年 天津 美术类 录取分数线 综合分"
            val searchUrl = "https://www.baidu.com/s?wd=${java.net.URLEncoder.encode(query, "UTF-8")}&rn=10"

            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext FetchResult.error("空响应")

            val scorePattern = Regex("(\\d{3}\\.\\d{1,2})")
            val matches = scorePattern.findAll(html)

            val scores = mutableListOf<AdmissionScoreEntity>()
            val now = System.currentTimeMillis()
            val formula = if (year >= 2025) "50/50" else "40/60"

            for (match in matches) {
                val score = match.value.toDoubleOrNull() ?: continue
                if (score in 300.0..750.0) {
                    scores.add(
                        AdmissionScoreEntity(
                            universityName = universityName,
                            year = year,
                            minScore = score,
                            minRank = null,
                            formula = formula,
                            source = "网页搜索",
                            fetchedAt = now,
                            isConfirmed = false
                        )
                    )
                    break
                }
            }

            if (scores.isNotEmpty()) {
                FetchResult.success(scores)
            } else {
                FetchResult.error("搜索未找到数据")
            }
        } catch (e: Exception) {
            FetchResult.error("搜索失败: ${e.message}")
        }
    }

    data class FetchResult(
        val success: Boolean,
        val scores: List<AdmissionScoreEntity>,
        val error: String? = null
    ) {
        companion object {
            fun success(scores: List<AdmissionScoreEntity>) = FetchResult(true, scores)
            fun error(msg: String) = FetchResult(false, emptyList(), msg)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DataFetcher? = null

        fun getInstance(): DataFetcher {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataFetcher().also { INSTANCE = it }
            }
        }
    }
}
