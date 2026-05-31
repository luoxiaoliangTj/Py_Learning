package com.tangtang.stockadvisor.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tangtang.stockadvisor.ui.screen.BacktestScreen
import com.tangtang.stockadvisor.ui.screen.HomeScreen
import com.tangtang.stockadvisor.ui.screen.PortfolioScreen
import com.tangtang.stockadvisor.ui.screen.PredictScreen
import com.tangtang.stockadvisor.ui.screen.SearchScreen
import com.tangtang.stockadvisor.ui.screen.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Filled.Home)
    data object Predict : Screen("predict/{symbol}", "预测", Icons.Filled.Timeline)
    data object Backtest : Screen("backtest/{symbol}", "回测", Icons.Filled.BarChart)
    data object Portfolio : Screen("portfolio", "持仓", Icons.Filled.Person)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
    data object Search : Screen("search", "搜索", Icons.Filled.Search)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Predict,
    Screen.Backtest,
    Screen.Portfolio,
    Screen.Settings
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == screen.route
                            } == true,
                            onClick = {
                                if (screen.route.contains("{symbol}")) {
                                    // For routes requiring arguments, skip or navigate with default
                                    return@NavigationBarItem
                                }
                                navController.navigate(screen.route) {
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
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToPredict = { symbol ->
                        navController.navigate("predict/$symbol")
                    },
                    onNavigateToSearch = {
                        navController.navigate("search")
                    }
                )
            }
            composable(
                route = Screen.Predict.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                PredictScreen(
                    symbol = symbol,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToBacktest = { code ->
                        navController.navigate("backtest/$code")
                    }
                )
            }
            composable(
                route = Screen.Backtest.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                BacktestScreen(
                    symbol = symbol,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Portfolio.route) {
                PortfolioScreen(
                    onNavigateToPredict = { symbol ->
                        navController.navigate("predict/$symbol")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPredict = { symbol ->
                        navController.navigate("predict/$symbol")
                    }
                )
            }
        }
    }
}
