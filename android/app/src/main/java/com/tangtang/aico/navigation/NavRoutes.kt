package com.tangtang.aico.navigation

sealed class NavRoutes(val route: String) {
    object Home : NavRoutes("home")
    object StockDetail : NavRoutes("stock_detail/{symbol}") {
        fun createRoute(symbol: String) = "stock_detail/$symbol"
    }
    object Predict : NavRoutes("predict/{symbol}") {
        fun createRoute(symbol: String) = "predict/$symbol"
    }
    object Backtest : NavRoutes("backtest/{symbol}") {
        fun createRoute(symbol: String) = "backtest/$symbol"
    }
    object Portfolio : NavRoutes("portfolio")
    object PortfolioManager : NavRoutes("portfolio_manager")
    object Settings : NavRoutes("settings")
    object StockSearch : NavRoutes("stock_search")
    object Strategy : NavRoutes("strategy")
    object Monitor : NavRoutes("monitor/{symbol}") {
        fun createRoute(symbol: String) = "monitor/$symbol"
    }
}
