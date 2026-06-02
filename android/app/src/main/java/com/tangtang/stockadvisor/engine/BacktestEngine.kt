package com.tangtang.stockadvisor.engine

import com.tangtang.stockadvisor.data.model.StockPrice
import com.tangtang.stockadvisor.data.model.EquityPoint
import com.tangtang.stockadvisor.data.model.TradeRecord
import com.tangtang.stockadvisor.engine.indicators.KlineData
import kotlin.math.*

/**
 * BacktestEngine - 回测引擎
 *
 * 本地回测核心，实现网格评估 + 回测循环 + 绩效指标计算。
 * 完全无后端依赖，所有计算在 Android 设备本地完成。
 *
 * 功能:
 *   1. runBacktest()         — 执行单策略回测
 *   2. evaluateAllStrategies() — 网格评估所有策略组合
 *   3. calculateMetrics()    — 从权益曲线计算绩效指标
 */
object BacktestEngine {

    // ==================== 交易成本常量 ====================
    /** 佣金费率 万1.31，最低 5 元 */
    private const val COMMISSION_RATE = 0.000131
    private const val MIN_COMMISSION = 5.0
    /** 印花税率 万5（仅卖出） */
    private const val STAMP_TAX_RATE = 0.0005
    /** 过户费率 万0.1 */
    private const val TRANSFER_FEE_RATE = 0.00001
    /** 默认滑点 万5 */
    private const val DEFAULT_SLIPPAGE = 0.0005

    /** 默认初始资金 */
    const val DEFAULT_INITIAL_CASH = 100_000.0

    /** k 值搜索网格: 0.5 ~ 4.0, 步长 0.5 */
    private val K_GRID = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0)

    /** 趋势策略 MA 参数组 */
    private val TREND_GRID = listOf(
        5 to 20,
        5 to 30,
        10 to 30,
        10 to 60,
        20 to 60
    )

    // ==================== 数据类 ====================

    /**
     * 回测引擎结果（区别于 API 模型 BacktestResult）
     */
    data class EngineBacktestResult(
        val strategyName: String,
        val strategyType: String,
        val params: Map<String, Any>,
        val initialCapital: Double,
        val finalCapital: Double,
        /** 总收益率（小数形式，如 0.15 表示 15%） */
        val totalReturn: Double,
        /** 年化收益率（小数形式） */
        val annualReturn: Double,
        val sharpeRatio: Double,
        /** 最大回撤（百分比，如 -12.5 表示 -12.5%） */
        val maxDrawdown: Double,
        val totalTrades: Int,
        val winRate: Double,
        val equityCurve: List<EquityPoint>,
        val tradeRecords: List<TradeRecord>,
        /** 交易天数（用于计算年化） */
        val tradingDays: Int
    )

    /**
     * 绩效指标
     */
    data class BacktestMetrics(
        val sharpeRatio: Double,
        val maxDrawdown: Double,
        val totalReturn: Double,
        val annualReturn: Double,
        val volatility: Double,
        val winRate: Double
    )

    /**
     * 策略评分（网格评估结果）
     */
    data class StrategyScore(
        val strategyName: String,
        val strategyType: String,
        val pluginOrParams: String,
        val k: Double? = null,
        val fastPeriod: Int? = null,
        val slowPeriod: Int? = null,
        val totalReturn: Double,
        val sharpeRatio: Double,
        val maxDrawdown: Double,
        val totalTrades: Int,
        val compositeScore: Double
    )

    // ==================== 类型枚举 ====================

    enum class StrategyType { CHANNEL, TREND }

    // ==================== 转换工具 ====================

    /** StockPrice → KlineData */
    fun toKlineData(sp: StockPrice): KlineData {
        return KlineData(
            date = sp.date,
            open = sp.open,
            high = sp.high,
            low = sp.low,
            close = sp.close,
            volume = sp.volume.toDouble()
        )
    }

    /** 批量转换 */
    fun toKlineDataList(klines: List<StockPrice>): List<KlineData> {
        return klines.map { toKlineData(it) }
    }

    /** KlineData → StockPrice */
    fun toStockPrice(kd: KlineData): StockPrice {
        return StockPrice(
            date = kd.date,
            open = kd.open,
            high = kd.high,
            low = kd.low,
            close = kd.close,
            volume = kd.volume.toLong()
        )
    }

    // ==================== 核心 API ====================

    /**
     * 执行回测
     *
     * @param klines      日线 K 线数据（按日期升序排列）
     * @param strategyType 策略类型：CHANNEL 或 TREND
     * @param params      策略参数:
     *                    - CHANNEL: mapOf("plugin" to "atr_channel", "k" to 2.0)
     *                    - TREND:   mapOf("fastPeriod" to 10, "slowPeriod" to 30)
     * @param initialCash 初始资金
     * @return 回测结果
     */
    fun runBacktest(
        klines: List<StockPrice>,
        strategyType: StrategyType,
        params: Map<String, Any>,
        initialCash: Double = DEFAULT_INITIAL_CASH
    ): EngineBacktestResult {
        require(klines.size >= 25) {
            "数据不足：需要至少 25 根 K 线，当前 ${klines.size} 根"
        }

        return when (strategyType) {
            StrategyType.CHANNEL -> {
                val pluginName = params["plugin"] as? String ?: "atr_channel"
                val k = params["k"] as? Double ?: 2.0
                runChannelBacktest(klines, pluginName, k, initialCash)
            }
            StrategyType.TREND -> {
                val fast = params["fastPeriod"] as? Int ?: 10
                val slow = params["slowPeriod"] as? Int ?: 30
                runTrendBacktest(klines, fast, slow, initialCash)
            }
        }
    }

    /**
     * 网格评估所有策略组合
     *
     * - 通道策略: 11 个插件 × 8 个 k 值 = 88 种组合
     * - 趋势策略: 5 组 MA 参数
     * - 总计最多 93 种组合
     *
     * @param klines 日线 K 线数据
     * @return 按综合评分降序排列的策略评分列表
     */
    fun evaluateAllStrategies(klines: List<StockPrice>): List<StrategyScore> {
        require(klines.size >= 50) {
            "数据不足：策略评估至少需要 50 根 K 线，当前 ${klines.size} 根"
        }

        val scores = mutableListOf<StrategyScore>()

        // ====== 通道策略网格评估 ======
        for (plugin in ChannelStrategy.plugins) {
            for (k in K_GRID) {
                try {
                    val result = runChannelBacktest(
                        klines, plugin.name, k, DEFAULT_INITIAL_CASH
                    )
                    val metrics = calculateMetricsFromResult(result)
                    val compositeScore = calculateCompositeScore(
                        metrics, klines.size, result.totalTrades
                    )
                    scores.add(
                        StrategyScore(
                            strategyName = plugin.name,
                            strategyType = "channel",
                            pluginOrParams = "${plugin.name}(k=$k)",
                            k = k,
                            totalReturn = metrics.totalReturn,
                            sharpeRatio = metrics.sharpeRatio,
                            maxDrawdown = metrics.maxDrawdown,
                            totalTrades = result.totalTrades,
                            compositeScore = compositeScore
                        )
                    )
                } catch (e: Exception) {
                    // 该组合数据不足或计算异常，跳过
                    continue
                }
            }
        }

        // ====== 趋势策略网格评估 ======
        for ((fast, slow) in TREND_GRID) {
            try {
                val result = runTrendBacktest(
                    klines, fast, slow, DEFAULT_INITIAL_CASH
                )
                val metrics = calculateMetricsFromResult(result)
                val compositeScore = calculateCompositeScore(
                    metrics, klines.size, result.totalTrades
                )
                scores.add(
                    StrategyScore(
                        strategyName = "trend_MA${fast}_${slow}",
                        strategyType = "trend",
                        pluginOrParams = "MA($fast/$slow)",
                        fastPeriod = fast,
                        slowPeriod = slow,
                        totalReturn = metrics.totalReturn,
                        sharpeRatio = metrics.sharpeRatio,
                        maxDrawdown = metrics.maxDrawdown,
                        totalTrades = result.totalTrades,
                        compositeScore = compositeScore
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }

        return scores.sortedByDescending { it.compositeScore }
    }

    /**
     * 从权益曲线计算绩效指标
     *
     * @param equityCurve 权益曲线（每日组合价值列表）
     * @param tradingDays 交易天数（用于年化计算），默认 250
     * @return 绩效指标
     */
    fun calculateMetrics(
        equityCurve: List<Double>,
        tradingDays: Int = 250
    ): BacktestMetrics {
        if (equityCurve.size < 2) {
            return BacktestMetrics(
                sharpeRatio = 0.0,
                maxDrawdown = 0.0,
                totalReturn = 0.0,
                annualReturn = 0.0,
                volatility = 0.0,
                winRate = 0.0
            )
        }

        // 日收益率序列
        val dailyReturns = mutableListOf<Double>()
        for (i in 1 until equityCurve.size) {
            if (equityCurve[i - 1] > 0) {
                dailyReturns.add(equityCurve[i] / equityCurve[i - 1] - 1.0)
            }
        }

        // 夏普比率 = mean(returns) / std(returns) * sqrt(250)
        val sharpeRatio = calculateSharpe(dailyReturns)

        // 最大回撤
        val maxDrawdown = calculateMaxDrawdown(equityCurve)

        // 总收益率
        val totalReturn = equityCurve.last() / equityCurve.first() - 1.0

        // 年化收益率
        val days = equityCurve.size
        val annualReturn = if (days > 1) {
            (1.0 + totalReturn).pow(tradingDays.toDouble() / days) - 1.0
        } else {
            0.0
        }

        // 年化波动率
        val volatility = if (dailyReturns.isNotEmpty()) {
            val mean = dailyReturns.average()
            val variance = dailyReturns.map { (it - mean) * (it - mean) }
                .sum() / dailyReturns.size
            sqrt(variance) * sqrt(tradingDays.toDouble())
        } else {
            0.0
        }

        // 胜率（正收益天数占比）
        val winRate = if (dailyReturns.isNotEmpty()) {
            dailyReturns.count { it > 0 }.toDouble() / dailyReturns.size
        } else {
            0.0
        }

        return BacktestMetrics(
            sharpeRatio = sharpeRatio,
            maxDrawdown = maxDrawdown,
            totalReturn = totalReturn,
            annualReturn = annualReturn,
            volatility = volatility,
            winRate = winRate
        )
    }

    // ==================== 通道策略回测 ====================

    private fun runChannelBacktest(
        klines: List<StockPrice>,
        pluginName: String,
        k: Double,
        initialCash: Double
    ): EngineBacktestResult {
        val plugin = ChannelStrategy.findPlugin(pluginName)
            ?: throw IllegalArgumentException("未知插件: $pluginName")

        val klineDataList = toKlineDataList(klines)
        val warmupPeriod = 20

        require(klines.size > warmupPeriod) {
            "数据不足：通道策略至少需要 ${warmupPeriod + 1} 根 K 线"
        }

        var cash = initialCash
        var shares = 0
        var costPrice = 0.0  // 持仓成本价（用于计算盈亏）
        val trades = mutableListOf<TradeRecord>()
        val equityCurve = mutableListOf<EquityPoint>()
        val portfolioValues = mutableListOf(initialCash)

        // 记录初始点
        equityCurve.add(EquityPoint(klines[0].date, initialCash))

        for (i in warmupPeriod until klines.size) {
            val slice = klineDataList.subList(0, i + 1)
            val close = klines[i].close
            val high = klines[i].high
            val low = klines[i].low
            val date = klines[i].date

            // 计算通道
            val width = plugin.calculateWidth(slice, k)
            if (width <= 0 || width.isNaN()) {
                // 宽度无效，跳过交易逻辑，记录持仓价值
                val dailyValue = cash + shares * close
                portfolioValues.add(dailyValue)
                equityCurve.add(EquityPoint(date, dailyValue))
                continue
            }

            val upper = close + width / 2.0
            val lower = close - width / 2.0

            // ===== 交易逻辑 =====
            if (high > upper && shares == 0) {
                // 突破上轨 → 买入
                val buyPrice = close * (1.0 + DEFAULT_SLIPPAGE)
                val maxShares = calculateMaxShares(cash, buyPrice)
                if (maxShares > 0) {
                    val amount = maxShares * buyPrice
                    val buyCost = calculateBuyCost(amount)
                    cash -= (amount + buyCost)
                    shares = maxShares
                    costPrice = buyPrice
                    trades.add(
                        TradeRecord(
                            date = date,
                            type = "buy",
                            price = buyPrice,
                            shares = maxShares,
                            amount = amount + buyCost
                        )
                    )
                }
            } else if (low < lower && shares > 0) {
                // 突破下轨 → 卖出
                val sellPrice = close * (1.0 - DEFAULT_SLIPPAGE)
                val amount = shares * sellPrice
                val sellCost = calculateSellCost(amount)
                val pnl = amount - sellCost - shares * costPrice
                cash += (amount - sellCost)
                trades.add(
                    TradeRecord(
                        date = date,
                        type = "sell",
                        price = sellPrice,
                        shares = shares,
                        amount = amount - sellCost,
                        pnl = pnl
                    )
                )
                shares = 0
                costPrice = 0.0
            }

            // 记录每日组合价值
            val dailyValue = cash + shares * close
            portfolioValues.add(dailyValue)
            equityCurve.add(EquityPoint(date, dailyValue))
        }

        // 如果最后还有持仓，按最后收盘价平仓计算
        if (shares > 0) {
            cash += shares * klines.last().close
            shares = 0
        }

        val finalCapital = cash
        val metrics = calculateMetrics(portfolioValues)
        val winRate = calculateWinRate(trades)

        return EngineBacktestResult(
            strategyName = pluginName,
            strategyType = "channel",
            params = mapOf("plugin" to pluginName, "k" to k),
            initialCapital = initialCash,
            finalCapital = finalCapital,
            totalReturn = metrics.totalReturn,
            annualReturn = metrics.annualReturn,
            sharpeRatio = metrics.sharpeRatio,
            maxDrawdown = metrics.maxDrawdown,
            totalTrades = trades.size,
            winRate = winRate,
            equityCurve = equityCurve,
            tradeRecords = trades,
            tradingDays = portfolioValues.size
        )
    }

    // ==================== 趋势策略回测 ====================

    private fun runTrendBacktest(
        klines: List<StockPrice>,
        fastPeriod: Int,
        slowPeriod: Int,
        initialCash: Double
    ): EngineBacktestResult {
        require(fastPeriod in 2 until slowPeriod) {
            "无效 MA 参数: fast=$fastPeriod, slow=$slowPeriod"
        }
        require(klines.size >= slowPeriod + 1) {
            "数据不足：趋势策略至少需要 ${slowPeriod + 1} 根 K 线"
        }

        val closes = klines.map { it.close }
        val maFast = TrendStrategy.calculateSMA(closes, fastPeriod)
        val maSlow = TrendStrategy.calculateSMA(closes, slowPeriod)

        var cash = initialCash
        var shares = 0
        var costPrice = 0.0
        val trades = mutableListOf<TradeRecord>()
        val equityCurve = mutableListOf<EquityPoint>()
        val portfolioValues = mutableListOf(initialCash)

        equityCurve.add(EquityPoint(klines[0].date, initialCash))

        for (i in slowPeriod until klines.size) {
            val close = closes[i]
            val date = klines[i].date

            val prevFast = maFast[i - 1]
            val prevSlow = maSlow[i - 1]
            val curFast = maFast[i]
            val curSlow = maSlow[i]

            // 跳过 NaN 值
            if (prevFast.isNaN() || prevSlow.isNaN() ||
                curFast.isNaN() || curSlow.isNaN()
            ) {
                val dailyValue = cash + shares * close
                portfolioValues.add(dailyValue)
                equityCurve.add(EquityPoint(date, dailyValue))
                continue
            }

            // 金叉买入
            if (TrendStrategy.isGoldenCross(prevFast, prevSlow, curFast, curSlow)
                && shares == 0
            ) {
                val buyPrice = TrendStrategy.calculateBuyPrice(close)
                val maxShares = TrendStrategy.calculateMaxShares(cash, buyPrice)
                if (maxShares > 0) {
                    val amount = maxShares * buyPrice
                    val cost = TrendStrategy.calculateBuyCost(amount)
                    cash -= (amount + cost)
                    shares = maxShares
                    costPrice = buyPrice
                    trades.add(
                        TradeRecord(
                            date = date,
                            type = "buy",
                            price = buyPrice,
                            shares = maxShares,
                            amount = amount + cost
                        )
                    )
                }
            }
            // 死叉卖出
            else if (TrendStrategy.isDeathCross(prevFast, prevSlow, curFast, curSlow)
                && shares > 0
            ) {
                val sellPrice = TrendStrategy.calculateSellPrice(close)
                val amount = shares * sellPrice
                val cost = TrendStrategy.calculateSellCost(amount)
                val pnl = amount - cost - shares * costPrice
                cash += (amount - cost)
                trades.add(
                    TradeRecord(
                        date = date,
                        type = "sell",
                        price = sellPrice,
                        shares = shares,
                        amount = amount - cost,
                        pnl = pnl
                    )
                )
                shares = 0
                costPrice = 0.0
            }

            val dailyValue = cash + shares * close
            portfolioValues.add(dailyValue)
            equityCurve.add(EquityPoint(date, dailyValue))
        }

        // 如果最后还有持仓
        if (shares > 0) {
            cash += shares * closes.last()
            shares = 0
        }

        val finalCapital = cash
        val metrics = calculateMetrics(portfolioValues)
        val winRate = calculateWinRate(trades)

        return EngineBacktestResult(
            strategyName = "trend_MA${fastPeriod}_${slowPeriod}",
            strategyType = "trend",
            params = mapOf(
                "fastPeriod" to fastPeriod,
                "slowPeriod" to slowPeriod
            ),
            initialCapital = initialCash,
            finalCapital = finalCapital,
            totalReturn = metrics.totalReturn,
            annualReturn = metrics.annualReturn,
            sharpeRatio = metrics.sharpeRatio,
            maxDrawdown = metrics.maxDrawdown,
            totalTrades = trades.size,
            winRate = winRate,
            equityCurve = equityCurve,
            tradeRecords = trades,
            tradingDays = portfolioValues.size
        )
    }

    // ==================== 交易费用计算 ====================

    private fun calculateBuyCost(amount: Double): Double {
        val commission = maxOf(amount * COMMISSION_RATE, MIN_COMMISSION)
        val transferFee = amount * TRANSFER_FEE_RATE
        return commission + transferFee
    }

    private fun calculateSellCost(amount: Double): Double {
        val commission = maxOf(amount * COMMISSION_RATE, MIN_COMMISSION)
        val stampTax = amount * STAMP_TAX_RATE
        val transferFee = amount * TRANSFER_FEE_RATE
        return commission + stampTax + transferFee
    }

    private fun calculateMaxShares(cash: Double, buyPrice: Double): Int {
        if (buyPrice <= 0 || cash <= 0) return 0
        // 先按 100 股估算费率
        val estAmount = buyPrice * 100
        val costRate = calculateBuyCost(estAmount) / estAmount
        val maxAmount = cash / (1.0 + costRate)
        val rawShares = (maxAmount / buyPrice).toInt()
        return (rawShares / 100) * 100  // 整百手
    }

    // ==================== 绩效指标内部计算 ====================

    /**
     * 从 EngineBacktestResult 提取指标
     */
    private fun calculateMetricsFromResult(result: EngineBacktestResult): BacktestMetrics {
        val equityValues = result.equityCurve.map { it.value }
        return calculateMetrics(equityValues, result.tradingDays)
    }

    /**
     * 夏普比率（标准公式）
     * sharpe = mean(returns) / std(returns) * sqrt(250)
     */
    private fun calculateSharpe(dailyReturns: List<Double>): Double {
        if (dailyReturns.size < 2) return 0.0

        val mean = dailyReturns.average()
        val variance = dailyReturns.map { (it - mean) * (it - mean) }
            .sum() / dailyReturns.size
        val std = sqrt(variance)

        if (std < 1e-10) return 0.0

        return mean / std * sqrt(250.0)
    }

    /**
     * 最大回撤（百分比，负数）
     * drawdown = (value - cumulativeMax) / cumulativeMax
     * max_drawdown = min(drawdown) * 100
     */
    private fun calculateMaxDrawdown(portfolioValues: List<Double>): Double {
        if (portfolioValues.size < 2) return 0.0

        var peak = portfolioValues[0]
        var maxDrawdown = 0.0

        for (value in portfolioValues) {
            if (value > peak) {
                peak = value
            }
            if (peak > 0) {
                val drawdown = (value - peak) / peak
                if (drawdown < maxDrawdown) {
                    maxDrawdown = drawdown
                }
            }
        }

        return maxDrawdown * 100.0  // 返回百分比
    }

    /**
     * 计算交易胜率
     * 胜率 = 盈利交易次数 / 总交易次数
     */
    private fun calculateWinRate(trades: List<TradeRecord>): Double {
        val sellTrades = trades.filter { it.type == "sell" }
        if (sellTrades.isEmpty()) return 0.0

        val winningTrades = sellTrades.count { it.pnl > 0 }
        return winningTrades.toDouble() / sellTrades.size
    }

    // ==================== 综合评分 ====================

    /**
     * 综合评分计算（与 Python data_driven_evaluator 一致）
     *
     * composite_score = sharpe_score * 0.45
     *                 + trade_frequency_score * 0.10
     *                 + return_score * 0.35
     *                 + drawdown_score * 0.10
     *
     * 归一化规则:
     *   sharpe_score = normalize(sharpe, -1, 3)  → [-1, 1]
     *   trade_frequency_score = min(n_trades / expected_trades, 2.0)
     *   return_score = normalize(total_return, -0.3, 1.0) → [-1, 1]
     *   drawdown_score = 1.0 - min(|max_drawdown| / 50, 1.0)
     *
     * 特殊规则:
     *   - 夏普为负 → sharpe_score × 0.5（加大惩罚）
     *   - 夏普≤0 且 收益≤0 → 总分上限 0.3
     */
    private fun calculateCompositeScore(
        metrics: BacktestMetrics,
        totalKlines: Int,
        nTrades: Int
    ): Double {
        val expectedTrades = max(1, totalKlines / 20)

        val sharpeScore = normalizeScore(metrics.sharpeRatio, -1.0, 3.0)
        val tradeFrequencyScore = min(nTrades.toDouble() / expectedTrades, 2.0)
        val returnScore = normalizeScore(metrics.totalReturn, -0.3, 1.0)
        val drawdownScore = 1.0 - min(abs(metrics.maxDrawdown) / 50.0, 1.0)

        // 夏普为负时加大惩罚
        val adjustedSharpeScore = if (metrics.sharpeRatio < 0) {
            sharpeScore * 0.5
        } else {
            sharpeScore
        }

        // 综合评分
        var compositeScore =
            adjustedSharpeScore * 0.45 +
                tradeFrequencyScore * 0.10 +
                returnScore * 0.35 +
                drawdownScore * 0.10

        // 亏损策略总分上限 0.3
        if (metrics.sharpeRatio <= 0 && metrics.totalReturn <= 0) {
            compositeScore = min(compositeScore, 0.3)
        }

        return compositeScore
    }

    /**
     * 归一化分数：将 value 从 [minVal, maxVal] 映射到 [-1, 1]
     */
    private fun normalizeScore(value: Double, minVal: Double, maxVal: Double): Double {
        if (maxVal <= minVal) return 0.0
        val normalized = (value - minVal) / (maxVal - minVal)
        return normalized.coerceIn(-1.0, 1.0)
    }
}
