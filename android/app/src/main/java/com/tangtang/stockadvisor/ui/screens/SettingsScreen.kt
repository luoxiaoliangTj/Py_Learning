package com.tangtang.stockadvisor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tangtang.stockadvisor.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var tushareToken by remember { mutableStateOf("") }
    var backendUrl by remember { mutableStateOf("http://10.0.2.2:8000") }
    var enableNotifications by remember { mutableStateOf(true) }
    var enableDarkMode by remember { mutableStateOf(false) }

    // Sync from viewModel
    LaunchedEffect(uiState.tushareToken) { tushareToken = uiState.tushareToken }
    LaunchedEffect(uiState.backendUrl) { backendUrl = uiState.backendUrl }
    LaunchedEffect(uiState.enableNotifications) { enableNotifications = uiState.enableNotifications }
    LaunchedEffect(uiState.enableDarkMode) { enableDarkMode = uiState.enableDarkMode }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("设置") })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // API 配置
            Text("API 配置", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = tushareToken,
                onValueChange = { tushareToken = it },
                label = { Text("Tushare Token") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                label = { Text("后端地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 应用设置
            Text("应用设置", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("推送通知", fontSize = 16.sp)
                    Text("接收预测提醒和交易信号", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked = enableNotifications,
                    onCheckedChange = { enableNotifications = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("深色模式", fontSize = 16.sp)
                    Text("开启深色主题", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked = enableDarkMode,
                    onCheckedChange = { enableDarkMode = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 保存按钮
            Button(
                onClick = {
                    viewModel.saveSettings(
                        tushareToken = tushareToken,
                        backendUrl = backendUrl,
                        enableNotifications = enableNotifications,
                        enableDarkMode = enableDarkMode
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存设置")
            }

            // 保存成功提示
            if (uiState.saved) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✅ 设置已保存",
                    color = Color(0xFF43A047),
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 关于
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("关于", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("StockAdvisor v1.0", fontSize = 14.sp, color = Color.Gray)
            Text("基于 Python 策略引擎的股票预测与回测", fontSize = 12.sp, color = Color.Gray)
        }
    }
}
