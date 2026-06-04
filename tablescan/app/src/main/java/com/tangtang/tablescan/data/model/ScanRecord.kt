package com.tangtang.tablescan.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val imagePath: String = "",
    val recognizedText: String = "",
    val tableData: String = "",  // JSON: List<List<String>>
    val rowCount: Int = 0,
    val colCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "debug_logs")
data class DebugLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tag: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
