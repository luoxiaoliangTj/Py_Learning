package com.tangtang.tjartvolunteer.data.db

import androidx.room.*
import com.tangtang.tjartvolunteer.data.model.AdmissionScoreEntity
import com.tangtang.tjartvolunteer.data.model.SyncLogEntity
import com.tangtang.tjartvolunteer.data.model.UniversityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UniversityDao {
    // ========== 院校查询 ==========
    @Query("SELECT * FROM universities ORDER BY isLocal DESC, name ASC")
    fun getAllUniversities(): Flow<List<UniversityEntity>>

    @Query("SELECT * FROM universities WHERE name = :name")
    suspend fun getUniversityByName(name: String): UniversityEntity?

    @Query("SELECT * FROM universities WHERE isLocal = 1 ORDER BY name ASC")
    fun getLocalUniversities(): Flow<List<UniversityEntity>>

    @Query("SELECT * FROM universities WHERE isLocal = 0 ORDER BY name ASC")
    fun getOtherUniversities(): Flow<List<UniversityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(university: UniversityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(universities: List<UniversityEntity>)

    @Query("SELECT COUNT(*) FROM universities")
    suspend fun getUniversityCount(): Int

    // ========== 录取分数查询 ==========
    @Query("SELECT * FROM admission_scores WHERE universityName = :name ORDER BY year DESC")
    fun getScoresByName(name: String): Flow<List<AdmissionScoreEntity>>

    @Query("SELECT * FROM admission_scores WHERE universityName = :name AND year = :year")
    suspend fun getScore(name: String, year: Int): AdmissionScoreEntity?

    @Query("SELECT * FROM admission_scores WHERE year = :year")
    fun getScoresByYear(year: Int): Flow<List<AdmissionScoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: AdmissionScoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScores(scores: List<AdmissionScoreEntity>)

    @Query("UPDATE admission_scores SET minScore = :score, minRank = :rank, source = :source, fetchedAt = :fetchedAt, isConfirmed = 0 WHERE universityName = :name AND year = :year")
    suspend fun updateScore(name: String, year: Int, score: Double?, rank: Int?, source: String, fetchedAt: Long)

    @Query("UPDATE admission_scores SET isConfirmed = 1 WHERE universityName = :name AND year = :year")
    suspend fun confirmScore(name: String, year: Int)

    @Query("SELECT COUNT(*) FROM admission_scores WHERE year = :year")
    suspend fun getScoreCountByYear(year: Int): Int

    // ========== 同步日志 ==========
    @Insert
    suspend fun insertSyncLog(log: SyncLogEntity)

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_logs WHERE userAction = 'pending' ORDER BY timestamp DESC")
    fun getPendingChanges(): Flow<List<SyncLogEntity>>

    @Query("UPDATE sync_logs SET userAction = :action WHERE id = :id")
    suspend fun updateLogAction(id: Long, action: String)

    @Query("SELECT COUNT(*) FROM sync_logs WHERE userAction = 'pending'")
    suspend fun getPendingCount(): Int
}
