import pandas as pd
from statistics.base import StatPluginBase

class AtrChannel(StatPluginBase):
    name = 'atr_channel'

    def fit(self, csv_path: str, meta: dict):
        # 1. 插件自己读文件，使用中文列名
        df = pd.read_csv(csv_path)
        
        # 确保日期列正确解析
        df['日期'] = pd.to_datetime(df['日期'])

        # 2. 截最近 2 年真实数据
        cutoff = df['日期'].max() - pd.DateOffset(years=2)
        sub = df[df['日期'] >= cutoff].copy()

        # 3. 用真实高低收算 20 日 ATR
        sub['h-l'] = sub['最高'] - sub['最低']
        sub['h-pc'] = (sub['最高'] - sub['收盘'].shift(1)).abs()
        sub['l-pc'] = (sub['最低'] - sub['收盘'].shift(1)).abs()
        sub['tr'] = sub[['h-l', 'h-pc', 'l-pc']].max(axis=1)
        atr20 = sub['tr'].rolling(20).mean().iloc[-1]

        # 4. 返回与真实波动同量级的宽度
        width = atr20 * 2.0
        return {'width': width, 'k': None, 'direction': 'both', 'confidence': 0.8}