# [file name]: scripts/backtest_modules/data_driven_evaluator.py
#!/usr/bin/env python3
"""
data_driven_evaluator.py - 基于真实数据的动态评估器
移除所有硬性限制，完全基于历史数据统计特性
"""
import pandas as pd
import numpy as np
from config.logging_config import get_logger

logger = get_logger("DataDrivenEvaluator")

class DataDrivenEvaluator:
    def __init__(self):
        self.historical_baselines = {}
    
    def calculate_market_baseline(self, df, symbol):
        """基于股票历史数据计算市场基准"""
        if len(df) < 100:
            raise ValueError(
                f"数据不足：当前数据仅{len(df)}条交易记录，不足以进行可靠的回测评估。\n"
                f"请下载至少1年的交易记录（约250个交易日）后重试。")
        
        # 计算历史收益率和波动率
        returns = df['收盘'].pct_change().dropna()
        volatility = returns.std() * np.sqrt(250)
        avg_return = returns.mean() * 250
        
        # 计算历史夏普比率（年化）
        if volatility > 0:
            historical_sharpe = avg_return / volatility
        else:
            historical_sharpe = 0
        
        # 分析交易频率特征
        price_changes = df['收盘'].diff()
        significant_moves = (price_changes.abs() > price_changes.std() * 0.5).sum()
        expected_trades = max(1, significant_moves // 10)  # 基于价格显著变动次数
        
        baseline = {
            'historical_sharpe': historical_sharpe,
            'volatility': volatility,
            'expected_trades': expected_trades,
            'avg_daily_range': (df['最高'] - df['最低']).mean() / df['收盘'].mean(),
            'trend_strength': self._calculate_trend_strength(df)
        }
        
        self.historical_baselines[symbol] = baseline
        logger.info(f"市场基准计算完成: 历史夏普={historical_sharpe:.3f}, 预期交易={expected_trades}")
        
        return baseline
    
    def _calculate_trend_strength(self, df):
        """计算历史趋势强度"""
        if len(df) < 60:
            return 0
        
        short_trend = df['收盘'].iloc[-20] / df['收盘'].iloc[-60] - 1
        medium_trend = df['收盘'].iloc[-1] / df['收盘'].iloc[-120] - 1 if len(df) >= 120 else short_trend
        
        return (short_trend + medium_trend) / 2
    
    
    def evaluate_plugin_performance(self, sharpe, n_trades, total_return, max_drawdown, baseline):
        """基于市场基准评估插件表现 - [修复]亏损策略自然低分"""

        # 动态阈值：基于历史表现
        min_acceptable_sharpe = baseline['historical_sharpe'] * 0.7
        min_acceptable_trades = max(1, baseline['expected_trades'] // 2)

        # [修复] 归一化不截断到0，负夏普/负收益产生负分贡献
        sharpe_score = self._normalize_score(sharpe, -1, 3)
        trade_frequency_score = min(n_trades / baseline['expected_trades'], 2.0)
        return_score = self._normalize_score(total_return, -0.3, 1.0)
        drawdown_score = 1.0 - min(abs(max_drawdown) / 0.5, 1.0)

        # [修复] 夏普为负时加大惩罚：夏普分再乘以0.5
        if sharpe < 0:
            sharpe_score *= 0.5

        # [修复] 综合评分权重 - 提升夏普权重，降低交易频率权重
        weights = {
            'sharpe': 0.45,
            'trade_frequency': 0.10,
            'return': 0.35,
            'drawdown': 0.10
        }

        composite_score = (
            sharpe_score * weights['sharpe'] +
            trade_frequency_score * weights['trade_frequency'] +
            return_score * weights['return'] +
            drawdown_score * weights['drawdown']
        )

        # [修复] 亏损策略总分不高于0.3
        if sharpe <= 0 and total_return <= 0:
            composite_score = min(composite_score, 0.3)

        is_acceptable = True
        evaluation = {
            'is_acceptable': is_acceptable,
            'composite_score': composite_score,
            'sharpe_score': sharpe_score,
            'trade_score': trade_frequency_score,
            'return_score': return_score,
            'drawdown_score': drawdown_score,
            'min_acceptable_sharpe': min_acceptable_sharpe,
            'min_acceptable_trades': min_acceptable_trades
        }
        return evaluation

    def _normalize_score(self, value, min_val, max_val):
        """将值归一化 - 允许负值，不截断到0"""
        normalized = (value - min_val) / (max_val - min_val)
        return max(-1.0, min(1.0, normalized))  # 范围-1~1
    
    def suggest_parameter_adjustment(self, current_params, evaluation, baseline):
        """基于评估结果建议参数调整"""
        suggestions = []
        
        if evaluation['sharpe_score'] < 0.3:
            suggestions.append("考虑降低通道宽度以减少假信号")
        
        if evaluation['trade_score'] < 0.3:
            suggestions.append("考虑放宽交易阈值以增加交易机会")
            
        if evaluation['return_score'] < 0.3:
            suggestions.append("考虑调整止盈止损比例")
            
        if evaluation['drawdown_score'] < 0.3:
            suggestions.append("考虑加强风险管理，降低仓位")
        
        return suggestions
    
    def find_best_plugin(self, plugin_results, baseline):
        """从多个插件结果中选择最佳的一个"""
        if not plugin_results:
            return None, "没有可用的插件结果"
        
        best_plugin = None
        best_score = -999
        
        for plugin_name, result in plugin_results.items():
            evaluation = self.evaluate_plugin_performance(
                result['sharpe'], 
                result['n_trades'],
                result['total_return'],
                result['max_drawdown'],
                baseline
            )
            
            if evaluation['composite_score'] > best_score:
                best_score = evaluation['composite_score']
                best_plugin = {
                    'name': plugin_name,
                    'params': result['params'],
                    'evaluation': evaluation,
                    'raw_results': result
                }
        
        return best_plugin, "找到最佳插件"