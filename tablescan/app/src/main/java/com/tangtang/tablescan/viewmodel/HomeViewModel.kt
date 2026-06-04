package com.tangtang.tablescan.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tangtang.tablescan.data.db.DatabaseHelper
import com.tangtang.tablescan.data.model.ScanRecord
import com.tangtang.tablescan.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class ScanState {
    object Idle : ScanState()
    object Processing : ScanState()
    data class Success(val recordId: Long) : ScanState()
    data class Error(val message: String) : ScanState()
}

class HomeViewModel : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private var pendingUri: Uri? = null

    // ML Kit text recognizer (on-device, no internet needed)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun setPendingUri(uri: Uri) {
        pendingUri = uri
    }

    fun prepareCamera() {}

    fun processImage(context: Context) {
        val uri = pendingUri ?: return
        processGalleryImage(context, uri)
    }

    fun processGalleryImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _scanState.value = ScanState.Processing
            _recognizedText.value = ""

            try {
                withContext(Dispatchers.IO) {
                    // 1. Load bitmap
                    val bitmap = loadBitmap(context, uri)
                    _capturedBitmap.value = bitmap

                    // 2. Save image to app storage
                    val imagePath = saveImage(context, bitmap)

                    // 3. OCR with ML Kit (on-device)
                    val ocrResult = recognizeText(bitmap)
                    val text = ocrResult.text

                    // 4. Parse table structure from text blocks
                    val (tableData, rows, cols) = parseTableStructure(ocrResult)

                    _recognizedText.value = text

                    // 5. Save to database
                    val db = DatabaseHelper.getInstance(context)
                    val record = ScanRecord(
                        title = "扫描 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                        imagePath = imagePath,
                        recognizedText = text,
                        tableData = tableData,
                        rowCount = rows,
                        colCount = cols
                    )
                    val recordId = db.insertRecord(record)

                    _scanState.value = ScanState.Success(recordId)
                }
            } catch (e: Exception) {
                Log.e("TableScan", "Process image failed", e)
                _scanState.value = ScanState.Error(e.message ?: "识别失败")
            }
        }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw Exception("无法加载图片")
    }

    private fun saveImage(context: Context, bitmap: Bitmap): String {
        val file = File(context.filesDir, "scan_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    /**
     * ML Kit on-device text recognition
     */
    private suspend fun recognizeText(bitmap: Bitmap): com.google.mlkit.vision.text.Text {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return textRecognizer.process(inputImage).await()
    }

    /**
     * Parse ML Kit text blocks into table structure.
     * Uses heuristics: groups blocks by Y-coordinate proximity (rows),
     * then splits each row by X-coordinate gaps (columns).
     */
    private fun parseTableStructure(text: com.google.mlkit.vision.text.Text): Triple<String, Int, Int> {
        val fullText = text.text

        // Get all text blocks with their bounding boxes
        val blocks = mutableListOf<TextBlock>()
        for (block in text.textBlocks) {
            val boundingBox = block.boundingBox ?: continue
            val centerY = boundingBox.centerY()
            val centerX = boundingBox.centerX()
            blocks.add(TextBlock(block.text, centerX, centerY, boundingBox.width(), boundingBox.height()))
        }

        if (blocks.isEmpty()) {
            return Triple(fullText, 0, 0)
        }

        // Group blocks into rows by Y-coordinate proximity
        val sortedByY = blocks.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<TextBlock>>()
        var currentRow = mutableListOf<TextBlock>()
        var lastY = sortedByY[0].centerY
        val avgHeight = blocks.map { it.height }.average()

        for (block in sortedByY) {
            if (kotlin.math.abs(block.centerY - lastY) < avgHeight * 0.6) {
                currentRow.add(block)
            } else {
                if (currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                }
                currentRow = mutableListOf(block)
            }
            lastY = block.centerY
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        // Sort each row by X coordinate
        val sortedRows = rows.map { row ->
            row.sortedBy { it.centerX }
        }

        // Build table data as List<List<String>>
        val tableData = sortedRows.map { row ->
            row.map { it.text }
        }

        val rowCount = tableData.size
        val colCount = tableData.maxOfOrNull { it.size } ?: 0

        // Serialize to JSON
        val gson = com.google.gson.Gson()
        val tableJson = gson.toJson(tableData)

        return Triple(tableJson, rowCount, colCount)
    }

    data class TextBlock(
        val text: String,
        val centerX: Int,
        val centerY: Int,
        val width: Int,
        val height: Int
    )

    fun reset() {
        _scanState.value = ScanState.Idle
        _capturedBitmap.value = null
        _recognizedText.value = ""
        pendingUri = null
    }

    override fun onCleared() {
        super.onCleared()
        textRecognizer.close()
    }
}
