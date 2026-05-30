# statistics/kdj_plugin.py
import pandas as pd
import numpy as np
from statistics.base import StatPluginBase

class KDJPlugin(StatPluginBase):
    """KDJ指标策略插件"""
    
    def __init__(self):
        self.name = "kdj"
    
    def fit(self, csv_path: str, meta: dict) -> dict:
        """计算KDJ参数 - 返回简单的参数集"""
        return {
            'strategy': 'kdj',
            'n': 9,           # KDJ周期
            'k_period': 3,    # K值平滑周期
            'd_period': 3,    # D值平滑周期
            'buy_threshold': 20,  # 买入阈值（超卖区）
            'sell_threshold': 80   # 卖出阈值（超买区）
        }
    
    def is_better(self, df_daily, old_params):
        """简单的改进判断"""
        if old_params is None:
            return True
        return len(df_daily) > old_params.get('fit_length', 0)