package com.tangtang.tjartvolunteer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tangtang.tjartvolunteer.DebugLog
import com.tangtang.tjartvolunteer.RecommendationHolder
import com.tangtang.tjartvolunteer.TJArtVolunteerApp
import com.tangtang.tjartvolunteer.data.model.AdmissionScoreEntity
import com.tangtang.tjartvolunteer.data.model.UniversityEntity
import com.tangtang.tjartvolunteer.data.sync.DataSyncManager
import com.tangtang.tjartvolunteer.domain.algorithm.RecommendationEngine
import com.tangtang.tjartvolunteer.domain.algorithm.ScoreCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = TJArtVolunteerApp.instance.database.universityDao()
    private val syncManager = DataSyncManager(dao)

    private val _cultureScore = MutableStateFlow("")
    val cultureScore: StateFlow<String> = _cultureScore.asStateFlow()

    private val _majorScore = MutableStateFlow("")
    val majorScore: StateFlow<String> = _majorScore.asStateFlow()

    private val _totalCount = MutableStateFlow("")
    val totalCount: StateFlow<String> = _totalCount.asStateFlow()

    private val _recommendResults = MutableStateFlow<List<RecommendationEngine.RecommendResult>>(emptyList())
    val recommendResults: StateFlow<List<RecommendationEngine.RecommendResult>> = _recommendResults.asStateFlow()

    private val _hasCalculated = MutableStateFlow(false)
    val hasCalculated: StateFlow<Boolean> = _hasCalculated.asStateFlow()

    val syncState = syncManager.syncState
    val pendingChanges = syncManager.pendingChanges

    private val _universityCount = MutableStateFlow(0)
    val universityCount: StateFlow<Int> = _universityCount.asStateFlow()

    private val _scoreCount = MutableStateFlow(0)
    val scoreCount: StateFlow<Int> = _scoreCount.asStateFlow()

    private var allUniversities: List<UniversityEntity> = emptyList()
    private var allScores: Map<String, Map<Int, AdmissionScoreEntity>> = emptyMap()

    init {
        DebugLog.append("[SYS] HomeViewModel初始化")
        viewModelScope.launch {
            dao.getAllUniversities().collect { universities ->
                allUniversities = universities
                _universityCount.value = universities.size
                DebugLog.append("[DATA] 院校列表更新: ${universityCount}所")
            }
        }
        viewModelScope.launch {
            dao.getScoresByYear(2025).collect { scores2025 ->
                val map = mutableMapOf<String, MutableMap<Int, AdmissionScoreEntity>>()
                scores2025.forEach { score ->
                    map.getOrPut(score.universityName) { mutableMapOf() }[score.year] = score
                }
                dao.getScoresByYear(2024).collect { scores2024 ->
                    scores2024.forEach { score ->
                        map.getOrPut(score.universityName) { mutableMapOf() }[score.year] = score
                    }
                    allScores = map
                    _scoreCount.value = map.size
                    DebugLog.append("[DATA] 分数数据更新: ${map.size}所有分数")
                }
            }
        }
        viewModelScope.launch {
            syncManager.loadPendingCount()
        }
    }

    fun updateCultureScore(score: String) {
        _cultureScore.value = score
        DebugLog.append("[USER] 输入文化分: $score")
    }

    fun updateMajorScore(score: String) {
        _majorScore.value = score
        DebugLog.append("[USER] 输入专业分: $score")
    }

    fun updateTotalCount(count: String) {
        _totalCount.value = count
        DebugLog.append("[USER] 输入总人数: $count")
    }

    fun getCompositeScore(): Double? {
        val culture = _cultureScore.value.toDoubleOrNull() ?: return null
        val major = _majorScore.value.toDoubleOrNull() ?: return null
        return ScoreCalculator.calc(culture, major, 2025)
    }

    fun calculateRecommendation() {
        val culture = _cultureScore.value.toDoubleOrNull() ?: return
        val major = _majorScore.value.toDoubleOrNull() ?: return
        if (culture <= 0 || major <= 0) return
        val totalCount = _totalCount.value.toIntOrNull() ?: 0

        DebugLog.append("[USER] 点击生成推荐: 文化=$culture 专业=$major 总人数=$totalCount")

        val userScore = ScoreCalculator.calc(culture, major, 2025)
        DebugLog.append("[CALC] 用户综合分=$userScore (公式: 文化×0.5+专×2.5×0.5)")
        DebugLog.append("[CALC] 参与计算: ${allUniversities.size}所院校, ${allScores.size}所有分数数据")

        val input = RecommendationEngine.RecommendInput(
            cultureScore = culture,
            majorScore = major,
            totalCount = totalCount,
            year = 2025
        )

        val results = RecommendationEngine.recommend(input, allUniversities, allScores)
        RecommendationHolder.set(results)
        _recommendResults.value = results
        _hasCalculated.value = true

        DebugLog.append("[RESULT] 推荐结果: ${results.size}所院校")
        if (results.isNotEmpty()) {
            val safeCount = results.count { it.category == RecommendationEngine.RecommendCategory.SAFE }
            val matchCount = results.count { it.category == RecommendationEngine.RecommendCategory.MATCH }
            val rushCount = results.count { it.category == RecommendationEngine.RecommendCategory.RUSH }
            DebugLog.append("[RESULT] 保=$safeCount 稳=$matchCount 冲=$rushCount")
            // 记录前3个结果
            results.take(3).forEach { r ->
                val c = r.debugCalculation
                DebugLog.append("[RESULT] ${r.university.name}: 分差=${String.format("%+.1f", c.scoreDiff)} 概率=${(c.admissionProbability*100).toInt()}% 位次=${c.rankRange}")
            }
        }
    }

    fun reset() {
        _cultureScore.value = ""
        _majorScore.value = ""
        _totalCount.value = ""
        _recommendResults.value = emptyList()
        _hasCalculated.value = false
        RecommendationHolder.clear()
        DebugLog.append("[USER] 重置所有输入")
    }

    fun startSync(year: Int = 2025) {
        DebugLog.append("[USER] 开始同步${year}年数据")
        viewModelScope.launch { syncManager.syncAll(year) }
    }

    fun confirmChange(logId: Long, acceptNew: Boolean) {
        DebugLog.append("[USER] 确认变更 logId=$logId accept=$acceptNew")
        viewModelScope.launch { syncManager.confirmChange(logId, acceptNew) }
    }

    fun confirmAllChanges(acceptNew: Boolean) {
        DebugLog.append("[USER] 确认所有变更 accept=$acceptNew")
        viewModelScope.launch { syncManager.confirmAll(acceptNew) }
    }
}
