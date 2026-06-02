package com.tangtang.stockadvisor.engine

import com.tangtang.stockadvisor.data.model.StockPrice

/**
 * TrendStrategy - 趋势策略引擎
 *
 * 基于 MA（移动平均线）交叉信号的本地趋势跟随策略。
 * 对应 Python 版 strategy_engine.py 中的 trend 模式。
 *
 * 信号逻辑：
 * - 金叉（Golden Cross）→ BUY：前一日 fast_ma <= slow_ma 且当日 fast_ma > slow_ma
 * - 死叉（Death Cross）→ SELL：前一日 fast_ma >= slow_ma 且当日 fast_ma < slow_ma
 * - 其他 → HOLD
 *
 * 交易成本：
 * - 买入：commission + transfer_fee
 * - 卖出：commission + stamp_tax + transfer_fee
 */
object TrendStrategy {

    // ==================== 交易费用常量 ====================

    /** 佣金费率 万1.31，最低 5 元 */
    const val COMMISSION_RATE = 0.000131
    const val MIN_COMMISSION = 5.0

    /** 印花税率 万5（仅卖出时收取） */
    const val STAMP_TAX_RATE = 0.0005

    /** 过户费率 万0.1 */
    const val TRANSFER_FEE_RATE = 0.00001

    /** 默认滑点 万5 */
    const val DEFAULT_SLIPPAGE_RATE = 0.0005

    // ==================== 信号枚举 ====================

    /**
     * 策略信号类型
     */
    enum class TrendSignal {
        BUY,
        SELL,
        HOLD
    }

    // ==================== MA 参数组合 ====================

    /**
     * MA 参数组：(fastPeriod, slowPeriod)
     * 默认 5 组参数用于多参数扫描
     */
    data class MaParams(
        val fastPeriod: Int,
        val slowPeriod: Int
    ) {
        init {
            require(fastPeriod in 2 until slowPeriod) {
                "Invalid MA params: fast=$fastPeriod, slow=$slowPeriod. " +
                    "要求 2 <= fast < slow"
            }
        }

        override fun toString(): String = "MA($fastPeriod/$slowPeriod)"
    }

    /**
     * 返回默认的 5 组 MA 参数
     */
    fun getDefaultMaParams(): List<MaParams> = listOf(
        MaParams(5, 20),
        MaParams(5, 30),
        MaParams(10, 30),
        MaParams(10, 60),
        MaParams(20, 60)
    )

    // ==================== 信号计算 ====================

    /**
     * 评估趋势信号
     *
     * @param klines  K线数据（需要足够长度，至少 slowPeriod + 1 根）
     * @param fastPeriod 快线周期，默认 10
     * @param slowPeriod 慢线周期，默认 30
     * @return TrendSignal BUY / SELL / HOLD
     */
    fun evaluateTrendSignal(
        klines: List<StockPrice>,
        fastPeriod: Int = 10,
        slowPeriod: Int = 30
    ): TrendSignal {
        require(fastPeriod in 2 until slowPeriod) {
            "Invalid MA params: fast=$fastPeriod, slow=$slowPeriod"
        }
        require(klines.size >= slowPeriod + 1) {
            "数据不足：需要至少 ${slowPeriod + 1} 根K线，当前 ${klines.size} 根"
        }

        val closes = klines.map { it.close }

        // 计算 MA 序列
        val maFast = calculateSMA(closes, fastPeriod)
        val maSlow = calculateSMA(closes, slowPeriod)

        // 取最后一根和倒数第二根有效数据
        val lastIndex = maFast.lastIndex
        val prevIndex = lastIndex - 1

        val curFast = maFast[lastIndex]
        val curSlow = maSlow[lastIndex]
        val prevFast = maFast[prevIndex]
        val prevSlow = maSlow[prevIndex]

        // 金叉买入：前一日 fast <= slow 且当日 fast > slow
        if (prevFast <= prevSlow && curFast > curSlow) {
            return TrendSignal.BUY
        }

        // 死叉卖出：前一日 fast >= slow 且当日 fast < slow
        if (prevFast >= prevSlow && curFast < curSlow) {
            return TrendSignal.SELL
        }

        return TrendSignal.HOLD
    }

    /**
     * 对所有默认参数组评估信号，返回每组的结果
     *
     * @param klines K线数据
     * @return List<Pair<MaParams, TrendSignal>>
     */
    fun evaluateAllParams(klines: List<StockPrice>): List<Pair<MaParams, TrendSignal>> {
        return getDefaultMaParams().map { params ->
            try {
                params to evaluateTrendSignal(klines, params.fastPeriod, params.slowPeriod)
            } catch (e: Exception) {
                params to TrendSignal.HOLD
            }
        }
    }

    /**
     * 计算各参数组的 MA 值（最新一根K线）
     *
     * @param klines K线数据
     * @return List<Pair<MaParams, Pair<Double, Double>>> (fastMA, slowMA)
     */
    fun getCurrentMaValues(klines: List<StockPrice>): List<Pair<MaParams, Pair<Double, Double>>> {
        return getDefaultMaParams().map { params ->
            val closes = klines.map { it.close }
            val maFast = calculateSMA(closes, params.fastPeriod)
            val maSlow = calculateSMA(closes, params.slowPeriod)
            val lastIdx = maFast.lastIndex
            params to Pair(maFast[lastIdx], maSlow[lastIdx])
        }
    }

    // ==================== 价格计算（含滑点） ====================

    /**
     * 计算买入价（含滑点）
     *
     * @param close 当前收盘价
     * @param slippageRate 滑点率，默认万5 (0.0005)
     * @return 实际买入价 = close * (1 + slippageRate)
     */
    fun calculateBuyPrice(close: Double, slippageRate: Double = DEFAULT_SLIPPAGE_RATE): Double {
        return close * (1.0 + slippageRate)
    }

    /**
     * 计算卖出价（含滑点）
     *
     * @param close 当前收盘价
     * @param slippageRate 滑点率，默认万5 (0.0005)
     * @return 实际卖出价 = close * (1 - slippageRate)
     */
    fun calculateSellPrice(close: Double, slippageRate: Double = DEFAULT_SLIPPAGE_RATE): Double {
        return close * (1.0 - slippageRate)
    }

    // ==================== 交易费用计算 ====================

    /**
     * 计算买入费用
     *
     * 费用 = max(交易额 × 佣金率, 最低佣金) + 交易额 × 过户费率
     *
     * @param amount 交易金额（股数 × 价格）
     * @return 总买入费用
     */
    fun calculateBuyCost(amount: Double): Double {
        val commission = maxOf(amount * COMMISSION_RATE, MIN_COMMISSION)
        val transferFee = amount * TRANSFER_FEE_RATE
        return commission + transferFee
    }

    /**
     * 计算卖出费用
     *
     * 费用 = max(交易额 × 佣金率, 最低佣金) + 交易额 × 印花税率 + 交易额 × 过户费率
     *
     * @param amount 交易金额（股数 × 价格）
     * @return 总卖出费用
     */
    fun calculateSellCost(amount: Double): Double {
        val commission = maxOf(amount * COMMISSION_RATE, MIN_COMMISSION)
        val stampTax = amount * STAMP_TAX_RATE
        val transferFee = amount * TRANSFER_FEE_RATE
        return commission + stampTax + transferFee
    }

    /**
     * 计算买入总费率（用于简化计算）
     *
     * @param amount 交易金额
     * @return 费用占交易额的比率
     */
    fun calculateBuyCostRate(amount: Double): Double {
        if (amount <= 0) return 0.0
        return calculateBuyCost(amount) / amount
    }

    /**
     * 计算卖出总费率
     *
     * @param amount 交易金额
     * @return 费用占交易额的比率
     */
    fun calculateSellCostRate(amount: Double): Double {
        if (amount <= 0) return 0.0
        return calculateSellCost(amount) / amount
    }

    // ==================== 辅助方法 ====================

    /**
     * 简单移动平均线（SMA）
     *
     * @param values 价格序列
     * @param period 周期
     * @return SMA 序列（前 period-1 个位置填充 NaN 用 Double.NaN 表示）
     */
    fun calculateSMA(values: List<Double>, period: Int): List<Double> {
        require(period >= 1) { "Period must be >= 1, got $period" }
        val result = mutableListOf<Double>()
        var sum = 0.0

        for (i in values.indices) {
            sum += values[i]
            if (i >= period) {
                sum -= values[i - period]
            }
            if (i >= period - 1) {
                result.add(sum / period)
            } else {
                result.add(Double.NaN)
            }
        }
        return result
    }

    /**
     * 判断是否处于金叉状态
     */
    fun isGoldenCross(
        prevFast: Double,
        prevSlow: Double,
        curFast: Double,
        curSlow: Double
    ): Boolean {
        return prevFast <= prevSlow && curFast > curSlow
    }

    /**
     * 判断是否处于死叉状态
     */
    fun isDeathCross(
        prevFast: Double,
        prevSlow: Double,
        curFast: Double,
        curSlow: Double
    ): Boolean {
        return prevFast >= prevSlow && curFast < curSlow
    }

    /**
     * 计算可买入股数（整百手）
     *
     * @param cash 可用现金
     * @param buyPrice 实际买入价
     * @return 可买入的股数（向下取整到100的倍数）
     */
    fun calculateMaxShares(cash: Double, buyPrice: Double): Int {
        if (buyPrice <= 0 || cash <= 0) return 0
        val costRate = calculateBuyCostRate(buyPrice * 100) // 按100股估算费率
        val maxAmount = cash / (1.0 + costRate)
        val rawShares = (maxAmount / buyPrice).toInt()
        return (rawShares / 100) * 100 // 向下取整到整百手
    }

    /**
     * 回测：对给定K线序列执行趋势策略，返回交易日志
     *
     * @param klines K线数据
     * @param fastPeriod 快线周期
     * @param slowPeriod 慢线周期
     * @param initialCash 初始资金
     * @param slippageRate 滑点率
     * @return 回测结果，包含交易记录和每日净值
     */
    fun backtest(
        klines: List<StockPrice>,
        fastPeriod: Int = 10,
        slowPeriod: Int = 30,
        initialCash: Double = 100_000.0,
        slippageRate: Double = DEFAULT_SLIPPAGE_RATE
    ): BacktestResult {
        require(fastPeriod in 2 until slowPeriod)
        require(klines.size >= slowPeriod + 1) {
            "数据不足：需要至少 ${slowPeriod + 1} 根K线"
        }

        val closes = klines.map { it.close }
        val maFast = calculateSMA(closes, fastPeriod)
        val maSlow = calculateSMA(closes, slowPeriod)

        var cash = initialCash
        var shares = 0
        val trades = mutableListOf<TradeRecord>()
        val portfolioValues = mutableListOf(initialCash)

        for (i in slowPeriod until klines.size) {
            val close = closes[i]
            val prevFast = maFast[i - 1]
            val prevSlow = maSlow[i - 1]
            val curFast = maFast[i]
            val curSlow = maSlow[i]

            // 金叉买入
            if (isGoldenCross(prevFast, prevSlow, curFast, curSlow) && shares == 0) {
                val buyPrice = calculateBuyPrice(close, slippageRate)
                val maxShares = calculateMaxShares(cash, buyPrice)
                if (maxShares > 0) {
                    val amount = maxShares * buyPrice
                    val cost = calculateBuyCost(amount)
                    cash -= (amount + cost)
                    shares = maxShares
                    trades.add(
                        TradeRecord(
                            date = klines[i].date,
                            type = "buy",
                            price = buyPrice,
                            shares = maxShares,
                            amount = amount + cost
                        )
                    )
                }
            }
            // 死叉卖出
            else if (isDeathCross(prevFast, prevSlow, curFast, curSlow) && shares > 0) {
                val sellPrice = calculateSellPrice(close, slippageRate)
                val amount = shares * sellPrice
                val cost = calculateSellCost(amount)
                cash += (amount - cost)
                trades.add(
                    TradeRecord(
                        date = klines[i].date,
                        type = "sell",
                        price = sellPrice,
                        shares = shares,
                        amount = amount - cost
                    )
                )
                shares = 0
            }

            portfolioValues.add(cash + shares * close)
        }

        // 如果最后还有持仓，按最后一根K线收盘价平仓
        if (shares > 0) {
            cash += shares * closes.last()
            shares = 0
        }

        val totalReturn = (cash / initialCash - 1.0)
        val maxDrawdown = calculateMaxDrawdown(portfolioValues)

        return BacktestResult(
            finalCash = cash,
            totalReturn = totalReturn,
            maxDrawdown = maxDrawdown,
            tradeCount = trades.size,
            trades = trades,
            portfolioValues = portfolioValues
        )
    }

    /**
     * 回测结果
     */
    data class BacktestResult(
        val finalCash: Double,
        val totalReturn: Double,
        val maxDrawdown: Double,
        val tradeCount: Int,
        val trades: List<TradeRecord>,
        val portfolioValues: List<Double>
    )

    /**
     * 交易记录（内部使用）
     */
    data class TradeRecord(
        val date: String,
        val type: String,
        val price: Double,
        val shares: Int,
        val amount: Double
    )

    /**
     * 计算最大回撤
     */
    private fun calculateMaxDrawdown(portfolioValues: List<Double>): Double {
        if (portfolioValues.isEmpty()) return 0.0
        var peak = portfolioValues[0]
        var maxDrawdown = 0.0

        for (value in portfolioValues) {
            if (value > peak) {
                peak = value
            }
            val drawdown = (peak - value) / peak
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown
            }
        }
        return maxDrawdown
    }
}
