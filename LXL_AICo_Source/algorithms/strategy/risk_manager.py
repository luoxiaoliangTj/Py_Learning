# algorithms/strategy/risk_manager.py
"""
风险管理模块
"""
from datetime import datetime
from typing import Dict, List, Optional
import numpy as np


class RiskManager:
    """实时风险管理"""
    
    def __init__(self):
        self.risk_history = []
        self.max_drawdown = 0
        self.consecutive_losses = 0
        self.risk_level = 'medium'  # low, medium, high
        
    def assess_market_risk(self, market_conditions: Dict) -> Dict:
        """评估市场风险"""
        volatility = market_conditions.get('real_time_volatility', 0)
        price_spike = market_conditions.get('price_spike', 0)
        sentiment = market_conditions.get('market_sentiment', 0)
        
        # 计算风险评分 (0-1)
        volatility_score = min(1.0, volatility / 0.05)  # 5%波动率为高风险
        spike_score = min(1.0, price_spike / 3)  # 3个标准差为高风险
        sentiment_score = abs(sentiment) * 2  # 极端情绪为高风险
        
        risk_score = max(volatility_score, spike_score, sentiment_score)
        
        # 确定风险等级
        if risk_score < 0.3:
            risk_level = 'low'
        elif risk_score < 0.6:
            risk_level = 'medium'
        else:
            risk_level = 'high'
        
        self.risk_level = risk_level
        
        return {
            'risk_score': float(risk_score),
            'risk_level': risk_level,
            'volatility_risk': float(volatility_score),
            'spike_risk': float(spike_score),
            'sentiment_risk': float(sentiment_score),
            'timestamp': datetime.now()
        }
    
    def adjust_position_size(self, base_position: int, risk_assessment: Dict) -> int:
        """根据风险调整仓位大小"""
        risk_score = risk_assessment.get('risk_score', 0.5)
        
        # 风险越高，仓位越小
        position_multiplier = max(0.2, 1 - risk_score)
        adjusted_position = int(base_position * position_multiplier)
        
        # 确保为100的整数倍
        adjusted_position = max(100, (adjusted_position // 100) * 100)
        
        return adjusted_position
    
    def should_stop_trading(self, risk_assessment: Dict, consecutive_losses: int) -> bool:
        """判断是否应该停止交易"""
        risk_score = risk_assessment.get('risk_score', 0)
        risk_level = risk_assessment.get('risk_level', 'medium')
        
        # 高风险且连续亏损
        if risk_level == 'high' and consecutive_losses >= 3:
            return True
        
        # 极高风险
        if risk_score > 0.8:
            return True
        
        return False
    
    def get_risk_limits(self, risk_level: str) -> Dict:
        """获取风险限制参数"""
        limits = {
            'low': {
                'max_position_size': 0.8,
                'max_daily_loss': 0.05,
                'min_confidence': 0.4
            },
            'medium': {
                'max_position_size': 0.5,
                'max_daily_loss': 0.03,
                'min_confidence': 0.5
            },
            'high': {
                'max_position_size': 0.3,
                'max_daily_loss': 0.02,
                'min_confidence': 0.6
            }
        }
        
        return limits.get(risk_level, limits['medium'])