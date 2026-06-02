package com.tangtang.stockadvisor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// import androidx.activity.enableEdgeToEdge
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tangtang.stockadvisor.navigation.NavRoutes
import com.tangtang.stockadvisor.ui.screen.*
import com.tangtang.stockadvisor.ui.theme.StockAdvisorTheme
import dagger.hilt.android.AndroidEntryPoint

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge()  // Disabled: requires API 30+, causes crash on lower devices
        setContent {
            StockAdvisorTheme {
                MainScreen()
            }
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
        BottomNavItem(NavRoutes.Portfolio.route, "持仓", Icons.Filled.AccountBalance),
        BottomNavItem(NavRoutes.Settings.route, "设置", Icons.Filled.Settings)
    )

    // Hide bottom bar on detail screens
    val showBottomBar = currentDestination?.route in listOf(
        NavRoutes.Home.route,
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
                        navController.navigate(NavRoutes.Predict.createRoute(symbol))
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
                    }
                )
            }
            composable(NavRoutes.Portfolio.route) {
                PortfolioScreen(
                    onBack = { navController.popBackStack() },
                    onStockClick = { symbol ->
                        navController.navigate(NavRoutes.Predict.createRoute(symbol))
                    }
                )
            }
            composable(NavRoutes.PortfolioManager.route) {
                PortfolioManagerScreen(
                    onBack = { navController.popBackStack() },
                    onStockClick = { symbol ->
                        navController.navigate(NavRoutes.Predict.createRoute(symbol))
                    }
                )
            }
            composable(NavRoutes.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
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
                        navController.navigate(NavRoutes.Predict.createRoute(symbol))
                    }
                )
            }
        }
    }
}
