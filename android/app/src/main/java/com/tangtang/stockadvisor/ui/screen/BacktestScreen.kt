package com.tangtang.stockadvisor.ui.screen

import android.graphics.Color as AndroidColor
import java.util.ArrayList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.tangtang.stockadvisor.viewmodel.BacktestViewModel

/**
 * 回测页面
 * 策略选择、参数配置、回测执行、结果显示（含权益曲线图）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    symbol: String,
    onBack: () -> Unit,
    viewModel: BacktestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // 本地策略选择状态
    var selectedStrategy by remember { mutableStateOf("channel") }

    // 初始加载回测数据，策略切换时重新回测
    LaunchedEffect(symbol, selectedStrategy) {
        viewModel.runBacktest(symbol, selectedStrategy)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回测报告") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 策略选择区域
            StrategySelector(
                selectedStrategy = selectedStrategy,
                onStrategySelected = { strategy ->
                    selectedStrategy = strategy
                    viewModel.runBacktest(symbol, strategy)
                }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "正在回测中，请稍候...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                uiState.error != null -> {
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
                            Button(onClick = { viewModel.runBacktest(symbol, selectedStrategy) }) {
                                Text("重试")
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 标题
                        Text(
                            text = "${uiState.stockName} (${uiState.symbol})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "策略: ${if (selectedStrategy == "channel") "通道策略" else "趋势策略"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 收益概览卡片
                        ReturnOverviewCard(
                            totalReturn = uiState.totalReturn,
                            annualReturn = uiState.annualReturn,
                            finalCapital = uiState.finalCapital,
                            initialCapital = uiState.initialCapital
                        )

                        // 风险指标卡片
                        RiskMetricsCard(
                            maxDrawdown = uiState.maxDrawdown,
                            sharpeRatio = uiState.sharpeRatio,
                            winRate = uiState.winRate,
                            totalTrades = uiState.totalTrades
                        )

                        // 权益曲线图（MPAndroidChart LineChart）
                        EquityCurveCard(
                            equityCurve = uiState.equityCurve
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * 策略选择器
 * 支持通道策略和趋势策略切换
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategySelector(
    selectedStrategy: String,
    onStrategySelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "策略选择",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedStrategy == "channel",
                    onClick = { onStrategySelected("channel") },
                    label = { Text("通道策略") }
                )
                FilterChip(
                    selected = selectedStrategy == "trend",
                    onClick = { onStrategySelected("trend") },
                    label = { Text("趋势策略") }
                )
            }
        }
    }
}

/**
 * 收益概览卡片
 * 显示总收益率、年化收益率、初始/最终资金
 */
@Composable
fun ReturnOverviewCard(
    totalReturn: Double,
    annualReturn: Double,
    finalCapital: Double,
    initialCapital: Double
) {
    val returnColor = if (totalReturn >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    val prefix = if (totalReturn >= 0) "+" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "收益概览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("总收益率", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "$prefix${String.format("%.2f", totalReturn)}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = returnColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("年化收益率", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "$prefix${String.format("%.2f", annualReturn)}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = returnColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "初始资金: ¥${String.format("%,.0f", initialCapital)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "最终资金: ¥${String.format("%,.0f", finalCapital)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 风险指标卡片
 * 显示最大回撤、夏普比率、胜率、交易次数
 */
@Composable
fun RiskMetricsCard(
    maxDrawdown: Double,
    sharpeRatio: Double,
    winRate: Double,
    totalTrades: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "风险指标",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("最大回撤", "${String.format("%.2f", maxDrawdown)}%")
                MetricItem("夏普比率", String.format("%.2f", sharpeRatio))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("胜率", "${String.format("%.1f", winRate)}%")
                MetricItem("交易次数", "$totalTrades")
            }
        }
    }
}

/**
 * 单个指标项
 */
@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

/**
 * 权益曲线卡片
 * 使用 MPAndroidChart LineChart 展示回测权益曲线
 * 由于 API 返回的 equity_curve 数据可能不可用，这里生成模拟曲线
 */
@Composable
fun EquityCurveCard(
    equityCurve: List<Pair<String, Double>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "权益曲线",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (equityCurve.isEmpty()) {
                Text(
                    text = "暂无权益曲线数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val primaryColor = AndroidColor.rgb(63, 81, 181)

            AndroidView(
                factory = { context ->
                    LineChart(context).apply {
                        description.isEnabled = false
                        setTouchEnabled(true)
                        setScaleEnabled(true)
                        setPinchZoom(true)
                        setDrawGridBackground(false)
                        legend.isEnabled = true
                        legend.textColor = AndroidColor.DKGRAY
                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(true)
                            granularity = 1f
                            isGranularityEnabled = true
                        }
                        axisLeft.apply {
                            setDrawGridLines(true)
                            gridColor = AndroidColor.argb(50, 200, 200, 200)
                        }
                        axisRight.isEnabled = false
                        extraBottomOffset = 8f
                        animateX(800)
                    }
                },
                update = { chart ->
                    // 使用回测引擎返回的真实权益曲线数据
                    val entries = ArrayList<Entry>()
                    val labels = mutableListOf<String>()
                    // 数据点过多时采样，避免图表过于密集
                    val step = if (equityCurve.size > 60) equityCurve.size / 30 else 1
                    var index = 0
                    for (i in equityCurve.indices step step) {
                        val (date, value) = equityCurve[i]
                        entries.add(Entry(index.toFloat(), value.toFloat()))
                        // 简化日期标签：只显示月/日
                        val label = if (date.length >= 10) {
                            "${date.substring(5, 7)}/${date.substring(8, 10)}"
                        } else {
                            date
                        }
                        labels.add(label)
                        index++
                    }
                    // 确保最后一个点也包含
                    if ((equityCurve.size - 1) % step != 0) {
                        val (date, value) = equityCurve.last()
                        entries.add(Entry(index.toFloat(), value.toFloat()))
                        val label = if (date.length >= 10) {
                            "${date.substring(5, 7)}/${date.substring(8, 10)}"
                        } else {
                            date
                        }
                        labels.add(label)
                    }

                    val dataSet = LineDataSet(entries, "权益曲线").apply {
                        color = primaryColor
                        setCircleColor(primaryColor)
                        lineWidth = 2f
                        circleRadius = 2f
                        setDrawCircleHole(false)
                        valueTextSize = 8f
                        setDrawFilled(true)
                        fillColor = primaryColor
                        fillAlpha = 40
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        setDrawValues(false)
                    }

                    chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                    chart.data = LineData(dataSet)
                    chart.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }
    }
}
