package com.tangtang.stockadvisor.util

/**
 * 解析 .md 持仓文件内容
 * 纯解析逻辑，不涉及文件扫描
 */
object MdFileParser {

    data class ParseResult(
        val holdings: List<Map<String, Any>>,
        val capital: Map<String, Double>?,
        val fileName: String = "",
        val errorMessage: String? = null
    )

    /**
     * 解析 .md 文件内容（对齐原始代码 position_manager_tool.py._parse_md_file）
     * @param content .md 文件的文本内容
     * @param fileName 文件名（用于日志）
     */
    fun parseContent(content: String, fileName: String = ""): ParseResult {
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

        if (headerLine == null) {
            val capital = if (totalAssets > 0 || availableCash > 0) {
                mapOf("total_capital" to totalAssets, "available_cash" to availableCash)
            } else null
            return ParseResult(
                holdings = emptyList(),
                capital = capital,
                fileName = fileName,
                errorMessage = "未找到持仓表格表头（需要包含'股票名称'和'市值'的行）"
            )
        }

        // 解析表头列名
        val headers = headerLine.split("|").drop(1).dropLast(1).map { it.trim() }
        val colIndex = mutableMapOf<String, Int>()
        for (i in headers.indices) {
            val col = headers[i]
            when {
                "股票名称" in col -> colIndex["name"] = i
                "股票代码" in col -> colIndex["code"] = i
                "持仓/可用" in col || ("持仓" in col && "可用" in col) -> colIndex["shares"] = i
                "成本价" in col -> colIndex["cost"] = i
            }
        }

        // 检查必要列
        if ("name" !in colIndex || "shares" !in colIndex || "cost" !in colIndex) {
            val capital = if (totalAssets > 0 || availableCash > 0) {
                mapOf("total_capital" to totalAssets, "available_cash" to availableCash)
            } else null
            return ParseResult(
                holdings = emptyList(),
                capital = capital,
                fileName = fileName,
                errorMessage = "表格缺少必要列。找到列: $colIndex，表头: $headers"
            )
        }

        // 遍历数据行
        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("|") || "---" in trimmed || trimmed == headerLine) continue

            val cells = trimmed.split("|").drop(1).dropLast(1)
            if (cells.size <= (colIndex.values.maxOrNull() ?: 0)) continue

            val nameCell = cells[colIndex["name"]!!].trim()
            val nameMatch = Regex("\\[\\[(.+?)\\]\\]").find(nameCell)
            if (nameMatch == null) continue
            val stockName = nameMatch.groupValues[1].trim()

            var symbol: String? = null
            if ("code" in colIndex) {
                val codeCell = cells[colIndex["code"]!!].trim()
                if (codeCell.isNotEmpty() && codeCell != "-" && codeCell != "N/A") {
                    symbol = Regex("\\d+").find(codeCell)?.value
                }
            }

            val sharesCell = cells[colIndex["shares"]!!].trim()
            val sharesPart = sharesCell.split("/")[0].trim()
            val shares = cleanNumber(sharesPart).toInt()

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

        val capital = if (totalAssets > 0 || availableCash > 0) {
            mapOf("total_capital" to totalAssets, "available_cash" to availableCash)
        } else null

        return ParseResult(
            holdings = holdings,
            capital = capital,
            fileName = fileName,
            errorMessage = if (holdings.isEmpty()) "未解析到任何持仓数据" else null
        )
    }

    fun cleanNumber(text: String): Double {
        val cleaned = text.replace(Regex("[^\\d.-]"), "")
        return if (cleaned.isNotEmpty()) {
            cleaned.toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
    }
}
