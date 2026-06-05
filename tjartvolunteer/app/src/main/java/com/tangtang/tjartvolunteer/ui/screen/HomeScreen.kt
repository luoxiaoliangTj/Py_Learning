package com.tangtang.tjartvolunteer.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.tangtang.tjartvolunteer.navigation.Routes
import com.tangtang.tjartvolunteer.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val cultureScore by viewModel.cultureScore.collectAsState()
    val majorScore by viewModel.majorScore.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val universityCount by viewModel.universityCount.collectAsState()
    val scoreCount by viewModel.scoreCount.collectAsState()
    val compositeScore = viewModel.getCompositeScore()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("天津美术志愿填报") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 数据状态
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("数据: ${universityCount}所院校 | ${scoreCount}所有分数",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 成绩输入
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("输入成绩", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = cultureScore,
                            onValueChange = { viewModel.updateCultureScore(it) },
                            label = { Text("文化课分数") },
                            placeholder = { Text("例如：450") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = majorScore,
                            onValueChange = { viewModel.updateMajorScore(it) },
                            label = { Text("专业课分数") },
                            placeholder = { Text("例如：240") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = totalCount,
                            onValueChange = { viewModel.updateTotalCount(it) },
                            label = { Text("今年总人数（可选）") },
                            placeholder = { Text("例如：30000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (compositeScore != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("预估综合分（2025公式）", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = String.format("%.2f", compositeScore),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.calculateRecommendation()
                                    navController.navigate(Routes.RESULT)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = cultureScore.isNotBlank() && majorScore.isNotBlank()
                            ) { Text("生成推荐") }

                            OutlinedButton(onClick = { viewModel.reset() }, modifier = Modifier.weight(1f)) { Text("重置") }
                        }
                    }
                }
            }
        }
    }
}
