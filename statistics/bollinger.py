# bollinger.py
import pandas as pd
from statistics.base import StatPluginBase

class Bollinger(StatPluginBase):
    name = 'bollinger'

    def fit(self, csv_path: str, meta: dict):
        # 1. 插件自己读文件，使用中文列名
        df = pd.read_csv(csv_path)
        
        # 确保日期列正确解析
        df['日期'] = pd.to_datetime(df['日期'])

        # 2. 截最近 2 年真实数据
        cutoff = df['日期'].max() - pd.DateOffset(years=2)
        sub = df[df['日期'] >= cutoff].copy()

        # 3. 计算布林带宽度
        close = sub['收盘']
        mean20 = close.rolling(20).mean().iloc[-1]
        std20 = close.rolling(20).std().iloc[-1]
        
        # 把 2σ 当宽度 → 引擎用 k=1 即可
        width = 2 * std20
        return {'k': 1.0, 'width': width, 'direction': 'both', 'confidence': 0.75}