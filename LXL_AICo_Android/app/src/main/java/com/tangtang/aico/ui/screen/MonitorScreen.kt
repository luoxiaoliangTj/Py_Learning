package com.tangtang.aico.ui.screen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.tangtang.aico.viewmodel.RealtimeViewModel

/**
 * 实时监测页面 — 对应 RunUI 的"在线预测"功能
 * 自动刷新实时行情，显示价格和信号变化
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    symbol: String,
    onBack: () -> Unit,
    viewModel: RealtimeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 启动实时监测
    LaunchedEffect(symbol) {
        viewModel.startRealtime(symbol)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.stockName.ifEmpty { symbol },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "实时监测 · $symbol",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopAutoRefresh()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (uiState.isAutoRefresh) {
                            viewModel.stopAutoRefresh()
                        } else {
                            viewModel.startRealtime(symbol)
                        }
                    }) {
                        Icon(
                            if (uiState.isAutoRefresh) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isAutoRefresh) "暂停" else "开始",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { viewModel.loadRealtimeData(symbol) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading && uiState.currentPrice == 0.0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 价格卡片
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val priceColor = when {
                                uiState.changePercent > 0 -> Color(0xFF4CAF50)
                                uiState.changePercent < 0 -> Color(0xFFF44336)
                                else -> Color.Gray
                            }
                            Text(
                                text = "¥${String.format("%.2f", uiState.currentPrice)}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = priceColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "${if (uiState.changePercent > 0) "+" else ""}${String.format("%.2f", uiState.changePercent)}%",
                                    color = priceColor,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "预测: ¥${String.format("%.2f", uiState.predictedPrice)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (uiState.updateTime.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = uiState.updateTime,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                // 状态指示
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isAutoRefresh)
                                Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isAutoRefresh) "🟢 自动刷新中 (30s)" else "🟡 已暂停",
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "置信度: ${String.format("%.0f", uiState.confidence * 100)}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 策略信号
                if (uiState.signals.isNotEmpty()) {
                    item {
                        Text(
                            text = "策略信号",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(uiState.signals) { signal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = signal.name, style = MaterialTheme.typography.bodyMedium)
                                val signalColor = when {
                                    signal.signal.contains("BUY", ignoreCase = true) -> Color(0xFF4CAF50)
                                    signal.signal.contains("SELL", ignoreCase = true) -> Color(0xFFF44336)
                                    else -> Color.Gray
                                }
                                Text(
                                    text = "${signal.signal} (${String.format("%.0f", signal.weight * 100)}%)",
                                    color = signalColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 错误提示
                if (uiState.error != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = uiState.error ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
