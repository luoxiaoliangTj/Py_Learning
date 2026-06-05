package com.tangtang.tjartvolunteer.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tangtang.tjartvolunteer.DebugLog
import com.tangtang.tjartvolunteer.RecommendationHolder
import com.tangtang.tjartvolunteer.domain.algorithm.RecommendationEngine
import com.tangtang.tjartvolunteer.navigation.Routes
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    navController: NavController
) {
    val recommendResults = RecommendationHolder.results
    val hasCalculated = RecommendationHolder.hasCalculated

    LaunchedEffect(Unit) {
        DebugLog.append("[PAGE] 推荐结果页打开, 结果数=${recommendResults.size}, 已计算=$hasCalculated")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("推荐结果") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (!hasCalculated) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("请先输入成绩并点击「生成推荐」", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        DebugLog.append("[USER] 点击'去首页'")
                        navController.navigate(Routes.HOME)
                    }) { Text("去首页") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (recommendResults.isNotEmpty()) {
                    val safeCount = recommendResults.count { it.category == RecommendationEngine.RecommendCategory.SAFE }
                    val matchCount = recommendResults.count { it.category == RecommendationEngine.RecommendCategory.MATCH }
                    val rushCount = recommendResults.count { it.category == RecommendationEngine.RecommendCategory.RUSH }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryChip("保 ($safeCount)", Color(0xFF4CAF50), Modifier.weight(1f))
                            CategoryChip("稳 ($matchCount)", Color(0xFF2196F3), Modifier.weight(1f))
                            CategoryChip("冲 ($rushCount)", Color(0xFFFF9800), Modifier.weight(1f))
                        }
                    }
                }

                items(recommendResults) { result ->
                    ResultCard(result = result) {
                        DebugLog.append("[USER] 点击院校: ${result.university.name}")
                        val encodedName = URLEncoder.encode(result.university.name, StandardCharsets.UTF_8.toString())
                        navController.navigate(Routes.universityDetail(encodedName))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ResultCard(result: RecommendationEngine.RecommendResult, onClick: () -> Unit) {
    val catColor = when (result.category) {
        RecommendationEngine.RecommendCategory.SAFE -> Color(0xFF4CAF50)
        RecommendationEngine.RecommendCategory.MATCH -> Color(0xFF2196F3)
        RecommendationEngine.RecommendCategory.RUSH -> Color(0xFFFF9800)
    }
    val calc = result.debugCalculation

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(result.university.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = catColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(result.category.label, color = catColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(String.format("%+.1f", calc.scoreDiff), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("分差", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("${(calc.admissionProbability * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("概率", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(calc.rankRange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("位次范围", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            var expanded by remember { mutableStateOf(false) }
            TextButton(onClick = {
                expanded = !expanded
                if (expanded) DebugLog.append("[USER] 展开计算过程: ${result.university.name}")
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "▼ 隐藏计算过程" else "▶ 查看计算过程", fontSize = 12.sp)
            }

            if (expanded) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📊 详细计算过程", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        HorizontalDivider()
                        Text("你的综合分: ${String.format("%.2f", calc.userCompositeScore)} (${calc.userYear}年)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("公式: 文化×0.5 + 专业×2.5×0.5", fontSize = 11.sp, color = Color(0xFF666666))
                        if (calc.totalCount > 0) Text("今年总人数: ${calc.totalCount}", fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("各年录取数据:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        calc.yearlyData.forEach { yd ->
                            Text("  ${yd.year}年:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("    录取最低分: ${yd.minScore?.let { String.format("%.2f", it) } ?: "无"}", fontSize = 11.sp)
                            Text("    录取位次: ${yd.minRank ?: "无"}", fontSize = 11.sp)
                            Text("    公式: ${yd.formula}", fontSize = 11.sp, color = Color(0xFF666666))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("位次范围: ${calc.rankRange}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("综合分差: ${String.format("%+.2f", calc.scoreDiff)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("匹配专业：${result.matchedMajors.take(5).joinToString("、")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.warnings.forEach { w -> Text("⚠ $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}
