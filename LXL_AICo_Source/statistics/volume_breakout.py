import pandas as pd
import numpy as np
from statistics.base import StatPluginBase


class VolumeBreakout(StatPluginBase):
    name = 'volume_breakout'

    def fit(self, csv_path: str, meta: dict):
        df = pd.read_csv(csv_path)
        df['日期'] = pd.to_datetime(df['日期'])
        cutoff = df['日期'].max() - pd.DateOffset(years=2)
        sub = df[df['日期'] >= cutoff].copy()

        # 成交量放大倍数（当日量/20日均量）
        vol_ma20 = sub['成交量'].rolling(20).mean()
        vol_ratio = sub['成交量'].iloc[-1] / vol_ma20.iloc[-1] if vol_ma20.iloc[-1] > 0 else 1.0

        # ATR20
        sub['h-l'] = sub['最高'] - sub['最低']
        sub['h-pc'] = (sub['最高'] - sub['收盘'].shift(1)).abs()
        sub['l-pc'] = (sub['最低'] - sub['收盘'].shift(1)).abs()
        sub['tr'] = sub[['h-l', 'h-pc', 'l-pc']].max(axis=1)
        atr20 = sub['tr'].rolling(20).mean().iloc[-1]

        # 放量倍数越大，width越宽
        vol_factor = max(0.5, min(2.5, vol_ratio / 2.0))
        width = atr20 * vol_factor

        return {'k': 1.0, 'width': width, 'vol_ratio': vol_ratio}
