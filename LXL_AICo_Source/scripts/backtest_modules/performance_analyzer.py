# [file name]: scripts/backtest_modules/performance_analyzer.py
#[file content begin]
#!/usr/bin/env python3
"""
performance_analyzer.py - 绩效分析器模块
快速评估策略表现，提供多维度绩效指标
"""
import pandas as pd
import numpy as np
from config.logging_config import get_logger
from .strategy_engine import StrategyEngine

logger = get_logger("PerformanceAnalyzer")

class PerformanceAnalyzer:
    def __init__(self):
        self.strategy_engine = StrategyEngine()
        self.metric_weights = {
            'sharpe_ratio': 0.25,
            'max_drawdown': 0.20,
            'profit_factor': 0.15,
            'win_rate': 0.15,
            'trade_frequency': 0.10,
            'consistency': 0.10,
            'recovery_factor': 0.05
        }
    
    def quick_evaluate(self, df, params, market_state=None):
        """快速评估策略表现"""
        # 执行快速回测
        quick_results = self.strategy_engine.quick_score(df, params)
        
        # 计算详细绩效指标
        detailed_metrics = self._calculate_detailed_metrics(quick_results)
        
        # 市场状态调整
        if market_state:
            detailed_metrics = self._adjust_for_market_state(detailed_metrics, market_state)
        
        # 计算综合评分
        composite_score = self._calculate_composite_score(detailed_metrics)
        
        # 生成评估报告
        evaluation_report = {
            'composite_score': composite_score,
            'detailed_metrics': detailed_metrics,
            'market_state': market_state,
            'quick_results': quick_results,
            'strengths': self._identify_strengths(detailed_metrics),
            'weaknesses': self._identify_weaknesses(detailed_metrics),
            'improvement_suggestions': self._generate_improvement_suggestions(detailed_metrics, market_state)
        }
        
        return evaluation_report
    
    def _calculate_detailed_metrics(self, quick_results):
        """计算详细的绩效指标"""
        portfolio_values = quick_results['portfolio_values']
        trades = quick_results['trades']
        
        # 基础指标
        returns = self._calculate_returns(portfolio_values)
        volatility = self._calculate_volatility(returns)
        
        # 风险调整收益指标
        sharpe_ratio = self._calculate_sharpe_ratio(returns, volatility)
        sortino_ratio = self._calculate_sortino_ratio(returns)
        
        # 回撤相关指标
        max_drawdown = self._calculate_max_drawdown(portfolio_values)
        recovery_factor = self._calculate_recovery_factor(portfolio_values, max_drawdown)
        
        # 交易相关指标
        trade_metrics = self._analyze_trades(trades)
        
        # 一致性指标
        consistency = self._calculate_consistency(returns)
        
        metrics = {
            # 收益指标
            'total_return': quick_results['total_return'],
            'annual_return': self._annualize_return(quick_results['total_return'], len(portfolio_values)),
            
            # 风险指标
            'sharpe_ratio': sharpe_ratio,
            'sortino_ratio': sortino_ratio,
            'volatility': volatility,
            
            # 回撤指标
            'max_drawdown': max_drawdown,
            'recovery_factor': recovery_factor,
            'calmar_ratio': self._calculate_calmar_ratio(quick_results['total_return'], max_drawdown),
            
            # 交易指标
            'total_trades': trade_metrics['total_trades'],
            'win_rate': trade_metrics['win_rate'],
            'profit_factor': trade_metrics['profit_factor'],
            'avg_trade_return': trade_metrics['avg_trade_return'],
            'trade_frequency': trade_metrics['trade_frequency'],
            
            # 稳定性指标
            'consistency': consistency,
            'monthly_positive': self._calculate_monthly_positive_ratio(portfolio_values)
        }
        
        return metrics
    
    def _calculate_returns(self, portfolio_values):
        """计算收益率序列"""
        portfolio_series = pd.Series(portfolio_values)
        returns = portfolio_series.pct_change().dropna()
        return returns
    
    def _calculate_volatility(self, returns):
        """计算波动率（年化）"""
        if len(returns) == 0:
            return 0
        return returns.std() * np.sqrt(250)
    
    def _calculate_sharpe_ratio(self, returns, volatility, risk_free_rate=0.02):
        """计算夏普比率"""
        if len(returns) == 0 or volatility == 0:
            return 0
        
        excess_returns = returns.mean() * 250 - risk_free_rate
        return excess_returns / volatility
    
    def _calculate_sortino_ratio(self, returns, risk_free_rate=0.02):
        """计算索提诺比率"""
        if len(returns) == 0:
            return 0
        
        # 只考虑下行波动率
        downside_returns = returns[returns < 0]
        if len(downside_returns) == 0:
            downside_vol = 0
        else:
            downside_vol = downside_returns.std() * np.sqrt(250)
        
        excess_return = returns.mean() * 250 - risk_free_rate
        
        if downside_vol == 0:
            return 0
        return excess_return / downside_vol
    
    def _calculate_max_drawdown(self, portfolio_values):
        """计算最大回撤"""
        portfolio_series = pd.Series(portfolio_values)
        cumulative_max = portfolio_series.expanding().max()
        drawdowns = (portfolio_series - cumulative_max) / cumulative_max
        return drawdowns.min() * 100  # 百分比
    
    def _calculate_recovery_factor(self, portfolio_values, max_drawdown):
        """计算恢复因子"""
        if max_drawdown == 0:
            return 0
        
        total_return = (portfolio_values[-1] / portfolio_values[0] - 1) * 100
        return abs(total_return / max_drawdown) if max_drawdown != 0 else 0
    
    def _calculate_calmar_ratio(self, total_return, max_drawdown):
        """计算Calmar比率"""
        if max_drawdown == 0:
            return 0
        return total_return / abs(max_drawdown)
    
    def _analyze_trades(self, trades):
        """分析交易记录"""
        if not trades:
            return {
                'total_trades': 0,
                'win_rate': 0,
                'profit_factor': 0,
                'avg_trade_return': 0,
                'trade_frequency': 0
            }
        
        # 简化分析：假设每个交易都有明确的盈亏
        # 在实际中需要更复杂的交易分析
        total_trades = len([t for t in trades if t['type'] in ['buy', 'sell']])
        
        # 估算胜率和盈亏比（简化版）
        # 在实际系统中需要真实的交易盈亏记录
        estimated_win_rate = 0.5  # 默认50%
        estimated_profit_factor = 1.2  # 默认1.2
        
        return {
            'total_trades': total_trades,
            'win_rate': estimated_win_rate,
            'profit_factor': estimated_profit_factor,
            'avg_trade_return': 0.01,  # 默认1%
            'trade_frequency': total_trades / 250  # 年化交易频率
        }
    
    def _calculate_consistency(self, returns):
        """计算收益一致性"""
        if len(returns) < 10:
            return 0.5
        
        # 计算正收益月份比例
        positive_periods = (returns > 0).sum()
        total_periods = len(returns)
        
        return positive_periods / total_periods
    
    def _calculate_monthly_positive_ratio(self, portfolio_values):
        """计算月度正收益比例（简化版）"""
        # 在实际系统中需要按月份聚合收益
        return 0.6  # 默认60%
    
    def _annualize_return(self, total_return, periods):
        """年化收益率"""
        if periods <= 0:
            return 0
        return (1 + total_return) ** (250 / periods) - 1
    
    def _adjust_for_market_state(self, metrics, market_state):
        """根据市场状态调整指标评估"""
        adjusted_metrics = metrics.copy()
        
        # 不同市场状态下的期望表现不同
        state_expectations = {
            'trending_up': {
                'sharpe_ratio': 1.0,  # 趋势市中期望更高的夏普
                'trade_frequency': 0.3  # 趋势市中交易频率可能较低
            },
            'trending_down': {
                'sharpe_ratio': 0.5,  # 下跌市中夏普可能较低
                'max_drawdown': -15   # 下跌市中回撤可能较大
            },
            'consolidation': {
                'sharpe_ratio': 0.8,
                'trade_frequency': 0.8  # 震荡市中交易频率较高
            },
            'high_volatility': {
                'sharpe_ratio': 0.6,
                'volatility': 0.3  # 高波动市中波动率较高是正常的
            }
        }
        
        if market_state in state_expectations:
            expectations = state_expectations[market_state]
            # 这里可以根据期望值调整评分权重或阈值
            # 目前先记录期望值，供后续分析使用
            adjusted_metrics['state_expectations'] = expectations
        
        return adjusted_metrics
    
    def _calculate_composite_score(self, metrics):
        """计算综合评分"""
        score_components = {}
        
        # 夏普比率评分
        sharpe_score = self._normalize_score(metrics['sharpe_ratio'], -1, 2)
        score_components['sharpe_ratio'] = sharpe_score
        
        # 最大回撤评分（回撤越小越好）
        drawdown_score = 1 - min(abs(metrics['max_drawdown']) / 50, 1)  # 假设最大回撤不超过50%
        score_components['max_drawdown'] = drawdown_score
        
        # 盈利因子评分
        profit_factor_score = self._normalize_score(metrics['profit_factor'], 0.5, 3)
        score_components['profit_factor'] = profit_factor_score
        
        # 胜率评分
        win_rate_score = self._normalize_score(metrics['win_rate'], 0.3, 0.8)
        score_components['win_rate'] = win_rate_score
        
        # 交易频率评分（适中为好）
        trade_freq = metrics['trade_frequency']
        if trade_freq < 0.1:
            freq_score = 0.3  # 交易太少
        elif trade_freq > 2:
            freq_score = 0.5  # 交易太频繁
        else:
            freq_score = 0.8  # 适中
        score_components['trade_frequency'] = freq_score
        
        # 一致性评分
        consistency_score = metrics['consistency']
        score_components['consistency'] = consistency_score
        
        # 恢复因子评分
        recovery_score = self._normalize_score(metrics['recovery_factor'], 0, 5)
        score_components['recovery_factor'] = recovery_score
        
        # 加权综合评分
        composite_score = 0
        for metric, weight in self.metric_weights.items():
            if metric in score_components:
                composite_score += score_components[metric] * weight
        
        return composite_score
    
    def _normalize_score(self, value, min_val, max_val):
        """将指标值归一化到0-1范围"""
        if max_val == min_val:
            return 0.5
        normalized = (value - min_val) / (max_val - min_val)
        return max(0, min(1, normalized))
    
    def _identify_strengths(self, metrics):
        """识别策略优势"""
        strengths = []
        
        if metrics['sharpe_ratio'] > 1.0:
            strengths.append('优秀的风险调整收益')
        elif metrics['sharpe_ratio'] > 0.5:
            strengths.append('良好的风险收益比')
        
        if metrics['max_drawdown'] > -10:  # 回撤小于10%
            strengths.append('优秀的回撤控制')
        elif metrics['max_drawdown'] > -20:
            strengths.append('良好的风险控制')
        
        if metrics['profit_factor'] > 2.0:
            strengths.append('高效的盈利能力')
        elif metrics['profit_factor'] > 1.5:
            strengths.append('稳定的盈利表现')
        
        if metrics['win_rate'] > 0.6:
            strengths.append('高胜率交易')
        
        if metrics['consistency'] > 0.7:
            strengths.append('稳定的收益表现')
        
        return strengths if strengths else ['无明显突出优势']
    
    def _identify_weaknesses(self, metrics):
        """识别策略弱点"""
        weaknesses = []
        
        if metrics['sharpe_ratio'] < 0:
            weaknesses.append('风险调整收益为负')
        elif metrics['sharpe_ratio'] < 0.3:
            weaknesses.append('风险收益比较差')
        
        if metrics['max_drawdown'] < -30:
            weaknesses.append('回撤过大')
        elif metrics['max_drawdown'] < -20:
            weaknesses.append('回撤控制需改进')
        
        if metrics['profit_factor'] < 1.0:
            weaknesses.append('整体亏损')
        elif metrics['profit_factor'] < 1.2:
            weaknesses.append('盈利效率较低')
        
        if metrics['win_rate'] < 0.4:
            weaknesses.append('胜率偏低')
        
        if metrics['consistency'] < 0.4:
            weaknesses.append('收益不稳定')
        
        if metrics['total_trades'] < 5:
            weaknesses.append('交易样本不足')
        
        return weaknesses if weaknesses else ['无明显重大弱点']
    
    def _generate_improvement_suggestions(self, metrics, market_state):
        """生成改进建议"""
        suggestions = []
        
        # 基于弱点的改进建议
        weaknesses = self._identify_weaknesses(metrics)
        
        if '风险调整收益为负' in weaknesses or '风险收益比较差' in weaknesses:
            suggestions.append('考虑调整参数以减少波动性或提高收益')
        
        if '回撤过大' in weaknesses or '回撤控制需改进' in weaknesses:
            suggestions.append('建议加强止损机制或降低仓位')
        
        if '整体亏损' in weaknesses or '盈利效率较低' in weaknesses:
            suggestions.append('需要优化入场出场时机，减少亏损交易')
        
        if '胜率偏低' in weaknesses:
            suggestions.append('考虑提高信号质量，减少假信号')
        
        if '交易样本不足' in weaknesses:
            suggestions.append('需要更长时间的数据验证策略稳定性')
        
        # 基于市场状态的建议
        if market_state:
            if market_state == 'high_volatility':
                suggestions.append('高波动市场中建议降低仓位，严格止损')
            elif market_state == 'consolidation':
                suggestions.append('震荡市中可考虑缩小交易区间，增加交易频率')
            elif 'trending' in market_state:
                suggestions.append('趋势市中可考虑放宽止盈，让利润奔跑')
        
        return suggestions if suggestions else ['策略表现良好，继续保持当前参数']
    
    def compare_strategies(self, strategy_results):
        """比较多个策略的表现"""
        comparisons = {}
        
        for strategy_name, results in strategy_results.items():
            evaluation = self.quick_evaluate(
                results['data'], 
                results['params'], 
                results.get('market_state')
            )
            comparisons[strategy_name] = evaluation
        
        # 排序并找出最佳策略
        ranked_strategies = sorted(
            comparisons.items(), 
            key=lambda x: x[1]['composite_score'], 
            reverse=True
        )
        
        return {
            'ranked_strategies': ranked_strategies,
            'best_strategy': ranked_strategies[0] if ranked_strategies else None,
            'detailed_comparisons': comparisons
        }
#[file content end]