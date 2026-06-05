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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tangtang.tjartvolunteer.DebugLog
import com.tangtang.tjartvolunteer.data.model.UniversityEntity
import com.tangtang.tjartvolunteer.navigation.Routes
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversityListScreen(
    navController: NavController
) {
    val allUniversities = remember { mutableStateListOf<UniversityEntity>() }
    var showLocal by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        DebugLog.append("[PAGE] 院校库页打开")
        val dao = com.tangtang.tjartvolunteer.TJArtVolunteerApp.instance.database.universityDao()
        dao.getAllUniversities().collect { list ->
            allUniversities.clear()
            allUniversities.addAll(list)
            DebugLog.append("[DATA] 院校库加载完成: ${list.size}所")
        }
    }

    val filtered = allUniversities.filter {
        val matchLocal = if (showLocal) it.isLocal else !it.isLocal
        val matchSearch = searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
        matchLocal && matchSearch
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("院校库") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.isNotBlank()) DebugLog.append("[USER] 搜索院校: $it")
                },
                label = { Text("搜索院校") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = showLocal,
                    onClick = {
                        showLocal = true
                        DebugLog.append("[USER] 筛选: 天津本地")
                    },
                    label = { Text("天津本地 (${allUniversities.count { it.isLocal }})") }
                )
                FilterChip(
                    selected = !showLocal,
                    onClick = {
                        showLocal = false
                        DebugLog.append("[USER] 筛选: 外地院校")
                    },
                    label = { Text("外地院校 (${allUniversities.count { !it.isLocal }})") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("共${filtered.size}所院校", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                items(filtered) { uni ->
                    UniversityCard(uni) {
                        DebugLog.append("[USER] 点击院校: ${uni.name} (${if (uni.isLocal) "本地" else "外地"})")
                        val encodedName = URLEncoder.encode(uni.name, StandardCharsets.UTF_8.toString())
                        navController.navigate(Routes.universityDetail(encodedName))
                    }
                }
            }
        }
    }
}

@Composable
private fun UniversityCard(uni: UniversityEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(uni.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uni.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(uni.level, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (uni.isLocal) {
                    Text("📍 天津本地", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${uni.majors.split(",").filter { it.isNotBlank() }.size}个专业", style = MaterialTheme.typography.bodySmall)
                val excludeCount = uni.examExcludeMajors.split(",").filter { it.isNotBlank() }.size
                if (excludeCount > 0) {
                    Text("${excludeCount}个需校考", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
