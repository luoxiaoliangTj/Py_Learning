# algo/improved_naive.py
import pandas as pd
import numpy as np

def algo_predict(df_daily, df_minute, meta):
    """
    改进版 naive：
    1. 用 ATR 替代原始 range，区间更紧
    2. 加入趋势偏移，区间非对称
    3. 低波动期再收紧
    """
    c = df_daily['close'].iloc[-1]

    # 1. ATR 代替高低点 range
    atr = (df_daily['high'] - df_daily['low']).rolling(14).mean().iloc[-1]
    pred_range = max(atr, c * 0.005)          # 兜底 0.5%

    # 2. 趋势偏移
    trend = (c / df_daily['close'].iloc[-20] - 1)
    bias = np.clip(trend / 0.06, -0.5, 0.5)   # 6% 趋势对应最大 50% 偏移

    # 3. 低波动过滤
    volatility = df_daily['close'].pct_change().rolling(10).std().iloc[-1]
    if volatility < 0.008:                    # 低波动阈值
        pred_range *= 0.5

    pred_low  = c - pred_range * (0.5 - bias)
    pred_high = c + pred_range * (0.5 + bias)

    return {
        "pred_low": pred_low,
        "pred_high": pred_high,
        "volatility": volatility,
        "trend_strength": trend,
        "market_regime": "trending" if abs(trend) > 0.03 else "choppy"
    }
