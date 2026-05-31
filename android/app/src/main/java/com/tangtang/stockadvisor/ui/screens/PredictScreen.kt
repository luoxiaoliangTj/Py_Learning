package com.tangtang.stockadvisor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tangtang.stockadvisor.viewmodel.PredictViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictScreen(
    symbol: String,
    onBack: () -> Unit,
    onBacktestClick: () -> Unit,
    viewModel: PredictViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(symbol) {
        viewModel.loadPrediction(symbol)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("预测详情") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = onBacktestClick) {
                    Icon(Icons.Filled.TrendingUp, contentDescription = "回测")
                }
            }
        )

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadPrediction(symbol) }) { Text("重试") }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 股票名称和代码
                Text(
                    text = uiState.stockName.ifEmpty { symbol },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = symbol,
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 当前价格
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("当前价格", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = String.format("¥%.2f", uiState.currentPrice),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 预测区间
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("预测区间", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("最低", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    text = String.format("¥%.2f", uiState.predictedLow),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF43A047)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("收盘预测", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    text = String.format("¥%.2f", uiState.predictedClose),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("最高", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    text = String.format("¥%.2f", uiState.predictedHigh),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE53935)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 交易建议
                val recColor = when (uiState.recommendation) {
                    "BUY" -> Color(0xFFE53935)
                    "SELL" -> Color(0xFF43A047)
                    "HOLD_BUY" -> Color(0xFFFF9800)
                    "HOLD_SELL" -> Color(0xFF2196F3)
                    else -> Color.Gray
                }
                val recText = when (uiState.recommendation) {
                    "BUY" -> "买入"
                    "SELL" -> "卖出"
                    "HOLD_BUY" -> "持有偏多"
                    "HOLD_SELL" -> "持有偏空"
                    else -> "观望"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = recColor.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("交易建议", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                text = recText,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = recColor
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("置信度", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                text = String.format("%.0f%%", uiState.confidence * 100),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 策略信号
                if (uiState.signals.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("策略信号", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.signals.forEach { signal ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(signal.name, fontSize = 14.sp)
                                    val signalColor = when (signal.signal) {
                                        "BUY" -> Color(0xFFE53935)
                                        "SELL" -> Color(0xFF43A047)
                                        else -> Color.Gray
                                    }
                                    Text(
                                        text = signal.signal,
                                        color = signalColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 回测按钮
                Button(
                    onClick = onBacktestClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.TrendingUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("查看回测报告")
                }
            }
        }
    }
}
