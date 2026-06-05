# [file name]: scripts/backtest_modules/parameter_optimizer.py
#!/usr/bin/env python3
"""
parameter_optimizer.py - 参数优化器模块
负责基于绩效反馈生成新的参数
"""
import numpy as np
import random
from config.logging_config import get_logger

logger = get_logger("ParameterOptimizer")

class ParameterOptimizer:
    def __init__(self):
        self.learning_strategies = {
            'gradient': self._gradient_based_optimization,
            'exploration': self._exploration_based_optimization,
            'refinement': self._refinement_based_optimization
        }
    
    def generate_next_parameters(self, current_params, performance_feedback, 
                               market_state, iteration, history):
        """生成下一组参数"""
        # 根据迭代阶段选择优化策略
        if iteration == 0:
            strategy = 'exploration'
        elif iteration < 3:
            strategy = 'gradient'
        else:
            strategy = 'refinement'
        
        # 执行优化策略
        new_params = self.learning_strategies[strategy](
            current_params, performance_feedback, market_state, iteration, history
        )
        
        # 确保参数合理性
        new_params = self._ensure_parameter_sanity(new_params)
        
        logger.debug(f"迭代 {iteration}: {strategy}策略生成新参数")
        
        return new_params
    
    def _gradient_based_optimization(self, current_params, feedback, market_state, iteration, history):
        """基于梯度的优化"""
        new_params = current_params.copy()
        
        # 计算参数调整方向
        gradient = self._estimate_parameter_gradient(history)
        
        # 动态学习率
        learning_rate = self._calculate_learning_rate(iteration, feedback)
        
        # 应用梯度更新
        for param_name in gradient:
            if param_name in new_params and isinstance(new_params[param_name], (int, float)):
                adjustment = learning_rate * gradient[param_name]
                new_params[param_name] += adjustment
        
        return new_params
    
    def _exploration_based_optimization(self, current_params, feedback, market_state, iteration, history):
        """探索式优化"""
        new_params = current_params.copy()
        
        # 更大的随机探索
        exploration_strength = 0.3 - iteration * 0.05  # 随着迭代减小探索强度
        
        for param_name in new_params:
            if isinstance(new_params[param_name], (int, float)):
                # 在当前值附近随机探索
                current_value = new_params[param_name]
                random_factor = 1 + random.uniform(-exploration_strength, exploration_strength)
                new_params[param_name] = current_value * random_factor
        
        return new_params
    
    def _refinement_based_optimization(self, current_params, feedback, market_state, iteration, history):
        """精细化优化"""
        new_params = current_params.copy()
        
        # 基于历史表现进行精细调整
        if len(history) >= 2:
            recent_improvement = self._analyze_recent_trend(history)
            
            for param_name in new_params:
                if (isinstance(new_params[param_name], (int, float)) and 
                    param_name in recent_improvement):
                    
                    # 沿着改善方向微调
                    improvement_direction = np.sign(recent_improvement[param_name])
                    refinement_step = 0.05 * improvement_direction
                    new_params[param_name] *= (1 + refinement_step)
        
        return new_params
    
    def _estimate_parameter_gradient(self, history):
        """估计参数梯度"""
        if len(history) < 2:
            return {}
        
        # 使用最近几次迭代计算梯度
        recent_steps = history[-3:] if len(history) >= 3 else history
        
        gradients = {}
        param_names = [key for key in recent_steps[0]['params'] 
                      if isinstance(recent_steps[0]['params'][key], (int, float))]
        
        for param_name in param_names:
            param_changes = []
            score_changes = []
            
            for i in range(1, len(recent_steps)):
                param_change = (recent_steps[i]['params'][param_name] - 
                               recent_steps[i-1]['params'][param_name])
                score_change = recent_steps[i]['score'] - recent_steps[i-1]['score']
                
                if abs(param_change) > 1e-6:  # 避免除零
                    param_changes.append(param_change)
                    score_changes.append(score_change)
            
            if param_changes:
                # 简单梯度估计
                gradient = np.mean([sc/pc for pc, sc in zip(param_changes, score_changes)])
                gradients[param_name] = gradient
        
        return gradients
    
    def _calculate_learning_rate(self, iteration, feedback):
        """计算动态学习率"""
        base_rate = 0.1
        decay_factor = max(0.5, 1 - iteration * 0.1)  # 随着迭代衰减
        
        # 根据绩效调整学习率
        performance_factor = 1.0
        if feedback['composite_score'] < 0:
            performance_factor = 1.5  # 表现差时加快学习
        elif feedback['composite_score'] > 0.5:
            performance_factor = 0.7  # 表现好时减慢学习
        
        return base_rate * decay_factor * performance_factor
    
    def _analyze_recent_trend(self, history):
        """分析近期参数变化趋势"""
        if len(history) < 2:
            return {}
        
        recent = history[-2:]
        param_trends = {}
        
        for param_name in recent[1]['params']:
            if isinstance(recent[1]['params'][param_name], (int, float)):
                old_val = recent[0]['params'][param_name]
                new_val = recent[1]['params'][param_name]
                old_score = recent[0]['score']
                new_score = recent[1]['score']
                
                if new_score > old_score and abs(new_val - old_val) > 1e-6:
                    # 记录改善方向
                    param_trends[param_name] = new_val - old_val
        
        return param_trends
    
    def _ensure_parameter_sanity(self, params):
        """确保参数合理性"""
        sane_params = params.copy()
        
        # k值范围限制
        if 'k' in sane_params:
            sane_params['k'] = max(0.5, min(sane_params['k'], 5.0))
        
        # width值范围限制  
        if 'width' in sane_params and sane_params['width'] is not None:
            sane_params['width'] = max(0.001, sane_params['width'])
        
        return sane_params