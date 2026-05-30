# [file name]: scripts/backtest_modules/online_learning_module.py
#!/usr/bin/env python3
"""
online_learning_module.py - 在线学习协调模块
保持backtest主流程不变，通过此模块调用在线学习能力
"""
import pandas as pd
import numpy as np
from config.logging_config import get_logger
from .parameter_optimizer import ParameterOptimizer
from .market_state_detector import MarketStateDetector
from .performance_analyzer import PerformanceAnalyzer

logger = get_logger("OnlineLearning")

class OnlineLearningModule:
    def __init__(self, learning_strategy='adaptive'):
        self.learning_strategy = learning_strategy
        self.parameter_optimizer = ParameterOptimizer()
        self.market_detector = MarketStateDetector()
        self.performance_analyzer = PerformanceAnalyzer()
        
        # 学习历史记录
        self.learning_history = {}
        self.regime_knowledge_base = {}
    
    def optimize_plugin_parameters(self, plugin, data, symbol, max_iterations=8):
        """优化插件参数 - 主入口函数"""
        logger.info(f"开始在线学习优化: {plugin.name}")
        
        # 检测当前市场状态
        market_state = self.market_detector.analyze_market_state(data)
        logger.info(f"检测到市场状态: {market_state}")
        
        # 执行参数优化循环
        best_params, optimization_report = self._run_optimization_loop(
            plugin, data, symbol, market_state, max_iterations
        )
        
        # 更新知识库
        self._update_knowledge_base(symbol, plugin.name, market_state, best_params, optimization_report)
        
        return best_params, optimization_report
    
    def _run_optimization_loop(self, plugin, data, symbol, market_state, max_iterations):
        """运行优化循环"""
        optimization_steps = []
        best_params = None
        best_score = -999
        
        # 初始参数
        current_params = plugin.fit(data, {"symbol": symbol})
        
        for iteration in range(max_iterations):
            # 基于市场状态调整参数
            regime_aware_params = self._adapt_parameters_to_regime(
                current_params, market_state, symbol, plugin.name
            )
            
            # 快速评估
            evaluation_result = self.performance_analyzer.quick_evaluate(
                data, regime_aware_params, market_state
            )
            
            # 记录优化步骤
            step_info = {
                'iteration': iteration,
                'params': regime_aware_params.copy(),
                'score': evaluation_result['composite_score'],
                'market_state': market_state,
                'metrics': evaluation_result
            }
            optimization_steps.append(step_info)
            
            # 更新最佳参数
            if evaluation_result['composite_score'] > best_score:
                best_score = evaluation_result['composite_score']
                best_params = regime_aware_params.copy()
            
            # 在线学习：基于反馈生成新参数
            current_params = self.parameter_optimizer.generate_next_parameters(
                current_params=current_params,
                performance_feedback=evaluation_result,
                market_state=market_state,
                iteration=iteration,
                history=optimization_steps
            )
            
            # 收敛检查
            if self._check_convergence(optimization_steps):
                logger.info(f"优化收敛于第 {iteration} 轮迭代")
                break
        
        # 生成优化报告
        optimization_report = {
            'best_params': best_params,
            'best_score': best_score,
            'total_iterations': len(optimization_steps),
            'optimization_steps': optimization_steps,
            'market_state': market_state,
            'convergence_reason': 'max_iterations' if len(optimization_steps) == max_iterations else 'converged'
        }
        
        return best_params, optimization_report
    
    def _adapt_parameters_to_regime(self, base_params, market_state, symbol, plugin_name):
        """基于市场状态调整参数"""
        # 检查知识库中是否有该市场状态的历史经验
        knowledge_key = f"{symbol}_{plugin_name}_{market_state}"
        
        if knowledge_key in self.regime_knowledge_base:
            # 融合历史经验
            historical_best = self.regime_knowledge_base[knowledge_key]
            blended_params = self._blend_parameters(base_params, historical_best)
            return blended_params
        else:
            # 基于市场状态特性调整参数
            return self._adjust_params_for_regime(base_params, market_state)
    
    def _blend_parameters(self, current_params, historical_params, blend_ratio=0.3):
        """融合当前参数和历史最佳参数"""
        blended = current_params.copy()
        for key in historical_params:
            if key in current_params and isinstance(current_params[key], (int, float)):
                blended[key] = (current_params[key] * (1 - blend_ratio) + 
                               historical_params[key] * blend_ratio)
        return blended
    
    def _adjust_params_for_regime(self, params, market_state):
        """根据市场状态调整参数"""
        adjusted = params.copy()
        
        if market_state == 'trending_up':
            # 趋势市：放宽止盈，收紧止损
            if 'k' in adjusted:
                adjusted['k'] = adjusted.get('k', 2.0) * 1.2
        elif market_state == 'trending_down':
            # 下跌市：收紧所有阈值
            if 'k' in adjusted:
                adjusted['k'] = adjusted.get('k', 2.0) * 0.8
        elif market_state == 'consolidation':
            # 震荡市：缩小通道，增加交易频率
            if 'k' in adjusted:
                adjusted['k'] = adjusted.get('k', 2.0) * 0.7
        
        return adjusted
    
    def _check_convergence(self, optimization_steps, window=3, threshold=0.01):
        """检查优化是否收敛"""
        if len(optimization_steps) < window:
            return False
        
        recent_scores = [step['score'] for step in optimization_steps[-window:]]
        score_std = np.std(recent_scores)
        
        return score_std < threshold
    
    def _update_knowledge_base(self, symbol, plugin_name, market_state, best_params, report):
        """更新知识库"""
        knowledge_key = f"{symbol}_{plugin_name}_{market_state}"
        self.regime_knowledge_base[knowledge_key] = best_params
        
        # 记录学习历史
        if symbol not in self.learning_history:
            self.learning_history[symbol] = {}
        
        self.learning_history[symbol][plugin_name] = {
            'timestamp': pd.Timestamp.now(),
            'market_state': market_state,
            'best_params': best_params,
            'final_score': report['best_score'],
            'iterations': report['total_iterations']
        }
    
    def get_learning_summary(self, symbol=None):
        """获取学习总结"""
        if symbol:
            return self.learning_history.get(symbol, {})
        else:
            return self.learning_history
    
    def reset_learning(self, symbol=None):
        """重置学习状态"""
        if symbol:
            if symbol in self.learning_history:
                del self.learning_history[symbol]
            # 删除相关的知识库条目
            keys_to_delete = [k for k in self.regime_knowledge_base.keys() if k.startswith(symbol)]
            for key in keys_to_delete:
                del self.regime_knowledge_base[key]
        else:
            self.learning_history.clear()
            self.regime_knowledge_base.clear()
        
        logger.info("学习状态已重置")