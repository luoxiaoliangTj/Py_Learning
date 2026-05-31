package com.tangtang.stockadvisor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tangtang.stockadvisor.viewmodel.PortfolioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onStockClick: (String) -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadPortfolio()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("持仓管理") })

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadPortfolio() }) { Text("重试") }
                }
            }
        } else if (uiState.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无持仓记录", color = Color.Gray)
            }
        } else {
            // 总览卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("总市值", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                text = String.format("¥%,.2f", uiState.totalMarketValue),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("总盈亏", fontSize = 12.sp, color = Color.Gray)
                            val plColor = when {
                                uiState.totalProfitLoss > 0 -> Color(0xFFE53935)
                                uiState.totalProfitLoss < 0 -> Color(0xFF43A047)
                                else -> Color.Gray
                            }
                            Text(
                                text = String.format("%s¥%,.2f",
                                    if (uiState.totalProfitLoss >= 0) "+" else "",
                                    uiState.totalProfitLoss
                                ),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = plColor
                            )
                            Text(
                                text = String.format("%.2f%%", uiState.totalProfitLossPercent),
                                fontSize = 14.sp,
                                color = plColor
                            )
                        }
                    }
                }
            }

            // 持仓列表
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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

@Composable
fun PortfolioItemCard(
    item: com.tangtang.stockadvisor.viewmodel.PortfolioItemUi,
    onClick: () -> Unit
) {
    val plColor = when {
        item.profitLoss > 0 -> Color(0xFFE53935)
        item.profitLoss < 0 -> Color(0xFF43A047)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.code} | ${item.shares}股",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format("¥%.2f", item.currentPrice),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = String.format("%s¥%.2f",
                            if (item.profitLoss >= 0) "+" else "",
                            item.profitLoss
                        ),
                        fontSize = 13.sp,
                        color = plColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("成本: ¥${String.format("%.2f", item.avgCost)}", fontSize = 12.sp, color = Color.Gray)
                Text("市值: ¥${String.format("%,.2f", item.marketValue)}", fontSize = 12.sp, color = Color.Gray)
                Text(
                    text = String.format("%.2f%%", item.profitLossPercent),
                    fontSize = 12.sp,
                    color = plColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
