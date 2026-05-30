# [file name]: scripts/backtest_modules/market_state_detector.py
#[file content begin]
#!/usr/bin/env python3
"""
market_state_detector.py - 市场状态检测器模块
基于价格、成交量、波动率等多维度特征识别市场状态
"""
import pandas as pd
import numpy as np
from config.logging_config import get_logger

logger = get_logger("MarketStateDetector")

class MarketStateDetector:
    def __init__(self):
        self.state_definitions = {
            'trending_up': '强势上升趋势',
            'trending_down': '强势下降趋势', 
            'consolidation': '震荡整理',
            'high_volatility': '高波动市场',
            'low_volatility': '低波动市场',
            'breakout': '突破市场',
            'reversal': '反转市场'
        }
        
        # 状态阈值参数（可基于历史数据动态调整）
        self.thresholds = {
            'trend_strength': 0.03,      # 趋势强度阈值
            'volatility_ratio': 1.2,     # 波动率比率阈值
            'volume_spike': 1.5,         # 成交量突增阈值
            'price_breakout': 0.02       # 价格突破阈值
        }
    
    def analyze_market_state(self, df, lookback_period=60):
        """综合分析市场状态"""
        if len(df) < lookback_period:
            return 'unknown'
        
        # 计算各项指标
        trend_metrics = self._calculate_trend_metrics(df, lookback_period)
        volatility_metrics = self._calculate_volatility_metrics(df, lookback_period)
        volume_metrics = self._calculate_volume_metrics(df, lookback_period)
        price_structure_metrics = self._calculate_price_structure_metrics(df, lookback_period)
        
        # 综合判断市场状态
        primary_state = self._determine_primary_state(
            trend_metrics, volatility_metrics, volume_metrics
        )
        
        # 识别次要特征
        secondary_features = self._identify_secondary_features(
            price_structure_metrics, volume_metrics
        )
        
        # 生成详细状态报告
        state_report = {
            'primary_state': primary_state,
            'secondary_features': secondary_features,
            'trend_metrics': trend_metrics,
            'volatility_metrics': volatility_metrics,
            'volume_metrics': volume_metrics,
            'confidence': self._calculate_state_confidence(
                trend_metrics, volatility_metrics
            )
        }
        
        logger.debug(f"市场状态分析: {primary_state}, 置信度: {state_report['confidence']:.2f}")
        
        return primary_state, state_report
    
    def _calculate_trend_metrics(self, df, lookback_period):
        """计算趋势相关指标"""
        closes = df['收盘'].values
        
        # 多时间尺度趋势
        short_trend = self._calculate_trend_strength(closes, 5, 20)
        medium_trend = self._calculate_trend_strength(closes, 20, 60)
        long_trend = self._calculate_trend_strength(closes, 60, lookback_period)
        
        # 移动平均线关系
        ma_short = np.mean(closes[-5:])
        ma_medium = np.mean(closes[-20:])
        ma_long = np.mean(closes[-60:])
        
        ma_alignment = self._check_ma_alignment(ma_short, ma_medium, ma_long)
        
        return {
            'short_trend': short_trend,
            'medium_trend': medium_trend,
            'long_trend': long_trend,
            'ma_alignment': ma_alignment,
            'dominant_trend': self._identify_dominant_trend(short_trend, medium_trend, long_trend)
        }
    
    def _calculate_trend_strength(self, prices, short_window, long_window):
        """计算特定时间尺度的趋势强度"""
        if len(prices) < long_window:
            return 0
        
        short_avg = np.mean(prices[-short_window:])
        long_avg = np.mean(prices[-long_window:])
        
        return (short_avg / long_avg - 1) * 100  # 百分比表示
    
    def _check_ma_alignment(self, ma_short, ma_medium, ma_long):
        """检查移动平均线排列"""
        if ma_short > ma_medium > ma_long:
            return 'bullish_alignment'
        elif ma_short < ma_medium < ma_long:
            return 'bearish_alignment'
        else:
            return 'mixed_alignment'
    
    def _identify_dominant_trend(self, short_trend, medium_trend, long_trend):
        """识别主导趋势"""
        trends = [short_trend, medium_trend, long_trend]
        avg_trend = np.mean(trends)
        
        if avg_trend > self.thresholds['trend_strength']:
            return 'up'
        elif avg_trend < -self.thresholds['trend_strength']:
            return 'down'
        else:
            return 'sideways'
    
    def _calculate_volatility_metrics(self, df, lookback_period):
        """计算波动率指标"""
        returns = df['收盘'].pct_change().dropna()
        
        if len(returns) < lookback_period:
            return {'current_volatility': 0, 'volatility_ratio': 1, 'regime': 'normal'}
        
        # 当前波动率
        current_vol = returns[-20:].std() * np.sqrt(250)  # 年化
        
        # 历史波动率（作为基准）
        historical_vol = returns.std() * np.sqrt(250)
        
        # 波动率比率
        vol_ratio = current_vol / historical_vol if historical_vol > 0 else 1
        
        # 波动状态
        if vol_ratio > self.thresholds['volatility_ratio']:
            vol_regime = 'high'
        elif vol_ratio < 1 / self.thresholds['volatility_ratio']:
            vol_regime = 'low'
        else:
            vol_regime = 'normal'
        
        return {
            'current_volatility': current_vol,
            'historical_volatility': historical_vol,
            'volatility_ratio': vol_ratio,
            'regime': vol_regime
        }
    
    def _calculate_volume_metrics(self, df, lookback_period):
        """计算成交量指标"""
        volumes = df['成交量'].values
        
        if len(volumes) < lookback_period:
            return {'volume_trend': 0, 'volume_spike': False, 'volume_regime': 'normal'}
        
        # 成交量趋势
        volume_ma_short = np.mean(volumes[-5:])
        volume_ma_long = np.mean(volumes[-20:])
        volume_trend = (volume_ma_short / volume_ma_long - 1) * 100
        
        # 成交量突增
        recent_volume = volumes[-1]
        volume_avg = np.mean(volumes[-20:])
        volume_spike = recent_volume > volume_avg * self.thresholds['volume_spike']
        
        # 量价配合
        price_trend = self._calculate_trend_strength(df['收盘'].values, 5, 20)
        volume_price_confirmation = (
            (volume_trend > 0 and price_trend > 0) or 
            (volume_trend < 0 and price_trend < 0)
        )
        
        return {
            'volume_trend': volume_trend,
            'volume_spike': volume_spike,
            'volume_regime': 'high' if volume_trend > 20 else 'low' if volume_trend < -20 else 'normal',
            'volume_price_confirmation': volume_price_confirmation
        }
    
    def _calculate_price_structure_metrics(self, df, lookback_period):
        """计算价格结构指标"""
        highs = df['最高'].values
        lows = df['最低'].values
        closes = df['收盘'].values
        
        # 支撑阻力分析
        resistance_level = np.max(highs[-20:])
        support_level = np.min(lows[-20:])
        current_close = closes[-1]
        
        # 突破分析
        resistance_breakout = current_close > resistance_level * (1 - self.thresholds['price_breakout'])
        support_breakdown = current_close < support_level * (1 + self.thresholds['price_breakout'])
        
        # 价格区间
        price_range = (resistance_level - support_level) / support_level
        
        return {
            'resistance_level': resistance_level,
            'support_level': support_level,
            'current_position': (current_close - support_level) / (resistance_level - support_level) if resistance_level != support_level else 0.5,
            'resistance_breakout': resistance_breakout,
            'support_breakdown': support_breakdown,
            'price_range': price_range
        }
    
    def _determine_primary_state(self, trend_metrics, volatility_metrics, volume_metrics):
        """确定主要市场状态"""
        dominant_trend = trend_metrics['dominant_trend']
        volatility_regime = volatility_metrics['regime']
        volume_regime = volume_metrics['volume_regime']
        
        # 趋势状态判断
        if dominant_trend == 'up' and trend_metrics['medium_trend'] > self.thresholds['trend_strength']:
            if trend_metrics['ma_alignment'] == 'bullish_alignment':
                return 'trending_up'
        elif dominant_trend == 'down' and trend_metrics['medium_trend'] < -self.thresholds['trend_strength']:
            if trend_metrics['ma_alignment'] == 'bearish_alignment':
                return 'trending_down'
        
        # 波动状态判断
        if volatility_regime == 'high':
            return 'high_volatility'
        elif volatility_regime == 'low':
            return 'low_volatility'
        
        # 成交量状态判断
        if volume_metrics['volume_spike']:
            return 'breakout'
        
        # 默认状态
        return 'consolidation'
    
    def _identify_secondary_features(self, price_metrics, volume_metrics):
        """识别次要市场特征"""
        features = []
        
        # 突破特征
        if price_metrics['resistance_breakout']:
            features.append('resistance_breakout')
        elif price_metrics['support_breakdown']:
            features.append('support_breakdown')
        
        # 成交量特征
        if volume_metrics['volume_price_confirmation']:
            features.append('volume_confirmation')
        else:
            features.append('volume_divergence')
        
        # 位置特征
        if price_metrics['current_position'] > 0.7:
            features.append('near_resistance')
        elif price_metrics['current_position'] < 0.3:
            features.append('near_support')
        else:
            features.append('mid_range')
        
        return features
    
    def _calculate_state_confidence(self, trend_metrics, volatility_metrics):
        """计算状态判断的置信度"""
        confidence_factors = []
        
        # 趋势一致性
        trend_consistency = 1 - (np.std([
            trend_metrics['short_trend'],
            trend_metrics['medium_trend'], 
            trend_metrics['long_trend']
        ]) / 10)  # 标准化
        
        confidence_factors.append(max(0, min(1, trend_consistency)))
        
        # 波动率显著性
        vol_ratio = volatility_metrics['volatility_ratio']
        if vol_ratio > 1.5 or vol_ratio < 0.67:
            vol_confidence = 0.8
        else:
            vol_confidence = 0.5
        confidence_factors.append(vol_confidence)
        
        # 综合置信度
        return np.mean(confidence_factors)
    
    def get_state_description(self, state):
        """获取市场状态的详细描述"""
        descriptions = {
            'trending_up': '市场处于强势上升趋势，均线呈多头排列，适合趋势跟踪策略',
            'trending_down': '市场处于强势下降趋势，均线呈空头排列，适合反弹做空策略',
            'consolidation': '市场处于震荡整理阶段，价格在区间内波动，适合区间交易策略',
            'high_volatility': '市场波动率显著升高，价格变动剧烈，需要严格风险管理',
            'low_volatility': '市场波动率较低，价格变动平缓，交易机会较少',
            'breakout': '市场出现突破信号，成交量配合，可能开启新趋势',
            'reversal': '市场出现反转信号，原有趋势可能结束'
        }
        return descriptions.get(state, '市场状态不明确')
    
    def adjust_thresholds(self, historical_performance):
        """基于历史表现动态调整阈值"""
        # 这里可以根据历史回测结果优化阈值
        # 例如：如果趋势策略在某个阈值下表现更好，就调整趋势强度阈值
        pass
#[file content end]