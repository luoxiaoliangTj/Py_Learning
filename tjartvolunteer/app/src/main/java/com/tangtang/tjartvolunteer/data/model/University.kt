package com.tangtang.tjartvolunteer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore

// ========== 静态数据：院校+专业（内置，不变）==========

@Entity(tableName = "universities")
data class UniversityEntity(
    @PrimaryKey val name: String,
    val type: String,           // 公办本科/民办本科/独立学院
    val level: String,          // 985/211/双一流/省重点/普通
    val majors: String,         // JSON数组存为字符串：["绘画","雕塑",...]
    val admissionType: String,  // "统考" / "校考" / "统考,校考"
    val examExcludeMajors: String, // 需校考排除的专业名
    val examIncludeMajors: String, // 确认可走统考的专业名
    val isLocal: Boolean,       // 天津本地=true
    val note: String           // 备注
)

// ========== 动态数据：录取分数（可从网络更新）==========

@Entity(
    tableName = "admission_scores",
    primaryKeys = ["universityName", "year"]
)
data class AdmissionScoreEntity(
    val universityName: String, // 关联大学名
    val year: Int,              // 2023/2024/2025
    val minScore: Double?,      // 最低综合分
    val minRank: Int?,          // 最低位次
    val formula: String,        // "40/60" 或 "50/50"，记录用的哪个公式
    val source: String,         // 数据来源："builtin"/"网络抓取"/"用户导入"
    val fetchedAt: Long,        // 抓取时间戳
    val isConfirmed: Boolean    // 用户是否已确认（变更时需确认）
)

// ========== 同步日志：记录每次数据变更 ==========

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,           // "new"(新增) / "changed"(变更) / "error"(失败)
    val universityName: String,
    val year: Int,
    val oldValue: String?,      // 旧分数（changed时）
    val newValue: String?,      // 新分数
    val source: String,
    val userAction: String = "pending" // pending/accepted/rejected
)

// ========== JSON模型：用于Gson解析预置数据和网络数据 ==========

data class UniversityData(
    val tianjinLocal: List<UniversityInfo>,
    val otherUniversities: List<UniversityInfo>
)

data class UniversityInfo(
    val name: String,
    val type: String,
    val level: String,
    val majors: List<String>,
    val admissionType: List<String>,
    val examExclude: List<String> = emptyList(),
    val examInclude: List<String> = emptyList(),
    val scores2024: ScoreData? = null,
    val scores2025: ScoreData? = null,
    val note: String = ""
)

data class ScoreData(
    val minScore: Double?,
    val minRank: Int?
)

// ========== 网络数据源配置 ==========

data class DataSourceConfig(
    val name: String,
    val baseUrl: String,
    val type: String, // "zhangshang" / "gaokao" / "custom"
    val enabled: Boolean = true
)

// ========== 转换函数 ==========

fun UniversityInfo.toEntity(isLocal: Boolean = false): UniversityEntity = UniversityEntity(
    name = name,
    type = type,
    level = level,
    majors = majors?.joinToString(",") ?: "",
    admissionType = admissionType?.joinToString(",") ?: "",
    examExcludeMajors = examExclude?.joinToString(",") ?: "",
    examIncludeMajors = examInclude?.joinToString(",") ?: "",
    isLocal = isLocal,
    note = note ?: ""
)

// 从UniversityEntity + ScoreData 构建推荐用的扩展数据
data class UniversityWithScores(
    val university: UniversityEntity,
    val scores: Map<Int, ScoreData> // year -> score data
) {
    val allMajors: List<String>
        get() = university.majors.split(",").filter { it.isNotBlank() }

    val excludeMajors: List<String>
        get() = university.examExcludeMajors.split(",").filter { it.isNotBlank() }

    val includeMajors: List<String>
        get() = university.examIncludeMajors.split(",").filter { it.isNotBlank() }

    fun getScoreForYear(year: Int): ScoreData? = scores[year]
        ?: scores.values.firstOrNull() // 回退到有数据的年份
}
