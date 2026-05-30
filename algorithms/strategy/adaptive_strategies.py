# algorithms/strategy/adaptive_strategies.py
"""
自适应策略模块（新增成交量调整逻辑）
"""
from typing import Dict, List
import numpy as np


class VolatilityAdaptiveStrategy:
    """波动率自适应策略"""
    
    @staticmethod
    def adjust_for_volatility(strategy: Dict, volatility: float) -> Dict:
        """基于波动率调整策略"""
        try:
            # 确保传入的是有效的策略字典
            if not strategy or not isinstance(strategy, dict):
                return strategy.copy() if strategy else {}
            
            adjusted = strategy.copy()
            
            # 确保关键字段存在
            if 'feasibility_score' not in adjusted:
                adjusted['feasibility_score'] = 0.5
            if 'risk_level' not in adjusted:
                adjusted['risk_level'] = 'medium'
            
            if volatility > 0.03:  # 高波动率
                # 扩大价格区间
                if 'prediction_range' in adjusted:
                    current_range = adjusted['prediction_range']
                    if isinstance(current_range, dict):
                        low = current_range.get('low', 0)
                        high = current_range.get('high', 0)
                        if high > low > 0:
                            width = high - low
                            new_width = width * (1 + volatility * 10)
                            mid = (low + high) / 2
                            adjusted['prediction_range'] = {
                                'low': mid - new_width / 2,
                                'high': mid + new_width / 2
                            }
                
                # 降低信心度
                adjusted['feasibility_score'] = adjusted.get('feasibility_score', 0.5) * 0.7
                
                # 调整风险等级
                adjusted['risk_level'] = 'high'
                
            elif volatility < 0.01:  # 低波动率
                # 收窄价格区间
                if 'prediction_range' in adjusted:
                    current_range = adjusted['prediction_range']
                    if isinstance(current_range, dict):
                        low = current_range.get('low', 0)
                        high = current_range.get('high', 0)
                        if high > low > 0:
                            width = high - low
                            new_width = width * 0.8  # 收窄20%
                            mid = (low + high) / 2
                            adjusted['prediction_range'] = {
                                'low': mid - new_width / 2,
                                'high': mid + new_width / 2
                            }
                
                # 提高信心度
                adjusted['feasibility_score'] = min(0.9, adjusted.get('feasibility_score', 0.5) * 1.1)
                
                # 调整风险等级
                adjusted['risk_level'] = 'low'
            
            return adjusted
            
        except Exception as e:
            print(f"⚠️ 波动率调整失败: {e}")
            return strategy.copy() if isinstance(strategy, dict) else {}

    @staticmethod
    def adjust_for_volume(strategy: Dict, rvi: float, vwapd: float) -> Dict:
        """
        基于相对成交量强度 (RVI) 和 VWAP 偏差 (VWAPD) 调整策略。
        用于在线学习判断成交量变化对策略信心度和价格区间的支持程度。
        RVI: >0 成交量放大; <0 成交量萎缩
        VWAPD: >0 价格在 VWAP 之上; <0 价格在 VWAP 之下
        """
        try:
            # 确保传入的是有效的策略字典
            if not strategy or not isinstance(strategy, dict):
                return strategy.copy() if strategy else {}
            
            adjusted = strategy.copy()
            
            # 确保关键字段存在
            if 'feasibility_score' not in adjusted:
                adjusted['feasibility_score'] = 0.5
            if 'risk_level' not in adjusted:
                adjusted['risk_level'] = 'medium'
            
            current_confidence = adjusted.get('feasibility_score', 0.5)
            
            # --- 1. 基于 RVI 和 VWAPD 调整信心度 (Feasibility Score) ---
            confidence_factor = 1.0
            reason = ""

            # 检查数据有效性
            if rvi is None or vwapd is None:
                return adjusted
            
            # 强劲的趋势确认 (成交量巨幅放大且趋势得到 VWAP 确认)
            if rvi > 1.5 and abs(vwapd) > 0.001: 
                confidence_factor = 1.3 # 大幅提高信心度
                reason = "成交量巨幅放大且趋势得到 VWAP 确认"
                adjusted['risk_level'] = 'aggressive' # 激进风险
            
            # 成交量放大 (高RVI)，但 VWAP 背离或偏差小
            elif rvi > 1.0 and abs(vwapd) < 0.001: 
                confidence_factor = 0.9 # 降低信心度，警惕虚假信号
                reason = "成交量放大但价格缺乏 VWAP 支撑"
                adjusted['risk_level'] = 'cautious' # 谨慎风险

            # 成交量萎缩 (低 RVI)，价格稳定
            elif rvi < 0.5 and abs(vwapd) < 0.0005: 
                confidence_factor = 1.05 # 略微提高，表示稳定
                reason = "成交量低迷，市场稳定等待方向"
                adjusted['risk_level'] = 'low' # 低风险

            # 持续低迷成交量 (RVI 负值)，价格震荡
            elif rvi < -0.3:
                confidence_factor = 0.8 # 降低信心度
                reason = "成交量持续萎缩，流动性不足"
                # 注意：低流动性通常被认为是高风险
                adjusted['risk_level'] = 'high'

            new_confidence = max(0.1, min(0.9, current_confidence * confidence_factor))
            adjusted['feasibility_score'] = new_confidence
            
            # --- 2. 基于 VWAP 偏差调整价格区间 ---
            if 'prediction_range' in adjusted:
                current_range = adjusted['prediction_range']
                
                # 确保价格区间字段存在
                if isinstance(current_range, dict):
                    low = current_range.get('low', 0)
                    high = current_range.get('high', 0)
                    
                    # VWAP 偏差越大，表明价格偏离平均成本越大，预测区间应向该方向倾斜
                    if abs(vwapd) > 0.0005 and high > low > 0:
                        # 偏差幅度（0.5%偏差导致 5% 的区间偏移）
                        shift_factor = vwapd * 10 
                        
                        # 调整区间位置：以当前区间为基础，向 VWAPD 方向移动
                        new_low = low * (1 + shift_factor)
                        new_high = high * (1 + shift_factor)
                        
                        # 确保调整不会导致区间上下限颠倒
                        if new_low < new_high:
                            adjusted['prediction_range'] = {
                                'low': float(new_low),
                                'high': float(new_high)
                            }
                    
            # 记录调整理由
            if reason:
                adjusted['volume_adjustment_reason'] = reason
            
            return adjusted
            
        except Exception as e:
            print(f"⚠️ 成交量调整失败: {e}")
            # 返回原始策略，避免影响整体流程
            return strategy.copy() if isinstance(strategy, dict) else {}


class TrendFollowingStrategy:
    """趋势跟随策略"""
    
    @staticmethod
    def adjust_for_trend(strategy: Dict, trend_strength: float, trend_direction: str) -> Dict:
        """基于趋势调整策略"""
        try:
            # 确保传入的是有效的策略字典
            if not strategy or not isinstance(strategy, dict):
                return strategy.copy() if strategy else {}
            
            adjusted = strategy.copy()
            
            if abs(trend_strength) > 0.001:
                # 调整买卖场景
                if 'buy_scenarios' in adjusted and adjusted['buy_scenarios']:
                    for scenario in adjusted['buy_scenarios']:
                        if trend_direction == 'bullish':
                            # 上升趋势：更激进的买入
                            scenario['probability'] = min(0.9, scenario.get('probability', 0.5) * 1.2)
                            scenario['reason'] = f"{scenario.get('reason', '')} | 趋势跟随"
                        else:
                            # 下降趋势：更保守的买入
                            scenario['probability'] = max(0.1, scenario.get('probability', 0.5) * 0.8)
                
                if 'sell_scenarios' in adjusted and adjusted['sell_scenarios']:
                    for scenario in adjusted['sell_scenarios']:
                        if trend_direction == 'bullish':
                            # 上升趋势：提高止盈目标
                            scenario['price'] = scenario.get('price', 0) * 1.02
                            scenario['type'] = '趋势止盈'
                        else:
                            # 下降趋势：降低止盈目标
                            scenario['price'] = scenario.get('price', 0) * 0.98
                            scenario['type'] = '风险止损'
                
                # 调整持有周期
                if 'holding_period' in adjusted:
                    if trend_direction == 'bullish':
                        # 上升趋势：延长持有
                        adjusted['holding_period']['typical_days'] = min(
                            20, adjusted['holding_period'].get('typical_days', 5) + 2
                        )
                    else:
                        # 下降趋势：缩短持有
                        adjusted['holding_period']['typical_days'] = max(
                            1, adjusted['holding_period'].get('typical_days', 5) - 2
                        )
            
            return adjusted
            
        except Exception as e:
            print(f"⚠️ 趋势调整失败: {e}")
            return strategy.copy() if isinstance(strategy, dict) else {}