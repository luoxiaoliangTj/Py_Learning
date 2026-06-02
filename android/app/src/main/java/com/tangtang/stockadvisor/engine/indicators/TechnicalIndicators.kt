package com.tangtang.stockadvisor.engine.indicators

/**
 * K线数据
 */
data class KlineData(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val amount: Double = 0.0
)

/**
 * 技术指标计算库
 * 所有指标接收 List<Double> 作为输入（K线相关指标需先用 closes() 提取收盘价），返回 List<Double>。
 * 返回列表长度与输入一致，前 period-1 个位置填充 NaN 表示数据不足。
 */
object TechnicalIndicators {

    // ======================== 基础工具 ========================

    /** 从 K 线列表中提取收盘价 */
    fun closes(klines: List<KlineData>): List<Double> = klines.map { it.close }

    // ======================== SMA ========================

    /**
     * 简单移动平均 (SMA)
     * MA(N) = (Close_1 + Close_2 + ... + Close_N) / N
     *
     * @param data   价格序列
     * @param period 计算周期，必须 >= 1
     * @return 与输入等长的列表，前 (period-1) 位为 NaN
     */
    fun sma(data: List<Double>, period: Int): List<Double> {
        if (period < 1) throw IllegalArgumentException("period must be >= 1")
        val result = MutableList(data.size) { Double.NaN }
        if (data.size < period) return result

        var sum = 0.0
        for (i in 0 until period) {
            sum += data[i]
        }
        result[period - 1] = sum / period

        for (i in period until data.size) {
            sum += data[i] - data[i - period]
            result[i] = sum / period
        }
        return result
    }

    // ======================== EMA ========================

    /**
     * 指数移动平均 (EMA)
     * EMA(N) = 2/(N+1) * Close + (N-1)/(N+1) * EMA_prev
     *
     * @param data   价格序列
     * @param period 计算周期，必须 >= 1
     * @return 与输入等长的列表，前 (period-1) 位为 NaN
     */
    fun ema(data: List<Double>, period: Int): List<Double> {
        if (period < 1) throw IllegalArgumentException("period must be >= 1")
        val result = MutableList(data.size) { Double.NaN }
        if (data.size < period) return result

        val multiplier = 2.0 / (period + 1)

        // 第一个 EMA 值使用前 period 个数据的 SMA 作为种子
        var emaValue = 0.0
        for (i in 0 until period) {
            emaValue += data[i]
        }
        emaValue /= period
        result[period - 1] = emaValue

        for (i in period until data.size) {
            emaValue = (data[i] - emaValue) * multiplier + emaValue
            result[i] = emaValue
        }
        return result
    }

    // ======================== 标准差 ========================

    /**
     * 移动标准差
     *
     * @param data   价格序列
     * @param period 计算周期，必须 >= 1
     * @return 与输入等长的列表，前 (period-1) 位为 NaN
     */
    fun stdDev(data: List<Double>, period: Int): List<Double> {
        if (period < 1) throw IllegalArgumentException("period must be >= 1")
        val result = MutableList(data.size) { Double.NaN }
        if (data.size < period) return result

        var sum = 0.0
        var sumSq = 0.0

        for (i in 0 until period) {
            sum += data[i]
            sumSq += data[i] * data[i]
        }
        result[period - 1] = kotlin.math.sqrt(sumSq / period - (sum / period) * (sum / period))

        for (i in period until data.size) {
            sum += data[i] - data[i - period]
            sumSq += data[i] * data[i] - data[i - period] * data[i - period]
            val mean = sum / period
            result[i] = kotlin.math.sqrt(sumSq / period - mean * mean)
        }
        return result
    }

    // ======================== 布林带 ========================

    /**
     * 布林带 (Bollinger Bands)
     * Upper = MA20 + 2 * StdDev20
     * Middle = MA20
     * Lower = MA20 - 2 * StdDev20
     *
     * @param closes 收盘价序列
     * @param period 计算周期，默认 20
     * @return Triple(上轨, 中轨, 下轨)，每个列表长度与输入一致
     */
    fun bollinger(
        closes: List<Double>,
        period: Int = 20
    ): Triple<List<Double>, List<Double>, List<Double>> {
        val middle = sma(closes, period)
        val sd = stdDev(closes, period)
        val multiplier = 2.0

        val upper = MutableList(closes.size) { Double.NaN }
        val lower = MutableList(closes.size) { Double.NaN }

        for (i in closes.indices) {
            if (!middle[i].isNaN() && !sd[i].isNaN()) {
                upper[i] = middle[i] + multiplier * sd[i]
                lower[i] = middle[i] - multiplier * sd[i]
            }
        }

        return Triple(upper, middle, lower)
    }

    // ======================== RSI ========================

    /**
     * 相对强弱指标 (RSI)
     * RSI(14) = 100 - 100 / (1 + SMA(max(Diff,0),14) / SMA(max(-Diff,0),14))
     *
     * 使用 Wilder 平滑法 (即 EMA 的 period = 2N-1 变体)，而非简单 SMA，
     * 这与主流交易软件 (通达信、同花顺) 的算法一致。
     *
     * @param closes 收盘价序列
     * @param period 计算周期，默认 14
     * @return 与输入等长的列表，前 period 位为 NaN
     */
    fun rsi(closes: List<Double>, period: Int = 14): List<Double> {
        if (period < 1) throw IllegalArgumentException("period must be >= 1")
        val result = MutableList(closes.size) { Double.NaN }
        if (closes.size < period + 1) return result

        // 计算每日涨跌幅
        val changes = MutableList(closes.size) { 0.0 }
        for (i in 1 until closes.size) {
            changes[i] = closes[i] - closes[i - 1]
        }

        // Wilder 平滑：使用 EMA 指数 = 1/period 的变体
        // 第一个平均涨幅和跌幅使用简单平均 (前 period 个涨跌)
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            if (changes[i] > 0) avgGain += changes[i]
            else avgLoss += -changes[i]
        }
        avgGain /= period
        avgLoss /= period

        // 初始 RSI
        if (avgLoss == 0.0) {
            result[period] = 100.0
        } else {
            result[period] = 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        }

        // 后续使用 Wilder 平滑
        for (i in (period + 1) until closes.size) {
            val gain = if (changes[i] > 0) changes[i] else 0.0
            val loss = if (changes[i] < 0) -changes[i] else 0.0

            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period

            if (avgLoss == 0.0) {
                result[i] = 100.0
            } else {
                result[i] = 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
            }
        }

        return result
    }

    // ======================== MACD ========================

    /**
     * MACD 指标
     * DIF = EMA12 - EMA26
     * DEA = EMA(DIF, 9)
     * Histogram = 2 * (DIF - DEA)
     *
     * @param closes 收盘价序列
     * @return Triple(DIF, DEA, Histogram)，每个列表长度与输入一致
     */
    fun macd(closes: List<Double>): Triple<List<Double>, List<Double>, List<Double>> {
        val ema12 = ema(closes, 12)
        val ema26 = ema(closes, 26)

        // DIF = EMA12 - EMA26
        val dif = MutableList(closes.size) { Double.NaN }
        for (i in closes.indices) {
            if (!ema12[i].isNaN() && !ema26[i].isNaN()) {
                dif[i] = ema12[i] - ema26[i]
            }
        }

        // DEA = EMA(DIF, 9)，需要过滤掉 NaN 前缀
        // 从第一个非 NaN 的 DIF 位置开始计算
        val deaResult = MutableList(closes.size) { Double.NaN }
        val deaMultiplier = 2.0 / (9 + 1)

        // 找到第一个有效 DIF 位置
        val firstValid = dif.indexOfFirst { !it.isNaN() }
        if (firstValid == -1) {
            val empty = MutableList(closes.size) { Double.NaN }
            return Triple(dif, empty, empty)
        }

        // DEA 的种子：前 9 个有效 DIF 的 SMA
        val deaStart = firstValid + 8
        if (deaStart >= closes.size) {
            val empty = MutableList(closes.size) { Double.NaN }
            return Triple(dif, empty, empty)
        }

        var deaValue = 0.0
        for (i in firstValid..deaStart) {
            deaValue += dif[i]
        }
        deaValue /= 9.0
        deaResult[deaStart] = deaValue

        for (i in (deaStart + 1) until closes.size) {
            if (!dif[i].isNaN()) {
                deaValue = (dif[i] - deaValue) * deaMultiplier + deaValue
                deaResult[i] = deaValue
            }
        }

        // Histogram = 2 * (DIF - DEA)
        val histogram = MutableList(closes.size) { Double.NaN }
        for (i in closes.indices) {
            if (!dif[i].isNaN() && !deaResult[i].isNaN()) {
                histogram[i] = 2.0 * (dif[i] - deaResult[i])
            }
        }

        return Triple(dif, deaResult, histogram)
    }

    // ======================== ATR ========================

    /**
     * 真实波幅 (ATR)
     * TR = max(H-L, |H-PrevC|, |L-PrevC|)
     * ATR = SMA(TR, N)
     *
     * @param klines K 线列表
     * @param period 计算周期，默认 20
     * @return 与输入等长的列表，前 period 位为 NaN
     */
    fun atr(klines: List<KlineData>, period: Int = 20): List<Double> {
        if (period < 1) throw IllegalArgumentException("period must be >= 1")
        val result = MutableList(klines.size) { Double.NaN }
        if (klines.size < period + 1) return result

        // 计算 True Range
        val tr = MutableList(klines.size) { 0.0 }
        // 第一根 K 线没有前一根收盘价，TR 就是 H-L
        tr[0] = klines[0].high - klines[0].low

        for (i in 1 until klines.size) {
            val hl = klines[i].high - klines[i].low
            val hc = kotlin.math.abs(klines[i].high - klines[i - 1].close)
            val lc = kotlin.math.abs(klines[i].low - klines[i - 1].close)
            tr[i] = maxOf(hl, hc, lc)
        }

        // ATR = SMA(TR, period)
        var sum = 0.0
        for (i in 0 until period) {
            sum += tr[i]
        }
        result[period - 1] = sum / period

        for (i in period until klines.size) {
            sum += tr[i] - tr[i - period]
            result[i] = sum / period
        }

        return result
    }

    // ======================== KDJ ========================

    /**
     * KDJ 随机指标 (9,3,3)
     * RSV = (C - L9) / (H9 - L9) * 100
     * K = 2/3 * prev_K + 1/3 * RSV
     * D = 2/3 * prev_D + 1/3 * K
     * J = 3*K - 2*D
     *
     * @param klines  K线列表
     * @param n       RSV周期，默认 9
     * @param m1      K平滑系数，默认 3
     * @param m2      D平滑系数，默认 3
     * @return Triple(K序列, D序列, J序列)，每个列表长度与输入一致
     */
    fun kdj(
        klines: List<KlineData>,
        n: Int = 9,
        m1: Int = 3,
        m2: Int = 3
    ): Triple<List<Double>, List<Double>, List<Double>> {
        val kValues = MutableList(klines.size) { 50.0 }
        val dValues = MutableList(klines.size) { 50.0 }
        val jValues = MutableList(klines.size) { 50.0 }
        var prevK = 50.0
        var prevD = 50.0

        for (i in klines.indices) {
            if (i < n - 1) continue
            val window = klines.subList(i - n + 1, i + 1)
            val h9 = window.maxOf { it.high }
            val l9 = window.minOf { it.low }
            val rsv = if (h9 - l9 < 1e-10) 50.0
                      else (klines[i].close - l9) / (h9 - l9) * 100.0

            val k = (m1 - 1).toDouble() / m1 * prevK + 1.0 / m1 * rsv
            val d = (m2 - 1).toDouble() / m2 * prevD + 1.0 / m2 * k
            val j = 3.0 * k - 2.0 * d

            kValues[i] = k
            dValues[i] = d
            jValues[i] = j
            prevK = k
            prevD = d
        }
        return Triple(kValues, dValues, jValues)
    }

    // ======================== Parkinson 波动率 ========================

    /**
     * Parkinson 估计量
     * σ_p = sqrt( mean(ln(H/L)^2) / (4*ln2) )
     *
     * @param klines  K线列表
     * @param period  计算周期，默认 20
     * @return Parkinson 波动率 (年化前的σ值)
     */
    fun parkinsonSigma(klines: List<KlineData>, period: Int = 20): Double {
        if (klines.size < 2) return 0.0
        val logHL2 = klines.map { kotlin.math.ln(it.high / it.low).let { v -> v * v } }
        val slice = if (logHL2.size >= period) logHL2.takeLast(period) else logHL2
        val mean = slice.average()
        return kotlin.math.sqrt(mean / (4.0 * kotlin.math.ln(2.0)))
    }

    // ======================== 已实现波动率 ========================

    /**
     * Realized Volatility
     * rv = std(log_returns) * sqrt(250)
     *
     * @param closes  收盘价序列
     * @param period  计算周期，默认 20
     * @return 年化已实现波动率
     */
    fun realizedVolatility(closes: List<Double>, period: Int = 20): Double {
        if (closes.size < 2) return 0.0
        val logReturns = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            if (closes[i - 1] > 0) {
                logReturns.add(kotlin.math.ln(closes[i] / closes[i - 1]))
            }
        }
        if (logReturns.isEmpty()) return 0.0
        val slice = if (logReturns.size >= period) logReturns.takeLast(period) else logReturns
        val mean = slice.average()
        val variance = slice.map { (it - mean) * (it - mean) }.sum() /
                (slice.size - 1).coerceAtLeast(1)
        return kotlin.math.sqrt(variance) * kotlin.math.sqrt(250.0)
    }

    // ======================== 辅助：提取 K 线字段 ========================

    /** 提取最高价序列 */
    fun highs(klines: List<KlineData>): List<Double> = klines.map { it.high }

    /** 提取最低价序列 */
    fun lows(klines: List<KlineData>): List<Double> = klines.map { it.low }

    /** 提取开盘价序列 */
    fun opens(klines: List<KlineData>): List<Double> = klines.map { it.open }

    /** 提取成交量序列 */
    fun volumes(klines: List<KlineData>): List<Double> = klines.map { it.volume }

    // ======================== 辅助：通用 SMA (对任意 Double 序列) ========================

    /**
     * 通用 SMA，适用于任意 Double 序列（如成交量等）
     */
    fun smaDouble(data: List<Double>, period: Int): List<Double> = sma(data, period)

    /**
     * 通用 EMA，适用于任意 Double 序列
     */
    fun emaDouble(data: List<Double>, period: Int): List<Double> = ema(data, period)
}
