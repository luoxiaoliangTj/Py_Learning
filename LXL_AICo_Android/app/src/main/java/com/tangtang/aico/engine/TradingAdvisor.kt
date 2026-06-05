package com.tangtang.aico.engine

import com.tangtang.aico.data.model.StockPrice
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * TradingAdvisor - 智能交易建议模块（本地计算版）
 *
 * 基于 [TradingAdvisorModule.py] 的核心逻辑移植到纯 Kotlin。
 * 所有计算在 Android 本地完成，无需后端依赖。
 *
 * 功能：
 * - 动态止盈止损阈值（根据波动率和趋势强度自动调整）
 * - 5 种交易建议：止盈 / 止损 / 加仓 / 做T / 开仓
 * - 交易费用计算（佣金、印花税、过户费）
 * - 盈亏金额和比例计算
 *
 * 交易费用（基于建行实际费率）：
 * - 买入佣金: 万1.31（最低 5 元）
 * - 卖出佣金: 万1.31（最低 5 元）
 * - 印花税: 万5（仅卖出）
 * - 其他费用: 万0.1
 */
object TradingAdvisor {

    // ==================== 交易费用常量 ====================

    /** 买入佣金费率 万1.31 */
    const val BUY_COMMISSION_RATE = 0.000131

    /** 卖出佣金费率 万1.31 */
    const val SELL_COMMISSION_RATE = 0.000131

    /** 印花税率 万5（仅卖出时收取） */
    const val STAMP_DUTY_RATE = 0.0005

    /** 其他费用率 万0.1 */
    const val OTHER_FEES_RATE = 0.00001

    /** 最低佣金 5 元 */
    const val MIN_COMMISSION = 5.0

    // ==================== 动态阈值基础参数 ====================

    /** 基础止盈阈值 3% */
    const val BASE_PROFIT_THRESHOLD = 0.03

    /** 基础止损阈值 2% */
    const val BASE_LOSS_THRESHOLD = 0.02

    /** 最小止盈阈值 1.5% */
    const val MIN_PROFIT_THRESHOLD = 0.015

    /** 最小止损阈值 1% */
    const val MIN_LOSS_THRESHOLD = 0.01

    // ==================== 信号枚举 ====================

    /**
     * 交易建议动作类型
     */
    enum class TradingAction(val label: String, val labelCn: String) {
        STOP_PROFIT("STOP_PROFIT", "止盈"),
        STOP_LOSS("STOP_LOSS", "止损"),
        ADD_POSITION("ADD_POSITION", "加仓"),
        DO_T("DO_T", "做T"),
        OPEN_POSITION("OPEN_POSITION", "开仓"),
        HOLD("HOLD", "持有")
    }

    /**
     * 建议紧急程度
     */
    enum class Urgency(val label: String) {
        HIGH("high"),
        MEDIUM("medium"),
        LOW("low")
    }

    // ==================== 数据类 ====================

    /**
     * 持仓信息
     */
    data class Position(
        val shares: Int,
        val costPrice: Double,
        val tPositionRatio: Double = 0.0,  // 做T仓位比例
        val aggressiveFactor: Double = 0.5  // 风险偏好系数
    ) {
        /** 是否有持仓 */
        val hasPosition: Boolean get() = shares > 0

        /** 总成本 */
        val totalCost: Double get() = shares * costPrice
    }

    /**
     * 盈亏信息
     */
    data class ProfitLoss(
        val grossProfit: Double,        // 毛利润（金额）
        val grossProfitPct: Double,     // 毛利润率（%）
        val netProfit: Double,          // 净利润（扣除卖出费用后）
        val netProfitPct: Double,       // 净利润率（%）
        val currentValue: Double,       // 当前市值
        val costValue: Double,          // 成本市值
        val sellFees: Double            // 卖出费用
    ) {
        /** 是否空仓 */
        val isNoPosition: Boolean get() = costValue == 0.0
    }

    /**
     * 动态止盈止损阈值
     */
    data class DynamicThresholds(
        val profitThreshold: Double,    // 止盈阈值（比例）
        val lossThreshold: Double,      // 止损阈值（比例）
        val profitThresholdPrice: Double, // 止盈阈值价格
        val lossThresholdPrice: Double,   // 止损阈值价格
        val volAdjustment: Double,      // 波动率调整量
        val trendImpact: Double         // 趋势影响量
    )

    /**
     * 交易建议
     */
    data class TradingAdvice(
        val action: TradingAction,
        val reason: String,
        val details: String = "",
        val targetPrice: Double = 0.0,
        val stopLossPrice: Double = 0.0,
        val confidence: Double = 0.0,   // 0.0 ~ 1.0
        val urgency: Urgency = Urgency.LOW,
        val profitThresholds: DynamicThresholds? = null,
        val profitLoss: ProfitLoss? = null
    )

    // ==================== 交易费用计算 ====================

    /**
     * 计算交易费用（买入或卖出）
     *
     * @param amount 交易金额
     * @param isSell 是否为卖出
     * @return 总费用
     */
    fun calculateTradingCost(amount: Double, isSell: Boolean): Double {
        if (amount <= 0) return 0.0

        val commissionRate = if (isSell) SELL_COMMISSION_RATE else BUY_COMMISSION_RATE
        val commission = max(amount * commissionRate, MIN_COMMISSION)
        val stampDuty = if (isSell) amount * STAMP_DUTY_RATE else 0.0
        val otherFees = amount * OTHER_FEES_RATE

        return commission + stampDuty + otherFees
    }

    /**
     * 计算买入费用
     */
    fun calculateBuyCost(amount: Double): Double = calculateTradingCost(amount, isSell = false)

    /**
     * 计算卖出费用
     */
    fun calculateSellCost(amount: Double): Double = calculateTradingCost(amount, isSell = true)

    // ==================== 盈亏计算 ====================

    /**
     * 计算盈亏金额和比例
     *
     * @param currentPrice 当前价格
     * @param costPrice 成本价格
     * @param shares 持仓股数
     * @return Pair(净利润金额, 净利润率)
     */
    fun calculateProfitLoss(
        currentPrice: Double,
        costPrice: Double,
        shares: Int
    ): Pair<Double, Double> {
        if (shares <= 0 || costPrice <= 0) {
            return Pair(0.0, 0.0)
        }

        val grossProfit = (currentPrice - costPrice) * shares
        val sellAmount = currentPrice * shares
        val sellFees = calculateSellCost(sellAmount)
        val netProfit = grossProfit - sellFees
        val costValue = costPrice * shares
        val netProfitPct = if (costValue > 0) (netProfit / costValue) * 100.0 else 0.0

        return Pair(netProfit, netProfitPct)
    }

    /**
     * 计算完整盈亏信息
     */
    fun calculateProfitLossDetail(
        currentPrice: Double,
        costPrice: Double,
        shares: Int
    ): ProfitLoss {
        if (shares <= 0 || costPrice <= 0) {
            return ProfitLoss(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val costValue = costPrice * shares
        val currentValue = currentPrice * shares
        val grossProfit = (currentPrice - costPrice) * shares
        val grossProfitPct = if (costPrice > 0) (currentPrice / costPrice - 1.0) * 100.0 else 0.0
        val sellFees = calculateSellCost(currentValue)
        val netProfit = grossProfit - sellFees
        val netProfitPct = if (costValue > 0) (netProfit / costValue) * 100.0 else 0.0

        return ProfitLoss(
            grossProfit = grossProfit,
            grossProfitPct = grossProfitPct,
            netProfit = netProfit,
            netProfitPct = netProfitPct,
            currentValue = currentValue,
            costValue = costValue,
            sellFees = sellFees
        )
    }

    // ==================== 动态止盈止损 ====================

    /**
     * 计算动态止盈止损阈值
     *
     * 根据波动率和趋势强度动态调整止盈止损阈值：
     * - 波动率越高 → 止盈止损阈值都增大（适应大波动）
     * - 上升趋势 → 止盈阈值提高，止损收紧
     * - 下降趋势 → 止盈阈值降低，止损放宽
     *
     * @param costPrice 成本价格
     * @param volatility 日内波动率
     * @param trendStrength 趋势强度（正=上升, 负=下降）
     * @param aggressiveFactor 风险偏好系数 (0.0~1.0)
     * @return Pair(profitThreshold, lossThreshold) 动态阈值比例
     */
    fun calculateDynamicThresholds(
        costPrice: Double,
        volatility: Double,
        trendStrength: Double,
        aggressiveFactor: Double = 0.5
    ): Pair<Double, Double> {
        // 基础阈值
        var profitThreshold = BASE_PROFIT_THRESHOLD  // 3%
        var lossThreshold = BASE_LOSS_THRESHOLD       // 2%

        // 波动率调整：放大波动率对阈值的影响
        val volAdjustment = volatility * 10.0
        profitThreshold += volAdjustment
        lossThreshold += volAdjustment

        // 趋势调整
        if (trendStrength > 0.01) {
            // 上升趋势：提高止盈，收紧止损
            profitThreshold += 0.02
            lossThreshold -= 0.01
        } else if (trendStrength < -0.01) {
            // 下降趋势：降低止盈，放宽止损
            profitThreshold -= 0.01
            lossThreshold += 0.02
        }

        // 风险偏好调整
        profitThreshold *= (1.0 + aggressiveFactor * 0.5)
        lossThreshold *= (1.0 - aggressiveFactor * 0.3)

        // 确保最小阈值
        profitThreshold = max(profitThreshold, MIN_PROFIT_THRESHOLD)
        lossThreshold = max(lossThreshold, MIN_LOSS_THRESHOLD)

        return Pair(profitThreshold, lossThreshold)
    }

    /**
     * 计算完整动态阈值（包含价格）
     */
    fun calculateDynamicThresholdsFull(
        costPrice: Double,
        volatility: Double,
        trendStrength: Double,
        aggressiveFactor: Double = 0.5
    ): DynamicThresholds {
        val volAdjustment = volatility * 10.0

        val (profitThreshold, lossThreshold) = calculateDynamicThresholds(
            costPrice, volatility, trendStrength, aggressiveFactor
        )

        return DynamicThresholds(
            profitThreshold = profitThreshold,
            lossThreshold = lossThreshold,
            profitThresholdPrice = costPrice * (1.0 + profitThreshold),
            lossThresholdPrice = costPrice * (1.0 - lossThreshold),
            volAdjustment = volAdjustment,
            trendImpact = trendStrength
        )
    }

    // ==================== 趋势分析 ====================

    /**
     * 分析趋势强度
     *
     * 通过短期、中期、长期均线的关系计算趋势强度。
     *
     * @param klines K线数据（至少需要 20 根）
     * @return 趋势强度值（-0.03 ~ 0.03），正值=上升趋势，负值=下降趋势
     */
    fun analyzeTrendStrength(klines: List<StockPrice>): Double {
        if (klines.size < 20) return 0.0

        val closes = klines.map { it.close }

        // 计算均线
        val shortMa = closes.takeLast(5).average()   // 5日均线
        val mediumMa = closes.takeLast(10).average()  // 10日均线
        val longMa = closes.takeLast(20).average()    // 20日均线

        // 趋势强度 = (短期均线/长期均线 - 1) * 100
        val trendStrength = (shortMa / longMa - 1.0) * 100.0

        // 标准化到 -0.03 ~ 0.03 范围
        return max(min(trendStrength / 100.0, 0.03), -0.03)
    }

    // ==================== 核心建议生成 ====================

    /**
     * 生成交易建议
     *
     * @param position 持仓信息（null 表示空仓）
     * @param currentPrice 当前价格
     * @param predictedLow 预测最低价
     * @param predictedHigh 预测最高价
     * @param volatility 日内波动率（默认 0.02）
     * @param trendStrength 趋势强度（默认 0.0，可由 analyzeTrendStrength 计算）
     * @return TradingAdvice 交易建议
     */
    fun generateTradingAdvice(
        position: Position?,
        currentPrice: Double,
        predictedLow: Double,
        predictedHigh: Double,
        volatility: Double = 0.02,
        trendStrength: Double = 0.0
    ): TradingAdvice {
        // 空仓时生成开仓建议
        if (position == null || !position.hasPosition) {
            return generateOpenPositionAdvice(
                currentPrice, predictedLow, predictedHigh, volatility
            )
        }

        // 计算盈亏
        val pl = calculateProfitLossDetail(currentPrice, position.costPrice, position.shares)

        // 计算动态阈值
        val thresholds = calculateDynamicThresholdsFull(
            costPrice = position.costPrice,
            volatility = volatility,
            trendStrength = trendStrength,
            aggressiveFactor = position.aggressiveFactor
        )

        // 1. 止盈判断
        if (currentPrice >= thresholds.profitThresholdPrice && pl.netProfit > 0) {
            return generateStopProfitAdvice(currentPrice, pl, thresholds, trendStrength)
        }

        // 2. 止损判断
        if (currentPrice <= thresholds.lossThresholdPrice && pl.netProfitPct < -1.0) {
            return generateStopLossAdvice(currentPrice, pl, thresholds, trendStrength)
        }

        // 3. 做T判断
        if (position.tPositionRatio > 0 && shouldDoT(
                currentPrice, predictedLow, predictedHigh, volatility, trendStrength
            )
        ) {
            val tAdvice = generateDoTAdvice(
                position, currentPrice, predictedLow, predictedHigh, volatility
            )
            if (tAdvice != null) return tAdvice
        }

        // 4. 加仓判断
        if (shouldAddPosition(currentPrice, position.costPrice, predictedLow, trendStrength, pl)) {
            return generateAddPositionAdvice(currentPrice, pl, thresholds, trendStrength)
        }

        // 5. 默认持有
        return TradingAdvice(
            action = TradingAction.HOLD,
            reason = "当前无触发信号，继续持有观察",
            details = "成本: ${String.format("%.2f", position.costPrice)} | " +
                    "当前: ${String.format("%.2f", currentPrice)} | " +
                    "盈亏: ${String.format("%.1f", pl.netProfitPct)}%",
            targetPrice = thresholds.profitThresholdPrice,
            stopLossPrice = thresholds.lossThresholdPrice,
            confidence = 0.5,
            profitThresholds = thresholds,
            profitLoss = pl
        )
    }

    // ==================== 建议生成子方法 ====================

    /**
     * 生成止盈建议
     */
    private fun generateStopProfitAdvice(
        currentPrice: Double,
        pl: ProfitLoss,
        thresholds: DynamicThresholds,
        trendStrength: Double
    ): TradingAdvice {
        val urgency = when {
            pl.netProfitPct > 8.0 || trendStrength < -0.01 -> Urgency.HIGH
            pl.netProfitPct > 5.0 -> Urgency.MEDIUM
            else -> Urgency.LOW
        }

        val actionText = when (urgency) {
            Urgency.HIGH -> "强烈建议止盈"
            Urgency.MEDIUM -> "建议止盈"
            Urgency.LOW -> "考虑止盈"
        }

        return TradingAdvice(
            action = TradingAction.STOP_PROFIT,
            reason = "$actionText，当前价格达到动态止盈阈值",
            details = "动态阈值: ${String.format("%.1f", thresholds.profitThreshold * 100)}% | " +
                    "当前盈利: ${String.format("%.1f", pl.netProfitPct)}%",
            targetPrice = currentPrice,
            stopLossPrice = thresholds.lossThresholdPrice,
            confidence = when (urgency) {
                Urgency.HIGH -> 0.9
                Urgency.MEDIUM -> 0.7
                Urgency.LOW -> 0.5
            },
            urgency = urgency,
            profitThresholds = thresholds,
            profitLoss = pl
        )
    }

    /**
     * 生成止损建议
     */
    private fun generateStopLossAdvice(
        currentPrice: Double,
        pl: ProfitLoss,
        thresholds: DynamicThresholds,
        trendStrength: Double
    ): TradingAdvice {
        val urgency = when {
            pl.netProfitPct < -8.0 || trendStrength < -0.02 -> Urgency.HIGH
            pl.netProfitPct < -5.0 -> Urgency.MEDIUM
            else -> Urgency.LOW
        }

        val actionText = when (urgency) {
            Urgency.HIGH -> "强烈建议止损"
            Urgency.MEDIUM -> "建议止损"
            Urgency.LOW -> "考虑止损"
        }

        return TradingAdvice(
            action = TradingAction.STOP_LOSS,
            reason = "$actionText，当前价格达到动态止损阈值",
            details = "动态阈值: ${String.format("%.1f", thresholds.lossThreshold * 100)}% | " +
                    "当前亏损: ${String.format("%.1f", pl.netProfitPct)}%",
            targetPrice = thresholds.profitThresholdPrice,
            stopLossPrice = currentPrice,
            confidence = when (urgency) {
                Urgency.HIGH -> 0.9
                Urgency.MEDIUM -> 0.7
                Urgency.LOW -> 0.5
            },
            urgency = urgency,
            profitThresholds = thresholds,
            profitLoss = pl
        )
    }

    /**
     * 判断是否适合做T
     */
    private fun shouldDoT(
        currentPrice: Double,
        predictedLow: Double,
        predictedHigh: Double,
        volatility: Double,
        trendStrength: Double
    ): Boolean {
        // 波动率太低不适合做T
        if (volatility < 0.01) return false

        // 预测区间太窄不适合做T
        val predictedRange = predictedHigh - predictedLow
        if (currentPrice <= 0) return false
        val rangeRatio = predictedRange / currentPrice
        if (rangeRatio < 0.02) return false

        // 趋势太强不适合做T（震荡市更适合）
        if (abs(trendStrength) > 0.02) return false

        return true
    }

    /**
     * 生成做T建议
     */
    private fun generateDoTAdvice(
        position: Position,
        currentPrice: Double,
        predictedLow: Double,
        predictedHigh: Double,
        volatility: Double
    ): TradingAdvice? {
        val tShares = (position.shares * position.tPositionRatio).toInt()
        if (tShares <= 0) return null

        // 计算做T预期收益
        val expectedProfit = (predictedHigh - predictedLow) * 0.6  // 保守估计
        val profitRatio = if (currentPrice > 0) expectedProfit / currentPrice else 0.0

        if (profitRatio <= 0.01) return null  // 至少 1% 预期收益

        return TradingAdvice(
            action = TradingAction.DO_T,
            reason = "日内波动较大，适合做T操作",
            details = "预期收益: ${String.format("%.1f", profitRatio * 100)}% | " +
                    "建议仓位: ${tShares}股",
            targetPrice = predictedHigh,
            stopLossPrice = predictedLow,
            confidence = 0.6,
            urgency = Urgency.MEDIUM
        )
    }

    /**
     * 判断是否适合加仓
     */
    private fun shouldAddPosition(
        currentPrice: Double,
        costPrice: Double,
        predictedLow: Double,
        trendStrength: Double,
        pl: ProfitLoss
    ): Boolean {
        if (costPrice <= 0) return false

        return currentPrice <= costPrice * 0.97 &&           // 显著低于成本（3%+）
                currentPrice <= predictedLow * 1.05 &&       // 接近支撑位
                trendStrength > -0.01 &&                     // 趋势不差
                pl.netProfitPct > -8.0                       // 亏损可控
    }

    /**
     * 生成加仓建议
     */
    private fun generateAddPositionAdvice(
        currentPrice: Double,
        pl: ProfitLoss,
        thresholds: DynamicThresholds,
        trendStrength: Double
    ): TradingAdvice {
        return TradingAdvice(
            action = TradingAction.ADD_POSITION,
            reason = "价格接近强支撑位，可考虑分批加仓",
            details = "当前低于成本${String.format("%.1f", abs(pl.netProfitPct))}% | " +
                    "趋势强度: ${String.format("%.4f", trendStrength)}",
            targetPrice = thresholds.profitThresholdPrice,
            stopLossPrice = thresholds.lossThresholdPrice,
            confidence = 0.4,
            urgency = Urgency.LOW,
            profitThresholds = thresholds,
            profitLoss = pl
        )
    }

    /**
     * 生成开仓建议
     */
    private fun generateOpenPositionAdvice(
        currentPrice: Double,
        predictedLow: Double,
        predictedHigh: Double,
        volatility: Double
    ): TradingAdvice {
        val predictedRange = predictedHigh - predictedLow
        val rangeRatio = if (currentPrice > 0) predictedRange / currentPrice else 0.0

        return if (rangeRatio > 0.01 && currentPrice <= predictedLow * 1.03) {
            // 价格接近支撑位，适合开仓
            TradingAdvice(
                action = TradingAction.OPEN_POSITION,
                reason = "价格接近支撑位，预测区间较宽，适合建立仓位",
                details = "预测区间: ${String.format("%.2f", predictedLow)}-" +
                        "${String.format("%.2f", predictedHigh)} | " +
                        "区间宽度: ${String.format("%.1f", rangeRatio * 100)}%",
                targetPrice = predictedHigh,
                stopLossPrice = predictedLow * 0.97,
                confidence = 0.5,
                urgency = Urgency.MEDIUM
            )
        } else {
            // 不满足开仓条件，建议持有
            TradingAdvice(
                action = TradingAction.HOLD,
                reason = "当前无持仓且未满足开仓条件，建议观望",
                details = "预测区间宽度: ${String.format("%.1f", rangeRatio * 100)}%",
                confidence = 0.3,
                urgency = Urgency.LOW
            )
        }
    }

    // ==================== 辅助工具方法 ====================

    /**
     * 格式化交易建议摘要
     */
    fun formatAdviceSummary(advice: TradingAdvice): String {
        return buildString {
            appendLine("📊 交易建议: ${advice.action.labelCn}")
            appendLine("原因: ${advice.reason}")
            if (advice.details.isNotEmpty()) {
                appendLine("详情: ${advice.details}")
            }
            if (advice.targetPrice > 0) {
                appendLine("目标价: ${String.format("%.2f", advice.targetPrice)}")
            }
            if (advice.stopLossPrice > 0) {
                appendLine("止损价: ${String.format("%.2f", advice.stopLossPrice)}")
            }
            appendLine("置信度: ${String.format("%.0f", advice.confidence * 100)}%")
            appendLine("紧急度: ${advice.urgency.label}")
            advice.profitThresholds?.let { t ->
                appendLine("止盈阈值: ${String.format("%.1f", t.profitThreshold * 100)}% " +
                        "(${String.format("%.2f", t.profitThresholdPrice)})")
                appendLine("止损阈值: ${String.format("%.1f", t.lossThreshold * 100)}% " +
                        "(${String.format("%.2f", t.lossThresholdPrice)})")
            }
            advice.profitLoss?.let { pl ->
                if (!pl.isNoPosition) {
                    appendLine("盈亏: ${String.format("%.2f", pl.netProfit)}元 " +
                            "(${String.format("%.1f", pl.netProfitPct)}%)")
                }
            }
        }
    }

    /**
     * 计算可买入股数（整百手）
     */
    fun calculateMaxShares(cash: Double, buyPrice: Double): Int {
        if (buyPrice <= 0 || cash <= 0) return 0
        val costRate = calculateBuyCost(buyPrice * 100) / (buyPrice * 100)
        val maxAmount = cash / (1.0 + costRate)
        val rawShares = (maxAmount / buyPrice).toInt()
        return (rawShares / 100) * 100  // 向下取整到整百手
    }
}
