package com.tangtang.tjartvolunteer

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tangtang.tjartvolunteer.data.db.AppDatabase
import com.tangtang.tjartvolunteer.data.model.*
import kotlinx.coroutines.runBlocking

/** 全局调试日志 - 所有模块都可调用 */
object DebugLog {
    private val buffer = StringBuilder()
    private const val MAX_LINES = 200

    @Synchronized
    fun append(msg: String) {
        val line = msg
        buffer.appendLine(line)
        Log.d("TJArt", line)
        // 防止无限增长
        val lines = buffer.lines()
        if (lines.size > MAX_LINES) {
            val trimmed = lines.takeLast(MAX_LINES / 2)
            buffer.clear()
            trimmed.forEach { buffer.appendLine(it) }
        }
    }

    fun getAll(): List<String> = buffer.lines().filter { it.isNotBlank() }

    fun clear() { buffer.clear() }
}

class TJArtVolunteerApp : Application() {

    lateinit var database: AppDatabase
        private set

    val debugLog get() = DebugLog.getAll()
    var preloadDone = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        DebugLog.append("[SYS] App启动, 开始初始化数据库")
        database = AppDatabase.getInstance(this)
        Thread { loadPresetData() }.start()
    }

    private fun loadPresetData() {
        try {
            DebugLog.append("[SYS] 预加载启动")
            database.openHelper.readableDatabase
            DebugLog.append("[SYS] 数据库已打开")

            val dao = database.universityDao()
            val count = runBlocking { dao.getUniversityCount() }
            DebugLog.append("[DATA] 现有院校数: $count")
            if (count >= 65) {
                DebugLog.append("[DATA] 数据已完整(>=65)，跳过预加载")
                preloadDone = true
                return
            }

            val json = assets.open("data/universities.json").bufferedReader().use { it.readText() }
            DebugLog.append("[DATA] JSON读取成功, ${json.length}字节")

            val data: UniversityData = Gson().fromJson(json, object : TypeToken<UniversityData>() {}.type)
            DebugLog.append("[DATA] 解析成功: 本地${data.tianjinLocal.size}所, 外地${data.otherUniversities.size}所")

            // 插入院校
            var uniOk = 0
            var uniFail = 0
            var lastError = ""
            val allInfo = data.tianjinLocal.map { it to true } + data.otherUniversities.map { it to false }
            for ((info, isLocal) in allInfo) {
                try {
                    runBlocking { dao.insert(info.toEntity(isLocal = isLocal)) }
                    uniOk++
                } catch (e: Exception) {
                    uniFail++
                    lastError = "${e.javaClass.simpleName}: ${e.message}"
                    DebugLog.append("[FAIL] 院校插入失败: ${info.name} - $lastError")
                }
            }
            DebugLog.append("[DATA] 院校插入: 成功=$uniOk 失败=$uniFail")

            // 插入分数
            var scoreOk = 0
            var scoreFail = 0
            val now = System.currentTimeMillis()
            for (info in data.tianjinLocal + data.otherUniversities) {
                if (info.scores2024 != null) {
                    try {
                        runBlocking {
                            dao.insertScore(AdmissionScoreEntity(
                                universityName = info.name, year = 2024,
                                minScore = info.scores2024!!.minScore,
                                minRank = info.scores2024!!.minRank,
                                formula = "40/60", source = "builtin",
                                fetchedAt = now, isConfirmed = true
                            ))
                        }
                        scoreOk++
                    } catch (e: Exception) {
                        scoreFail++
                        DebugLog.append("[FAIL] 分数插入失败: ${info.name} 2024 - ${e.message}")
                    }
                }
                if (info.scores2025 != null) {
                    try {
                        runBlocking {
                            dao.insertScore(AdmissionScoreEntity(
                                universityName = info.name, year = 2025,
                                minScore = info.scores2025!!.minScore,
                                minRank = info.scores2025!!.minRank,
                                formula = "50/50", source = "builtin",
                                fetchedAt = now, isConfirmed = true
                            ))
                        }
                        scoreOk++
                    } catch (e: Exception) {
                        scoreFail++
                        DebugLog.append("[FAIL] 分数插入失败: ${info.name} 2025 - ${e.message}")
                    }
                }
            }
            DebugLog.append("[DATA] 分数插入: 成功=$scoreOk 失败=$scoreFail")

            val finalCount = runBlocking { dao.getUniversityCount() }
            DebugLog.append("[DATA] 最终院校数: $finalCount / 65")
            preloadDone = true
        } catch (e: Exception) {
            DebugLog.append("[ERROR] 预加载异常: ${e.javaClass.simpleName}: ${e.message}")
            Log.e("TJArt", "预加载异常", e)
        }
    }

    companion object {
        lateinit var instance: TJArtVolunteerApp
            private set
    }
}
