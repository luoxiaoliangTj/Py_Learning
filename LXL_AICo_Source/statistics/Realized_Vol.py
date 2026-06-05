# Realized_Vol.py
import pandas as pd
from statistics.base import StatPluginBase
import numpy as np

class RealizedVol(StatPluginBase):
    name = 'realized_vol'

    def fit(self, csv_path: str, meta: dict):
        # 1. 插件自己读文件，使用中文列名
        df = pd.read_csv(csv_path)
        
        # 确保日期列正确解析
        df['日期'] = pd.to_datetime(df['日期'])

        # 2. 截最近 2 年真实数据
        cutoff = df['日期'].max() - pd.DateOffset(years=2)
        sub = df[df['日期'] >= cutoff].copy()

        # 3. 计算已实现波动率
        ret = np.log(sub['收盘'] / sub['收盘'].shift(1)).dropna()
        rv = ret.rolling(20).std().iloc[-1] * np.sqrt(250)   # 年化波动
        width = rv * sub['收盘'].iloc[-1]
        
        return {'k': 1.0, 'width': width, 'direction': 'both', 'confidence': 0.75}