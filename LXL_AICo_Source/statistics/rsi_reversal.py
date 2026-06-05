import pandas as pd
import numpy as np
from statistics.base import StatPluginBase


class RsiReversal(StatPluginBase):
    name = 'rsi_reversal'

    def fit(self, csv_path: str, meta: dict):
        df = pd.read_csv(csv_path)
        df['日期'] = pd.to_datetime(df['日期'])
        cutoff = df['日期'].max() - pd.DateOffset(years=2)
        sub = df[df['日期'] >= cutoff].copy()

        # 计算RSI(14)
        delta = sub['收盘'].diff()
        gain = delta.where(delta > 0, 0).rolling(14).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(14).mean()
        rs = gain / loss
        rsi = 100 - (100 / (1 + rs))
        current_rsi = rsi.iloc[-1]

        # ATR20作为width基准
        sub['h-l'] = sub['最高'] - sub['最低']
        sub['h-pc'] = (sub['最高'] - sub['收盘'].shift(1)).abs()
        sub['l-pc'] = (sub['最低'] - sub['收盘'].shift(1)).abs()
        sub['tr'] = sub[['h-l', 'h-pc', 'l-pc']].max(axis=1)
        atr20 = sub['tr'].rolling(20).mean().iloc[-1]

        # RSI越低width越窄（超卖区信号更敏感）
        rsi_factor = max(0.5, min(2.0, current_rsi / 50.0))
        width = atr20 * rsi_factor

        return {'k': 1.0, 'width': width, 'rsi': current_rsi}
