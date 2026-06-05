package com.tangtang.tjartvolunteer

import com.tangtang.tjartvolunteer.domain.algorithm.RecommendationEngine

/** 全局推荐结果持有者 — 解决跨页面 ViewModel 不共享的问题 */
object RecommendationHolder {
    var results: List<RecommendationEngine.RecommendResult> = emptyList()
    var hasCalculated: Boolean = false

    fun set(results: List<RecommendationEngine.RecommendResult>) {
        this.results = results
        this.hasCalculated = true
        DebugLog.append("[SYS] 推荐结果已保存: ${results.size}所")
    }

    fun clear() {
        results = emptyList()
        hasCalculated = false
        DebugLog.append("[SYS] 推荐结果已清空")
    }
}
