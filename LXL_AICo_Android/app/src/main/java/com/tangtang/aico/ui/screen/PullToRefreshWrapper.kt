package com.tangtang.aico.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 顶部刷新按钮包装器
 * 替代 PullToRefresh（需要 Material3 1.3+）
 * 使用 TopAppBar 中的刷新按钮实现兼容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableTopBar(
    title: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = if (isRefreshing) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                           else MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White
        ),
        modifier = modifier
    )
}
