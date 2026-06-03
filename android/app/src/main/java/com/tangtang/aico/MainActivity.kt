package com.tangtang.aico

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tangtang.aico.navigation.NavRoutes
import com.tangtang.aico.ui.screen.*
import com.tangtang.aico.ui.theme.AICoTheme
import dagger.hilt.android.AndroidEntryPoint

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 存储权限请求 launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // 权限请求结果（用户可选择拒绝，不影响主流程）
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求存储权限（Android 6.0+ 需要运行时请求）
        requestStoragePermission()

        setContent {
            AICoTheme {
                MainScreen()
            }
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用媒体权限
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(permission)
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(
        BottomNavItem(NavRoutes.Home.route, "首页", Icons.Filled.Home),
        BottomNavItem(NavRoutes.Strategy.route, "策略", Icons.Filled.TrendingUp),
        BottomNavItem(NavRoutes.Portfolio.route, "持仓", Icons.Filled.AccountBalance),
        BottomNavItem(NavRoutes.Settings.route, "设置", Icons.Filled.Settings)
    )

    // Hide bottom bar on detail screens
    val showBottomBar = currentDestination?.route in listOf(
        NavRoutes.Home.route,
        NavRoutes.Strategy.route,
        NavRoutes.Portfolio.route,
        NavRoutes.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
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
        NavHost(
            navController = navController,
            startDestination = NavRoutes.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoutes.Home.route) {
                HomeScreen(
                    onStockClick = { symbol ->
                        navController.navigate(NavRoutes.StockDetail.createRoute(symbol))
                    },
                    onSearchClick = {
                        navController.navigate(NavRoutes.StockSearch.route)
                    },
                    onPortfolioClick = {
                        navController.navigate(NavRoutes.Portfolio.route)
                    },
                    onPortfolioManagerClick = {
                        navController.navigate(NavRoutes.PortfolioManager.route)
                    },
                    onSettingsClick = {
                        navController.navigate(NavRoutes.Settings.route)
                    },
                    onStrategyClick = {
                        navController.navigate(NavRoutes.Strategy.route)
                    }
                )
            }
            composable(NavRoutes.Portfolio.route) {
                PortfolioScreen(
                    onBack = { navController.popBackStack() },
                    onStockClick = { symbol ->
                        navController.navigate(NavRoutes.StockDetail.createRoute(symbol))
                    }
                )
            }
            composable(NavRoutes.PortfolioManager.route) {
                PortfolioManagerScreen(
                    onBack = { navController.popBackStack() },
                    onStockClick = { symbol ->
                        navController.navigate(NavRoutes.StockDetail.createRoute(symbol))
                    }
                )
            }
            composable(NavRoutes.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = NavRoutes.StockDetail.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                StockDetailScreen(
                    symbol = symbol,
                    onBack = { navController.popBackStack() },
                    onPredictClick = { navController.navigate(NavRoutes.Predict.createRoute(symbol)) },
                    onBacktestClick = { navController.navigate(NavRoutes.Backtest.createRoute(symbol)) },
                    onMonitorClick = { navController.navigate(NavRoutes.Monitor.createRoute(symbol)) }
                )
            }
            composable(
                route = NavRoutes.Predict.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                PredictScreen(
                    symbol = symbol,
                    onBack = { navController.popBackStack() },
                    onBacktestClick = { navController.navigate(NavRoutes.Backtest.createRoute(symbol)) }
                )
            }
            composable(
                route = NavRoutes.Backtest.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                BacktestScreen(
                    symbol = symbol,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(NavRoutes.StockSearch.route) {
                StockSearchScreen(
                    onBack = { navController.popBackStack() },
                    onStockSelected = { symbol ->
                        navController.navigate(NavRoutes.StockDetail.createRoute(symbol))
                    }
                )
            }
            composable(NavRoutes.Strategy.route) {
                StrategyScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = NavRoutes.Monitor.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                MonitorScreen(
                    symbol = symbol,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
