# Quantile_Range.py
import pandas as pd
from statistics.base import StatPluginBase

class QuantileRange(StatPluginBase):
    name = 'quantile_range'

    def fit(self, csv_path: str, meta: dict):
        # 1. 插件自己读文件，使用中文列名
        df = pd.read_csv(csv_path)
        
        # 确保日期列正确解析
        df['日期'] = pd.to_datetime(df['日期'])

        # 2. 截最近 2 年真实数据
        cutoff = df['日期'].max() - pd.DateOffset(years=2)
        sub = df[df['日期'] >= cutoff].copy()

        # 3. 用 20 日最高/最低 分位数差当宽度
        c = sub['收盘']
        hi = c.rolling(20).max().iloc[-1]
        lo = c.rolling(20).min().iloc[-1]
        
        return {'k': 0.25, 'width': hi - lo, 'direction': 'both', 'confidence': 0.7}