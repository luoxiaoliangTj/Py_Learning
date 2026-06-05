import pandas as pd
import numpy as np
from statistics.base import StatPluginBase


class MeanReversion(StatPluginBase):
    name = 'mean_reversion'

    def fit(self, csv_path: str, meta: dict):
        df = pd.read_csv(csv_path)
        df['日期'] = pd.to_datetime(df['日期'])
        cutoff = df['日期'].max() - pd.DateOffset(years=2)
        sub = df[df['日期'] >= cutoff].copy()

        # 价格偏离60日MA的百分比
        ma60 = sub['收盘'].rolling(60).mean()
        deviation = (sub['收盘'].iloc[-1] - ma60.iloc[-1]) / ma60.iloc[-1]

        # ATR20
        sub['h-l'] = sub['最高'] - sub['最低']
        sub['h-pc'] = (sub['最高'] - sub['收盘'].shift(1)).abs()
        sub['l-pc'] = (sub['最低'] - sub['收盘'].shift(1)).abs()
        sub['tr'] = sub[['h-l', 'h-pc', 'l-pc']].max(axis=1)
        atr20 = sub['tr'].rolling(20).mean().iloc[-1]

        # 偏离越大，width越宽
        dev_factor = max(0.5, min(2.0, abs(deviation) / 0.05))
        width = atr20 * (1 + dev_factor)

        return {'k': 1.2, 'width': width, 'deviation': deviation}
