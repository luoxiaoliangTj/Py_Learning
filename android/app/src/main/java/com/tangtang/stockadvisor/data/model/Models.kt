package com.tangtang.stockadvisor.data.model

import com.google.gson.annotations.SerializedName

data class StockInfo(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("current_price") val currentPrice: Double = 0.0,
    @SerializedName("change_percent") val changePercent: Double = 0.0,
    @SerializedName("change_amount") val changeAmount: Double = 0.0,
    @SerializedName("volume") val volume: Long = 0,
    @SerializedName("turnover") val turnover: Double = 0.0,
    @SerializedName("high") val high: Double = 0.0,
    @SerializedName("low") val low: Double = 0.0,
    @SerializedName("open") val open: Double = 0.0,
    @SerializedName("prev_close") val prevClose: Double = 0.0,
    @SerializedName("market_cap") val marketCap: Double = 0.0,
    @SerializedName("pe_ratio") val peRatio: Double = 0.0
)

data class StockPrice(
    @SerializedName("date") val date: String,
    @SerializedName("open") val open: Double,
    @SerializedName("high") val high: Double,
    @SerializedName("low") val low: Double,
    @SerializedName("close") val close: Double,
    @SerializedName("volume") val volume: Long
)

data class PredictionResult(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("current_price") val currentPrice: Double,
    @SerializedName("predicted_high") val predictedHigh: Double,
    @SerializedName("predicted_low") val predictedLow: Double,
    @SerializedName("predicted_close") val predictedClose: Double,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("strategies") val strategies: List<StrategySignal> = emptyList(),
    @SerializedName("timestamp") val timestamp: String
)

data class StrategySignal(
    @SerializedName("name") val name: String,
    @SerializedName("signal") val signal: String,
    @SerializedName("weight") val weight: Double,
    @SerializedName("value") val value: Double
)

data class BacktestResult(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String,
    @SerializedName("initial_capital") val initialCapital: Double,
    @SerializedName("final_capital") val finalCapital: Double,
    @SerializedName("total_return") val totalReturn: Double,
    @SerializedName("annual_return") val annualReturn: Double,
    @SerializedName("max_drawdown") val maxDrawdown: Double,
    @SerializedName("sharpe_ratio") val sharpeRatio: Double,
    @SerializedName("win_rate") val winRate: Double,
    @SerializedName("total_trades") val totalTrades: Int,
    @SerializedName("equity_curve") val equityCurve: List<EquityPoint> = emptyList(),
    @SerializedName("trades") val trades: List<TradeRecord> = emptyList()
)

data class EquityPoint(
    @SerializedName("date") val date: String,
    @SerializedName("value") val value: Double
)

data class TradeRecord(
    @SerializedName("date") val date: String,
    @SerializedName("type") val type: String,
    @SerializedName("price") val price: Double,
    @SerializedName("shares") val shares: Int,
    @SerializedName("amount") val amount: Double,
    @SerializedName("pnl") val pnl: Double = 0.0
)

data class PortfolioItem(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("shares") val shares: Int,
    @SerializedName("avg_cost") val avgCost: Double,
    @SerializedName("current_price") val currentPrice: Double = 0.0,
    @SerializedName("market_value") val marketValue: Double = 0.0,
    @SerializedName("profit_loss") val profitLoss: Double = 0.0,
    @SerializedName("profit_loss_percent") val profitLossPercent: Double = 0.0
)

data class PortfolioSummary(
    @SerializedName("total_market_value") val totalMarketValue: Double,
    @SerializedName("total_cost") val totalCost: Double,
    @SerializedName("total_profit_loss") val totalProfitLoss: Double,
    @SerializedName("total_profit_loss_percent") val totalProfitLossPercent: Double,
    @SerializedName("items") val items: List<PortfolioItem>
)

data class StrategyInfo(
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String = ""
)

data class OnlinePredictionResult(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("current_price") val currentPrice: Double,
    @SerializedName("predicted_price") val predictedPrice: Double,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("signals") val signals: List<StrategySignal> = emptyList(),
    @SerializedName("update_time") val updateTime: String
)

// Concrete response types using JsonElement to avoid Gson generic type erasure
data class StockListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: com.google.gson.JsonElement?
)

data class StockSelectResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: com.google.gson.JsonElement?
)

data class DailyPredictionResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: com.google.gson.JsonElement?
)

data class RealtimePredictionResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: com.google.gson.JsonElement?
)

data class BacktestResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: com.google.gson.JsonElement?
)

data class HoldingsResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: com.google.gson.JsonElement?
)

data class CapitalResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: com.google.gson.JsonElement?
)

data class StrategyListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: com.google.gson.JsonElement?
)

data class MapResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: com.google.gson.JsonElement?
)
