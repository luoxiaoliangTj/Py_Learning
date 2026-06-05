import pandas as pd
import numpy as np
from statistics.base import StatPluginBase


class MacdCross(StatPluginBase):
    name = 'macd_cross'

    def fit(self, csv_path: str, meta: dict):
        df = pd.read_csv(csv_path)
        df['日期'] = pd.to_datetime(df['日期'])
        cutoff = df['日期'].max() - pd.DateOffset(years=2)
        sub = df[df['日期'] >= cutoff].copy()

        # 计算MACD(12,26,9)
        ema12 = sub['收盘'].ewm(span=12).mean()
        ema26 = sub['收盘'].ewm(span=26).mean()
        dif = ema12 - ema26
        dea = dif.ewm(span=9).mean()
        macd_hist = 2 * (dif - dea)

        # ATR20
        sub['h-l'] = sub['最高'] - sub['最低']
        sub['h-pc'] = (sub['最高'] - sub['收盘'].shift(1)).abs()
        sub['l-pc'] = (sub['最低'] - sub['收盘'].shift(1)).abs()
        sub['tr'] = sub[['h-l', 'h-pc', 'l-pc']].max(axis=1)
        atr20 = sub['tr'].rolling(20).mean().iloc[-1]

        # MACD柱状图绝对值越大，width越宽
        hist_factor = max(0.5, min(2.0, abs(macd_hist.iloc[-1]) / (atr20 * 0.1 + 1e-8)))
        width = atr20 * hist_factor

        return {'k': 1.5, 'width': width, 'dif': dif.iloc[-1], 'dea': dea.iloc[-1]}
