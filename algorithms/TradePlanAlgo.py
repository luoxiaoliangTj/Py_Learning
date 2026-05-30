# algorithms/TradePlanAlgo.py
"""
TradePlanAlgo - 基于回测结果的交易预案生成算法
即时学习版本 - 每次预测都使用最新数据学习
支持状态保存功能
"""

import pandas as pd
import numpy as np
import os
import sys
import json
from datetime import datetime

# 添加模块路径
BASE_DIR = os.path.dirname(os.path.dirname(__file__))
sys.path.insert(0, BASE_DIR)
sys.path.insert(0, os.path.join(BASE_DIR, 'algorithms'))

from config.config import cfg
from config.logging_config import get_logger

# 导入历史学习模块
try:
    from TradePlanLearner import TradePlanLearner
except ImportError:
    class TradePlanLearner:
        def generate_trade_plan(self, *args, **kwargs):
            raise ImportError("❌ TradePlanLearner 导入失败，无法生成交易预案")

logger = get_logger("TradePlanAlgo")

class BacktestResultValidator:
    """回测结果验证器"""
    
    def __init__(self):
        self.cfg = cfg
        
    def validate_backtest_requirements(self, symbol):
        """验证回测结果，如果不存在则创建初始版本"""
        curve_file, trades_file = self._find_backtest_files_case_insensitive(symbol)
        
        files_exist = curve_file is not None and trades_file is not None
        is_initial_file = False
        
        if not files_exist:
            curve_file, trades_file = self._create_initial_backtest_files(symbol)
            logger.info(f"✅ 为 {symbol} 创建初始回测文件")
            is_initial_file = True
        else:
            logger.info(f"📁 找到回测文件: {curve_file}")
        
        try:
            curve_df = pd.read_csv(curve_file) if curve_file else pd.DataFrame()
            trades_df = pd.read_csv(trades_file) if trades_file else pd.DataFrame()
        except Exception as e:
            raise Exception(f"❌ 回测文件读取失败: {e}")
        
        if not is_initial_file:
            is_initial_file = self._is_initial_backtest_file(curve_df, trades_df)
        
        return curve_file, trades_file, curve_df, trades_df, is_initial_file
    
    def _find_backtest_files_case_insensitive(self, symbol):
        """不区分大小写查找回测文件"""
        backtest_dir = self.cfg.BACKTEST_DIR
        
        if not os.path.exists(backtest_dir):
            return None, None
        
        all_files = os.listdir(backtest_dir)
        
        curve_file = None
        trades_file = None
        
        for filename in all_files:
            filename_lower = filename.lower()
            symbol_lower = symbol.lower()
            
            if f"{symbol_lower}_curve.csv" in filename_lower:
                curve_file = os.path.join(backtest_dir, filename)
            elif f"{symbol_lower}_trades.csv" in filename_lower:
                trades_file = os.path.join(backtest_dir, filename)
        
        return curve_file, trades_file
    
    def _is_initial_backtest_file(self, curve_df, trades_df):
        """判断是否是初始回测文件"""
        if len(curve_df) <= 1 and len(trades_df) <= 1:
            return True
            
        if 'note' in curve_df.columns and len(curve_df) > 0:
            if curve_df['note'].iloc[0] == 'initial_backtest_file':
                return True
                
        if 'note' in trades_df.columns and len(trades_df) > 0:
            if any(trades_df['note'] == 'initial_backtest_file'):
                return True
                
        return False
    
    def _create_initial_backtest_files(self, symbol):
        """创建初始回测文件"""
        symbol_lower = symbol.lower()
        curve_file = os.path.join(self.cfg.BACKTEST_DIR, f"{symbol_lower}_curve.csv")
        trades_file = os.path.join(self.cfg.BACKTEST_DIR, f"{symbol_lower}_trades.csv")
        
        os.makedirs(os.path.dirname(curve_file), exist_ok=True)
        
        initial_curve = pd.DataFrame({
            'date': [datetime.now().strftime('%Y-%m-%d')],
            'portfolio_value': [100000.0],
            'note': ['initial_backtest_file']
        })
        
        initial_trades = pd.DataFrame([{
            'date': datetime.now().strftime('%Y-%m-%d'),
            'type': 'init',
            'price': 0,
            'shares': 0,
            'note': 'initial_backtest_file'
        }])
        
        initial_curve.to_csv(curve_file, index=False)
        initial_trades.to_csv(trades_file, index=False)
        
        logger.info(f"📁 创建初始回测文件: {curve_file}")
        return curve_file, trades_file

    def analyze_backtest_performance(self, curve_df, trades_df):
        """分析回测性能指标"""
        try:
            if self._is_initial_backtest_file(curve_df, trades_df):
                return {
                    'sharpe_ratio': 0.0,
                    'max_drawdown': 0.0,
                    'win_rate': 0.0,
                    'total_trades': 0,
                    'curve_length': len(curve_df),
                    'is_initial_data': True,
                    'message': '未回测模拟结果'
                }
            
            if 'note' in curve_df.columns:
                curve_df = curve_df[curve_df['note'] != 'initial_backtest_file']
            if 'note' in trades_df.columns:
                trades_df = trades_df[trades_df['note'] != 'initial_backtest_file']
            
            if 'portfolio_value' in curve_df.columns and len(curve_df) > 1:
                returns = curve_df['portfolio_value'].pct_change().dropna()
                if len(returns) == 0:
                    sharpe_ratio = 0
                else:
                    sharpe_ratio = returns.mean() / returns.std() * np.sqrt(252) if returns.std() != 0 else 0
            else:
                sharpe_ratio = 0
            
            if 'portfolio_value' in curve_df.columns and len(curve_df) > 1:
                portfolio_values = curve_df['portfolio_value']
                peak = portfolio_values.expanding().max()
                drawdown = (portfolio_values - peak) / peak
                max_drawdown = drawdown.min()
            else:
                max_drawdown = 0
            
            win_rate = 0.0
            total_trades = 0
            
            if len(trades_df) > 0 and 'type' in trades_df.columns and 'price' in trades_df.columns:
                real_trades = trades_df[
                    (trades_df['type'].isin(['buy', 'sell'])) & 
                    (trades_df['shares'] > 0)
                ]
                total_trades = len(real_trades)
                
                if total_trades > 0:
                    buy_trades = real_trades[real_trades['type'] == 'buy']
                    win_count = 0
                    analyzed_count = 0
                    
                    for i, buy_trade in buy_trades.iterrows():
                        buy_date = buy_trade['date']
                        buy_price = buy_trade['price']
                        
                        subsequent_sells = real_trades[
                            (real_trades['type'] == 'sell') & 
                            (real_trades['date'] > buy_date)
                        ].head(1)
                        
                        if len(subsequent_sells) > 0:
                            sell_price = subsequent_sells.iloc[0]['price']
                            if sell_price > buy_price:
                                win_count += 1
                            analyzed_count += 1
                    
                    win_rate = win_count / analyzed_count if analyzed_count > 0 else 0
            
            return {
                'sharpe_ratio': round(sharpe_ratio, 3),
                'max_drawdown': round(abs(max_drawdown), 3),
                'win_rate': round(win_rate, 3),
                'total_trades': total_trades,
                'curve_length': len(curve_df),
                'is_initial_data': False
            }
            
        except Exception as e:
            logger.error(f"回测性能分析失败: {e}")
            return {
                'sharpe_ratio': 0.0,
                'max_drawdown': 0.0,
                'win_rate': 0.0,
                'total_trades': 0,
                'curve_length': 0,
                'is_initial_data': True,
                'error': str(e)
            }

class TradePlanPredictor:
    """交易预案预测器 - 即时学习版本"""
    
    def __init__(self):
        self.cfg = cfg
        self.validator = BacktestResultValidator()
        try:
            self.learner = TradePlanLearner(cfg.BACKTEST_DIR, cfg.DATA_DIR)
        except:
            self.learner = None
        
    def prepare_data(self, df_daily, df_minute, meta):
        """准备预测数据"""
        try:
            if df_daily is None or len(df_daily) == 0:
                logger.warning("传入的df_daily为空，尝试从文件加载")
                return self._load_data_from_file(meta)
            
            current_price = self._get_current_price(df_daily)
            volatility = self._calculate_volatility(df_daily)
            trend_strength = self._calculate_trend_strength(df_daily)
            available_cash = self._get_available_cash(meta.get('symbol', cfg.STOCK_SYMBOL))
            
            logger.info(f"使用传入数据: 当前价格={current_price:.2f}, 波动率={volatility:.4f}, 趋势={trend_strength:.4f}")
            
            return df_daily, current_price, volatility, trend_strength, available_cash
            
        except Exception as e:
            logger.error(f"处理传入数据失败: {e}")
            raise Exception(f"数据准备失败: {e}")
    
    def _load_data_from_file(self, meta):
        """从文件加载数据"""
        symbol = meta.get('symbol', cfg.STOCK_SYMBOL)
        data_dir = os.path.join(BASE_DIR, "data")
        if not os.path.exists(data_dir):
            raise Exception(f"数据目录不存在: {data_dir}")
        
        all_files = os.listdir(data_dir)
        data_file = None
        
        for filename in all_files:
            if f"ccb_{symbol.lower()}_daily.csv" in filename.lower():
                data_file = os.path.join(data_dir, filename)
                break
        
        if not data_file:
            raise Exception(f"数据文件不存在: ccb_{symbol}_daily.csv")
        
        logger.info(f"从文件加载数据: {data_file}")
        df_daily = pd.read_csv(data_file)
        if '日期' in df_daily.columns:
            df_daily['日期'] = pd.to_datetime(df_daily['日期'])
            df_daily = df_daily.sort_values('日期').reset_index(drop=True)
        
        current_price = self._get_current_price(df_daily)
        volatility = self._calculate_volatility(df_daily)
        trend_strength = self._calculate_trend_strength(df_daily)
        available_cash = self._get_available_cash(symbol)
        
        return df_daily, current_price, volatility, trend_strength, available_cash
    
    def _get_current_price(self, df_daily):
        """获取当前价格"""
        if '收盘' in df_daily.columns:
            return df_daily['收盘'].iloc[-1]
        elif 'STOCK_Close' in df_daily.columns:
            return df_daily['STOCK_Close'].iloc[-1]
        else:
            for col in ['close', 'Close', 'CLOSE']:
                if col in df_daily.columns:
                    return df_daily[col].iloc[-1]
            raise ValueError("无法找到价格列")
    
    def _calculate_volatility(self, df_daily):
        """计算波动率"""
        if '收盘' in df_daily.columns:
            price_col = '收盘'
        elif 'STOCK_Close' in df_daily.columns:
            price_col = 'STOCK_Close'
        else:
            for col in ['close', 'Close', 'CLOSE']:
                if col in df_daily.columns:
                    price_col = col
                    break
            else:
                raise ValueError("无法找到价格列计算波动率")
        
        returns = df_daily[price_col].pct_change().dropna()
        if len(returns) >= 20:
            volatility = returns.rolling(20).std().iloc[-1]
        else:
            volatility = returns.std() if len(returns) > 0 else 0.02
        
        if pd.isna(volatility) or volatility == 0:
            volatility = 0.02
            
        return volatility
    
    def _calculate_trend_strength(self, df_daily):
        """计算趋势强度"""
        if len(df_daily) < 20:
            return 0.0
            
        if '收盘' in df_daily.columns:
            price_col = '收盘'
        elif 'STOCK_Close' in df_daily.columns:
            price_col = 'STOCK_Close'
        else:
            for col in ['close', 'Close', 'CLOSE']:
                if col in df_daily.columns:
                    price_col = col
                    break
            else:
                return 0.0
        
        current_price = df_daily[price_col].iloc[-1]
        price_20_days_ago = df_daily[price_col].iloc[-20]
        
        return (current_price / price_20_days_ago - 1) / 20
    
    def _get_available_cash(self, symbol):
        """获取可用资金"""
        try:
            temp_file = os.path.join(cfg.DATA_DIR, "backtest_temp_positions.json")
            if os.path.exists(temp_file):
                with open(temp_file, 'r', encoding='utf-8') as f:
                    config = json.load(f)
                return config.get('available_cash', 50000)
        except:
            pass
            
        return 50000
    
    def predict_price_range(self, df_daily, volatility, trend_strength, backtest_stats):
        """预测价格区间"""
        current_price = self._get_current_price(df_daily)
        
        if backtest_stats.get('is_initial_data', True):
            base_range = current_price * volatility * 3.0
            pred_low = current_price - base_range
            pred_high = current_price + base_range
            
            logger.info(f"🔰 使用初始策略价格区间: {pred_low:.2f} - {pred_high:.2f}")
            return pred_low, pred_high
        
        sharpe_adjustment = 1.0
        if backtest_stats['sharpe_ratio'] > 1.0:
            sharpe_adjustment = 0.8
        elif backtest_stats['sharpe_ratio'] < 0.3:
            sharpe_adjustment = 1.3
        
        drawdown_adjustment = 1.0
        if backtest_stats['max_drawdown'] > 0.3:
            drawdown_adjustment = 1.2
        
        base_range = current_price * volatility * 2.5 * sharpe_adjustment * drawdown_adjustment
        
        if abs(trend_strength) > 0.01:
            range_adjustment = 1 + abs(trend_strength) * 10
            base_range *= range_adjustment
            
        min_range = current_price * 0.03
        max_range = current_price * 0.15
        adjusted_range = max(min(base_range, max_range), min_range)
        
        pred_low = current_price - adjusted_range / 2
        pred_high = current_price + adjusted_range / 2
        
        logger.info(f"🎯 使用优化策略价格区间: {pred_low:.2f} - {pred_high:.2f}")
        
        return pred_low, pred_high

def algo_predict(df_daily=None, df_minute=None, meta=None):
    """
    主预测函数 - 支持状态保存
    """
    logger.info("开始基于回测结果的交易预案生成...")
    
    try:
        predictor = TradePlanPredictor()
        validator = BacktestResultValidator()
        
        if meta is None:
            meta = {}
        
        symbol = meta.get('symbol', cfg.STOCK_SYMBOL)
        if not symbol:
            raise Exception("❌ 未指定股票代码")
        
        logger.info(f"🔍 验证股票 {symbol} 的回测结果...")
        curve_file, trades_file, curve_df, trades_df, is_initial_file = validator.validate_backtest_requirements(symbol)
        
        logger.info("📊 分析回测性能指标...")
        backtest_stats = validator.analyze_backtest_performance(curve_df, trades_df)
        
        df_daily, current_price, volatility, trend_strength, available_cash = \
            predictor.prepare_data(df_daily, df_minute, meta)
        
        pred_low, pred_high = predictor.predict_price_range(
            df_daily, volatility, trend_strength, backtest_stats
        )
        
        trade_plan = {}
        if predictor.learner and not backtest_stats.get('is_initial_data', True):
            try:
                trade_plan = predictor.learner.generate_trade_plan(
                    symbol, df_daily, current_price, pred_low, pred_high,
                    volatility, trend_strength, available_cash, backtest_stats
                )
            except Exception as e:
                logger.warning(f"交易预案生成失败: {e}")
                trade_plan = _create_basic_trade_plan(symbol, current_price, pred_low, pred_high)
        else:
            trade_plan = _create_basic_trade_plan(symbol, current_price, pred_low, pred_high)
        
        # 确保信心度字段存在
        if 'confidence' not in trade_plan:
            trade_plan['confidence'] = trade_plan.get('feasibility_score', 0.5)
        
        # 添加状态保存标记
        trade_plan['_source'] = 'TradePlanAlgo'
        trade_plan['_generated_at'] = datetime.now().isoformat()
        trade_plan['_trading_date'] = datetime.now().strftime('%Y-%m-%d')
        trade_plan['_is_initial_strategy'] = backtest_stats.get('is_initial_data', True)
        
        result = {
            "pred_low": round(pred_low, 2),
            "pred_high": round(pred_high, 2),
            "current_price": round(current_price, 2),
            "volatility": round(volatility, 4),
            "trend_strength": round(trend_strength, 4),
            "market_regime": trade_plan.get('market_state', 'normal'),
            "backtest_stats": backtest_stats,
            "trade_plan": trade_plan,
            "is_initial_strategy": backtest_stats.get('is_initial_data', True)
        }
        
        # 尝试保存状态（如果调用方传入了状态管理器）
        try:
            # 检查调用上下文是否有状态管理器
            import inspect
            call_stack = inspect.stack()
            for frame_info in call_stack:
                frame = frame_info.frame
                if 'self' in frame.f_locals:
                    caller_self = frame.f_locals['self']
                    if hasattr(caller_self, 'state_manager'):
                        # 找到调用方的状态管理器，保存日线策略
                        state_manager = caller_self.state_manager
                        if hasattr(state_manager, 'save_daily_strategy'):
                            reason = "日线分析策略" if not backtest_stats.get('is_initial_data', True) else "初始日线策略"
                            state_manager.save_daily_strategy(trade_plan, reason)
                            logger.info(f"💾 已保存日线策略状态: {reason}")
                        break
        except Exception as e:
            logger.warning(f"状态保存失败（可能无状态管理器）: {e}")
        
        _print_plan_summary(result, symbol)
        
        return result
        
    except Exception as e:
        logger.error(f"交易预案生成失败: {e}")
        return _fallback_prediction(df_daily, meta)

def _create_basic_trade_plan(symbol, current_price, pred_low, pred_high):
    """创建基础交易预案 - 确保信心度有值"""
    price_range = pred_high - pred_low
    
    # 创建基础价格概率
    price_probs = {}
    price_points = np.linspace(pred_low, pred_high, 10)
    for price in price_points:
        distance = abs(price - current_price) / price_range
        prob = max(0.1, 0.6 - distance * 0.5)
        price_probs[round(price, 2)] = prob
    
    # 计算基础信心度
    base_confidence = 0.5
    
    return {
        'market_state': 'normal',
        'holding_period': {'min_days': 3, 'max_days': 10},
        'price_probabilities': price_probs,
        'expected_profit': price_range * 0.3,
        'feasibility_score': base_confidence,
        'confidence': base_confidence,  # 确保信心度有值
        'risk_level': 'medium',
        'buy_scenarios': [
            {
                'price': round(pred_low * 1.02, 2),
                'probability': 0.6,
                'quantity': 100,
                'reason': '接近支撑位'
            }
        ],
        'sell_scenarios': [
            {
                'price': round(pred_high * 0.98, 2),
                'probability': 0.5,
                'type': '止盈',
                'reason': '接近阻力位'
            }
        ],
        'note': '基于技术分析的基础交易预案'
    }

def _fallback_prediction(df_daily, meta):
    """终极回退预测"""
    current_price = df_daily['收盘'].iloc[-1] if len(df_daily) > 0 else 0
    
    fallback_plan = {
        'market_state': 'normal',
        'feasibility_score': 0.3,
        'confidence': 0.3,  # 确保信心度有值
        'risk_level': 'high',
        'note': '系统异常，使用回退策略',
        '_source': 'fallback',
        '_generated_at': datetime.now().isoformat(),
        '_trading_date': datetime.now().strftime('%Y-%m-%d')
    }
    
    return {
        "pred_low": current_price * 0.95,
        "pred_high": current_price * 1.05,
        "current_price": current_price,
        "volatility": 0.02,
        "trend_strength": 0.0,
        "market_regime": "normal",
        "is_fallback": True,
        "message": "使用回退策略，建议检查系统配置",
        "backtest_stats": {
            'is_initial_data': True,
            'message': '系统异常，使用回退策略'
        },
        "trade_plan": fallback_plan
    }

def _print_plan_summary(result, symbol):
    """打印交易预案摘要"""
    plan = result['trade_plan']
    stats = result['backtest_stats']
    is_initial = result.get('is_initial_strategy', True)
    
    print("\n" + "="*60)
    
    if is_initial:
        print("⚠️  未回测模拟结果！基于初始策略参数")
        print("💡 建议运行完整回测以获得优化策略")
    else:
        print("🎯 基于回测结果的交易预案生成完成")
        
    print("="*60)
    print(f"📈 股票: {symbol} | 市场状态: {plan['market_state']}")
    print(f"📊 当前价格: {result['current_price']:.2f}元")
    print(f"📊 预测区间: {result['pred_low']:.2f} - {result['pred_high']:.2f}")
    
    # 显示信心度
    confidence = plan.get('confidence', plan.get('feasibility_score', 0.5))
    print(f"🎯 策略信心度: {confidence:.1%}")
    
    # 显示价格概率
    price_probs = plan.get('price_probabilities', {})
    if price_probs:
        print(f"\n🎯 价格概率分布:")
        
        # 按价格排序显示高概率点
        sorted_probs = sorted(price_probs.items(), key=lambda x: x[0])
        high_prob_points = [(price, prob) for price, prob in sorted_probs if prob > 0.3]
        
        if high_prob_points:
            for price, prob in high_prob_points[:5]:
                price_type = "📉 支撑位" if price < result['current_price'] else "📈 阻力位" if price > result['current_price'] else "⚖️  当前区"
                print(f"   {price_type} {price:.2f}元: {prob:.1%}")
    
    # 显示回测性能
    if is_initial:
        print(f"\n📈 回测状态: {stats.get('message', '未回测模拟结果')}")
    else:
        print(f"\n📈 回测性能:")
        print(f"   夏普比率: {stats['sharpe_ratio']:.3f} | 最大回撤: {stats['max_drawdown']:.1%}")
        print(f"   胜率: {stats['win_rate']:.1%} | 交易次数: {stats['total_trades']}")
    
    if plan.get('buy_scenarios'):
        print("\n🛒 买入场景:")
        for scenario in plan['buy_scenarios'][:2]:
            print(f"   • {scenario['price']:.2f}元 (概率{scenario['probability']:.1%})")
    
    if plan.get('sell_scenarios'):
        print("\n📤 卖出场景:")  
        for scenario in plan['sell_scenarios'][:2]:
            print(f"   • {scenario['price']:.2f}元 (概率{scenario['probability']:.1%}) - {scenario['type']}")
    
    if is_initial:
        print(f"\n💡 重要提示: 此为未回测模拟结果，建议运行日线回测进行策略优化")
    
    print("="*60)