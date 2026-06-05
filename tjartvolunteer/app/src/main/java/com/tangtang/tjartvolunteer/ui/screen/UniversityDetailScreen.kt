package com.tangtang.tjartvolunteer.ui.screen

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
import com.tangtang.tjartvolunteer.TJArtVolunteerApp
import com.tangtang.tjartvolunteer.data.model.AdmissionScoreEntity
import com.tangtang.tjartvolunteer.data.model.UniversityEntity
import com.tangtang.tjartvolunteer.domain.algorithm.ScoreCalculator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversityDetailScreen(
    name: String,
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    var university by remember { mutableStateOf<UniversityEntity?>(null) }
    var scores by remember { mutableStateOf<List<AdmissionScoreEntity>>(emptyList()) }

    LaunchedEffect(name) {
        DebugLog.append("[PAGE] 院校详情页打开: $name")
        scope.launch {
            val dao = TJArtVolunteerApp.instance.database.universityDao()
            university = dao.getUniversityByName(name)
            dao.getScoresByName(name).collect { scores = it }
            DebugLog.append("[DATA] $name 加载完成: ${scores.size}条分数数据")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(university?.name ?: "加载中...") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        university?.let { uni ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 基本信息
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(uni.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = {}, label = { Text(uni.type) })
                                AssistChip(onClick = {}, label = { Text(uni.level) })
                                if (uni.isLocal) AssistChip(onClick = {}, label = { Text("📍 天津本地") })
                            }
                            if (uni.note.isNotBlank()) {
                                Text(uni.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // 录取分数总览（按年份）
                item {
                    Text("录取分数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                if (scores.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text("暂无分数数据", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                } else {
                    items(scores.sortedByDescending { it.year }) { score ->
                        ScoreOverviewCard(score)
                    }
                }

                // 专业列表 + 各专业分数
                item {
                    Text("专业列表（${uni.majors.split(",").filter { it.isNotBlank() }.size}个）",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                val allMajors = uni.majors.split(",").filter { it.isNotBlank() }
                val excludeMajors = uni.examExcludeMajors.split(",").filter { it.isNotBlank() }
                val includeMajors = uni.examIncludeMajors.split(",").filter { it.isNotBlank() }

                items(allMajors) { major ->
                    val isExcluded = major in excludeMajors
                    val isIncluded = major in includeMajors
                    MajorCard(major = major, isExcluded = isExcluded, isIncluded = isIncluded, scores = scores, uniNote = uni.note)
                }
            }
        } ?: Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
    }
}

@Composable
private fun ScoreOverviewCard(score: AdmissionScoreEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${score.year}年", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("公式: ${score.formula}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("来源: ${score.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    score.minScore?.let { String.format("%.1f", it) } ?: "无数据",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                score.minRank?.let {
                    Text("位次: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MajorCard(major: String, isExcluded: Boolean, isIncluded: Boolean, scores: List<AdmissionScoreEntity>, uniNote: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isExcluded) CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
        else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(major, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (isExcluded) {
                    Surface(color = Color(0xFFFF9800).copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                        Text("需校考", color = Color(0xFFFF9800), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else if (isIncluded) {
                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                        Text("统考", color = Color(0xFF4CAF50), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else {
                    Surface(color = Color(0xFF2196F3).copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                        Text("待确认", color = Color(0xFF2196F3), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            // 显示该专业的往年录取分数（如果有专业细分数据）
            // 目前数据库只有学校整体的专业，将来可扩展为专业细分
            // 这里显示该校所有年份的录取数据供参考
            if (scores.isNotEmpty()) {
                Text("历年录取参考:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                scores.sortedByDescending { it.year }.forEach { score ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${score.year}年 (${score.formula})", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                score.minScore?.let { String.format("%.1f", it) } ?: "-",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            score.minRank?.let {
                                Text("位次$it", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
