package com.tangtang.stockadvisor.ui.screen

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
import androidx.compose.material.icons.filled.Refresh
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
import com.tangtang.stockadvisor.viewmodel.ScanStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onBack: () -> Unit,
    onStockClick: (String) -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
                    // 扫描状态指示
                    when (uiState.scanStatus) {
                        ScanStatus.SCANNING -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .height(24.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                        ScanStatus.SUCCESS -> {
                            Text(
                                text = "✓ 已同步",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                        ScanStatus.ERROR -> {
                            Text(
                                text = "✗ 失败",
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                        ScanStatus.IDLE -> { /* 不显示 */ }
                    }
                    IconButton(onClick = {
                        viewModel.importFromMdFile(context)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新持仓")
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
                                text = "请在手机存储 /Documents/mindmaps/炒股/ 目录下放置持仓 .md 文件，然后点击刷新",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = {
                                viewModel.importFromMdFile(context)
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                Text("扫描并导入")
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
