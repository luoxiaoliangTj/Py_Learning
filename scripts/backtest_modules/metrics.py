# [file name]: scripts/backtest_modules/metrics.py
#!/usr/bin/env python3
"""
metrics.py - 指标计算模块（标准金融指标）
包含夏普比率、最大回撤等标准计算
"""
import pandas as pd
import numpy as np
from config.logging_config import get_logger

logger = get_logger("Metrics")

class MetricsCalculator:
    def __init__(self, risk_free_rate=0.0):
        self.risk_free_rate = risk_free_rate  # 无风险利率
    
    def calculate_sharpe(self, portfolio_values, period='daily'):
        """计算标准夏普比率"""
        if len(portfolio_values) < 2:
            return 0.0
        
        portfolio_series = pd.Series(portfolio_values)
        returns = portfolio_series.pct_change().dropna()
        
        if len(returns) < 2:
            return 0.0
        
        # 标准夏普比率计算
        excess_returns = returns - self.risk_free_rate / 250  # 日化无风险利率
        
        if excess_returns.std() == 0:
            return 0.0
        
        # 根据周期调整年化因子
        if period == 'daily':
            annual_factor = np.sqrt(250)
        elif period == 'weekly':
            annual_factor = np.sqrt(52)
        else:
            annual_factor = 1
        
        sharpe = excess_returns.mean() / excess_returns.std() * annual_factor
        
        # 处理异常值
        if pd.isna(sharpe) or np.isinf(sharpe):
            return 0.0
            
        return sharpe
    
    def calculate_max_drawdown(self, portfolio_values):
        """计算最大回撤"""
        if len(portfolio_values) < 2:
            return 0.0
        
        portfolio_series = pd.Series(portfolio_values)
        cumulative_max = portfolio_series.expanding().max()
        drawdown = (portfolio_series - cumulative_max) / cumulative_max
        
        return drawdown.min() * 100  # 返回百分比
    
    def calculate_total_return(self, portfolio_values):
        """计算总收益率"""
        if len(portfolio_values) < 2:
            return 0.0
        return (portfolio_values[-1] / portfolio_values[0] - 1) * 100
    
    def calculate_volatility(self, portfolio_values, period='daily'):
        """计算波动率"""
        if len(portfolio_values) < 2:
            return 0.0
        
        portfolio_series = pd.Series(portfolio_values)
        returns = portfolio_series.pct_change().dropna()
        
        if period == 'daily':
            annual_factor = np.sqrt(250)
        elif period == 'weekly':
            annual_factor = np.sqrt(52)
        else:
            annual_factor = 1
        
        return returns.std() * annual_factor * 100  # 年化波动率百分比
    
    def calculate_trade_metrics(self, trades):
        """计算交易相关指标"""
        if not trades:
            return {
                'total_trades': 0,
                'win_rate': 0,
                'avg_profit': 0,
                'profit_factor': 0
            }
        
        # 这里可以扩展更复杂的交易分析
        total_trades = len(trades)
        
        return {
            'total_trades': total_trades,
            'win_rate': 0.5,  # 简化，实际需要交易记录计算
            'avg_profit': 0,
            'profit_factor': 1.0
        }
    
    def calculate_all_metrics(self, portfolio_values, trades=None):
        """计算所有核心指标"""
        return {
            'sharpe_ratio': self.calculate_sharpe(portfolio_values),
            'max_drawdown': self.calculate_max_drawdown(portfolio_values),
            'total_return': self.calculate_total_return(portfolio_values),
            'volatility': self.calculate_volatility(portfolio_values),
            'trade_metrics': self.calculate_trade_metrics(trades or [])
        }