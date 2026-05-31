package com.tangtang.stockadvisor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tangtang.stockadvisor.viewmodel.BacktestViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    symbol: String,
    onBack: () -> Unit,
    viewModel: BacktestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedStrategy by remember { mutableStateOf("channel") }

    LaunchedEffect(symbol) {
        viewModel.runBacktest(symbol, selectedStrategy)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("回测报告") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )

        // 策略选择
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedStrategy == "channel",
                onClick = {
                    selectedStrategy = "channel"
                    viewModel.runBacktest(symbol, "channel")
                },
                label = { Text("通道策略") }
            )
            FilterChip(
                selected = selectedStrategy == "trend",
                onClick = {
                    selectedStrategy = "trend"
                    viewModel.runBacktest(symbol, "trend")
                },
                label = { Text("趋势策略") }
            )
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("回测失败: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.runBacktest(symbol, selectedStrategy) }) { Text("重试") }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 股票信息
                Text(
                    text = uiState.stockName.ifEmpty { symbol },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$symbol | ${if (selectedStrategy == "channel") "通道策略" else "趋势跟随"}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 核心指标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "总收益率",
                        value = String.format("%.2f%%", uiState.totalReturn * 100),
                        color = if (uiState.totalReturn >= 0) Color(0xFFE53935) else Color(0xFF43A047),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "年化收益",
                        value = String.format("%.2f%%", uiState.annualReturn * 100),
                        color = if (uiState.annualReturn >= 0) Color(0xFFE53935) else Color(0xFF43A047),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "夏普比率",
                        value = String.format("%.2f", uiState.sharpeRatio),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "最大回撤",
                        value = String.format("%.2f%%", uiState.maxDrawdown * 100),
                        color = Color(0xFFE53935),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "胜率",
                        value = String.format("%.1f%%", uiState.winRate * 100),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "交易次数",
                        value = "${uiState.totalTrades}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 资金变化
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("资金变化", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("初始资金", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    text = String.format("¥%,.0f", uiState.initialCapital),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("最终资金", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    text = String.format("¥%,.0f", uiState.finalCapital),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.finalCapital >= uiState.initialCapital)
                                        Color(0xFFE53935) else Color(0xFF43A047)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
