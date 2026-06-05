# TradingAdvisorModule.py
#!/usr/bin/env python3
"""
TradingAdvisorModule.py - 智能交易建议模块（持仓持久化版）
功能：提供持仓管理、动态止盈止损、交易建议生成
版本: 2.0 (独立模块版)
"""
import os, sys, time, requests
from datetime import datetime, timedelta
import json
import numpy as np
import pandas as pd
from datetime import datetime

# ------------- 路径插入 -------------
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)

# 导入配置 - 使用新的配置文件结构
try:
    from config.config import cfg
    print(f"✅ 成功导入配置，数据目录: {cfg.DATA_DIR}")
except ImportError as e:
    print(f"❌ 导入配置失败: {e}")
    # 创建默认配置 - 移除硬编码股票代码
    class Config:
        BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        DATA_DIR = os.path.join(BASE_DIR, "data")
        LOG_DIR = os.path.join(BASE_DIR, "logs")
        RESULT_DIR = os.path.join(BASE_DIR, "realtime_results")
        BACKTEST_DIR = os.path.join(BASE_DIR, "backtest_results")
        ALGO_DIR = os.path.join(BASE_DIR, "algorithms")
        # 移除硬编码的股票代码和名称
        STOCK_SYMBOL = ""  # 改为空字符串，强制调用方提供
        STOCK_NAME = ""    # 改为空字符串，强制调用方提供
    cfg = Config()

# 创建简单的日志配置
def get_logger(name):
    import logging
    logger = logging.getLogger(name)
    if not logger.handlers:
        handler = logging.StreamHandler()
        formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
        handler.setFormatter(formatter)
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
    return logger

logger = get_logger("TradingAdvisorModule")

class TradingAdvisorModule:
    """
    智能交易建议模块 - 独立模块版本
    支持持仓管理、动态止盈止损、交易建议生成
    """

    def __init__(self):
        self.holding_info = None
        # 费率调整：基于建行实际交易费率
        self.buy_commission_rate = 0.000131  # 买入佣金率：万分之1.31
        self.sell_commission_rate = 0.000131  # 卖出佣金率：万分之1.31
        self.stamp_duty_rate = 0.0005  # 印花税率：0.05%（仅卖出）
        self.other_fees_rate = 0.00001  # 其他费用率：万分之0.1
        self.min_commission = 5.00  # 最低佣金：5元
        
        # 动态参数
        self.aggressive_factor = 0.5  # 风险偏好系数
        self.t_position_ratio = 0.0   # 做T仓位比例
        self.dynamic_thresholds = {}  # 动态阈值存储
        
        # 持仓文件路径 - 使用配置中的数据目录
        self.holdings_file = os.path.join(cfg.DATA_DIR, "stock_holdings.json")
        
        # 全局资金文件路径
        self.capital_file = os.path.join(cfg.DATA_DIR, "global_capital.json")
        
        # 交易历史记录
        self.trading_history = []
        self.advice_history = []
        
        # 资金管理
        self.available_cash = 0.0  # 可用资金
        self.total_capital = 0.0   # 总资金（现金+持仓价值）
        
        # 当前处理的股票信息 - 新增字段
        self.current_symbol = ""
        self.current_name = ""
        
        # 加载全局资金配置
        self.load_global_capital()
        
        print("✅ 智能交易建议模块初始化完成")

    def ensure_holdings_file(self):
        """确保持仓文件存在"""
        os.makedirs(cfg.DATA_DIR, exist_ok=True)
        if not os.path.exists(self.holdings_file):
            with open(self.holdings_file, 'w', encoding='utf-8') as f:
                json.dump({}, f, ensure_ascii=False, indent=2)

    def load_global_capital(self):
        """加载全局资金配置"""
        try:
            if os.path.exists(self.capital_file):
                with open(self.capital_file, 'r', encoding='utf-8') as f:
                    capital_data = json.load(f)
                
                self.available_cash = capital_data.get('available_cash', 100000.0)
                self.total_capital = capital_data.get('total_capital', self.available_cash)
                logger.info(f"💰 已加载全局资金: 可用{self.available_cash:.2f}, 总资金{self.total_capital:.2f}")
            else:
                # 默认资金
                self.available_cash = 100000.0
                self.total_capital = 100000.0
                self.save_global_capital()
                logger.info("💰 使用默认全局资金: 100,000.00")
                
        except Exception as e:
            logger.error(f"❌ 加载全局资金配置失败: {e}")
            self.available_cash = 100000.0
            self.total_capital = 100000.0

    def save_global_capital(self):
        """保存全局资金配置"""
        try:
            capital_data = {
                'available_cash': self.available_cash,
                'total_capital': self.total_capital,
                'last_updated': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                'note': '全局资金配置，适用于所有股票回测'
            }
            
            with open(self.capital_file, 'w', encoding='utf-8') as f:
                json.dump(capital_data, f, ensure_ascii=False, indent=2)
                
            logger.info(f"✅ 全局资金配置已保存: {self.capital_file}")
            
        except Exception as e:
            logger.error(f"❌ 保存全局资金配置失败: {e}")

    def update_global_capital(self, available_cash=None, total_capital=None):
        """更新全局资金配置（用户主动更改时调用）"""
        if available_cash is not None:
            self.available_cash = available_cash
        if total_capital is not None:
            self.total_capital = total_capital
        
        self.save_global_capital()
        logger.info(f"💰 全局资金已更新: 可用{self.available_cash:.2f}, 总资金{self.total_capital:.2f}")

    def load_holding_info(self, stock_symbol):
        """从文件加载持仓信息"""
        try:
            self.ensure_holdings_file()
            with open(self.holdings_file, 'r', encoding='utf-8') as f:
                holdings_data = json.load(f)
            
            if stock_symbol in holdings_data:
                holding = holdings_data[stock_symbol]
                self.holding_info = {
                    'shares': holding['shares'],
                    'cost_price': holding['cost_price'],
                    'total_cost': holding.get('total_cost', holding['shares'] * holding['cost_price'])
                }
                self.aggressive_factor = holding.get('aggressive_factor', 0.5)
                self.t_position_ratio = holding.get('t_position_ratio', 0.0)
                logger.info(f"✅ 已加载持仓: {stock_symbol} - {holding['stock_name']}")
                return True
            return False
        except Exception as e:
            logger.error(f"❌ 加载持仓信息失败: {e}")
            return False

    def _save_holding_info(self, stock_symbol, stock_name):
        """保存持仓信息到文件"""
        try:
            # 读取现有数据
            if os.path.exists(self.holdings_file):
                with open(self.holdings_file, 'r', encoding='utf-8') as f:
                    holdings_data = json.load(f)
            else:
                holdings_data = {}
            
            # 更新当前股票持仓
            holdings_data[stock_symbol] = {
                'stock_name': stock_name,
                'shares': self.holding_info['shares'],
                'cost_price': self.holding_info['cost_price'],
                'total_cost': self.holding_info.get('total_cost', self.holding_info['shares'] * self.holding_info['cost_price']),
                'aggressive_factor': self.aggressive_factor,
                't_position_ratio': self.t_position_ratio,
                'last_updated': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            }
            
            # 写回文件
            with open(self.holdings_file, 'w', encoding='utf-8') as f:
                json.dump(holdings_data, f, ensure_ascii=False, indent=2)
                
            logger.info(f"✅ 持仓信息已保存到: {self.holdings_file}")
            
        except Exception as e:
            logger.error(f"❌ 保存持仓信息失败: {e}")

    def update_holding_info(self, stock_symbol, stock_name, shares=None, cost_price=None, 
                          aggressive_factor=None, t_position_ratio=None):
        """更新持仓信息（程序化更新）"""
        try:
            # 设置当前处理的股票信息
            self.current_symbol = stock_symbol
            self.current_name = stock_name
            
            # 如果 holding_info 不存在，先创建
            if self.holding_info is None:
                self.holding_info = {}
                
            if shares is not None:
                self.holding_info['shares'] = shares
            if cost_price is not None:
                self.holding_info['cost_price'] = cost_price
                if shares is not None:
                    self.holding_info['total_cost'] = shares * cost_price
            if aggressive_factor is not None:
                self.aggressive_factor = aggressive_factor
            if t_position_ratio is not None:
                self.t_position_ratio = t_position_ratio
                
            self._save_holding_info(stock_symbol, stock_name)
            logger.info(f"✅ 持仓信息已更新: {stock_symbol} - {stock_name}")
            return True
        except Exception as e:
            logger.error(f"❌ 更新持仓信息失败: {e}")
            return False

    def set_capital_info(self, available_cash=None, total_capital=None):
        """设置资金信息（用于回测期间临时调整）"""
        if available_cash is not None:
            self.available_cash = available_cash
        if total_capital is not None:
            self.total_capital = total_capital
        
        logger.info(f"💰 设置资金信息 - 可用现金: {self.available_cash:.2f}, 总资金: {self.total_capital:.2f}")

    def update_cash_after_trade(self, trade_type, amount, price, shares):
        """交易后更新现金（用于回测期间临时调整）"""
        if trade_type == 'buy':
            # 买入：减少现金
            trade_value = shares * price
            fees = self.calculate_buy_fees(trade_value)
            self.available_cash -= (trade_value + fees)
            logger.info(f"💸 买入更新 - 减少现金: {trade_value + fees:.2f}, 剩余: {self.available_cash:.2f}")
        
        elif trade_type == 'sell':
            # 卖出：增加现金
            trade_value = shares * price
            fees = self.calculate_sell_fees(trade_value)
            self.available_cash += (trade_value - fees)
            logger.info(f"💵 卖出更新 - 增加现金: {trade_value - fees:.2f}, 剩余: {self.available_cash:.2f}")
        
        # 更新总资金
        if self.holding_info and 'shares' in self.holding_info and 'cost_price' in self.holding_info:
            holding_value = self.holding_info['shares'] * self.holding_info['cost_price']
            self.total_capital = self.available_cash + holding_value

    def get_capital_info(self):
        """获取资金信息"""
        holding_value = 0
        if self.holding_info and 'shares' in self.holding_info and 'cost_price' in self.holding_info:
            holding_value = self.holding_info['shares'] * self.holding_info['cost_price']
        
        return {
            'available_cash': self.available_cash,
            'total_capital': self.total_capital,
            'holding_value': holding_value,
            'cash_ratio': (self.available_cash / self.total_capital * 100) if self.total_capital > 0 else 0,
            'note': '全局资金，所有股票共享'
        }

    def calculate_buy_fees(self, amount):
        """计算买入费用"""
        commission = max(amount * self.buy_commission_rate, self.min_commission)
        other_fees = amount * self.other_fees_rate
        return commission + other_fees

    def calculate_sell_fees(self, amount):
        """计算卖出费用"""
        commission = max(amount * self.sell_commission_rate, self.min_commission)
        stamp_duty = amount * self.stamp_duty_rate
        other_fees = amount * self.other_fees_rate
        return commission + stamp_duty + other_fees

    def calculate_profit_loss(self, current_price):
        """计算盈亏情况 - 修复空仓返回None的问题"""
        # 确保 holding_info 存在且格式正确
        if self.holding_info is None:
            self.holding_info = {'shares': 0, 'cost_price': 0}
        
        # 确保必要的字段存在
        if 'shares' not in self.holding_info:
            self.holding_info['shares'] = 0
        if 'cost_price' not in self.holding_info:
            self.holding_info['cost_price'] = 0
        
        shares = self.holding_info['shares']
        cost_price = self.holding_info['cost_price']
        
        # 空仓情况：返回特殊结构而不是None
        if shares == 0:
            return {
                'gross_profit': 0,
                'gross_profit_pct': 0,
                'net_profit': 0, 
                'net_profit_pct': 0,
                'current_value': 0,
                'cost_value': 0,
                'sell_fees': 0,
                'no_position': True  # 标记为空仓状态
            }
        
        # 有持仓时的正常计算逻辑
        gross_profit = (current_price - cost_price) * shares
        gross_profit_pct = (current_price / cost_price - 1) * 100
        
        # 计算考虑卖出费用后的净盈亏
        sell_amount = current_price * shares
        sell_fees = self.calculate_sell_fees(sell_amount)
        net_profit = gross_profit - sell_fees
        net_profit_pct = (net_profit / (cost_price * shares)) * 100
        
        return {
            'gross_profit': gross_profit,
            'gross_profit_pct': gross_profit_pct,
            'net_profit': net_profit,
            'net_profit_pct': net_profit_pct,
            'current_value': sell_amount,
            'cost_value': cost_price * shares,
            'sell_fees': sell_fees,
            'no_position': False  # 标记为有持仓状态
        }

    def calculate_dynamic_thresholds(self, current_price, predicted_low, predicted_high, 
                                   intraday_volatility, trend_strength, market_regime):
        """计算动态止盈止损阈值 - 新增的缺失方法"""
        # 基础阈值
        base_profit_threshold = 0.03  # 3%
        base_loss_threshold = 0.02    # 2%
        
        # 根据波动率调整
        vol_adjustment = intraday_volatility * 10  # 放大波动率影响
        profit_threshold = base_profit_threshold + vol_adjustment
        loss_threshold = base_loss_threshold + vol_adjustment
        
        # 根据趋势强度调整
        if trend_strength > 0.01:  # 强上升趋势
            profit_threshold += 0.02  # 提高止盈点
            loss_threshold -= 0.01    # 收紧止损点
        elif trend_strength < -0.01:  # 强下降趋势
            profit_threshold -= 0.01  # 降低止盈点
            loss_threshold += 0.02    # 放宽止损点
        
        # 根据风险偏好调整
        profit_threshold *= (1 + self.aggressive_factor * 0.5)
        loss_threshold *= (1 - self.aggressive_factor * 0.3)
        
        # 确保最小阈值
        profit_threshold = max(profit_threshold, 0.015)  # 至少1.5%
        loss_threshold = max(loss_threshold, 0.01)       # 至少1%
        
        self.dynamic_thresholds = {
            'profit_threshold_pct': profit_threshold,
            'loss_threshold_pct': loss_threshold,
            'vol_adjustment': vol_adjustment,
            'trend_impact': trend_strength
        }
        
        return profit_threshold, loss_threshold

    def analyze_trend_strength(self, stock_data):
        """分析趋势强度 - 新增的缺失方法"""
        if stock_data is None or len(stock_data) < 20:
            return 0.0
            
        closes = stock_data['收盘'].values
        if len(closes) < 20:
            return 0.0
            
        # 计算短期和长期均线
        short_ma = np.mean(closes[-5:])   # 5日均线
        medium_ma = np.mean(closes[-10:]) # 10日均线  
        long_ma = np.mean(closes[-20:])   # 20日均线
        
        # 计算趋势强度
        trend_strength = (short_ma / long_ma - 1) * 100
        
        # 标准化到 -0.03 到 0.03 范围
        normalized_strength = max(min(trend_strength / 100, 0.03), -0.03)
        
        return normalized_strength

    def detect_market_regime(self, stock_data, index_data):
        """检测市场状态 - 新增的缺失方法"""
        # 简化实现，实际中可以根据更多指标判断
        if stock_data is None or len(stock_data) < 20:
            return "normal"
            
        closes = stock_data['收盘'].values
        recent_volatility = np.std(closes[-10:] / closes[-11:-1] - 1)
        
        if recent_volatility > 0.03:
            return "high_volatility"
        elif recent_volatility < 0.01:
            return "low_volatility"
        else:
            return "normal"

    def _should_suggest_trading(self, current_price, predicted_low, predicted_high, 
                              intraday_volatility, trend_strength):
        """是否建议做T - 新增的缺失方法"""
        if self.holding_info['shares'] == 0:
            return False
            
        # 检查波动率是否足够
        if intraday_volatility < 0.01:  # 波动率太低
            return False
            
        # 检查预测区间是否足够宽
        predicted_range = predicted_high - predicted_low
        range_ratio = predicted_range / current_price
        
        if range_ratio < 0.02:  # 预测区间太窄
            return False
            
        # 检查趋势是否适合做T（震荡市更适合）
        if abs(trend_strength) > 0.02:  # 趋势太强
            return False
            
        return True

    def _generate_t_advice(self, current_price, predicted_low, predicted_high,
                         intraday_volatility, trend_strength):
        """生成做T建议 - 新增的缺失方法"""
        t_shares = int(self.holding_info['shares'] * self.t_position_ratio)
        if t_shares == 0:
            return None
            
        # 计算做T的预期收益
        expected_profit = (predicted_high - predicted_low) * 0.6  # 保守估计
        profit_ratio = expected_profit / current_price
        
        if profit_ratio > 0.01:  # 至少1%的预期收益
            return {
                'type': '做T',
                'reason': '日内波动较大，适合做T操作',
                'details': f'预期收益: {profit_ratio*100:.1f}% | 建议仓位: {t_shares}股',
                'urgency': 'medium'
            }
        
        return None

    def _generate_open_position_advice(self, current_price, predicted_low, predicted_high, 
                                     intraday_volatility, stock_data=None):
        """生成开仓建议 - 简化版本"""
        recommendations = []
        urgency_level = 'low'
        
        # 计算预测区间特征
        predicted_range = predicted_high - predicted_low
        range_ratio = predicted_range / current_price if current_price > 0 else 0
        
        # 简化的开仓条件：只要预测区间足够宽就建议开仓
        if range_ratio > 0.01:  # 至少1%的预测区间
            # 判断买入方向
            if current_price <= predicted_low * 1.03:  # 当前价格接近支撑位
                recommendations.append({
                    'type': '开仓买入',
                    'reason': '价格接近支撑位，预测区间较宽，适合建立仓位',
                    'details': f'预测区间: {predicted_low:.2f}-{predicted_high:.2f} | 区间宽度: {range_ratio*100:.1f}%',
                    'urgency': 'medium'
                })
                urgency_level = 'medium'
        
        return {
            'recommendations': recommendations,
            'urgency_level': urgency_level
        } if recommendations else None

    def generate_trading_advice(self, current_price, predicted_low, predicted_high, 
                               stock_data=None, index_data=None, intraday_volatility=0.02):
        """
        生成智能交易建议（修复空仓问题）
        """
        # 确保 holding_info 存在
        if self.holding_info is None:
            self.holding_info = {'shares': 0, 'cost_price': 0}
        
        # 计算盈亏情况（现在空仓时不会返回None）
        pl_data = self.calculate_profit_loss(current_price)
        
        # 分析市场状态和趋势
        trend_strength = self.analyze_trend_strength(stock_data) if stock_data is not None else 0.0
        market_regime = self.detect_market_regime(stock_data, index_data)
        
        # 计算动态阈值（即使空仓也要计算，用于开仓判断）
        profit_threshold_pct, loss_threshold_pct = self.calculate_dynamic_thresholds(
            current_price, predicted_low, predicted_high, 
            intraday_volatility, trend_strength, market_regime
        )
        
        advice = {
            'timestamp': datetime.now(),
            'pl_data': pl_data,
            'dynamic_thresholds': self.dynamic_thresholds,
            'recommendations': [],
            'urgency_level': 'low'
        }
        
        # ========== 关键修改：处理空仓情况 ==========
        if pl_data.get('no_position', False):
            # 空仓时生成开仓建议
            open_advice = self._generate_open_position_advice(
                current_price, predicted_low, predicted_high, intraday_volatility, stock_data
            )
            if open_advice and open_advice['recommendations']:
                advice['recommendations'].extend(open_advice['recommendations'])
                advice['urgency_level'] = max(advice['urgency_level'], open_advice['urgency_level'])
                return advice
            else:
                return None  # 无开仓建议时返回None
        
        # ========== 原有持仓管理逻辑 ==========
        # 只有在有持仓时才执行以下逻辑
        cost = self.holding_info['cost_price']
        current_net_profit_pct = pl_data['net_profit_pct']
        
        # 计算实际价格阈值
        profit_threshold_price = cost * (1 + profit_threshold_pct)
        loss_threshold_price = cost * (1 - loss_threshold_pct)
        
        # 1. 止盈建议（动态阈值）
        if current_price >= profit_threshold_price and current_net_profit_pct > 0:
            # 根据盈利程度和趋势决定紧急程度
            if current_net_profit_pct > 8 or trend_strength < -0.01:  # 高盈利或趋势转弱
                urgency = 'high'
                action = "强烈建议止盈"
            elif current_net_profit_pct > 5:
                urgency = 'medium'
                action = "建议止盈"
            else:
                urgency = 'low' 
                action = "考虑止盈"
                
            advice['recommendations'].append({
                'type': '止盈',
                'reason': f'{action}，当前价格达到动态止盈阈值',
                'details': f'动态阈值: {profit_threshold_pct*100:.1f}% | 当前盈利: {current_net_profit_pct:.1f}%',
                'urgency': urgency
            })
            advice['urgency_level'] = urgency
        
        # 2. 止损建议（动态阈值）
        elif current_price <= loss_threshold_price and current_net_profit_pct < -1:
            # 根据亏损程度和趋势决定紧急程度
            if current_net_profit_pct < -8 or trend_strength < -0.02:  # 大亏损或强下跌趋势
                urgency = 'high'
                action = "强烈建议止损"
            elif current_net_profit_pct < -5:
                urgency = 'medium' 
                action = "建议止损"
            else:
                urgency = 'low'
                action = "考虑止损"
                
            advice['recommendations'].append({
                'type': '止损', 
                'reason': f'{action}，当前价格达到动态止损阈值',
                'details': f'动态阈值: {loss_threshold_pct*100:.1f}% | 当前亏损: {current_net_profit_pct:.1f}%',
                'urgency': urgency
            })
            advice['urgency_level'] = urgency
        
        # 3. 做T建议（考虑仓位和波动率）
        if self.t_position_ratio > 0 and self._should_suggest_trading(
            current_price, predicted_low, predicted_high, intraday_volatility, trend_strength):
            
            t_advice = self._generate_t_advice(current_price, predicted_low, predicted_high, 
                                             intraday_volatility, trend_strength)
            if t_advice:
                advice['recommendations'].append(t_advice)
        
        # 4. 加仓建议（谨慎条件）
        if (current_price <= cost * 0.97 and  # 显著低于成本
            current_price <= predicted_low * 1.05 and  # 接近支撑位
            trend_strength > -0.01 and  # 趋势不差
            current_net_profit_pct > -8):  # 亏损可控
            
            advice['recommendations'].append({
                'type': '加仓',
                'reason': '价格接近强支撑位，可考虑分批加仓',
                'details': f'当前低于成本{abs(current_net_profit_pct):.1f}% | 趋势强度: {trend_strength:.4f}',
                'urgency': 'low'
            })
        
        # 记录建议历史
        if advice['recommendations']:
            self.advice_history.append(advice)
            # 限制历史记录长度
            if len(self.advice_history) > 100:
                self.advice_history.pop(0)
        
        return advice if advice['recommendations'] else None