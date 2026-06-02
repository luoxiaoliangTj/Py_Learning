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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tangtang.stockadvisor.viewmodel.PortfolioItemUi
import com.tangtang.stockadvisor.viewmodel.PortfolioUiState
import com.tangtang.stockadvisor.viewmodel.PortfolioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioManagerScreen(
    onBack: () -> Unit,
    onStockClick: (String) -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<PortfolioItemUi?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importPortfolioFromUri(uri, context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadPortfolio(context)
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
                    IconButton(onClick = { viewModel.loadPortfolio(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = {
                        filePickerLauncher.launch(arrayOf("text/*"))
                    }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "导入持仓")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加持仓")
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "清空持仓")
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
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
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
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadPortfolio(context) }) {
                            Text("重试")
                        }
                    }
                }
            }
            uiState.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "暂无持仓数据",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击右上角 + 添加持仓，或 📂 导入 .md 文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加持仓")
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // 总览卡片
                    PortfolioManagerSummaryCard(
                        totalMarketValue = uiState.totalMarketValue,
                        totalCost = uiState.totalCost,
                        totalProfitLoss = uiState.totalProfitLoss,
                        totalProfitLossPercent = uiState.totalProfitLossPercent,
                        itemCount = uiState.items.size
                    )

                    // 持仓列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.items) { item ->
                            PortfolioManagerItemCard(
                                item = item,
                                onClick = { onStockClick(item.code) },
                                onEdit = {
                                    editingItem = item
                                    showEditDialog = true
                                },
                                onDelete = {
                                    viewModel.deletePosition(item.code, context)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加持仓对话框
    if (showAddDialog) {
        AddPositionDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { symbol, name, shares, cost ->
                viewModel.addOrUpdatePosition(symbol, name, shares, cost, context)
                showAddDialog = false
            }
        )
    }

    // 编辑持仓对话框
    if (showEditDialog && editingItem != null) {
        EditPositionDialog(
            item = editingItem!!,
            onDismiss = {
                showEditDialog = false
                editingItem = null
            },
            onConfirm = { symbol, name, shares, cost ->
                viewModel.addOrUpdatePosition(symbol, name, shares, cost, context)
                showEditDialog = false
                editingItem = null
            }
        )
    }

    // 清空确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空所有持仓") },
            text = { Text("确认清空所有持仓数据？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllPositions(context)
                        showClearDialog = false
                    }
                ) {
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun PortfolioManagerSummaryCard(
    totalMarketValue: Double,
    totalCost: Double,
    totalProfitLoss: Double,
    totalProfitLossPercent: Double,
    itemCount: Int
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "持仓总览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$itemCount 只股票",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
fun PortfolioManagerItemCard(
    item: PortfolioItemUi,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
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

@Composable
fun AddPositionDialog(
    onDismiss: () -> Unit,
    onConfirm: (symbol: String, name: String, shares: Int, cost: Double) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var shares by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加持仓") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text("股票代码") },
                    placeholder = { Text("如 600519") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("股票名称") },
                    placeholder = { Text("如 贵州茅台") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shares,
                    onValueChange = { shares = it },
                    label = { Text("持仓股数") },
                    placeholder = { Text("如 100") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("成本价") },
                    placeholder = { Text("如 1500.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sharesInt = shares.toIntOrNull() ?: 0
                    val costDouble = cost.toDoubleOrNull() ?: 0.0
                    if (symbol.isNotBlank() && sharesInt > 0) {
                        onConfirm(symbol, name.ifBlank { symbol }, sharesInt, costDouble)
                    }
                },
                enabled = symbol.isNotBlank() && shares.toIntOrNull() != null && shares.toInt() > 0
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun EditPositionDialog(
    item: PortfolioItemUi,
    onDismiss: () -> Unit,
    onConfirm: (symbol: String, name: String, shares: Int, cost: Double) -> Unit
) {
    var symbol by remember { mutableStateOf(item.code) }
    var name by remember { mutableStateOf(item.name) }
    var shares by remember { mutableStateOf(item.shares.toString()) }
    var cost by remember { mutableStateOf(item.avgCost.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑持仓") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text("股票代码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("股票名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shares,
                    onValueChange = { shares = it },
                    label = { Text("持仓股数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("成本价") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sharesInt = shares.toIntOrNull() ?: 0
                    val costDouble = cost.toDoubleOrNull() ?: 0.0
                    if (symbol.isNotBlank()) {
                        onConfirm(symbol, name.ifBlank { symbol }, sharesInt, costDouble)
                    }
                }
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
