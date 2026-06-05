package com.tangtang.aico.engine

import com.tangtang.aico.engine.indicators.KlineData
import com.tangtang.aico.engine.indicators.TechnicalIndicators
import kotlin.math.*

// ======================== 信号枚举 ========================

enum class Signal {
    BUY,    // 买入: High > Upper
    SELL,   // 卖出: Low < Lower
    HOLD    // 持有: 无突破
}

// ======================== 通道策略引擎 ========================

/**
 * 通道策略引擎 - 11个统计插件的 width 计算 + 信号判断
 *
 * 核心逻辑:
 *   width = plugin.calculateWidth(klines, k)
 *   upper = close + width / 2
 *   lower = close - width / 2
 *   买入: high > upper
 *   卖出: low < lower
 */
object ChannelStrategy {

    /** 所有可用插件注册表 */
    val plugins: List<ChannelPlugin> = listOf(
        AtrChannelPlugin(),
        BollingerPlugin(),
        MacdCrossPlugin(),
        RsiReversalPlugin(),
        MeanReversionPlugin(),
        DualThrustPlugin(),
        VolumeBreakoutPlugin(),
        ParkinsonPlugin(),
        QuantileRangePlugin(),
        RealizedVolatilityPlugin(),
        KdjPlugin()
    )

    /** 按名称查找插件 */
    fun findPlugin(name: String): ChannelPlugin? {
        return plugins.find { it.name == name }
    }

    /** 评估信号: 买入/卖出/持有 */
    fun evaluateSignal(
        klines: List<KlineData>,
        plugin: ChannelPlugin,
        k: Double = 1.0
    ): StrategyResult {
        if (klines.size < 5) {
            return StrategyResult(
                plugin = plugin.name,
                signal = Signal.HOLD,
                width = 0.0,
                upper = 0.0,
                lower = 0.0,
                close = 0.0,
                message = "数据不足"
            )
        }

        val lastClose = klines.last().close
        val lastHigh = klines.last().high
        val lastLow = klines.last().low
        val width = plugin.calculateWidth(klines, k)

        val upper = lastClose + width / 2.0
        val lower = lastClose - width / 2.0

        val signal = when {
            lastHigh > upper -> Signal.BUY
            lastLow < lower -> Signal.SELL
            else -> Signal.HOLD
        }

        val msg = when (signal) {
            Signal.BUY -> "突破上轨 %.2f > %.2f".format(lastHigh, upper)
            Signal.SELL -> "突破下轨 %.2f < %.2f".format(lastLow, lower)
            Signal.HOLD -> "未突破通道 [%.2f ~ %.2f]".format(lower, upper)
        }

        return StrategyResult(
            plugin = plugin.name,
            signal = signal,
            width = width,
            upper = upper,
            lower = lower,
            close = lastClose,
            message = msg,
            details = plugin.getDetails(klines, k)
        )
    }

    /** 批量评估所有插件 */
    fun evaluateAll(
        klines: List<KlineData>,
        k: Double = 1.0
    ): List<StrategyResult> {
        return plugins.map { evaluateSignal(klines, it, k) }
    }

    /** 综合投票: 多数插件的信号作为最终信号 */
    fun aggregateVote(
        klines: List<KlineData>,
        k: Double = 1.0
    ): AggregatedResult {
        val results = evaluateAll(klines, k)
        val buyCount = results.count { it.signal == Signal.BUY }
        val sellCount = results.count { it.signal == Signal.SELL }
        val holdCount = results.count { it.signal == Signal.HOLD }
        val total = results.size

        val finalSignal = when {
            buyCount > sellCount && buyCount > total / 2 -> Signal.BUY
            sellCount > buyCount && sellCount > total / 2 -> Signal.SELL
            else -> Signal.HOLD
        }

        return AggregatedResult(
            signal = finalSignal,
            buyVotes = buyCount,
            sellVotes = sellCount,
            holdVotes = holdCount,
            totalPlugins = total,
            details = results
        )
    }
}

// ======================== 通道插件接口 ========================

interface ChannelPlugin {
    /** 插件名称 */
    val name: String

    /**
     * 计算通道宽度 (width)
     * @param klines K线数据列表（已按日期升序排列）
     * @param k 乘数系数（各插件默认使用方式不同）
     * @return width 值，即通道的总宽度
     */
    fun calculateWidth(klines: List<KlineData>, k: Double): Double

    /** 获取详细信息（可选） */
    fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> = emptyMap()
}

// ======================== 结果数据类 ========================

data class StrategyResult(
    val plugin: String,
    val signal: Signal,
    val width: Double,
    val upper: Double,
    val lower: Double,
    val close: Double,
    val message: String = "",
    val details: Map<String, Double> = emptyMap()
)

data class AggregatedResult(
    val signal: Signal,
    val buyVotes: Int,
    val sellVotes: Int,
    val holdVotes: Int,
    val totalPlugins: Int,
    val details: List<StrategyResult>
) {
    val buyRatio get() = if (totalPlugins > 0) buyVotes.toDouble() / totalPlugins else 0.0
    val sellRatio get() = if (totalPlugins > 0) sellVotes.toDouble() / totalPlugins else 0.0
}

// ======================================================================
//  辅助：列表尾部取值
// ======================================================================

/** 取列表尾部 n 个元素 */
private fun <T> List<T>.tail(n: Int): List<T> {
    return if (size <= n) this else takeLast(n)
}

// ======================================================================
//  11 个统计插件实现
// ======================================================================

/**
 * 1. ATR 通道
 * width = k * ATR20 (k 默认 2.0)
 * Upper = close + width/2, Lower = close - width/2
 */
class AtrChannelPlugin : ChannelPlugin {
    override val name = "atr_channel"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val atrSeries = TechnicalIndicators.atr(klines, 20)
        val atr20 = atrSeries.lastOrNull { !it.isNaN() } ?: 0.0
        val multiplier = if (k > 0) k else 2.0
        return atr20 * multiplier
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val atrSeries = TechnicalIndicators.atr(klines, 20)
        val atr20 = atrSeries.lastOrNull { !it.isNaN() } ?: 0.0
        return mapOf("atr20" to atr20)
    }
}

/**
 * 2. 布林带 (Bollinger Bands)
 * width = 2 * k * StdDev20
 * Upper = MA20 + k * σ, Lower = MA20 - k * σ
 */
class BollingerPlugin : ChannelPlugin {
    override val name = "bollinger"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val closes = klines.map { it.close }
        val (upper, _, lower) = TechnicalIndicators.bollinger(closes, 20)

        val lastUpper = upper.lastOrNull { !it.isNaN() } ?: return 0.0
        val lastLower = lower.lastOrNull { !it.isNaN() } ?: return 0.0
        val std = (lastUpper - lastLower) / 4.0  // Upper - Lower = 4σ

        val kEff = if (k > 0) k else 1.0
        return 2.0 * kEff * std
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val closes = klines.map { it.close }
        val (upper, middle, lower) = TechnicalIndicators.bollinger(closes, 20)
        return mapOf(
            "upper" to (upper.lastOrNull { !it.isNaN() } ?: Double.NaN),
            "middle" to (middle.lastOrNull { !it.isNaN() } ?: Double.NaN),
            "lower" to (lower.lastOrNull { !it.isNaN() } ?: Double.NaN)
        )
    }
}

/**
 * 3. MACD 交叉
 * 基于 MACD 柱状图绝对值调整 ATR 宽度
 * width = ATR20 * clamp(|MACD_hist| / (ATR20*0.1), 0.5, 2.0)
 */
class MacdCrossPlugin : ChannelPlugin {
    override val name = "macd_cross"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val closes = klines.map { it.close }
        val (_, _, hist) = TechnicalIndicators.macd(closes)
        val atrSeries = TechnicalIndicators.atr(klines, 20)
        val atr20 = atrSeries.lastOrNull { !it.isNaN() } ?: 0.0
        if (atr20 < 1e-10) return 0.0

        val histValue = hist.lastOrNull { !it.isNaN() } ?: 0.0
        val histFactor = (abs(histValue) / (atr20 * 0.1))
            .coerceIn(0.5, 2.0)
        return atr20 * histFactor
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val closes = klines.map { it.close }
        val (dif, dea, hist) = TechnicalIndicators.macd(closes)
        val atrSeries = TechnicalIndicators.atr(klines, 20)
        return mapOf(
            "dif" to (dif.lastOrNull { !it.isNaN() } ?: 0.0),
            "dea" to (dea.lastOrNull { !it.isNaN() } ?: 0.0),
            "macd_hist" to (hist.lastOrNull { !it.isNaN() } ?: 0.0),
            "atr20" to (atrSeries.lastOrNull { !it.isNaN() } ?: 0.0)
        )
    }
}

/**
 * 4. RSI 反转
 * RSI 越低 → 通道越窄（超卖区更敏感）
 * width = ATR20 * clamp(RSI / 50, 0.5, 2.0)
 */
class RsiReversalPlugin : ChannelPlugin {
    override val name = "rsi_reversal"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val closes = klines.map { it.close }
        val rsiSeries = TechnicalIndicators.rsi(closes, 14)
        val atrSeries = TechnicalIndicators.atr(klines, 20)
        val atr20 = atrSeries.lastOrNull { !it.isNaN() } ?: 0.0

        val currentRsi = rsiSeries.lastOrNull { !it.isNaN() } ?: 50.0
        val rsiFactor = (currentRsi / 50.0).coerceIn(0.5, 2.0)
        return atr20 * rsiFactor
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val closes = klines.map { it.close }
        val rsiSeries = TechnicalIndicators.rsi(closes, 14)
        return mapOf("rsi" to (rsiSeries.lastOrNull { !it.isNaN() } ?: 50.0))
    }
}

/**
 * 5. 均值回归 (Mean Reversion)
 * 价格偏离 60 日 MA 越大 → width 越宽
 * deviation = (close - MA60) / MA60
 * width = ATR20 * (1 + clamp(|deviation|/0.05, 0.5, 2.0))
 */
class MeanReversionPlugin : ChannelPlugin {
    override val name = "mean_reversion"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val closes = klines.map { it.close }
        val ma60Series = TechnicalIndicators.sma(closes, 60)
        val atrSeries = TechnicalIndicators.atr(klines, 20)
        val atr20 = atrSeries.lastOrNull { !it.isNaN() } ?: 0.0

        val lastClose = closes.last()
        val lastMa60 = ma60Series.lastOrNull { !it.isNaN() } ?: return atr20 * 1.5

        if (lastMa60 < 1e-10) return atr20 * 1.5
        val deviation = (lastClose - lastMa60) / lastMa60

        val devFactor = (abs(deviation) / 0.05).coerceIn(0.5, 2.0)
        return atr20 * (1.0 + devFactor)
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val closes = klines.map { it.close }
        val ma60Series = TechnicalIndicators.sma(closes, 60)
        val atrSeries = TechnicalIndicators.atr(klines, 20)
        val lastMa60 = ma60Series.lastOrNull { !it.isNaN() } ?: Double.NaN
        val atr20 = atrSeries.lastOrNull { !it.isNaN() } ?: 0.0
        val lastClose = closes.last()
        val deviation = if (lastMa60 > 1e-10) (lastClose - lastMa60) / lastMa60 else 0.0
        return mapOf(
            "ma60" to lastMa60,
            "deviation" to deviation,
            "atr20" to atr20
        )
    }
}

/**
 * 6. Dual Thrust
 * range_val = max(HH - LC, HC - LL)  基于近 N 日
 * width = 2 * k * range_val (k 默认 0.5)
 */
class DualThrustPlugin : ChannelPlugin {
    override val name = "dual_thrust"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val n = min(5, klines.size)
        val recent = klines.tail(n)

        val hh = recent.maxOf { it.high }
        val hc = recent.maxOf { it.close }
        val lc = recent.minOf { it.close }
        val ll = recent.minOf { it.low }

        val rangeVal = max(hh - lc, hc - ll)
        val kEff = if (k > 0) k else 0.5
        return 2.0 * kEff * rangeVal
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val n = min(5, klines.size)
        val recent = klines.tail(n)
        val hh = recent.maxOf { it.high }
        val hc = recent.maxOf { it.close }
        val lc = recent.minOf { it.close }
        val ll = recent.minOf { it.low }
        return mapOf(
            "hh" to hh,
            "hc" to hc,
            "lc" to lc,
            "ll" to ll,
            "range_val" to max(hh - lc, hc - ll)
        )
    }
}

/**
 * 7. 成交量突破 (Volume Breakout)
 * vol_ratio = 当日成交量 / 20日均量
 * width = ATR20 * clamp(vol_ratio/2, 0.5, 2.5)
 */
class VolumeBreakoutPlugin : ChannelPlugin {
    override val name = "volume_breakout"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val atrSeries = TechnicalIndicators.atr(klines, 20)
        val atr20 = atrSeries.lastOrNull { !it.isNaN() } ?: 0.0

        // 计算成交量比
        val volSeries = TechnicalIndicators.volumes(klines)
        val lastVol = volSeries.lastOrNull() ?: 0.0
        val recentVols = volSeries.tail(20)
        val volMa20 = if (recentVols.size >= 2) recentVols.dropLast(1).average() else 0.0
        val volRatio = if (volMa20 > 0) lastVol / volMa20 else 1.0

        val volFactor = (volRatio / 2.0).coerceIn(0.5, 2.5)
        return atr20 * volFactor
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val volSeries = TechnicalIndicators.volumes(klines)
        val lastVol = volSeries.lastOrNull() ?: 0.0
        val recentVols = volSeries.tail(20)
        val volMa20 = if (recentVols.size >= 2) recentVols.dropLast(1).average() else 0.0
        val volRatio = if (volMa20 > 0) lastVol / volMa20 else 1.0
        return mapOf(
            "vol_ratio" to volRatio,
            "vol_ma20" to volMa20
        )
    }
}

/**
 * 8. Parkinson 波动率
 * σ_p = sqrt( mean(ln(H/L)^2) / (4*ln2) )
 * width = σ_p * close * k
 */
class ParkinsonPlugin : ChannelPlugin {
    override val name = "parkinson"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val sigma = TechnicalIndicators.parkinsonSigma(klines, 20)
        val lastClose = klines.last().close
        val kEff = if (k > 0) k else 1.0
        return sigma * lastClose * kEff
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val sigma = TechnicalIndicators.parkinsonSigma(klines, 20)
        val lastClose = klines.last().close
        return mapOf(
            "sigma_p" to sigma,
            "close" to lastClose,
            "width_no_k" to (sigma * lastClose)
        )
    }
}

/**
 * 9. 分位数区间 (Quantile Range)
 * width = (20日最高收盘 - 20日最低收盘) * k
 */
class QuantileRangePlugin : ChannelPlugin {
    override val name = "quantile_range"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val period = 20
        val closes = klines.tail(period).map { it.close }
        val hi = closes.max()
        val lo = closes.min()
        val kEff = if (k > 0) k else 0.25
        return (hi - lo) * kEff
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val period = 20
        val closes = klines.tail(period).map { it.close }
        return mapOf(
            "hi_close" to closes.max(),
            "lo_close" to closes.min(),
            "range" to (closes.max() - closes.min())
        )
    }
}

/**
 * 10. 已实现波动率 (Realized Volatility)
 * rv = std(log_returns) * sqrt(250)  (年化)
 * width = rv * close * k
 */
class RealizedVolatilityPlugin : ChannelPlugin {
    override val name = "realized_volatility"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val closes = klines.map { it.close }
        val rv = TechnicalIndicators.realizedVolatility(closes, 20)
        val lastClose = klines.last().close
        val kEff = if (k > 0) k else 1.0
        return rv * lastClose * kEff
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val closes = klines.map { it.close }
        val rv = TechnicalIndicators.realizedVolatility(closes, 20)
        val lastClose = klines.last().close
        return mapOf(
            "annualized_vol" to rv,
            "close" to lastClose
        )
    }
}

/**
 * 11. KDJ 振荡器
 * 基于 KDJ 的 K/D 值调整通道宽度
 * - K > 80 或 D > 80 → 超买区，通道收窄
 * - K < 20 或 D < 20 → 超卖区，通道收窄
 * - 中性区 → 通道正常
 * width = ATR20 * kdjFactor * k
 */
class KdjPlugin : ChannelPlugin {
    override val name = "kdj"

    override fun calculateWidth(klines: List<KlineData>, k: Double): Double {
        val atrSeries = TechnicalIndicators.atr(klines, 20)
        val atr20 = atrSeries.lastOrNull { !it.isNaN() } ?: 0.0
        val (kValues, dValues, _) = TechnicalIndicators.kdj(klines)

        val currentK = kValues.lastOrNull() ?: 50.0
        val currentD = dValues.lastOrNull() ?: 50.0

        // KDJ 因子: 超买超卖区域通道收窄，中性区域正常
        val kdjFactor = when {
            currentK > 80 || currentD > 80 -> 0.5  // 超买收窄
            currentK < 20 || currentD < 20 -> 0.5  // 超卖收窄
            currentK > 60 && currentD > 60 -> 0.7  // 偏高区域
            currentK < 40 && currentD < 40 -> 0.7  // 偏低区域
            else -> 1.0                             // 中性区域
        }

        val kEff = if (k > 0) k else 1.0
        return atr20 * kdjFactor * kEff
    }

    override fun getDetails(klines: List<KlineData>, k: Double): Map<String, Double> {
        val (kValues, dValues, jValues) = TechnicalIndicators.kdj(klines)
        return mapOf(
            "k" to (kValues.lastOrNull() ?: 50.0),
            "d" to (dValues.lastOrNull() ?: 50.0),
            "j" to (jValues.lastOrNull() ?: 50.0)
        )
    }
}
