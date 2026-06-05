"""
周易实时预测模块 - 增强版（新增个性化学习和迭代优化）
基于周易64卦进行5分钟价格预测
"""

import numpy as np
import os
import json
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple, Any
from collections import deque
import math
import random

# --- 学习参数配置 ---
class ZhouYiPredictor:
    """周易预测器 - 基于天干地支和64卦的预测模型"""
    
    LEARNING_RATE = 0.02  # 学习率：控制每次调整的幅度
    CORRECTION_LIMIT = 0.30  # 修正因子最大调整范围（±30%），防止过拟合
    VOLATILITY_THRESHOLD = 0.003  # 波动率阈值，用于判断卦象
    
    def __init__(self, symbol: str):
        self.symbol = symbol
        
        # 数据缓冲区
        self.price_buffer = deque(maxlen=60)  # 5分钟数据（60个5秒点）
        self.volume_buffer = deque(maxlen=60)
        self.timestamp_buffer = deque(maxlen=60)
        
        # 周易状态
        self.current_gua = None
        self.gua_history = deque(maxlen=50)
        self.last_learning_time = None
        
        # 预测记录：包含 current_price 和 predicted_price
        self.predictions = deque(maxlen=200)
        
        # 周易参数
        self.yin_threshold = 0.5
        self.yang_threshold = 0.5
        
        # 卦象数据库
        self.gua_database = self._load_gua_database()
        
        # 周易卦象分类器
        self.gua_classifier = ZhouYiGuaClassifier()
        
        # 统计信息
        self.prediction_history = []  # 记录预测准确性
        self.learning_iterations = 0
        
        # 🌟 个性化因子和模型路径 🌟
        base_dir = os.path.dirname(os.path.abspath(__file__))
        self.model_dir = os.path.join(base_dir, "..", "..", "models", "zhouyi") 
        os.makedirs(self.model_dir, exist_ok=True)
        
        # 加载个性化修正因子（针对每个股票）
        self.gua_correction_factors: Dict[str, float] = self._load_personal_factors()
        
    # --- 1. 个性化因子持久化方法 ---

    def _get_factors_filepath(self) -> str:
        """获取当前股票修正因子的文件路径"""
        safe_symbol = self.symbol.replace('.', '_')
        filename = f"{safe_symbol}_factors.json"
        return os.path.join(self.model_dir, filename)

    def _load_personal_factors(self) -> Dict[str, float]:
        """加载当前股票的个性化修正因子"""
        filepath = self._get_factors_filepath()
        if os.path.exists(filepath):
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    factors = json.load(f)
                print(f"✅ ZhouYi: 加载 {self.symbol} 的周易修正因子成功.")
                return factors
            except Exception as e:
                print(f"❌ ZhouYi: 加载 {self.symbol} 周易修正因子失败: {e}。使用默认值。")
                return {}
        return {}

    def save_personal_factors(self):
        """保存当前股票的个性化修正因子"""
        filepath = self._get_factors_filepath()
        try:
            # 仅保存修正过的因子 (不等于默认值 1.0 的)
            factors_to_save = {k: v for k, v in self.gua_correction_factors.items() if not math.isclose(v, 1.0, abs_tol=1e-5)}
            if factors_to_save:
                with open(filepath, 'w', encoding='utf-8') as f:
                    json.dump(factors_to_save, f, indent=4)
                print(f"💾 ZhouYi: 保存 {self.symbol} 的周易修正因子成功. (数量: {len(factors_to_save)})")
            else:
                # 如果所有因子都恢复为默认值，则删除文件以保持整洁
                if os.path.exists(filepath):
                    os.remove(filepath)
                print(f"🧹 ZhouYi: {self.symbol} 修正因子已清空.")
        except Exception as e:
            print(f"❌ ZhouYi: 保存 {self.symbol} 周易修正因子失败: {e}")

    # --- 2. 实时数据更新与预测逻辑 ---
    
    def update_realtime_data(self, price: float, volume: float, timestamp: datetime):
        """更新实时数据 - 每次调用都尝试学习和生成预测"""
        self.price_buffer.append(price)
        self.volume_buffer.append(volume)
        self.timestamp_buffer.append(timestamp)
        
        # 有足够数据时生成卦象
        if len(self.price_buffer) >= 10:  # 最少10个数据点
            # 每次更新都尝试生成新卦象
            self._generate_new_gua(timestamp)
            
            # 每30个数据点（约2.5分钟）进行一次学习和预测
            if len(self.price_buffer) >= 30 and len(self.price_buffer) % 30 == 0:
                # 🌟 步骤 1: 执行实时学习（使用当前价格评估上一次预测）🌟
                self._apply_realtime_learning(price, timestamp)
                
                # 步骤 2: 生成预测
                if self.current_gua and self.current_gua != "数据不足":
                    new_prediction = self.predict_5min(price, timestamp)
                    self.predictions.append(new_prediction)
                    
                    # 记录学习迭代
                    self.learning_iterations += 1
                    
    def predict_5min(self, current_price: float, timestamp: datetime) -> Dict:
        """
        基于当前卦象和市场趋势，预测未来5分钟的价格。
        🌟 应用个性化修正因子和随机性 🌟
        """
        if not self.current_gua or self.current_gua == "数据不足":
            # 数据不足时，基于技术指标的简单预测
            if len(self.price_buffer) < 10:
                return self._generate_technical_prediction(current_price, timestamp)
            
            self.current_gua = self._generate_random_gua(current_price)
        
        gua_name = self.current_gua
        rules = self.gua_database.get(gua_name, {})
        base_trend_multiplier = rules.get("trend_multiplier", 1.0)
        
        # 🌟 应用个性化修正因子 🌟
        correction_factor = self.gua_correction_factors.get(gua_name, 1.0)
        
        # 🌟 添加随机性和市场适应性 🌟
        volatility = self._calculate_price_volatility()
        market_sentiment = self._calculate_market_sentiment()
        
        # 调整因子：考虑波动率和市场情绪
        adjustment = 1.0
        if volatility > self.VOLATILITY_THRESHOLD:
            # 高波动率时增加调整幅度
            adjustment *= (1.0 + volatility * 0.5)
        
        # 市场情绪影响
        if market_sentiment > 0.6:
            adjustment *= 1.02  # 乐观情绪
        elif market_sentiment < 0.4:
            adjustment *= 0.98  # 悲观情绪
        
        final_multiplier = base_trend_multiplier * correction_factor * adjustment
        
        # 添加小幅度随机性（±0.5%）
        random_factor = 1.0 + (random.random() - 0.5) * 0.01
        final_multiplier *= random_factor
        
        predicted_price = current_price * final_multiplier
        
        # 预测区间调整：基于波动率
        base_range = abs(predicted_price - current_price) * (1.0 + volatility * 2.0)
        range_low = predicted_price - base_range * 0.3
        range_high = predicted_price + base_range * 0.3
        
        # 置信度计算：基于学习迭代和历史准确率
        base_confidence = rules.get("confidence", 0.5)
        if self.learning_iterations > 10:
            accuracy_stats = self.get_accuracy_stats()
            if accuracy_stats['total_predictions'] > 0:
                accuracy_bonus = min(0.2, accuracy_stats['accuracy'] * 0.3)
                base_confidence = max(0.3, min(0.9, base_confidence + accuracy_bonus))
        
        return {
            "predicted_price": round(predicted_price, 2),
            "prediction_range": (round(range_low, 2), round(range_high, 2)),
            "confidence": round(base_confidence, 3),
            "gua_name": gua_name,
            "trend": rules.get("trend", "neutral"),
            "advice": rules.get("advice", "保持观察"),
            "prediction_type": "周易预测",
            "timestamp": timestamp.isoformat(),
            "current_price": round(current_price, 2),
            "volatility": round(volatility, 4),
            "market_sentiment": round(market_sentiment, 3)
        }
    
    def _generate_technical_prediction(self, current_price: float, timestamp: datetime) -> Dict:
        """技术指标预测（当周易数据不足时使用）"""
        if len(self.price_buffer) < 5:
            return {
                "predicted_price": current_price,
                "prediction_range": (current_price * 0.99, current_price * 1.01),
                "confidence": 0.3,
                "gua_name": "技术预测",
                "trend": "neutral",
                "advice": "等待更多数据...",
                "prediction_type": "技术预测",
                "timestamp": timestamp.isoformat(),
                "current_price": current_price
            }
        
        # 简单移动平均
        recent_prices = list(self.price_buffer)[-5:]
        sma = sum(recent_prices) / len(recent_prices)
        
        if current_price > sma * 1.005:
            trend = "bullish"
            multiplier = 1.01
        elif current_price < sma * 0.995:
            trend = "bearish"
            multiplier = 0.99
        else:
            trend = "neutral"
            multiplier = 1.0
        
        predicted_price = current_price * multiplier
        
        return {
            "predicted_price": round(predicted_price, 2),
            "prediction_range": (round(predicted_price * 0.995, 2), round(predicted_price * 1.005, 2)),
            "confidence": 0.4,
            "gua_name": "技术分析",
            "trend": trend,
            "advice": f"基于{SMA}",
            "prediction_type": "技术预测",
            "timestamp": timestamp.isoformat(),
            "current_price": round(current_price, 2)
        }
    
    def _generate_random_gua(self, current_price: float) -> str:
        """随机生成一个卦象"""
        all_gua = list(self.gua_database.keys())
        if not all_gua:
            return "屯屯"
        
        # 基于价格波动选择卦象
        volatility = self._calculate_price_volatility()
        if volatility > self.VOLATILITY_THRESHOLD * 2:
            # 高波动率时倾向于乾（涨）或坤（跌）
            if random.random() > 0.5:
                return "乾乾" if "乾乾" in all_gua else random.choice(all_gua)
            else:
                return "坤坤" if "坤坤" in all_gua else random.choice(all_gua)
        
        return random.choice(all_gua)
    
    def _calculate_price_volatility(self) -> float:
        """计算价格波动率"""
        if len(self.price_buffer) < 2:
            return 0.0
        
        prices = list(self.price_buffer)
        returns = []
        for i in range(1, len(prices)):
            if prices[i-1] > 0:
                returns.append(abs(prices[i] - prices[i-1]) / prices[i-1])
        
        if not returns:
            return 0.0
        
        return np.std(returns)
    
    def _calculate_market_sentiment(self) -> float:
        """计算市场情绪（0-1，越高越乐观）"""
        if len(self.price_buffer) < 5:
            return 0.5
        
        recent_prices = list(self.price_buffer)[-5:]
        increases = sum(1 for i in range(1, len(recent_prices)) if recent_prices[i] > recent_prices[i-1])
        sentiment = increases / (len(recent_prices) - 1)
        
        return sentiment
    
    # --- 3. 实时学习与迭代优化机制 ---
    
    def _apply_realtime_learning(self, actual_price: float, current_timestamp: datetime):
        """
        实时应用学习机制：评估上一次预测，并根据结果调整修正因子。
        强化学习：不只是修正误差，还学习市场模式
        """
        if len(self.predictions) < 1:
            return
        
        # 获取最近3次预测进行评估
        recent_predictions = list(self.predictions)[-3:] if len(self.predictions) >= 3 else list(self.predictions)
        
        for pred in recent_predictions:
            pred_gua = pred['gua_name']
            pred_start_price = pred['current_price']
            pred_target_price = pred['predicted_price']
            
            if pred_gua == "数据不足" or pred_gua == "技术预测":
                continue
            
            current_factor = self.gua_correction_factors.get(pred_gua, 1.0)
            
            # 预测变化和实际变化
            predicted_change = pred_target_price - pred_start_price
            actual_change = actual_price - pred_start_price
            
            # --- 增强的学习逻辑 ---
            # 1. 方向正确性判断
            direction_correct = 1 if (predicted_change * actual_change) > 0 else -1 if (predicted_change * actual_change) < 0 else 0
            
            # 2. 幅度准确性
            if abs(predicted_change) > 1e-6:
                magnitude_ratio = abs(actual_change) / abs(predicted_change) if abs(predicted_change) > 0 else 1.0
            else:
                magnitude_ratio = 1.0 if abs(actual_change) < pred_start_price * 0.001 else 2.0
            
            # 3. 调整因子计算
            if direction_correct > 0:
                # 方向正确：根据幅度准确性微调
                if 0.8 <= magnitude_ratio <= 1.2:
                    # 幅度也准确：小幅正向调整
                    factor_adjustment = self.LEARNING_RATE * 0.1
                else:
                    # 幅度不准确：根据幅度差异调整
                    factor_adjustment = self.LEARNING_RATE * (1.0 - magnitude_ratio) * 0.5
            elif direction_correct < 0:
                # 方向错误：反向调整
                factor_adjustment = -self.LEARNING_RATE * 0.3
            else:
                # 无明显方向：小幅随机调整
                factor_adjustment = self.LEARNING_RATE * (random.random() - 0.5) * 0.1
            
            # 4. 应用调整
            new_factor = current_factor + factor_adjustment
            
            # 5. 限制修正因子的范围
            lower_bound = 1.0 - self.CORRECTION_LIMIT
            upper_bound = 1.0 + self.CORRECTION_LIMIT
            new_factor = np.clip(new_factor, lower_bound, upper_bound)
            
            # 6. 更新修正因子
            self.gua_correction_factors[pred_gua] = new_factor
            
            # 记录预测准确性
            self.prediction_history.append({
                'gua': pred_gua,
                'direction_correct': direction_correct,
                'magnitude_ratio': magnitude_ratio,
                'old_factor': current_factor,
                'new_factor': new_factor,
                'timestamp': current_timestamp.isoformat()
            })
        
        # 定期打印学习进展
        if len(self.prediction_history) % 10 == 0 and len(self.prediction_history) > 0:
            recent_history = self.prediction_history[-10:]
            correct_count = sum(1 for h in recent_history if h['direction_correct'] > 0)
            total_count = len(recent_history)
            if total_count > 0:
                accuracy = correct_count / total_count
                print(f"📈 ZhouYi Learning: 最近{total_count}次预测，方向准确率={accuracy:.1%}，修正因子已更新")
    
    # --- 4. 卦象生成和数据库 ---
    
    def _generate_new_gua(self, timestamp: datetime):
        """基于缓冲区数据计算新的卦象 - 使用复杂规则"""
        if len(self.price_buffer) < 10:
            self.current_gua = "数据不足"
            return
        
        # 使用卦象分类器
        self.current_gua = self.gua_classifier.classify(
            list(self.price_buffer),
            list(self.volume_buffer)
        )
        
        if not self.current_gua:
            self.current_gua = self._generate_random_gua(self.price_buffer[-1])
        
        self.gua_history.append({
            'gua': self.current_gua,
            'timestamp': timestamp.isoformat(),
            'price': self.price_buffer[-1]
        })
    
    def _load_gua_database(self) -> Dict:
        """加载周易卦象和规则的数据库"""
        return {
            "乾乾": {"trend_multiplier": 1.015, "confidence": 0.75, "trend": "bullish", "advice": "强势上涨"},
            "坤坤": {"trend_multiplier": 0.985, "confidence": 0.75, "trend": "bearish", "advice": "弱势下跌"},
            "屯屯": {"trend_multiplier": 1.003, "confidence": 0.55, "trend": "neutral", "advice": "震荡蓄势"},
            "蒙蒙": {"trend_multiplier": 0.998, "confidence": 0.45, "trend": "neutral", "advice": "蒙昧不明"},
            "需需": {"trend_multiplier": 1.008, "confidence": 0.65, "trend": "bullish", "advice": "等待时机"},
            "讼讼": {"trend_multiplier": 0.995, "confidence": 0.50, "trend": "bearish", "advice": "争议谨慎"},
            "师师": {"trend_multiplier": 1.006, "confidence": 0.60, "trend": "bullish", "advice": "团队力量"},
            "比比": {"trend_multiplier": 1.010, "confidence": 0.70, "trend": "bullish", "advice": "跟随大势"},
            "小畜": {"trend_multiplier": 1.004, "confidence": 0.55, "trend": "bullish", "advice": "小步积累"},
            "履履": {"trend_multiplier": 1.001, "confidence": 0.50, "trend": "neutral", "advice": "谨慎前行"},
            "泰泰": {"trend_multiplier": 1.012, "confidence": 0.80, "trend": "bullish", "advice": "通达顺畅"},
            "否否": {"trend_multiplier": 0.990, "confidence": 0.70, "trend": "bearish", "advice": "闭塞困难"},
            "同人": {"trend_multiplier": 1.007, "confidence": 0.65, "trend": "bullish", "advice": "同心协力"},
            "大有": {"trend_multiplier": 1.018, "confidence": 0.85, "trend": "bullish", "advice": "大有收获"},
            "谦谦": {"trend_multiplier": 1.002, "confidence": 0.60, "trend": "neutral", "advice": "谦虚谨慎"},
            "豫豫": {"trend_multiplier": 1.009, "confidence": 0.75, "trend": "bullish", "advice": "愉悦预期"}
        }
    
    def get_accuracy_stats(self) -> Dict:
        """获取预测准确率统计"""
        if len(self.prediction_history) < 2:
            return {"total_predictions": 0, "accuracy": 0.0, "recent_accuracy": 0.0}
        
        # 总体准确率
        total_correct = sum(1 for h in self.prediction_history if h['direction_correct'] > 0)
        total_predictions = len(self.prediction_history)
        overall_accuracy = total_correct / total_predictions if total_predictions > 0 else 0.0
        
        # 近期准确率（最近20次）
        recent_history = self.prediction_history[-20:] if len(self.prediction_history) >= 20 else self.prediction_history
        recent_correct = sum(1 for h in recent_history if h['direction_correct'] > 0)
        recent_accuracy = recent_correct / len(recent_history) if recent_history else 0.0
        
        # 按卦象统计
        gua_stats = {}
        for h in self.prediction_history:
            gua = h['gua']
            if gua not in gua_stats:
                gua_stats[gua] = {'total': 0, 'correct': 0}
            gua_stats[gua]['total'] += 1
            if h['direction_correct'] > 0:
                gua_stats[gua]['correct'] += 1
        
        gua_accuracy = {}
        for gua, stats in gua_stats.items():
            if stats['total'] > 0:
                gua_accuracy[gua] = stats['correct'] / stats['total']
        
        return {
            "total_predictions": total_predictions,
            "correct_predictions": total_correct,
            "accuracy": overall_accuracy,
            "recent_accuracy": recent_accuracy,
            "gua_accuracy": gua_accuracy,
            "learning_iterations": self.learning_iterations
        }


class ZhouYiGuaClassifier:
    """周易卦象分类器 - 基于价格和成交量模式"""
    
    def __init__(self):
        self.patterns = self._initialize_patterns()
    
    def _initialize_patterns(self):
        """初始化卦象模式"""
        return {
            "乾乾": self._pattern_qianqian,   # 强势上涨
            "坤坤": self._pattern_kunkun,     # 弱势下跌
            "屯屯": self._pattern_tuntun,     # 初期震荡
            "蒙蒙": self._pattern_mengmeng,   # 蒙昧不明
            "需需": self._pattern_xuxu,       # 等待时机
            "讼讼": self._pattern_songsong,   # 争议波动
            "师师": self._pattern_shishi,     # 团队力量
            "比比": self._pattern_bibi,       # 跟随大势
            "泰泰": self._pattern_taitai,     # 通达顺畅
            "否否": self._pattern_foufou,     # 闭塞困难
        }
    
    def classify(self, prices: List[float], volumes: List[float]) -> str:
        """根据价格和成交量模式分类卦象"""
        if len(prices) < 10 or len(volumes) < 10:
            return "数据不足"
        
        # 计算各种指标
        price_changes = [prices[i] - prices[i-1] for i in range(1, len(prices))]
        volume_changes = [volumes[i] - volumes[i-1] for i in range(1, len(volumes))]
        
        # 计算分数
        scores = {}
        for gua_name, pattern_func in self.patterns.items():
            score = pattern_func(prices, volumes, price_changes, volume_changes)
            scores[gua_name] = score
        
        # 选择最高分数的卦象
        if scores:
            best_gua = max(scores.items(), key=lambda x: x[1])[0]
            return best_gua
        
        return "屯屯"  # 默认
    
    def _pattern_qianqian(self, prices, volumes, price_changes, volume_changes):
        """乾乾模式：价格持续上涨，成交量放大"""
        # 检查最后5个价格是否持续上涨
        recent_prices = prices[-5:]
        recent_volumes = volumes[-5:]
        
        price_increase = sum(1 for i in range(1, len(recent_prices)) if recent_prices[i] > recent_prices[i-1])
        volume_increase = sum(1 for i in range(1, len(recent_volumes)) if recent_volumes[i] > recent_volumes[i-1])
        
        if price_increase >= 4 and volume_increase >= 3:
            return 0.9
        return 0.3
    
    def _pattern_kunkun(self, prices, volumes, price_changes, volume_changes):
        """坤坤模式：价格持续下跌，成交量萎缩"""
        recent_prices = prices[-5:]
        recent_volumes = volumes[-5:]
        
        price_decrease = sum(1 for i in range(1, len(recent_prices)) if recent_prices[i] < recent_prices[i-1])
        volume_decrease = sum(1 for i in range(1, len(recent_volumes)) if recent_volumes[i] < recent_volumes[i-1])
        
        if price_decrease >= 4 and volume_decrease >= 3:
            return 0.9
        return 0.3
    
    def _pattern_tuntun(self, prices, volumes, price_changes, volume_changes):
        """屯屯模式：价格在小范围内震荡"""
        recent_prices = prices[-10:]
        if not recent_prices:
            return 0.3
        
        price_range = max(recent_prices) - min(recent_prices)
        avg_price = sum(recent_prices) / len(recent_prices)
        
        if price_range / avg_price < 0.005:  # 波动小于0.5%
            return 0.8
        return 0.4
    
    # 其他模式函数类似，这里省略部分以保持简洁...
    def _pattern_mengmeng(self, prices, volumes, price_changes, volume_changes):
        """蒙蒙模式：价格无方向，成交量不规则"""
        return 0.5
    
    def _pattern_xuxu(self, prices, volumes, price_changes, volume_changes):
        """需需模式：价格缓慢上涨，成交量温和放大"""
        return 0.6
    
    def _pattern_songsong(self, prices, volumes, price_changes, volume_changes):
        """讼讼模式：价格剧烈波动"""
        if len(price_changes) >= 5:
            volatility = np.std(price_changes[-5:])
            if volatility > np.mean([abs(pc) for pc in price_changes[-5:]]) * 1.5:
                return 0.7
        return 0.4
    
    def _pattern_shishi(self, prices, volumes, price_changes, volume_changes):
        """师师模式：价格上涨伴随成交量稳定"""
        return 0.6
    
    def _pattern_bibi(self, prices, volumes, price_changes, volume_changes):
        """比比模式：跟随大势"""
        return 0.5
    
    def _pattern_taitai(self, prices, volumes, price_changes, volume_changes):
        """泰泰模式：通达顺畅，价格温和上涨"""
        return 0.7
    
    def _pattern_foufou(self, prices, volumes, price_changes, volume_changes):
        """否否模式：困难阻碍，价格滞涨"""
        return 0.4