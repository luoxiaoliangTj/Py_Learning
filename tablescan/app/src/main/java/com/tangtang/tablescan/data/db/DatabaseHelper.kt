package com.tangtang.tablescan.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tangtang.tablescan.data.model.ScanRecord

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "tablescan.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                val instance = DatabaseHelper(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE scan_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '',
                imagePath TEXT NOT NULL DEFAULT '',
                recognizedText NOT NULL DEFAULT '',
                tableData NOT NULL DEFAULT '',
                rowCount INTEGER NOT NULL DEFAULT 0,
                colCount INTEGER NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS scan_records")
        onCreate(db)
    }

    fun insertRecord(record: ScanRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", record.title)
            put("imagePath", record.imagePath)
            put("recognizedText", record.recognizedText)
            put("tableData", record.tableData)
            put("rowCount", record.rowCount)
            put("colCount", record.colCount)
            put("timestamp", record.timestamp)
        }
        return db.insert("scan_records", null, values)
    }

    fun getAllRecords(): List<ScanRecord> {
        val db = readableDatabase
        val records = mutableListOf<ScanRecord>()
        db.query("scan_records", null, null, null, null, null, "timestamp DESC").use { cursor ->
            while (cursor.moveToNext()) {
                records.add(cursorToRecord(cursor))
            }
        }
        return records
    }

    fun getRecordById(id: Long): ScanRecord? {
        val db = readableDatabase
        db.query("scan_records", null, "id = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursorToRecord(cursor) else null
        }
    }

    fun deleteRecord(id: Long): Int {
        val db = writableDatabase
        return db.delete("scan_records", "id = ?", arrayOf(id.toString()))
    }

    fun deleteAll(): Int {
        val db = writableDatabase
        return db.delete("scan_records", null, null)
    }

    fun getCount(): Int {
        val db = readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM scan_records", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun cursorToRecord(cursor: android.database.Cursor): ScanRecord {
        return ScanRecord(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            imagePath = cursor.getString(cursor.getColumnIndexOrThrow("imagePath")),
            recognizedText = cursor.getString(cursor.getColumnIndexOrThrow("recognizedText")),
            tableData = cursor.getString(cursor.getColumnIndexOrThrow("tableData")),
            rowCount = cursor.getInt(cursor.getColumnIndexOrThrow("rowCount")),
            colCount = cursor.getInt(cursor.getColumnIndexOrThrow("colCount")),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
        )
    }
}
