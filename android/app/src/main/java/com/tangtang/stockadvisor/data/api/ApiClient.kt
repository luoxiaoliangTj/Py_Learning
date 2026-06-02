package com.tangtang.stockadvisor.data.api

import android.content.Context
import com.google.gson.Gson
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

/**
 * 通用 HTTP 客户端（后端 API 已移除，保留基础设施供后续扩展使用）
 */
@Singleton
class ApiClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val gson = Gson()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw Exception("Empty response body")
            body.string()
        }
    }

    suspend fun post(url: String, body: Any): String = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw Exception("Empty response body")
            body.string()
        }
    }

    suspend fun delete(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw Exception("Empty response body")
            body.string()
        }
    }

    inline fun <reified T> parseResponse(json: String): T {
        return gson.fromJson(json, T::class.java)
    }
}
