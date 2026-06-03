package com.tangtang.stockadvisor.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tangtang.stockadvisor.viewmodel.StockDetailViewModel

/**
 * 股票详情页 — "当前股票"的核心页面
 * 
 * 对应 RunUI 的"切换股票后"状态：
 * - 顶部：股票名称、代码、现价、涨跌幅
 * - 中部：行情详情（开/高/低/量/额）
 * - 底部：操作按钮（日线预测 / 回测 / 实时监测）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    symbol: String,
    onBack: () -> Unit,
    onPredictClick: (String) -> Unit,
    onBacktestClick: (String) -> Unit,
    onMonitorClick: (String) -> Unit,
    viewModel: StockDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 进入页面时加载实时行情
    LaunchedEffect(symbol) {
        viewModel.loadStockDetail(symbol)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.stockName.ifEmpty { symbol },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = symbol,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshPrice(symbol) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("正在加载行情数据...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: "未知错误",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadStockDetail(symbol) }) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 价格卡片
                    PriceCard(uiState)

                    // 行情详情卡片
                    QuoteDetailCard(uiState)

                    // 操作按钮区 — 对应 RunUI 的 1/2/3 操作
                    ActionButtons(
                        symbol = symbol,
                        onPredictClick = onPredictClick,
                        onBacktestClick = onBacktestClick,
                        onMonitorClick = onMonitorClick
                    )
                }
            }
        }
    }
}

/**
 * 价格卡片 — 显示现价、涨跌额、涨跌幅
 */
@Composable
private fun PriceCard(uiState: com.tangtang.stockadvisor.viewmodel.StockDetailUiState) {
    val isUp = uiState.change >= 0
    val priceColor = when {
        uiState.change > 0 -> Color(0xFF4CAF50)
        uiState.change < 0 -> Color(0xFFF44336)
        else -> Color.Gray
    }
    val prefix = if (uiState.change > 0) "+" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUp) Color(0xFFF1F8E9) else Color(0xFFFFEBEE)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "¥${String.format("%.2f", uiState.currentPrice)}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = priceColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$prefix${String.format("%.2f", uiState.change)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = priceColor
                )
                Text(
                    text = "$prefix${String.format("%.2f", uiState.changePercent)}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = priceColor
                )
            }
            if (uiState.updateTime.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${uiState.dataSource} · ${uiState.updateTime}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * 行情详情卡片 — 开/高/低/量/额
 */
@Composable
private fun QuoteDetailCard(uiState: com.tangtang.stockadvisor.viewmodel.StockDetailUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "行情详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 两列布局
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    QuoteField("开盘", "¥${String.format("%.2f", uiState.open)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    QuoteField("最高", "¥${String.format("%.2f", uiState.high)}", Color(0xFFF44336))
                    Spacer(modifier = Modifier.height(8.dp))
                    QuoteField("成交量", formatVolume(uiState.volume))
                }
                Column(modifier = Modifier.weight(1f)) {
                    QuoteField("昨收", "¥${String.format("%.2f", uiState.prevClose)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    QuoteField("最低", "¥${String.format("%.2f", uiState.low)}", Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(8.dp))
                    QuoteField("成交额", formatAmount(uiState.amount))
                }
            }
        }
    }
}

@Composable
private fun QuoteField(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * 操作按钮区 — 对应 RunUI 的 1/2/3 操作
 * 1 - 日线预测
 * 3 - 日线回测
 * 2 - 在线预测（实时监测）
 */
@Composable
private fun ActionButtons(
    symbol: String,
    onPredictClick: (String) -> Unit,
    onBacktestClick: (String) -> Unit,
    onMonitorClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 日线预测（主操作，用填充按钮）
            Button(
                onClick = { onPredictClick(symbol) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("日线预测", fontSize = 16.sp)
            }

            // 回测 + 监测（并排）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onBacktestClick(symbol) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("回测")
                }
                OutlinedButton(
                    onClick = { onMonitorClick(symbol) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Monitor, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("实时监测")
                }
            }
        }
    }
}

private fun formatVolume(volume: Long): String {
    return when {
        volume >= 100_000_000 -> String.format("%.1f亿", volume / 100_000_000.0)
        volume >= 10_000 -> String.format("%.1f万", volume / 10_000.0)
        else -> volume.toString()
    }
}

private fun formatAmount(amount: Double): String {
    return when {
        amount >= 100_000_000 -> String.format("%.1f亿", amount / 100_000_000.0)
        amount >= 10_000 -> String.format("%.1f万", amount / 10_000.0)
        else -> String.format("%.0f", amount)
    }
}
