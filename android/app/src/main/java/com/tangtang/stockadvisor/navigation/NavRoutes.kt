package com.tangtang.stockadvisor.navigation

sealed class NavRoutes(val route: String) {
    object Home : NavRoutes("home")
    object Predict : NavRoutes("predict/{symbol}") {
        fun createRoute(symbol: String) = "predict/$symbol"
    }
    object Backtest : NavRoutes("backtest/{symbol}") {
        fun createRoute(symbol: String) = "backtest/$symbol"
    }
    object Portfolio : NavRoutes("portfolio")
    object Settings : NavRoutes("settings")
    object StockSearch : NavRoutes("stock_search")
}
