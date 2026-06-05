import pandas as pd
import numpy as np
from statistics.base import StatPluginBase


class DualThrust(StatPluginBase):
    name = 'dual_thrust'

    def fit(self, csv_path: str, meta: dict):
        df = pd.read_csv(csv_path)
        df['日期'] = pd.to_datetime(df['日期'])
        cutoff = df['日期'].max() - pd.DateOffset(years=2)
        sub = df[df['日期'] >= cutoff].copy()

        # Dual Thrust: 基于近N日高低开收
        N = min(5, len(sub))
        recent = sub.tail(N)

        hh = recent['最高'].max()
        hc = recent['收盘'].max()
        lc = recent['收盘'].min()
        ll = recent['最低'].min()

        range_val = max(hh - lc, hc - ll)

        k = 0.5
        last_close = sub['收盘'].iloc[-1]
        upper = last_close + k * range_val
        lower = last_close - k * range_val
        width = upper - lower

        return {'k': 0.5, 'width': width, 'range_val': range_val}
