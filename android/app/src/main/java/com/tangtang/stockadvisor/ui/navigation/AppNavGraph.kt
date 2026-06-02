package com.tangtang.stockadvisor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tangtang.stockadvisor.ui.screen.BacktestScreen
import com.tangtang.stockadvisor.ui.screen.HomeScreen
import com.tangtang.stockadvisor.ui.screen.PortfolioManagerScreen
import com.tangtang.stockadvisor.ui.screen.PortfolioScreen
import com.tangtang.stockadvisor.ui.screen.PredictScreen
import com.tangtang.stockadvisor.ui.screen.SettingsScreen
import com.tangtang.stockadvisor.ui.screen.StockSearchScreen
import com.tangtang.stockadvisor.ui.screen.StrategyScreen

object Routes {
    const val HOME = "home"
    const val PREDICT = "predict/{symbol}"
    const val BACKTEST = "backtest/{symbol}"
    const val PORTFOLIO = "portfolio"
    const val PORTFOLIO_MANAGER = "portfolio_manager"
    const val SETTINGS = "settings"
    const val STOCK_SEARCH = "stock_search"
    const val STRATEGY = "strategy"

    fun predict(symbol: String) = "predict/$symbol"
    fun backtest(symbol: String) = "backtest/$symbol"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.HOME
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStockClick = { symbol ->
                    navController.navigate(Routes.predict(symbol))
                },
                onSearchClick = {
                    navController.navigate(Routes.STOCK_SEARCH)
                },
                onPortfolioClick = {
                    navController.navigate(Routes.PORTFOLIO)
                },
                onPortfolioManagerClick = {
                    navController.navigate(Routes.PORTFOLIO_MANAGER)
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.PREDICT) { backStackEntry ->
            val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
            PredictScreen(
                symbol = symbol,
                onBack = { navController.popBackStack() },
                onBacktestClick = { sym ->
                    navController.navigate(Routes.backtest(sym))
                }
            )
        }

        composable(Routes.BACKTEST) { backStackEntry ->
            val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
            BacktestScreen(
                symbol = symbol,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PORTFOLIO) {
            PortfolioScreen(
                onBack = { navController.popBackStack() },
                onStockClick = { symbol ->
                    navController.navigate(Routes.predict(symbol))
                }
            )
        }

        composable(Routes.PORTFOLIO_MANAGER) {
            PortfolioManagerScreen(
                onBack = { navController.popBackStack() },
                onStockClick = { symbol ->
                    navController.navigate(Routes.predict(symbol))
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STOCK_SEARCH) {
            StockSearchScreen(
                onBack = { navController.popBackStack() },
                onStockSelected = { symbol ->
                    navController.navigate(Routes.predict(symbol))
                }
            )
        }

        composable(Routes.STRATEGY) {
            StrategyScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
