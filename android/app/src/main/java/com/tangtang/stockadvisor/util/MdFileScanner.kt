package com.tangtang.stockadvisor.util

import java.io.File

/**
 * 扫描存储卡目录中的持仓 .md 文件并解析
 * 固定扫描路径：/storage/emulated/0/Documents/mindmaps/炒股/
 */
object MdFileScanner {

    private const val SCAN_DIR = "/storage/emulated/0/Documents/mindmaps/炒股/"

    data class ScanResult(
        val holdings: List<Map<String, Any>>,
        val capital: Map<String, Double>?,
        val fileName: String,
        val errorMessage: String? = null
    )

    /**
     * 扫描目录，按文件名中的8位日期排序取最新 .md 文件，解析后返回结果
     * 在 Dispatchers.IO 中调用（涉及文件读取）
     */
    fun scanAndParse(): ScanResult {
        val dir = File(SCAN_DIR)
        if (!dir.exists() || !dir.isDirectory) {
            return ScanResult(
                holdings = emptyList(),
                capital = null,
                fileName = "",
                errorMessage = "目录不存在: $SCAN_DIR"
            )
        }

        // 获取所有 .md 文件
        val mdFiles = dir.listFiles { file ->
            file.isFile && file.extension.lowercase() == "md"
        } ?: emptyArray()

        if (mdFiles.isEmpty()) {
            return ScanResult(
                holdings = emptyList(),
                capital = null,
                fileName = "",
                errorMessage = "目录中未找到 .md 文件: $SCAN_DIR"
            )
        }

        // 按文件名中的8位日期排序，取最新
        val sorted = mdFiles.sortedByDescending { file ->
            val dateMatch = Regex("(\\d{8})").find(file.name)
            dateMatch?.value ?: "00000000"
        }
        val latestFile = sorted[0]

        return parseFile(latestFile)
    }

    /**
     * 解析单个 .md 文件
     * 在 Dispatchers.Default 中调用（CPU 密集解析）
     */
    fun parseFile(file: File): ScanResult {
        val content: String
        try {
            content = file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return ScanResult(
                holdings = emptyList(),
                capital = null,
                fileName = file.name,
                errorMessage = "读取文件失败: ${e.message}"
            )
        }

        val holdings = mutableListOf<Map<String, Any>>()
        var totalAssets = 0.0
        var availableCash = 0.0

        // 解析总资产
        val totalAssetsMatch = Regex("总资产\\s*:\\s*([￥$]?\\s*[\\d,]+\\.?\\d*)").find(content)
        if (totalAssetsMatch != null) {
            totalAssets = cleanNumber(totalAssetsMatch.groupValues[1])
        }

        // 解析可用资金
        val availableCashMatch = Regex("可用资金\\s*:\\s*([￥$]?\\s*[\\d,]+\\.?\\d*)").find(content)
        if (availableCashMatch != null) {
            availableCash = cleanNumber(availableCashMatch.groupValues[1])
        }

        // 找到表头行
        val lines = content.split("\n")
        var headerLine: String? = null
        for (line in lines) {
            if (line.contains("股票名称") && line.contains("市值")) {
                headerLine = line
                break
            }
        }

        if (headerLine != null) {
            // 解析表头列索引
            val headers = headerLine.split("|").drop(1).dropLast(1).map { it.trim() }
            val colIndex = mutableMapOf<String, Int>()
            for (i in headers.indices) {
                when {
                    "股票名称" in headers[i] -> colIndex["name"] = i
                    "股票代码" in headers[i] -> colIndex["code"] = i
                    "持仓" in headers[i] && "可用" in headers[i] -> colIndex["shares"] = i
                    "成本价" in headers[i] -> colIndex["cost"] = i
                }
            }

            if ("name" in colIndex && "shares" in colIndex && "cost" in colIndex) {
                for (line in lines) {
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("|") || "---" in trimmed || trimmed == headerLine) continue

                    val cells = trimmed.split("|").drop(1).dropLast(1)
                    if (cells.size <= (colIndex.values.maxOrNull() ?: 0)) continue

                    // 提取股票名称
                    val nameCell = cells[colIndex["name"]!!].trim()
                    val nameMatch = Regex("\\[\\[(.+?)\\]\\]").find(nameCell)
                    if (nameMatch == null) continue
                    val stockName = nameMatch.groupValues[1].trim()

                    // 提取股票代码
                    var symbol: String? = null
                    if ("code" in colIndex) {
                        val codeCell = cells[colIndex["code"]!!].trim()
                        if (codeCell.isNotEmpty() && codeCell != "-" && codeCell != "N/A") {
                            symbol = Regex("\\d+").find(codeCell)?.value
                        }
                    }

                    // 提取持仓股数
                    val sharesCell = cells[colIndex["shares"]!!].trim()
                    val sharesPart = sharesCell.split("/")[0].trim()
                    val shares = cleanNumber(sharesPart).toInt()

                    // 提取成本价
                    val costCell = cells[colIndex["cost"]!!].trim()
                    val costPrice = if (costCell == "-" || costCell.isEmpty() || "特殊" in costCell) {
                        0.0
                    } else {
                        cleanNumber(costCell)
                    }

                    holdings.add(
                        mapOf(
                            "symbol" to (symbol ?: ""),
                            "name" to stockName,
                            "shares" to shares,
                            "cost_price" to costPrice
                        )
                    )
                }
            }
        }

        val capital = if (totalAssets > 0 || availableCash > 0) {
            mapOf(
                "total_capital" to totalAssets,
                "available_cash" to availableCash
            )
        } else null

        return ScanResult(
            holdings = holdings,
            capital = capital,
            fileName = file.name,
            errorMessage = if (holdings.isEmpty()) "未解析到任何持仓数据" else null
        )
    }

    /**
     * 清洗数字字符串，移除货币符号、千分位逗号等
     */
    fun cleanNumber(text: String): Double {
        val cleaned = text.replace(Regex("[^\\d.-]"), "")
        return if (cleaned.isNotEmpty()) {
            cleaned.toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
    }
}
