# parkinson.py
import pandas as pd
from statistics.base import StatPluginBase
import numpy as np

class Parkinson(StatPluginBase):
    name = 'parkinson'

    def fit(self, csv_path: str, meta: dict):
        # 1. 插件自己读文件，使用中文列名
        df = pd.read_csv(csv_path)
        
        # 确保日期列正确解析
        df['日期'] = pd.to_datetime(df['日期'])

        # 2. 截最近 2 年真实数据
        cutoff = df['日期'].max() - pd.DateOffset(years=2)
        sub = df[df['日期'] >= cutoff].copy()

        # 3. Parkinson 估计量：σ_p = sqrt( ln(H/L)^2 / (4*ln2) )
        hp = np.log(sub['最高'] / sub['最低'])**2
        sigma_p = np.sqrt(hp.rolling(20).mean().iloc[-1] / (4 * np.log(2)))
        width = sigma_p * sub['收盘'].iloc[-1]   # 转成价格
        
        return {'k': 1.0, 'width': width, 'direction': 'both', 'confidence': 0.7}