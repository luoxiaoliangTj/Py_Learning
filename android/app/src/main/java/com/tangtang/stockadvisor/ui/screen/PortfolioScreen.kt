package com.tangtang.stockadvisor.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tangtang.stockadvisor.viewmodel.PortfolioViewModel
import com.tangtang.stockadvisor.viewmodel.PortfolioUiState
import com.tangtang.stockadvisor.viewmodel.PortfolioItemUi
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onBack: () -> Unit,
    onStockClick: (String) -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val gson = remember { Gson() }

    // SAF 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // 读取文件内容
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                reader.close()

                // 解析 .md 文件
                val (holdings, capital) = parseMdPortfolio(content)

                // 转换为 JSON 并导入
                val holdingsJson = gson.toJson(holdings)
                val capitalJson = if (capital != null) gson.toJson(capital) else null

                viewModel.importPortfolio(holdingsJson, capitalJson)
            } catch (e: Exception) {
                // 解析失败，显示错误
                android.util.Log.e("PortfolioScreen", "导入失败", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("持仓管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        filePickerLauncher.launch(arrayOf("text/*"))
                    }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "导入持仓")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.error ?: "未知错误",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.TextButton(onClick = { viewModel.loadPortfolio() }) {
                        Text("重试")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 导入提示卡片（无持仓时显示）
                if (uiState.items.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无持仓数据",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击右上角 📂 按钮，选择持仓 .md 文件导入",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = {
                                filePickerLauncher.launch(arrayOf("text/*"))
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                Text("选择 .md 文件")
                            }
                        }
                    }
                } else {
                    // 总览卡片
                    PortfolioSummaryCard(
                        totalMarketValue = uiState.totalMarketValue,
                        totalCost = uiState.totalCost,
                        totalProfitLoss = uiState.totalProfitLoss,
                        totalProfitLossPercent = uiState.totalProfitLossPercent
                    )

                    // 持仓列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.items) { item ->
                            PortfolioItemCard(
                                item = item,
                                onClick = { onStockClick(item.code) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 解析持仓 Markdown 文件
 * 返回 (holdings列表, 资金信息)
 *
 * 支持的格式：
 * 总资产: ￥346,919.21
 * 可用资金: ￥10,203.08
 *
 * | 序号 | 股票名称 (链接) | 股票代码 | 持仓/可用 | 成本价 | 市值 | 盈亏 |
 * |------|----------------|---------|----------|--------|------|------|
 * | 1 | [[建设银行]] | 601939 | 100/100 | 114.544 | 11,454.40 | +0.00 |
 */
private fun parseMdPortfolio(content: String): Pair<List<Map<String, Any>>, Map<String, Double>?> {
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

    return Pair(holdings, capital)
}

private fun cleanNumber(text: String): Double {
    val cleaned = text.replace(Regex("[^\\d.-]"), "")
    return cleaned.toDoubleOrNull() ?: 0.0
}

@Composable
fun PortfolioSummaryCard(
    totalMarketValue: Double,
    totalCost: Double,
    totalProfitLoss: Double,
    totalProfitLossPercent: Double
) {
    val plColor = if (totalProfitLoss >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    val prefix = if (totalProfitLoss >= 0) "+" else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "持仓总览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("总市值", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "¥${String.format("%,.2f", totalMarketValue)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("总盈亏", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "$prefix¥${String.format("%,.2f", totalProfitLoss)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = plColor
                    )
                    Text(
                        text = "$prefix${String.format("%.2f", totalProfitLossPercent)}%",
                        fontSize = 14.sp,
                        color = plColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "总成本: ¥${String.format("%,.2f", totalCost)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PortfolioItemCard(
    item: PortfolioItemUi,
    onClick: () -> Unit
) {
    val plColor = if (item.profitLoss >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    val prefix = if (item.profitLoss >= 0) "+" else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${item.code} | ${item.shares}股",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "¥${String.format("%.2f", item.currentPrice)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "$prefix${String.format("%.2f", item.profitLossPercent)}%",
                        color = plColor,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Divider()
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "成本 ¥${String.format("%.2f", item.avgCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "市值 ¥${String.format("%,.2f", item.marketValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
