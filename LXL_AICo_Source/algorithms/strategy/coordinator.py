# algorithms/strategy/coordinator.py (完整代码)
"""
主策略协调器 - 优化版（基于市场条件的自适应调整，移除硬性频率限制）
"""
from dataclasses import dataclass, asdict
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple
import numpy as np
from collections import deque
import json
import os

from algorithms.data_models import RealtimeData, StrategyState
from algorithms.exceptions import StrategyException
from algorithms.strategy.adaptive_strategies import VolatilityAdaptiveStrategy, TrendFollowingStrategy


@dataclass
class StrategyAdjustment:
    """策略调整记录"""
    timestamp: datetime
    reason: str
    changes: Dict[str, Any]
    confidence_before: float
    confidence_after: float
    market_conditions: Dict[str, float]
    trigger_data: Optional[Dict] = None
    
    def to_dict(self) -> Dict:
        """转换为字典"""
        return {
            'timestamp': self.timestamp.isoformat(),
            'reason': self.reason,
            'changes': self.changes,
            'confidence_before': self.confidence_before,
            'confidence_after': self.confidence_after,
            'market_conditions': self.market_conditions,
            'trigger_data': self.trigger_data
        }


class BaseStrategyCoordinator:
    """基础策略协调器 - 优化功能（移除硬性调整限制）"""
    
    def __init__(self, symbol: str, initial_strategy: Dict, data_dir: str = None):
        self.symbol = symbol
        
        # 确保初始策略完整性
        self.initial_strategy = self._ensure_strategy_completeness(initial_strategy.copy())
        self.current_strategy = self.initial_strategy.copy()
        
        # 数据缓冲区
        self.realtime_data_buffer = deque(maxlen=300)  # 25分钟数据（5秒×300）
        self.minute_data_buffer = deque(maxlen=60)     # 60分钟数据
        self.hourly_trends = deque(maxlen=24)          # 24小时趋势
        
        # 状态管理
        self.adjustment_history: List[StrategyAdjustment] = []
        self.last_adjustment_time = None
        
        # 性能指标 - 移除硬性限制
        self.start_time = datetime.now()
        self.total_adjustments = 0
        self.successful_adjustments = 0
        
        # 基于市场条件的自适应调整参数（而非硬性限制）
        self.min_adjustment_interval = timedelta(seconds=5)  # 最小调整间隔5秒（足够快）
        
        # 市场状态记录
        self.market_state_history = deque(maxlen=50)  # 记录最近50次市场状态
        
        # 调整质量评估
        self.adjustment_quality_scores = deque(maxlen=100)  # 最近100次调整质量评分
        
        # 涨跌幅限制配置
        self.price_limit_percent = 0.10  # A股普通股票为±10%
        self.price_limit_applied = False
        
        # 数据目录
        self.data_dir = data_dir or os.path.join(
            os.path.dirname(os.path.dirname(__file__)), 
            "..", "data", "strategy_history"
        )
        os.makedirs(self.data_dir, exist_ok=True)
        
    def _ensure_strategy_completeness(self, strategy: Dict) -> Dict:
        """确保策略字典包含所有必要字段"""
        if not strategy:
            strategy = {}
        
        # 确保有核心字段
        defaults = {
            'feasibility_score': strategy.get('feasibility_score', 0.5),
            'confidence': strategy.get('confidence', 0.5),
            'risk_level': strategy.get('risk_level', 'medium'),
            'prediction_range': strategy.get('prediction_range', {
                'low': 0.0,
                'high': 0.0,
                'width_change': 0.0
            }),
            'buy_scenarios': strategy.get('buy_scenarios', []),
            'sell_scenarios': strategy.get('sell_scenarios', []),
            'holding_period': strategy.get('holding_period', {
                'typical_days': 5,
                'min_days': 1,
                'max_days': 20
            }),
            'last_updated': datetime.now().isoformat()
        }
        
        # 更新策略，保留原有值，填充缺失值
        for key, default_value in defaults.items():
            if key not in strategy or strategy[key] is None:
                strategy[key] = default_value
        
        return strategy
        
    def update_realtime_data(self, realtime_data: RealtimeData) -> None:
        """更新实时数据并进行预处理"""
        try:
            # 添加到缓冲区
            self.realtime_data_buffer.append(realtime_data)
            
            # 生成分钟级数据（每12个数据点生成一个分钟数据）
            if len(self.realtime_data_buffer) % 12 == 0:
                minute_data = self._aggregate_minute_data()
                if minute_data:
                    self.minute_data_buffer.append(minute_data)
                    
                    # 更新小时趋势（每60分钟更新一次）
                    if len(self.minute_data_buffer) % 60 == 0:
                        hourly_trend = self._calculate_hourly_trend()
                        if hourly_trend:
                            self.hourly_trends.append(hourly_trend)
                            
        except Exception as e:
            print(f"⚠️ 数据更新失败: {e}")
    
    def _aggregate_minute_data(self) -> Optional[Dict]:
        """聚合分钟级数据"""
        if len(self.realtime_data_buffer) < 12:
            return None
            
        recent_data = list(self.realtime_data_buffer)[-12:]  # 最近1分钟数据
        
        return {
            'timestamp': datetime.now(),
            'open': recent_data[0].price,
            'high': max(d.price for d in recent_data),
            'low': min(d.price for d in recent_data),
            'close': recent_data[-1].price,
            'volume': sum(d.volume for d in recent_data),
            'avg_price': np.mean([d.price for d in recent_data]),
            'volatility': np.std([d.price for d in recent_data]) / np.mean([d.price for d in recent_data]) if len(recent_data) > 1 else 0
        }
    
    def _calculate_hourly_trend(self) -> Optional[Dict]:
        """计算小时趋势"""
        if len(self.minute_data_buffer) < 30:  # 至少30分钟数据
            return None
            
        minute_data = list(self.minute_data_buffer)
        prices = [d['close'] for d in minute_data]
        volumes = [d['volume'] for d in minute_data]
        
        try:
            # 计算趋势
            x = np.arange(len(prices))
            y = np.array(prices)
            slope, intercept = np.polyfit(x, y, 1)
            r_squared = np.corrcoef(x, y)[0, 1] ** 2
            
            return {
                'timestamp': datetime.now(),
                'slope': float(slope),
                'intercept': float(intercept),
                'r_squared': float(r_squared),
                'price_change': (prices[-1] - prices[0]) / prices[0] if prices[0] != 0 else 0,
                'volume_change': (volumes[-1] - volumes[0]) / volumes[0] if volumes[0] != 0 else 0,
                'avg_volatility': np.mean([d.get('volatility', 0) for d in minute_data])
            }
        except:
            return None
    
    # 新增方法：计算成交量相关指标 RVI 和 VWAPD
    def _calculate_volume_metrics(self) -> Tuple[float, float]:
        """
        计算相对成交量强度 (RVI) 和 VWAP 偏差 (VWAPD)。
        这些指标是相对和动态的，用于在线学习判断成交量的相对放大或缩小。
        """
        try:
            buffer = list(self.realtime_data_buffer)
            
            # RVI 和 VWAPD 至少需要 1分钟的数据 (12个点)
            min_points_for_volume = 12 
            if len(buffer) < min_points_for_volume:
                return 0.0, 0.0

            prices = np.array([d.price for d in buffer])
            volumes = np.array([d.volume for d in buffer])
            
            # 确保成交量至少有数据
            if np.sum(volumes) == 0 or len(volumes) == 0:
                return 0.0, 0.0

            # --- 计算相对成交量强度 (RVI) ---
            # 短期窗口：1分钟 (12个点)
            # 长期窗口：5分钟 (60个点)
            short_window = min(12, len(volumes))
            long_window = min(60, len(volumes))

            if len(volumes) < long_window:
                # 数据不足时，无法计算相对值，返回默认值
                return 0.0, 0.0
            else:
                short_avg = np.mean(volumes[-short_window:])
                long_avg = np.mean(volumes[-long_window:])
                rvi = (short_avg / long_avg - 1) if long_avg > 0 else 0.0
                
            # --- 计算 VWAP 偏差 (VWAPD) ---
            # 实时 VWAP 计算（使用当前缓冲区所有数据）
            prices_f = prices.astype(float)
            volumes_f = volumes.astype(float)
            
            cumulative_pv = np.sum(prices_f * volumes_f)
            cumulative_v = np.sum(volumes_f)
            
            if cumulative_v == 0:
                return 0.0, 0.0
                
            vwap = cumulative_pv / cumulative_v if cumulative_v > 0 else prices[-1]

            # VWAP 偏差: 当前价相对于 VWAP 的百分比偏差
            current_price = prices[-1]
            vwapd = (current_price - vwap) / vwap if vwap != 0 else 0.0

            # RVI 限制在 [-2, 5] 之间，防止极端值影响
            rvi = max(-2.0, min(5.0, rvi))

            return rvi, vwapd
            
        except Exception as e:
            print(f"⚠️ 成交量指标计算失败: {e}")
            return 0.0, 0.0
    
    def _apply_price_limit_constraint(self, price_range: Dict) -> Dict:
        """
        应用涨跌幅限制约束到价格区间
        这是修复价格区间超出涨跌幅限制的核心方法
        """
        if not price_range or 'low' not in price_range or 'high' not in price_range:
            return price_range
        
        low = price_range.get('low', 0)
        high = price_range.get('high', 0)
        
        # 如果区间无效，直接返回
        if low <= 0 or high <= 0:
            return price_range
        
        # 获取基准价格（使用区间中点或实时数据）
        reference_price = None
        
        # 方法1：从当前策略中获取参考价格
        if 'prediction_range' in self.current_strategy:
            current_range = self.current_strategy['prediction_range']
            if 'reference_price' in current_range:
                reference_price = current_range['reference_price']
            else:
                # 使用当前区间中点
                current_low = current_range.get('low', 0)
                current_high = current_range.get('high', 0)
                if current_low > 0 and current_high > 0:
                    reference_price = (current_low + current_high) / 2
        
        # 方法2：从实时数据获取
        if reference_price is None and self.realtime_data_buffer:
            current_data = list(self.realtime_data_buffer)[-1]
            reference_price = current_data.price
        
        # 方法3：使用当前区间的中点
        if reference_price is None:
            reference_price = (low + high) / 2
        
        # 应用涨跌幅限制（±10%）
        price_limit_lower = reference_price * (1 - self.price_limit_percent)
        price_limit_upper = reference_price * (1 + self.price_limit_percent)
        
        # 约束价格区间
        constrained_low = max(low, price_limit_lower)
        constrained_high = min(high, price_limit_upper)
        
        # 检查是否应用了约束
        if constrained_low != low or constrained_high != high:
            self.price_limit_applied = True
            print(f"📏 应用涨跌幅限制: [{low:.2f}, {high:.2f}] → [{constrained_low:.2f}, {constrained_high:.2f}]")
            print(f"📊 基准价格: {reference_price:.2f}, 限制范围: [{price_limit_lower:.2f}, {price_limit_upper:.2f}]")
        
        price_range['low'] = float(constrained_low)
        price_range['high'] = float(constrained_high)
        
        return price_range
    
    def analyze_market_conditions(self) -> Dict[str, float]:
        """综合分析市场状况 (更新：融入成交量指标)"""
        if len(self.realtime_data_buffer) < 20:
            return {'data_insufficient': True}
        
        # 获取不同时间框架的数据
        recent_data = list(self.realtime_data_buffer)[-20:]  # 最近100秒
        if self.minute_data_buffer:
            recent_minutes = list(self.minute_data_buffer)[-15:]  # 最近15分钟
        else:
            recent_minutes = []
        
        prices = [d.price for d in recent_data]
        volumes = [d.volume for d in recent_data]
        changes = [d.change_pct for d in recent_data]
        
        # 1. 实时指标
        current_volatility = np.std(prices) / np.mean(prices) if len(prices) > 1 else 0
        current_momentum = np.mean(changes[-5:]) if len(changes) >= 5 else 0
        volume_ratio = volumes[-1] / np.mean(volumes[-10:]) if len(volumes) >= 10 and np.mean(volumes[-10:]) > 0 else 1
        
        # 2. 分钟级指标
        minute_volatility = 0
        minute_trend = 0
        if recent_minutes:
            minute_prices = [d['close'] for d in recent_minutes]
            minute_volatility = np.std(minute_prices) / np.mean(minute_prices) if len(minute_prices) > 1 else 0
            
            # 计算分钟趋势
            if len(minute_prices) >= 5:
                x = np.arange(len(minute_prices))
                y = np.array(minute_prices)
                try:
                    minute_slope = np.polyfit(x, y, 1)[0]
                    minute_trend = minute_slope / np.mean(minute_prices)
                except:
                    minute_trend = 0
        
        # 3. 小时趋势指标
        hourly_trend_strength = 0
        if self.hourly_trends:
            recent_trends = list(self.hourly_trends)[-4:]  # 最近4小时
            if recent_trends:
                hourly_trend_strength = np.mean([t.get('r_squared', 0) for t in recent_trends])
        
        # 4. 异常检测
        price_spike = 0
        if len(prices) >= 10:
            current_price = prices[-1]
            price_mean = np.mean(prices[:-1])
            price_std = np.std(prices[:-1])
            if price_std > 0:
                price_spike = abs(current_price - price_mean) / price_std
        
        # 5. 市场情绪指标 (修改：结合成交量指标)
        
        # 5.1 计算成交量指标
        rvi, vwapd = self._calculate_volume_metrics()

        # 5.2 市场情绪计算
        market_sentiment = 0
        if recent_data:
            positive_changes = sum(1 for d in recent_data if d.change_pct > 0)
            total_changes = len([d for d in recent_data if d.change_pct != 0])
            if total_changes > 0:
                # 基础情绪：价格涨跌数量的比值 [-0.5, 0.5]
                base_sentiment = positive_changes / total_changes - 0.5
                
                # 情绪修正：如果 VWAPD 和 RVI 表现积极，则放大情绪指标
                volume_confidence_factor = 1.0 
                if vwapd > 0.0005 and rvi > 0.5: # 价格在 VWAP 之上且成交量放大 (强劲买入信号)
                    volume_confidence_factor = 1.5 
                elif vwapd < -0.0005 and rvi > 0.5: # 价格在 VWAP 之下且成交量放大 (强劲卖出信号)
                    volume_confidence_factor = 1.3 
                
                market_sentiment = base_sentiment * volume_confidence_factor
        
        # 6. 构建返回字典 (新增 RVI 和 VWAPD)
        return {
            'real_time_volatility': float(current_volatility),
            'real_time_momentum': float(current_momentum),
            'volume_ratio': float(volume_ratio),
            'minute_volatility': float(minute_volatility),
            'minute_trend': float(minute_trend),
            'hourly_trend_strength': float(hourly_trend_strength),
            'price_spike': float(price_spike),
            'market_sentiment': float(market_sentiment),
            'relative_volume_intensity': float(rvi), # 新增
            'vwap_deviation': float(vwapd), # 新增
            'data_points': len(self.realtime_data_buffer),
            'data_quality': min(1.0, len(self.realtime_data_buffer) / 100)
        }
    
    def should_adjust_strategy(self, market_conditions: Dict) -> Tuple[bool, str]:
        """判断是否应该调整策略 - 基于市场条件的智能判断"""
        reasons = []
        
        # 1. 检查数据充足性
        if market_conditions.get('data_insufficient', False):
            return False, "数据不足"
        
        # 2. 基于市场条件的智能判断（移除硬性频率限制）
        
        # 2.1 检查调整间隔（但允许紧急情况下突破）
        time_since_last = None
        if self.last_adjustment_time:
            time_since_last = datetime.now() - self.last_adjustment_time
        
        # 2.2 基于市场条件的触发逻辑
        trigger_conditions = self._get_market_based_triggers(market_conditions)
        
        triggered = False
        for condition, reason in trigger_conditions:
            if condition:
                reasons.append(reason)
                triggered = True
        
        # 3. 如果市场条件显著变化，即使刚刚调整过也允许再次调整
        if triggered:
            # 检查是否是紧急情况
            is_urgent = self._is_urgent_market_change(market_conditions)
            
            if is_urgent:
                # 紧急情况：立即调整
                return True, "紧急情况: " + " | ".join(reasons)
            elif time_since_last and time_since_last < timedelta(seconds=2):
                # 非紧急情况但刚调整过，等待至少2秒
                return False, f"距离上次调整仅{time_since_last.seconds}秒，等待市场反应"
            elif time_since_last and time_since_last < self.min_adjustment_interval:
                # 检查调整质量，低质量调整可以更快再次调整
                if self._should_allow_quick_readjustment():
                    return True, "低质量调整后快速重调: " + " | ".join(reasons)
                else:
                    return False, f"调整间隔不足: {time_since_last.seconds}秒"
            else:
                return True, " | ".join(reasons)
        
        # 4. 定期策略评估（每2-5分钟一次，动态间隔）
        if not self.last_adjustment_time or self._should_perform_periodic_check():
            # 动态调整定期检查间隔（基于市场活跃度）
            check_interval = self._get_dynamic_check_interval(market_conditions)
            time_since_last = datetime.now() - self.last_adjustment_time if self.last_adjustment_time else timedelta(hours=1)
            
            if time_since_last > check_interval:
                return True, f"定期策略评估（间隔: {check_interval.seconds}秒）"
        
        return False, "无显著变化"
    
    def _get_market_based_triggers(self, market_conditions: Dict) -> List[Tuple[bool, str]]:
        """基于市场条件的动态触发阈值"""
        triggers = []
        
        # 动态阈值：根据市场波动性调整
        volatility = market_conditions.get('real_time_volatility', 0)
        volatility_multiplier = 1.0 + min(volatility * 10, 3.0)  # 波动性越高，阈值越宽松
        
        # 基本触发条件（带动态阈值）
        triggers.extend([
            (market_conditions.get('real_time_volatility', 0) > 0.015 / volatility_multiplier, 
             f"波动率触发 (动态阈值: {0.015/volatility_multiplier:.4f})"),
            (abs(market_conditions.get('real_time_momentum', 0)) > 0.15 / volatility_multiplier, 
             f"动量触发 (动态阈值: {0.15/volatility_multiplier:.3f})"),
            (market_conditions.get('price_spike', 0) > 1.2, "价格异常波动"),
            (abs(market_conditions.get('market_sentiment', 0)) > 0.25, "市场情绪极端化"),
            (market_conditions.get('hourly_trend_strength', 0) > 0.6, "强趋势确认"),
            (market_conditions.get('relative_volume_intensity', 0) > 0.8, "成交量明显变化"),
            (abs(market_conditions.get('vwap_deviation', 0)) > 0.0008, "VWAP显著偏离"),
            (market_conditions.get('volume_ratio', 0) > 1.8, "成交量比率异常"),
        ])
        
        # 复合条件：多个指标同时变化
        multi_signal_score = 0
        if volatility > 0.02:
            multi_signal_score += 1
        if abs(market_conditions.get('market_sentiment', 0)) > 0.2:
            multi_signal_score += 1
        if market_conditions.get('relative_volume_intensity', 0) > 0.5:
            multi_signal_score += 1
        
        if multi_signal_score >= 2:
            triggers.append((True, f"多指标复合信号 ({multi_signal_score}个指标)"))
        
        return triggers
    
    def _is_urgent_market_change(self, market_conditions: Dict) -> bool:
        """判断是否为紧急市场变化"""
        # 紧急情况判断
        urgent_conditions = [
            market_conditions.get('price_spike', 0) > 2.5,  # 极端价格波动
            market_conditions.get('real_time_volatility', 0) > 0.05,  # 极高波动率
            abs(market_conditions.get('market_sentiment', 0)) > 0.4,  # 极端情绪
            market_conditions.get('volume_ratio', 0) > 3.0,  # 成交量急剧放大
        ]
        
        return any(urgent_conditions)
    
    def _should_allow_quick_readjustment(self) -> bool:
        """判断是否允许快速重新调整"""
        if len(self.adjustment_quality_scores) < 3:
            return True  # 数据不足时允许
        
        # 如果最近几次调整质量较低，允许快速重新调整
        recent_scores = list(self.adjustment_quality_scores)[-3:]
        avg_score = sum(recent_scores) / len(recent_scores)
        
        return avg_score < 0.5  # 质量低于0.5时允许快速重调
    
    def _should_perform_periodic_check(self) -> bool:
        """判断是否应进行定期检查"""
        # 基于调整历史动态决定定期检查频率
        if self.total_adjustments < 10:
            return True  # 初期更频繁检查
        
        # 根据最近调整质量调整检查频率
        if len(self.adjustment_quality_scores) > 0:
            recent_quality = sum(list(self.adjustment_quality_scores)[-5:]) / 5
            # 质量越高，检查间隔可以越长
            if recent_quality > 0.7:
                return False  # 高质量策略，减少检查
        
        return True
    
    def _get_dynamic_check_interval(self, market_conditions: Dict) -> timedelta:
        """获取动态检查间隔（基于市场活跃度）"""
        volatility = market_conditions.get('real_time_volatility', 0)
        
        # 基础间隔：2-5分钟，根据波动性调整
        base_interval = 120  # 2分钟
        volatility_adjustment = min(volatility * 600, 180)  # 最多调整3分钟
        
        if volatility > 0.02:
            # 高波动性市场：缩短检查间隔
            interval_seconds = max(60, base_interval - volatility_adjustment)  # 最少1分钟
        else:
            # 低波动性市场：延长检查间隔
            interval_seconds = min(300, base_interval + volatility_adjustment)  # 最多5分钟
        
        return timedelta(seconds=interval_seconds)
    
    def calculate_strategy_adjustment(self, market_conditions: Dict) -> Dict[str, Any]:
        """计算策略调整 - 基于市场条件动态调整幅度"""
        adjustment = {}
        
        # 1. 基于市场波动性确定调整幅度
        volatility = market_conditions.get('real_time_volatility', 0)
        adjustment_intensity = self._get_adjustment_intensity(market_conditions)
        
        # 2. 调整信心度（基于市场条件和调整强度）
        confidence_adjustment = self._calculate_confidence_adjustment(market_conditions, adjustment_intensity)
        if confidence_adjustment is not None:
            adjustment['confidence'] = confidence_adjustment
        
        # 3. 调整价格区间（应用涨跌幅限制）
        if 'prediction_range' in self.current_strategy:
            price_range_adjustment = self._calculate_price_range_adjustment(market_conditions, adjustment_intensity)
            if price_range_adjustment:
                adjustment['prediction_range'] = price_range_adjustment
        
        # 4. 调整买卖场景
        if 'buy_scenarios' in self.current_strategy and self.current_strategy['buy_scenarios']:
            adjustment['buy_scenarios'] = self._adjust_buy_scenarios(
                self.current_strategy['buy_scenarios'], 
                market_conditions,
                adjustment_intensity
            )
        
        if 'sell_scenarios' in self.current_strategy and self.current_strategy['sell_scenarios']:
            adjustment['sell_scenarios'] = self._adjust_sell_scenarios(
                self.current_strategy['sell_scenarios'],
                market_conditions,
                adjustment_intensity
            )
        
        return adjustment
    
    def _get_adjustment_intensity(self, market_conditions: Dict) -> float:
        """根据市场条件确定调整强度"""
        # 基础强度
        intensity = 1.0
        
        # 市场波动性影响
        volatility = market_conditions.get('real_time_volatility', 0)
        if volatility > 0.03:
            intensity *= 1.5  # 高波动性市场，调整更激进
        elif volatility < 0.01:
            intensity *= 0.7  # 低波动性市场，调整更保守
        
        # 市场趋势影响
        trend_strength = market_conditions.get('hourly_trend_strength', 0)
        if trend_strength > 0.7:
            intensity *= 1.3  # 强趋势市场，调整更积极
        
        # 成交量影响
        rvi = market_conditions.get('relative_volume_intensity', 0)
        if abs(rvi) > 1.0:
            intensity *= 1.2  # 成交量显著变化，调整更积极
        
        return max(0.3, min(2.0, intensity))  # 限制在合理范围内
    
    def _calculate_confidence_adjustment(self, market_conditions: Dict, intensity: float) -> Optional[float]:
        """计算信心度调整"""
        current_confidence = self.current_strategy.get('feasibility_score', 0.5)
        
        # 基于多个指标调整
        adjustment_factors = []
        
        # 波动率调整
        volatility = market_conditions.get('real_time_volatility', 0)
        if volatility > 0.03:
            adjustment_factors.append(0.8)  # 高波动率降低信心
        elif volatility < 0.01:
            adjustment_factors.append(1.1)  # 低波动率提高信心
        
        # 趋势调整
        trend_strength = market_conditions.get('hourly_trend_strength', 0)
        if trend_strength > 0.6:
            adjustment_factors.append(1.15)  # 强趋势提高信心
        
        # 市场情绪调整
        sentiment = market_conditions.get('market_sentiment', 0)
        if abs(sentiment) > 0.3:
            # 极端情绪可能降低信心（不确定性）
            adjustment_factors.append(0.9)
        
        # 成交量确认
        rvi = market_conditions.get('relative_volume_intensity', 0)
        vwapd = market_conditions.get('vwap_deviation', 0)
        if rvi > 0.5 and vwapd > 0:
            # 成交量放大且价格在VWAP之上，提高信心
            adjustment_factors.append(1.1)
        
        if adjustment_factors:
            # 计算综合调整因子
            combined_factor = np.prod(adjustment_factors)
            # 应用调整强度
            adjusted_factor = 1.0 + (combined_factor - 1.0) * intensity
            
            new_confidence = max(0.1, min(0.9, current_confidence * adjusted_factor))
            return new_confidence
        
        return None
    
    def _calculate_price_range_adjustment(self, market_conditions: Dict, intensity: float) -> Dict:
        """计算价格区间调整"""
        if 'prediction_range' not in self.current_strategy:
            return {}
        
        current_range = self.current_strategy['prediction_range']
        low, high = current_range.get('low', 0), current_range.get('high', 0)
        
        if low <= 0 or high <= 0:
            return {}
        
        mid = (low + high) / 2
        width = high - low
        
        # 基于市场条件的动态调整
        volatility = market_conditions.get('real_time_volatility', 0)
        trend = market_conditions.get('minute_trend', 0)
        
        # 宽度调整：波动性越高，区间越宽
        width_adjustment = 1.0 + volatility * 3 * intensity
        new_width = width * width_adjustment
        
        # 位置调整：跟随趋势
        trend_adjustment = min(abs(trend) * 2, 0.1) * intensity  # 最大10%偏移
        shift = mid * trend_adjustment * (1 if trend > 0 else -1)
        
        new_low = mid + shift - new_width / 2
        new_high = mid + shift + new_width / 2
        
        # 确保高低顺序
        if new_low > new_high:
            new_low, new_high = new_high, new_low
        
        # 应用涨跌幅限制
        constrained_range = self._apply_price_limit_constraint({
            'low': float(new_low),
            'high': float(new_high),
            'width_change': float((new_width - width) / width) if width > 0 else 0
        })
        
        return constrained_range
    
    def apply_strategy_adjustment(self, adjustment: Dict[str, Any], reason: str, market_conditions: Dict) -> bool:
        """应用策略调整 - 添加调整质量评估"""
        if not adjustment:
            return False
        
        try:
            # 记录调整前状态
            confidence_before = self.current_strategy.get('feasibility_score', 0.5)
            
            # 保存当前市场状态
            self.market_state_history.append({
                'timestamp': datetime.now(),
                'conditions': market_conditions.copy(),
                'strategy_before': self.current_strategy.copy()
            })
            
            # 应用各种调整（保持原有逻辑）
            # ... [原有调整应用逻辑] ...
            
            # 应用自适应策略调整
            
            # 1. 应用 VWAP 和 RVI 调整
            rvi = market_conditions.get('relative_volume_intensity', 0)
            vwapd = market_conditions.get('vwap_deviation', 0)
            self.current_strategy = VolatilityAdaptiveStrategy.adjust_for_volume(
                self.current_strategy, rvi, vwapd
            )
            
            # 2. 应用波动率调整
            volatility = market_conditions.get('real_time_volatility', 0)
            self.current_strategy = VolatilityAdaptiveStrategy.adjust_for_volatility(
                self.current_strategy, volatility
            )

            # 3. 应用趋势调整
            minute_trend = market_conditions.get('minute_trend', 0)
            if abs(minute_trend) > 0.0001:
                trend_direction = 'bullish' if minute_trend > 0 else 'bearish'
                self.current_strategy = TrendFollowingStrategy.adjust_for_trend(
                    self.current_strategy, abs(minute_trend), trend_direction
                )

            # 应用 calculate_strategy_adjustment 中计算的直接更新
            for key, value in adjustment.items():
                if key in self.current_strategy and isinstance(self.current_strategy[key], dict) and isinstance(value, dict):
                    # 合并字典类型的值
                    if key == 'prediction_range':
                        # 对价格区间应用约束
                        self.current_strategy[key].update(value)
                        self.current_strategy[key] = self._apply_price_limit_constraint(self.current_strategy[key])
                    else:
                        self.current_strategy[key].update(value)
                elif key == 'confidence':
                    # 特殊处理，更新信心度
                    self.current_strategy['feasibility_score'] = value
                else:
                    # 直接更新其他键
                    self.current_strategy[key] = value

            # 确保策略完整性
            self.current_strategy = self._ensure_strategy_completeness(self.current_strategy)
            
            # 更新状态
            confidence_after = self.current_strategy.get('feasibility_score', 0.5)
            now = datetime.now()
            
            self.last_adjustment_time = now
            self.total_adjustments += 1
            self.successful_adjustments += 1
            
            # 评估调整质量
            quality_score = self._evaluate_adjustment_quality(
                adjustment, reason, market_conditions,
                confidence_before, confidence_after
            )
            self.adjustment_quality_scores.append(quality_score)
            
            # 记录调整历史
            adjustment_record = StrategyAdjustment(
                timestamp=now,
                reason=reason,
                changes=adjustment,
                confidence_before=confidence_before,
                confidence_after=confidence_after,
                market_conditions=market_conditions,
                trigger_data=self.current_strategy.copy()
            )
            self.adjustment_history.append(adjustment_record)
            
            # 显示调整信息
            print(f"✅ 策略调整: {reason}")
            print(f"  信心度: {confidence_before:.3f} → {confidence_after:.3f}")
            print(f"  调整质量评分: {quality_score:.2f}")
            
            if 'prediction_range' in adjustment:
                pr = adjustment['prediction_range']
                low, high = pr.get('low', 0), pr.get('high', 0)
                print(f"  价格区间: [{low:.2f}, {high:.2f}]")
            
            return True
            
        except Exception as e:
            print(f"❌ 策略调整应用失败: {e}")
            return False
    
    def _evaluate_adjustment_quality(self, adjustment: Dict, reason: str, 
                                   market_conditions: Dict, 
                                   confidence_before: float, confidence_after: float) -> float:
        """评估调整质量（0.0-1.0）"""
        quality_score = 0.5  # 基础分
        
        # 1. 调整理由合理性
        if "紧急情况" in reason:
            quality_score += 0.2
        elif "多指标复合信号" in reason:
            quality_score += 0.15
        elif "定期策略评估" in reason:
            quality_score += 0.05
        
        # 2. 市场条件支持度
        volatility = market_conditions.get('real_time_volatility', 0)
        if volatility > 0.02 and "波动率" in reason:
            quality_score += 0.1
        
        # 3. 调整幅度合理性
        if abs(confidence_after - confidence_before) < 0.3:
            quality_score += 0.1  # 适度调整
        
        # 4. 调整一致性（与近期调整方向是否一致）
        if len(self.adjustment_history) > 0:
            last_adj = self.adjustment_history[-1]
            if last_adj.confidence_after < confidence_after and confidence_before < confidence_after:
                quality_score += 0.05  # 连续同向调整
        
        return max(0.0, min(1.0, quality_score))
    
    def _adjust_buy_scenarios(self, buy_scenarios: List[Dict], market_conditions: Dict, intensity: float = 1.0) -> List[Dict]:
        """调整买入场景"""
        adjusted = []
        volatility = market_conditions.get('real_time_volatility', 0)
        sentiment = market_conditions.get('market_sentiment', 0)
        
        for scenario in buy_scenarios:
            scenario_copy = scenario.copy()
            
            # 基于波动率调整
            if volatility > 0.03:  # 高波动率
                scenario_copy['probability'] = scenario_copy.get('probability', 0.5) * 0.8 * intensity
            
            # 基于市场情绪调整
            if sentiment > 0.3:  # 积极情绪
                scenario_copy['probability'] = min(0.9, scenario_copy.get('probability', 0.5) * 1.2 * intensity)
            elif sentiment < -0.3:  # 消极情绪
                scenario_copy['probability'] = max(0.1, scenario_copy.get('probability', 0.5) * 0.8 * intensity)
            
            adjusted.append(scenario_copy)
        
        return adjusted
    
    def _adjust_sell_scenarios(self, sell_scenarios: List[Dict], market_conditions: Dict, intensity: float = 1.0) -> List[Dict]:
        """调整卖出场景"""
        adjusted = []
        volatility = market_conditions.get('real_time_volatility', 0)
        sentiment = market_conditions.get('market_sentiment', 0)
        
        for scenario in sell_scenarios:
            scenario_copy = scenario.copy()
            
            # 基于波动率调整
            if volatility > 0.03:  # 高波动率
                # 高波动率下，降低止盈目标，提高止损灵敏度
                if scenario_copy.get('type') == '止盈':
                    scenario_copy['price'] = scenario_copy.get('price', 0) * 0.98 * intensity
                elif scenario_copy.get('type') == '止损':
                    scenario_copy['price'] = scenario_copy.get('price', 0) * 1.02 * intensity
            
            # 基于市场情绪调整
            if sentiment > 0.3:  # 积极情绪
                if scenario_copy.get('type') == '止盈':
                    scenario_copy['price'] = scenario_copy.get('price', 0) * 1.02 * intensity
            
            adjusted.append(scenario_copy)
        
        return adjusted
    
    # ------------------ 修复缺失的方法 ------------------
    def get_current_strategy_state(self) -> StrategyState:
        """
        供 OnlinePredictor 在初始化时获取当前策略状态。
        """
        try:
            # 确保当前策略完整性
            self.current_strategy = self._ensure_strategy_completeness(self.current_strategy)
            
            confidence = self.current_strategy.get('feasibility_score', 0.5)
            
            # 如果 self.last_adjustment_time 存在，说明进行过调整，使用最近的状态
            change_reason = f"策略初始化/恢复, 调整次数: {len(self.adjustment_history)}"
            last_updated = self.last_adjustment_time or datetime.now()
            
            # 构建 previous_strategy（如果有调整历史）
            previous_strategy = None
            if self.adjustment_history and len(self.adjustment_history) > 0:
                # 使用最近一次的调整记录
                previous_strategy = self.adjustment_history[-1].trigger_data
            
            return StrategyState(
                current_strategy=self.current_strategy,
                previous_strategy=previous_strategy,
                change_reason=change_reason,
                confidence=confidence,
                last_updated=last_updated
            )
            
        except Exception as e:
            print(f"⚠️ 获取策略状态失败: {e}")
            # 返回一个安全的默认状态
            return StrategyState(
                current_strategy={},
                previous_strategy=None,
                change_reason="策略状态初始化失败",
                confidence=0.5,
                last_updated=datetime.now()
            )
    
    def get_strategy_insights(self) -> Dict[str, Any]:
        """获取策略洞察"""
        # 统计最近1小时的调整次数
        now = datetime.now()
        recent_adj_count = 0
        for adj in self.adjustment_history[-100:]:
            if (now - adj.timestamp) < timedelta(hours=1):
                recent_adj_count += 1
        
        # 计算平均调整质量
        avg_quality = 0
        if len(self.adjustment_quality_scores) > 0:
            avg_quality = sum(self.adjustment_quality_scores) / len(self.adjustment_quality_scores)
        
        return {
            'adjustment_count': len(self.adjustment_history),
            'recent_adjustments_per_hour': recent_adj_count,
            'last_adjustment': self.last_adjustment_time.isoformat() if self.last_adjustment_time else None,
            'data_points': len(self.realtime_data_buffer),
            'minute_data_points': len(self.minute_data_buffer),
            'hourly_trends': len(self.hourly_trends),
            'data_quality': min(1.0, len(self.realtime_data_buffer) / 100),
            'avg_adjustment_quality': avg_quality,
            'price_limit_applied': self.price_limit_applied,
            'price_limit_percent': self.price_limit_percent * 100
        }
    
    def get_performance_summary(self) -> Dict[str, Any]:
        """获取性能摘要"""
        if self.total_adjustments > 0:
            success_rate = self.successful_adjustments / self.total_adjustments
        else:
            success_rate = 0
        
        return {
            'total_adjustments': self.total_adjustments,
            'successful_adjustments': self.successful_adjustments,
            'success_rate': success_rate,
            'uptime': str(datetime.now() - self.start_time)
        }