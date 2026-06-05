package com.tangtang.tjartvolunteer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tangtang.tjartvolunteer.navigation.NavGraph
import com.tangtang.tjartvolunteer.navigation.Routes
import com.tangtang.tjartvolunteer.ui.theme.TJArtVolunteerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TJArtVolunteerTheme { MainScreen() }
        }
    }
}

data class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val app = TJArtVolunteerApp.instance

    val bottomNavItems = listOf(
        BottomNavItem(Routes.HOME, "首页", Icons.Default.Home),
        BottomNavItem(Routes.RESULT, "推荐结果", Icons.Default.School),
        BottomNavItem(Routes.PROFILE, "我的", Icons.Default.Person)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    // 调试日志状态
    val debugLines = remember { mutableStateListOf<String>() }
    var showDebug by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            val newLines = DebugLog.getAll()
            if (newLines.size != debugLines.size) {
                debugLines.clear()
                debugLines.addAll(newLines)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                selected = currentRoute == item.route,
                                onClick = {
                                    DebugLog.append("[USER] 导航: ${item.label} (${item.route})")
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavGraph(navController = navController)
        }

        // ===== 全局悬浮调试面板 =====
        if (showDebug) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 8.dp)
                    .widthIn(max = 220.dp)
                    .background(
                        color = Color(0xCC1a1a2e),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { showDebug = false }
                    .padding(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔧 调试", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("✕", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (debugLines.isEmpty()) {
                        Text("加载中...", color = Color(0xFF888888), fontSize = 10.sp)
                    } else {
                        debugLines.takeLast(20).forEach { line ->
                            val color = when {
                                line.contains("[ERROR]") || line.contains("[FAIL]") -> Color(0xFFFF6B6B)
                                line.contains("[WARN]") -> Color(0xFFFFAA00)
                                line.contains("[RESULT]") -> Color(0xFF51CF66)
                                line.contains("[USER]") -> Color(0xFF82B1FF)
                                line.contains("[CALC]") -> Color(0xFFCE93D8)
                                else -> Color(0xFFCCCCCC)
                            }
                            Text(line, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                        }
                    }
                }
            }
        } else {
            // 最小化状态：一个小按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 8.dp)
                    .size(32.dp)
                    .background(color = Color(0xCC1a1a2e), shape = RoundedCornerShape(16.dp))
                    .clickable { showDebug = true },
                contentAlignment = Alignment.Center
            ) {
                Text("🔧", fontSize = 14.sp)
            }
        }
    }
}
