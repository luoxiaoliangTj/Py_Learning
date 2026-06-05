# algorithms/TradePlanLearner.py
"""
TradePlanLearner - 基于回测结果的交易模式学习模块
即时学习版本 - 每次预测都从最新数据学习
"""

import pandas as pd
import numpy as np
import json
import os
from datetime import datetime, timedelta
from collections import defaultdict
import warnings
warnings.filterwarnings('ignore')

class MarketStateAnalyzer:
    """市场状态分析器 - 即时学习版本"""
    
    def __init__(self):
        self.learned_thresholds = None
        self.last_learning_symbol = None
        self.last_learning_hash = None
    
    def _calculate_window_stats(self, df_window):
        """计算窗口内的趋势和波动率"""
        if len(df_window) < 5:
            return None, None
        
        # 找到价格列
        price_col = self._get_price_column_name(df_window)
        if price_col is None:
            return None, None
        
        close_prices = df_window[price_col]
        
        # 计算趋势
        current_price = close_prices.iloc[-1]
        past_price = close_prices.iloc[0]
        trend = (current_price - past_price) / past_price if past_price != 0 else 0
        
        # 计算波动率
        returns = close_prices.pct_change().dropna()
        volatility = returns.std() if len(returns) > 0 else 0.02
        
        return trend, volatility
    
    def _get_price_column_name(self, df):
        """获取价格列名"""
        for col in ['收盘', 'STOCK_Close', 'close', 'Close', 'CLOSE']:
            if col in df.columns:
                return col
        return None
    
    def _learn_thresholds_from_data(self, df_daily, lookback_days=30):
        """从历史数据学习阈值"""
        if len(df_daily) < lookback_days * 2:
            # 数据不足，使用经验值
            return {
                'bull': {'trend_threshold': 0.02, 'volatility_threshold': 0.015},
                'bear': {'trend_threshold': -0.02, 'volatility_threshold': 0.025},
                'normal': {'trend_threshold': 0, 'volatility_threshold': 0.02}
            }
        
        trends = []
        volatilities = []
        
        # 滑动窗口分析历史数据
        step = max(1, len(df_daily) // 20)  # 采样约20个窗口
        for i in range(lookback_days, len(df_daily), step):
            df_window = df_daily.iloc[max(0, i-lookback_days):i]
            trend, volatility = self._calculate_window_stats(df_window)
            if trend is not None and volatility is not None:
                trends.append(trend)
                volatilities.append(volatility)
        
        # 基于历史分布确定自适应阈值
        if trends and volatilities:
            # 使用分位数确定阈值
            trend_sorted = sorted(trends)
            vol_sorted = sorted(volatilities)
            
            # 取33%和67%分位数
            idx_33 = int(len(trend_sorted) * 0.33)
            idx_67 = int(len(trend_sorted) * 0.67)
            
            trend_33 = trend_sorted[idx_33] if idx_33 < len(trend_sorted) else -0.02
            trend_67 = trend_sorted[idx_67] if idx_67 < len(trend_sorted) else 0.02
            
            vol_33 = vol_sorted[idx_33] if idx_33 < len(vol_sorted) else 0.015
            vol_67 = vol_sorted[idx_67] if idx_67 < len(vol_sorted) else 0.025
            
            # 确保阈值合理
            bull_trend = max(0.01, trend_67)
            bull_vol = max(0.01, min(0.05, vol_33))
            bear_trend = min(-0.01, trend_33)
            bear_vol = max(0.015, min(0.1, vol_67))
            
            return {
                'bull': {'trend_threshold': bull_trend, 'volatility_threshold': bull_vol},
                'bear': {'trend_threshold': bear_trend, 'volatility_threshold': bear_vol},
                'normal': {'trend_threshold': (bull_trend + bear_trend) / 2, 
                          'volatility_threshold': (bull_vol + bear_vol) / 2}
            }
        else:
            # 默认阈值
            return {
                'bull': {'trend_threshold': 0.02, 'volatility_threshold': 0.015},
                'bear': {'trend_threshold': -0.02, 'volatility_threshold': 0.025},
                'normal': {'trend_threshold': 0, 'volatility_threshold': 0.02}
            }
    
    def _calculate_data_hash(self, df_daily):
        """计算数据哈希，用于检测数据变化"""
        if len(df_daily) < 10:
            return "short"
        
        price_col = self._get_price_column_name(df_daily)
        if price_col is None:
            return "no_price"
        
        # 使用最后10个价格点的哈希
        last_prices = df_daily[price_col].iloc[-10:].values.tobytes()
        import hashlib
        return hashlib.md5(last_prices).hexdigest()[:8]
    
    def detect_market_state(self, df_daily, lookback_days=30):
        """检测当前市场状态 - 即时学习版本"""
        if len(df_daily) < lookback_days:
            return "normal"
        
        # 检查是否需要重新学习
        current_hash = self._calculate_data_hash(df_daily)
        need_relearn = (self.learned_thresholds is None or 
                       self.last_learning_hash != current_hash)
        
        if need_relearn:
            self.learned_thresholds = self._learn_thresholds_from_data(df_daily, lookback_days)
            self.last_learning_hash = current_hash
        
        # 找到价格列
        price_col = self._get_price_column_name(df_daily)
        if price_col is None:
            return "normal"
        
        close_prices = df_daily[price_col]
        
        # 计算当前趋势
        current_price = close_prices.iloc[-1]
        price_lookback = close_prices.iloc[-lookback_days]
        trend = (current_price - price_lookback) / price_lookback
        
        # 计算当前波动率
        returns = close_prices.pct_change().dropna()
        if len(returns) >= lookback_days:
            volatility = returns.rolling(lookback_days).std().iloc[-1]
        else:
            volatility = returns.std() if len(returns) > 0 else 0.02
        
        if pd.isna(volatility):
            volatility = 0.02
        
        # 使用学习到的阈值判断
        thresholds = self.learned_thresholds
        
        if trend > thresholds['bull']['trend_threshold'] and volatility < thresholds['bull']['volatility_threshold']:
            return "bull"
        elif trend < thresholds['bear']['trend_threshold'] and volatility > thresholds['bear']['volatility_threshold']:
            return "bear"
        else:
            return "normal"

class HoldingPeriodLearner:
    """持有周期学习器 - 即时学习版本"""
    
    def __init__(self, backtest_dir):
        self.backtest_dir = backtest_dir
        self.market_analyzer = MarketStateAnalyzer()
        
    def analyze_holding_patterns(self, symbol, df_daily):
        """分析不同市场状态下的持有周期模式"""
        trades_file = os.path.join(self.backtest_dir, f"{symbol}_trades.csv")
        
        # 检查回测文件是否存在
        if not os.path.exists(trades_file):
            return self._get_default_holding_periods()
            
        try:
            trades_df = pd.read_csv(trades_file)
            if len(trades_df) == 0:
                return self._get_default_holding_periods()
                
            trades_df['date'] = pd.to_datetime(trades_df['date'])
            
            # 准备df_daily的日期列
            if '日期' not in df_daily.columns and 'date' not in df_daily.columns:
                df_daily = df_daily.copy()
                if '日期' in df_daily.columns:
                    df_daily['date'] = pd.to_datetime(df_daily['日期'])
                else:
                    start_date = datetime.now() - timedelta(days=len(df_daily)*2)
                    df_daily['date'] = pd.date_range(start=start_date, periods=len(df_daily), freq='D')
            else:
                date_col = '日期' if '日期' in df_daily.columns else 'date'
                df_daily = df_daily.copy()
                df_daily['date'] = pd.to_datetime(df_daily[date_col])
            
            df_daily = df_daily.sort_values('date').reset_index(drop=True)
            
            # 按市场状态分组分析持有周期
            holding_periods_by_state = {
                'bull': [],
                'bear': [], 
                'normal': []
            }
            
            valid_trades_count = 0
            
            for i in range(len(trades_df)-1):
                if trades_df.iloc[i]['type'] == 'buy' and trades_df.iloc[i+1]['type'] == 'sell':
                    buy_date = trades_df.iloc[i]['date']
                    sell_date = trades_df.iloc[i+1]['date']
                    
                    # 计算持有天数
                    holding_days = (pd.to_datetime(sell_date) - pd.to_datetime(buy_date)).days
                    if holding_days <= 0:
                        continue
                        
                    # 获取交易期间的市场状态
                    try:
                        trade_period_data = df_daily[
                            (df_daily['date'] >= pd.to_datetime(buy_date)) & 
                            (df_daily['date'] <= pd.to_datetime(sell_date))
                        ]
                        
                        if len(trade_period_data) > 0:
                            market_state = self.market_analyzer.detect_market_state(trade_period_data)
                            holding_periods_by_state[market_state].append(holding_days)
                            valid_trades_count += 1
                    except Exception as e:
                        continue
            
            if valid_trades_count == 0:
                return self._get_default_holding_periods()
                
            return self._summarize_holding_periods(holding_periods_by_state)
            
        except Exception as e:
            return self._get_default_holding_periods()
    
    def _get_default_holding_periods(self):
        """获取默认持有周期"""
        return {
            'bull': {'min_days': 3, 'max_days': 10, 'typical_days': 5, 'confidence': 0.1},
            'bear': {'min_days': 1, 'max_days': 5, 'typical_days': 3, 'confidence': 0.1},
            'normal': {'min_days': 3, 'max_days': 8, 'typical_days': 5, 'confidence': 0.1}
        }
    
    def _summarize_holding_periods(self, holding_periods_by_state):
        """汇总不同市场状态的持有周期"""
        result = {}
        
        for state, periods in holding_periods_by_state.items():
            if len(periods) > 0:
                result[state] = {
                    'min_days': int(np.min(periods)),
                    'max_days': int(np.max(periods)),
                    'typical_days': int(np.median(periods)),
                    'confidence': min(len(periods) / 10, 1.0)
                }
            else:
                default = self._get_default_holding_periods()[state]
                result[state] = default
                
        return result

class PriceProbabilityCalculator:
    """价格概率计算器 - 基于历史频率版本"""
    
    def __init__(self, data_dir):
        self.data_dir = data_dir
        
    def calculate_probabilities(self, df_daily, current_price, pred_low, pred_high, 
                              volatility, trend_strength, market_state, backtest_stats):
        """计算价格区间的概率分布 - 基于历史频率"""
        
        # 生成基础价格点
        price_points = np.linspace(pred_low, pred_high, 15)
        
        # 基于历史频率计算概率
        historical_probs = self._calculate_historical_frequency(
            df_daily, current_price, price_points
        )
        
        # 趋势调整
        trend_adjusted = self._adjust_for_trend(
            historical_probs, current_price, trend_strength
        )
        
        # 市场状态调整
        market_adjusted = self._adjust_for_market_state(
            trend_adjusted, market_state
        )
        
        # 回测结果调整
        backtest_adjusted = self._adjust_for_backtest(
            market_adjusted, backtest_stats
        )
        
        return backtest_adjusted
    
    def _calculate_historical_frequency(self, df_daily, current_price, price_points):
        """计算历史频率"""
        if len(df_daily) < 100:
            return self._create_base_probabilities(current_price, price_points)
        
        # 获取价格列
        price_col = self._get_price_column_name(df_daily)
        if price_col is None:
            return self._create_base_probabilities(current_price, price_points)
        
        probabilities = {}
        
        # 分析历史数据中价格达到各点的频率
        for target_price in price_points:
            hit_count = 0
            total_cases = 0
            
            for i in range(50, len(df_daily)-10):
                past_price = df_daily[price_col].iloc[i]
                future_prices = df_daily[price_col].iloc[i+1:i+11]  # 未来10天
                
                # 如果过去价格与当前价格相似（在10%以内）
                if abs(past_price - current_price) / current_price < 0.1:
                    # 检查未来是否达到目标价格
                    if any(abs(fp - target_price) / target_price < 0.02 for fp in future_prices):
                        hit_count += 1
                    total_cases += 1
            
            # 计算概率
            if total_cases > 0:
                prob = hit_count / total_cases
            else:
                # 没有足够历史数据，使用距离衰减
                distance_ratio = abs(target_price - current_price) / (max(price_points) - min(price_points))
                prob = max(0.1, 0.5 - distance_ratio)
            
            probabilities[round(target_price, 2)] = max(0.05, min(0.95, prob))
        
        return probabilities
    
    def _get_price_column_name(self, df):
        """获取价格列名"""
        for col in ['收盘', 'STOCK_Close', 'close', 'Close', 'CLOSE']:
            if col in df.columns:
                return col
        return None
    
    def _create_base_probabilities(self, current_price, price_points):
        """创建基础概率分布"""
        probabilities = {}
        mid_price = np.median(price_points)
        
        for price in price_points:
            # 基于距离当前价格的距离计算概率
            distance_ratio = abs(price - current_price) / (max(price_points) - min(price_points))
            base_prob = 0.6 - distance_ratio * 0.5
            
            # 当前价格附近有更高概率
            if abs(price - current_price) < current_price * 0.01:
                base_prob = 0.7
            
            probabilities[round(price, 2)] = max(0.05, min(0.9, base_prob))
        
        return probabilities
    
    def _adjust_for_trend(self, probabilities, current_price, trend_strength):
        """根据趋势调整概率"""
        if abs(trend_strength) < 0.005:
            return probabilities
        
        adjusted_probs = {}
        
        for price, prob in probabilities.items():
            adjustment = 1.0
            
            if trend_strength > 0:  # 上升趋势
                if price > current_price:
                    adjustment = 1 + abs(trend_strength) * 3
                else:
                    adjustment = 1 - abs(trend_strength) * 2
                    
            elif trend_strength < 0:  # 下降趋势
                if price < current_price:
                    adjustment = 1 + abs(trend_strength) * 3
                else:
                    adjustment = 1 - abs(trend_strength) * 2
            
            adjusted_prob = prob * adjustment
            adjusted_probs[price] = max(0.05, min(0.95, adjusted_prob))
            
        return adjusted_probs
    
    def _adjust_for_market_state(self, probabilities, market_state):
        """根据市场状态调整概率"""
        state_factors = {
            'bull': {'high_bias': 1.2, 'low_bias': 0.8},
            'bear': {'high_bias': 0.8, 'low_bias': 1.2},
            'normal': {'high_bias': 1.0, 'low_bias': 1.0}
        }
        
        factor = state_factors[market_state]
        adjusted_probs = {}
        prices = list(probabilities.keys())
        mid_price = np.median(prices)
        
        for price, prob in probabilities.items():
            if price > mid_price:
                adjustment = factor['high_bias']
            else:
                adjustment = factor['low_bias']
                
            adjusted_probs[price] = max(0.05, min(0.95, prob * adjustment))
            
        return adjusted_probs
    
    def _adjust_for_backtest(self, probabilities, backtest_stats):
        """根据回测结果调整概率"""
        win_rate = backtest_stats.get('win_rate', 0.5)
        sharpe_ratio = backtest_stats.get('sharpe_ratio', 0)
        
        # 计算调整因子
        win_adjustment = 0.5 + win_rate  # 胜率0.5 -> 1.0, 胜率0.7 -> 1.2
        
        if sharpe_ratio > 0.8:
            sharpe_adjustment = 1.1
        elif sharpe_ratio < 0.2:
            sharpe_adjustment = 0.9
        else:
            sharpe_adjustment = 1.0
        
        total_adjustment = win_adjustment * sharpe_adjustment
        
        adjusted_probs = {}
        for price, prob in probabilities.items():
            adjusted_prob = prob * total_adjustment
            adjusted_probs[price] = max(0.05, min(0.95, adjusted_prob))
            
        return adjusted_probs

class TradePlanLearner:
    """交易预案学习器 - 即时学习版本"""
    
    def __init__(self, backtest_dir, data_dir):
        self.backtest_dir = backtest_dir
        self.data_dir = data_dir
        self.market_analyzer = MarketStateAnalyzer()
        self.holding_learner = HoldingPeriodLearner(backtest_dir)
        self.probability_calculator = PriceProbabilityCalculator(data_dir)
        
    def generate_trade_plan(self, symbol, df_daily, current_price, pred_low, pred_high, 
                          volatility, trend_strength, available_cash, backtest_stats):
        """生成完整的交易预案 - 即时学习版本"""
        
        try:
            # 1. 检测当前市场状态（即时学习）
            market_state = self.market_analyzer.detect_market_state(df_daily)
            
            # 2. 学习持有周期
            holding_periods = self.holding_learner.analyze_holding_patterns(symbol, df_daily)
            current_holding = holding_periods[market_state]
            
            # 3. 计算价格概率（基于历史频率）
            price_probs = self.probability_calculator.calculate_probabilities(
                df_daily, current_price, pred_low, pred_high, 
                volatility, trend_strength, market_state, backtest_stats
            )
            
            # 4. 生成买入卖出场景
            buy_scenarios = self._generate_buy_scenarios(
                price_probs, current_price, available_cash, market_state, backtest_stats
            )
            sell_scenarios = self._generate_sell_scenarios(
                price_probs, current_price, market_state, backtest_stats
            )
            
            # 5. 计算预期盈利
            expected_profit = self._calculate_expected_profit(buy_scenarios, sell_scenarios, current_holding)
            
            # 6. 计算可行性评分
            feasibility_score = self._calculate_feasibility_score(
                buy_scenarios, current_holding, price_probs, market_state, backtest_stats
            )
            
            return {
                'market_state': market_state,
                'holding_period': current_holding,
                'price_probabilities': price_probs,  # 包含价格概率
                'buy_scenarios': buy_scenarios,
                'sell_scenarios': sell_scenarios,
                'expected_profit': expected_profit,
                'feasibility_score': feasibility_score,
                'risk_level': self._assess_risk_level(feasibility_score, volatility, market_state, backtest_stats)
            }
            
        except Exception as e:
            # 返回基础预案
            return self._create_basic_plan(current_price, pred_low, pred_high, market_state)
    
    def _create_basic_plan(self, current_price, pred_low, pred_high, market_state):
        """创建基础交易预案"""
        price_range = pred_high - pred_low
        
        # 创建基础价格概率
        price_probs = {}
        price_points = np.linspace(pred_low, pred_high, 10)
        for price in price_points:
            distance = abs(price - current_price) / price_range
            prob = max(0.1, 0.6 - distance * 0.5)
            price_probs[round(price, 2)] = prob
        
        return {
            'market_state': market_state,
            'holding_period': {'min_days': 3, 'max_days': 10, 'typical_days': 5, 'confidence': 0.1},
            'price_probabilities': price_probs,
            'expected_profit': price_range * 0.3,
            'feasibility_score': 0.5,
            'risk_level': 'medium',
            'buy_scenarios': [
                {
                    'price': round(pred_low * 1.02, 2),
                    'probability': 0.6,
                    'quantity': 100,
                    'investment': round(pred_low * 1.02 * 100, 2),
                    'reason': '接近支撑位'
                }
            ],
            'sell_scenarios': [
                {
                    'price': round(pred_high * 0.98, 2),
                    'probability': 0.5,
                    'type': '止盈',
                    'potential_return': round((pred_high * 0.98 - current_price) / current_price * 100, 1),
                    'reason': '接近阻力位'
                }
            ],
            'note': '基于技术分析的基础交易预案'
        }
    
    def _generate_buy_scenarios(self, price_probs, current_price, available_cash, market_state, backtest_stats):
        """生成买入场景"""
        buy_scenarios = []
        prices = sorted(price_probs.keys())
        
        # 选择低于当前价2%以上的价格点
        buy_candidates = [p for p in prices if p < current_price * 0.98]
        
        for price in buy_candidates[:3]:  # 最多3个买入场景
            prob = price_probs[price]
            if prob < 0.15:
                continue
                
            # 计算买入数量
            quantity = self._calculate_buy_quantity(price, available_cash, prob, market_state, backtest_stats)
            
            buy_scenarios.append({
                'price': price,
                'probability': round(prob, 3),
                'quantity': quantity,
                'investment': round(price * quantity, 2),
                'reason': f"支撑位买入，概率{prob:.1%}"
            })
        
        return sorted(buy_scenarios, key=lambda x: x['probability'], reverse=True)
    
    def _generate_sell_scenarios(self, price_probs, current_price, market_state, backtest_stats):
        """生成卖出场景"""
        sell_scenarios = []
        prices = sorted(price_probs.keys())
        
        # 选择高于当前价2%以上的价格点
        sell_candidates = [p for p in prices if p > current_price * 1.02]
        
        for price in sell_candidates[:3]:  # 最多3个卖出场景
            prob = price_probs[price]
            if prob < 0.15:
                continue
                
            # 确定卖出类型
            win_rate = backtest_stats.get('win_rate', 0.5)
            if win_rate > 0.6 and price > current_price * 1.08:
                sell_type = "激进止盈"
            elif price > current_price * 1.05:
                sell_type = "止盈"
            else:
                sell_type = "目标卖出"
            
            sell_scenarios.append({
                'price': price,
                'probability': round(prob, 3),
                'type': sell_type,
                'potential_return': round((price - current_price) / current_price * 100, 1),
                'reason': f"{sell_type}点位，概率{prob:.1%}"
            })
        
        return sorted(sell_scenarios, key=lambda x: x['probability'], reverse=True)
    
    def _calculate_buy_quantity(self, price, available_cash, probability, market_state, backtest_stats):
        """计算买入数量"""
        sharpe_ratio = backtest_stats.get('sharpe_ratio', 0)
        if sharpe_ratio > 1.0:
            base_risk = 0.6
        elif sharpe_ratio > 0.5:
            base_risk = 0.4
        else:
            base_risk = 0.2
            
        state_factors = {'bull': 1.2, 'normal': 1.0, 'bear': 0.8}
        state_factor = state_factors[market_state]
        
        risk_factor = base_risk * state_factor * probability
        
        max_investment = available_cash * risk_factor
        quantity = int(max_investment / (price * 1.002))
        
        quantity = max(100, quantity)
        quantity = (quantity // 100) * 100
        
        return quantity
    
    def _calculate_expected_profit(self, buy_scenarios, sell_scenarios, holding_period):
        """计算预期盈利"""
        if not buy_scenarios or not sell_scenarios:
            return 0.0
            
        best_buy = buy_scenarios[0]
        best_sell = sell_scenarios[0]
        
        gross_profit = (best_sell['price'] - best_buy['price']) * best_buy['quantity']
        trade_cost = best_buy['investment'] * 0.0015 + gross_profit * 0.001
        time_adjustment = 1 - (holding_period['typical_days'] / 365 * 0.03)
        
        net_profit = gross_profit * time_adjustment - trade_cost
        
        return round(max(0, net_profit), 2)
    
    def _calculate_feasibility_score(self, buy_scenarios, holding_period, price_probs, market_state, backtest_stats):
        """计算可行性评分"""
        if not buy_scenarios:
            return 0.0
            
        buy_prob_score = np.mean([s['probability'] for s in buy_scenarios]) * 0.4
        holding_confidence_score = holding_period['confidence'] * 0.3
        price_range_score = min(1.0, len(price_probs) / 5) * 0.2
        market_state_score = 1.0 if market_state == 'normal' else 0.7
        backtest_score = self._calculate_backtest_score(backtest_stats) * 0.3
        
        total_score = (buy_prob_score + holding_confidence_score + price_range_score + backtest_score) * market_state_score
        
        return round(min(1.0, total_score), 3)
    
    def _calculate_backtest_score(self, backtest_stats):
        """计算回测性能得分"""
        sharpe_score = min(1.0, backtest_stats.get('sharpe_ratio', 0) / 2.0)
        win_rate_score = backtest_stats.get('win_rate', 0)
        trades_score = min(1.0, backtest_stats.get('total_trades', 0) / 20)
        
        return (sharpe_score * 0.5 + win_rate_score * 0.3 + trades_score * 0.2)
    
    def _assess_risk_level(self, feasibility_score, volatility, market_state, backtest_stats):
        """评估风险等级"""
        base_risk = 1 - feasibility_score
        vol_adjustment = min(1.0, volatility * 50)
        state_adjustment = 1.2 if market_state == 'bear' else 0.8 if market_state == 'bull' else 1.0
        
        sharpe_ratio = backtest_stats.get('sharpe_ratio', 0)
        if sharpe_ratio > 1.0:
            backtest_risk_adjustment = 0.7
        elif sharpe_ratio < 0.3:
            backtest_risk_adjustment = 1.3
        else:
            backtest_risk_adjustment = 1.0
        
        risk_score = base_risk * vol_adjustment * state_adjustment * backtest_risk_adjustment
        
        if risk_score < 0.3:
            return "低风险"
        elif risk_score < 0.6:
            return "中风险"
        else:
            return "高风险"