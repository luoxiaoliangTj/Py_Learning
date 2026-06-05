package com.tangtang.tjartvolunteer.domain.algorithm

import com.tangtang.tjartvolunteer.data.model.AdmissionScoreEntity
import com.tangtang.tjartvolunteer.data.model.UniversityEntity

/**
 * 天津美术类综合分计算器 & 推荐引擎
 *
 * 核心思路：用排名做换算，不用绝对分数。
 * 不同年份公式不同，分数不能直接比，但排名比例可以换算。
 *
 * 2025年：综合分 = 文化课×50% + 专业课×2.5×50%
 * 2024年：综合分 = 文化课×40% + 专业课×2.5×60%
 * 2023年：同2024
 */
object ScoreCalculator {

    /** 计算综合分 */
    fun calculateScore(culture: Double, major: Double, year: Int = 2025): Double {
        return if (year >= 2025) {
            culture * 0.5 + major * 2.5 * 0.5
        } else {
            culture * 0.4 + major * 2.5 * 0.6
        }
    }

    /** 简写 */
    fun calc(culture: Double, major: Double, year: Int = 2025): Double =
        calculateScore(culture, major, year)
}

/**
 * 推荐引擎
 *
 * 算法核心：
 * 1. 用户输入今年文化分、专业分、今年总人数
 * 2. 算出今年综合分
 * 3. 对每个有历史数据的院校：
 *    a. 获取该校近3年(2023/2024/2025)的录取最低分和位次
 *    b. 将历史位次转换为"位次比例" = 位次/当年总人数
 *    c. 用今年的总人数 × 历史位次比例 = 换算到今年的等效位次
 *    d. 取多年数据的均值/最小值/最大值，得到录取位次范围
 *    e. 用户的位次 = 今年总人数 × (1 - 综合分排名百分比)，简化处理用综合分对比
 * 4. 分类：保/稳/冲
 */
object RecommendationEngine {

    data class RecommendInput(
        val cultureScore: Double,
        val majorScore: Double,
        val totalCount: Int,         // 今年总人数（用户输入）
        val preferredMajors: List<String> = emptyList(),
        val year: Int = 2025
    )

    data class RecommendResult(
        val university: UniversityEntity,
        val category: RecommendCategory,
        val matchedMajors: List<String>,
        val warnings: List<String> = emptyList(),
        // ===== 详细计算过程（调试用） =====
        val debugCalculation: DetailedCalculation
    )

    data class DetailedCalculation(
        val userCompositeScore: Double,     // 用户综合分
        val userYear: Int,                 // 目标年份
        val totalCount: Int,               // 今年总人数
        val yearlyData: List<YearlyDebugData>,  // 每年原始数据
        val avgEquivalentRank: Double,     // 平均等效位次（今年）
        val minEquivalentRank: Double,     // 最好情况位次
        val maxEquivalentRank: Double,     // 最差情况位次
        val rankRange: String,             // 位次范围描述
        val scoreDiff: Double,             // 综合分差（仅当年数据）
        val admissionProbability: Double   // 录取概率
    )

    data class YearlyDebugData(
        val year: Int,
        val minScore: Double?,            // 原始录取最低分
        val minRank: Int?,               // 原始录取位次
        val totalCount: Int?,             // 当年总人数（如果有）
        val equivalentRank: Double?,      // 换算到今年的等效位次
        val formula: String,              // 该年公式
        val note: String                   // 备注
    )

    enum class RecommendCategory(val label: String) {
        SAFE("保"), MATCH("稳"), RUSH("冲")
    }

    /**
     * 核心推荐函数
     * @param input 用户输入
     * @param universities 所有院校
     * @param scoresMap 院校名 → 年份 → 分数数据（来自数据库）
     */
    fun recommend(
        input: RecommendInput,
        universities: List<UniversityEntity>,
        scoresMap: Map<String, Map<Int, AdmissionScoreEntity>> = emptyMap()
    ): List<RecommendResult> {

        val userScore = ScoreCalculator.calc(input.cultureScore, input.majorScore, input.year)

        return universities.mapNotNull { uni ->
            // 校考排除
            val excludeMajors = parseMajors(uni.examExcludeMajors)
            val allMajors = parseMajors(uni.majors)
            val availableMajors = if (excludeMajors.isNotEmpty()) {
                allMajors.filter { it !in excludeMajors }
            } else allMajors

            if (availableMajors.isEmpty()) return@mapNotNull null

            // 获取该校所有年份的分数数据
            val uniScores = scoresMap[uni.name] ?: return@mapNotNull null

            // 构建每年的详细数据
            val yearlyData = mutableListOf<YearlyDebugData>()
            val equivalentRanks = mutableListOf<Double>()

            for (targetYear in listOf(2025, 2024, 2023)) {
                val scoreEntity = uniScores[targetYear] ?: continue
                val minScore = scoreEntity.minScore ?: continue
                val minRank = scoreEntity.minRank  // 可以为null，不影响匹配

                // 将历史位次换算到今年：
                // 位次比例 = 历史位次 / 当年总人数
                // 今年等效位次 = 位次比例 × 今年总人数
                // 如果没有当年总人数数据，直接用绝对位次
                val equivalentRank: Double
                val note: String
                if (minRank != null) {
                    equivalentRank = minRank.toDouble()
                    note = "位次$minRank"
                } else {
                    equivalentRank = 0.0  // 无位次数据
                    note = "位次未知"
                }

                equivalentRanks.add(equivalentRank)

                yearlyData.add(YearlyDebugData(
                    year = targetYear,
                    minScore = minScore,
                    minRank = minRank,
                    totalCount = null,
                    equivalentRank = equivalentRank,
                    formula = if (targetYear >= 2025) "文化×0.5+专×2.5×0.5" else "文化×0.4+专×2.5×0.6",
                    note = note
                ))
            }

            if (yearlyData.isEmpty()) return@mapNotNull null

            // 计算位次范围（过滤掉0值=无位次数据）
            val validRanks = equivalentRanks.filter { it > 0 }
            val avgRank = if (validRanks.isNotEmpty()) validRanks.average() else 0.0
            val minRankBest = if (validRanks.isNotEmpty()) validRanks.min() else 0.0
            val maxRankWorst = if (validRanks.isNotEmpty()) validRanks.max() else 0.0
            val rankRange = if (validRanks.isNotEmpty()) {
                "${minRankBest.toInt()}~${maxRankWorst.toInt()} (均值${avgRank.toInt()})"
            } else {
                "无位次数据"
            }

            // 综合分差 = 用户综合分 - 最近一年的录取最低分
            val latestYearData = yearlyData.first()
            val scoreDiff = userScore - (latestYearData.minScore ?: 0.0)

            // 录取概率（基于综合分差）
            val probability = when {
                scoreDiff > 20 -> 0.90
                scoreDiff > 10 -> 0.75
                scoreDiff > 5 -> 0.65
                scoreDiff > 0 -> 0.55
                scoreDiff > -5 -> 0.40
                scoreDiff > -10 -> 0.25
                scoreDiff > -20 -> 0.15
                else -> 0.05
            }

            // 分类
            val category = when {
                scoreDiff > 10 -> RecommendCategory.SAFE
                scoreDiff > -5 -> RecommendCategory.MATCH
                else -> RecommendCategory.RUSH
            }

            // 匹配专业
            val matchedMajors = if (input.preferredMajors.isNotEmpty()) {
                availableMajors.filter { it in input.preferredMajors }.ifEmpty { availableMajors }
            } else availableMajors

            // 警告
            val warnings = mutableListOf<String>()
            if (uni.admissionType.contains("校考")) {
                warnings.add("部分专业需校考，仅显示省统考专业")
            }
            if (yearlyData.none { it.year == input.year }) {
                val availableYears = yearlyData.map { it.year }.sortedDescending()
                warnings.add("使用${availableYears.joinToString("/")}年数据（无${input.year}年）")
            }

            RecommendResult(
                university = uni,
                category = category,
                matchedMajors = matchedMajors,
                warnings = warnings,
                debugCalculation = DetailedCalculation(
                    userCompositeScore = userScore,
                    userYear = input.year,
                    totalCount = input.totalCount,
                    yearlyData = yearlyData,
                    avgEquivalentRank = avgRank,
                    minEquivalentRank = minRankBest,
                    maxEquivalentRank = maxRankWorst,
                    rankRange = rankRange,
                    scoreDiff = scoreDiff,
                    admissionProbability = probability
                )
            )
        }.sortedByDescending { it.debugCalculation.admissionProbability }
    }

    private fun parseMajors(majorsStr: String): List<String> {
        if (majorsStr.isBlank()) return emptyList()
        return majorsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
