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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tangtang.stockadvisor.viewmodel.StrategyViewModel

/**
 * 策略库页面
 * 列出所有策略、查看详情、保存/删除策略
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyScreen(
    onBack: () -> Unit = {},
    viewModel: StrategyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var detailSymbol by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadStrategies()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("策略库", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = { viewModel.loadStrategies() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加策略")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.strategies.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "正在加载策略列表...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                uiState.error != null && uiState.strategies.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.error ?: "未知错误",
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadStrategies() }) {
                                Text("重试")
                            }
                        }
                    }
                }
                uiState.strategies.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无策略，点击右下角添加",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.strategies) { strategy ->
                            StrategyListItem(
                                symbol = strategy.symbol,
                                name = strategy.name,
                                sharpeRatio = strategy.sharpeRatio,
                                annualReturn = strategy.annualReturn,
                                onClick = {
                                    detailSymbol = strategy.symbol.ifEmpty { strategy.name }
                                    viewModel.loadStrategyDetail(strategy.symbol.ifEmpty { strategy.name })
                                    showDetailDialog = true
                                },
                                onDelete = {
                                    viewModel.deleteStrategy(strategy.symbol.ifEmpty { strategy.name })
                                }
                            )
                        }
                    }
                }
            }

            // 底部错误提示
            if (uiState.error != null && uiState.strategies.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.error ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearStatus() }) {
                                Text("知道了")
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加策略弹窗
    if (showAddDialog) {
        AddStrategyDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { symbol, strategyType ->
                viewModel.saveStrategy(symbol, strategyType)
                showAddDialog = false
            }
        )
    }

    // 策略详情弹窗
    if (showDetailDialog) {
        StrategyDetailDialog(
            symbol = detailSymbol,
            strategy = uiState.selectedStrategy,
            isLoading = uiState.isLoading,
            onDismiss = {
                showDetailDialog = false
                viewModel.clearStatus()
            }
        )
    }
}

/**
 * 策略列表项
 */
@Composable
fun StrategyListItem(
    symbol: String,
    name: String,
    sharpeRatio: Double,
    annualReturn: Double,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (symbol.isNotEmpty()) {
                        Text(
                            text = symbol,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = name,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "夏普: ${String.format("%.2f", sharpeRatio)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val returnColor = when {
                        annualReturn > 0 -> Color(0xFF4CAF50)
                        annualReturn < 0 -> Color(0xFFF44336)
                        else -> Color.Gray
                    }
                    val returnPrefix = if (annualReturn > 0) "+" else ""
                    Text(
                        text = "年化: $returnPrefix${String.format("%.2f", annualReturn)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = returnColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 添加策略弹窗
 */
@Composable
fun AddStrategyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var strategyType by remember { mutableStateOf("channel") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加策略") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text("股票代码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("策略类型", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("channel" to "通道策略", "trend" to "趋势策略").forEach { (value, label) ->
                        val selected = strategyType == value
                        Card(
                            modifier = Modifier.clickable { strategyType = value },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(symbol, strategyType) },
                enabled = symbol.isNotEmpty()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 策略详情弹窗
 */
@Composable
fun StrategyDetailDialog(
    symbol: String,
    strategy: com.tangtang.stockadvisor.data.model.StrategyInfo?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("策略详情") },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (strategy != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (strategy.symbol.isNotEmpty()) {
                        Text("代码: ${strategy.symbol}", fontWeight = FontWeight.Medium)
                    }
                    Text("名称: ${strategy.name}", fontWeight = FontWeight.Medium)
                    if (strategy.description.isNotEmpty()) {
                        Text(
                            "描述: ${strategy.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "夏普比率: ${String.format("%.2f", strategy.sharpeRatio)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val returnPrefix = if (strategy.annualReturn > 0) "+" else ""
                    Text(
                        "年化收益: $returnPrefix${String.format("%.2f", strategy.annualReturn)}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (strategy.strategyType.isNotEmpty()) {
                        Text(
                            "策略类型: ${strategy.strategyType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text("未找到策略详情")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
