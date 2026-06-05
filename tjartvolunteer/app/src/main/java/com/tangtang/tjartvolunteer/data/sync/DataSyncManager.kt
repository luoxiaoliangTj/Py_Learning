package com.tangtang.tjartvolunteer.data.sync

import com.tangtang.tjartvolunteer.data.db.UniversityDao
import com.tangtang.tjartvolunteer.data.model.AdmissionScoreEntity
import com.tangtang.tjartvolunteer.data.model.SyncLogEntity
import com.tangtang.tjartvolunteer.data.model.UniversityEntity
import com.tangtang.tjartvolunteer.data.network.DataFetcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * 数据同步管理器
 */
class DataSyncManager(
    private val dao: UniversityDao,
    private val fetcher: DataFetcher = DataFetcher.getInstance()
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _pendingChanges = MutableStateFlow<List<SyncLogEntity>>(emptyList())
    val pendingChanges: StateFlow<List<SyncLogEntity>> = _pendingChanges.asStateFlow()

    sealed class SyncState {
        object Idle : SyncState()
        data class Running(
            val current: String,
            val progress: Int,
            val total: Int
        ) : SyncState()
        data class Completed(
            val newCount: Int,
            val changedCount: Int,
            val errorCount: Int
        ) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    /**
     * 全量同步
     */
    suspend fun syncAll(year: Int = 2025) {
        _syncState.value = SyncState.Running("准备中...", 0, 0)

        try {
            // 使用 first() 一次性获取数据，不阻塞
            val allUnis = dao.getAllUniversities().first()

            if (allUnis.isEmpty()) {
                _syncState.value = SyncState.Error("院校数据为空，请等待数据加载完成")
                return
            }

            _syncState.value = SyncState.Running("开始同步", 0, allUnis.size)

            var newCount = 0
            var changedCount = 0
            var errorCount = 0

            for ((index, uni) in allUnis.withIndex()) {
                _syncState.value = SyncState.Running(uni.name, index + 1, allUnis.size)

                try {
                    // 先尝试掌上高考API
                    val result = fetcher.fetchFromZhangshang(uni.name, year = year)

                    if (result.success && result.scores.isNotEmpty()) {
                        for (score in result.scores) {
                            val action = processScore(score)
                            when (action) {
                                Action.NEW -> newCount++
                                Action.CHANGED -> changedCount++
                                Action.SAME -> {}
                            }
                        }
                    } else {
                        // API没数据，尝试网页搜索
                        val searchResult = fetcher.fetchViaSearch(uni.name, year)
                        if (searchResult.success && searchResult.scores.isNotEmpty()) {
                            for (score in searchResult.scores) {
                                val action = processScore(score)
                                when (action) {
                                    Action.NEW -> newCount++
                                    Action.CHANGED -> changedCount++
                                    Action.SAME -> {}
                                }
                            }
                        } else {
                            errorCount++
                        }
                    }

                    // 每请求一次间隔1.5秒，避免被封
                    delay(1500)
                } catch (e: Exception) {
                    errorCount++
                    try {
                        dao.insertSyncLog(
                            SyncLogEntity(
                                timestamp = System.currentTimeMillis(),
                                type = "error",
                                universityName = uni.name,
                                year = year,
                                oldValue = null,
                                newValue = null,
                                source = "sync",
                                userAction = "error"
                            )
                        )
                    } catch (_: Exception) {}
                }
            }

            _syncState.value = SyncState.Completed(newCount, changedCount, errorCount)
            refreshPendingChanges()
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("同步失败: ${e.message}")
        }
    }

    private suspend fun processScore(newScore: AdmissionScoreEntity): Action {
        return try {
            val existing = dao.getScore(newScore.universityName, newScore.year)

            when {
                existing == null -> {
                    dao.insertScore(newScore.copy(isConfirmed = true))
                    dao.insertSyncLog(
                        SyncLogEntity(
                            timestamp = System.currentTimeMillis(),
                            type = "new",
                            universityName = newScore.universityName,
                            year = newScore.year,
                            oldValue = null,
                            newValue = newScore.minScore?.toString(),
                            source = newScore.source
                        )
                    )
                    Action.NEW
                }
                existing.minScore != newScore.minScore -> {
                    dao.insertSyncLog(
                        SyncLogEntity(
                            timestamp = System.currentTimeMillis(),
                            type = "changed",
                            universityName = newScore.universityName,
                            year = newScore.year,
                            oldValue = existing.minScore?.toString(),
                            newValue = newScore.minScore?.toString(),
                            source = newScore.source
                        )
                    )
                    Action.CHANGED
                }
                else -> {
                    dao.insertScore(newScore.copy(isConfirmed = true))
                    Action.SAME
                }
            }
        } catch (e: Exception) {
            Action.SAME
        }
    }

    suspend fun confirmChange(logId: Long, acceptNew: Boolean) {
        try {
            dao.updateLogAction(logId, if (acceptNew) "accepted" else "rejected")
            refreshPendingChanges()
        } catch (_: Exception) {}
    }

    suspend fun confirmAll(acceptNew: Boolean) {
        try {
            val pending = dao.getPendingChanges().first()
            for (log in pending) {
                dao.updateLogAction(log.id, if (acceptNew) "accepted" else "rejected")
            }
            refreshPendingChanges()
        } catch (_: Exception) {}
    }

    private suspend fun refreshPendingChanges() {
        try {
            _pendingChanges.value = dao.getPendingChanges().first()
        } catch (_: Exception) {}
    }

    enum class Action { NEW, CHANGED, SAME }

    suspend fun loadPendingCount(): Int {
        refreshPendingChanges()
        return _pendingChanges.value.size
    }
}
