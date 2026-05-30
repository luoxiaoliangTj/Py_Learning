# [file name]: scripts/backtest_day.py
#!/usr/bin/env python3
"""
scripts/backtest_day.py - 日线回测（数据驱动版本）
移除所有硬性限制，完全基于真实数据统计特性
"""
import os
import sys
import time
import json
import glob
import importlib.util
from datetime import datetime

# 工作日志 - 放在路径设置之后导入
# from .daily_logger import log_backtest  # 相对导入在直接运行脚本时失败

# ---------- 修复导入路径 ----------
BASE_DIR = os.path.dirname(os.path.dirname(__file__))
sys.path.insert(0, BASE_DIR)  # 项目根目录
sys.path.insert(0, os.path.join(BASE_DIR, 'statistics'))
sys.path.insert(0, os.path.join(BASE_DIR, 'scripts', 'backtest_modules'))
sys.path.insert(0, os.path.join(BASE_DIR, 'scripts'))  # 用于导入 daily_logger

# 导入工作日志记录器
from daily_logger import log_backtest

# 首先确保 StatPluginBase 被定义
try:
    from statistics.base import StatPluginBase
except ImportError:
    try:
        sys.path.insert(0, os.path.join(BASE_DIR, 'statistics'))
        from base import StatPluginBase
    except ImportError:
        import abc
        class StatPluginBase(abc.ABC):
            name: str = ''
            @abc.abstractmethod
            def fit(self, csv_path: str, meta: dict) -> dict:
                pass
            def is_better(self, df_daily, old_params):
                if old_params is None:
                    return True
                return len(df_daily) > 1.05 * old_params.get('fit_length', 0)

# 导入新的策略引擎和评估模块
try:
    from backtest_modules.strategy_engine import StrategyEngine
    from backtest_modules.metrics import MetricsCalculator
    from backtest_modules.data_driven_evaluator import DataDrivenEvaluator
    from backtest_modules.online_learning_module import OnlineLearningModule
    from backtest_modules.market_state_detector import MarketStateDetector
    from backtest_modules.performance_analyzer import PerformanceAnalyzer
    from backtest_modules.parameter_optimizer import ParameterOptimizer
except ImportError as e:
    print(f"导入 backtest_modules 失败: {e}")
    sys.exit(1)

# 导入 TradingAdvisorModule - 修复路径问题
try:
    from TradingAdvisorModule import TradingAdvisorModule
except ImportError:
    try:
        # 尝试直接导入
        trading_advisor_path = os.path.join(BASE_DIR, 'TradingAdvisorModule.py')
        spec = importlib.util.spec_from_file_location("TradingAdvisorModule", trading_advisor_path)
        TradingAdvisorModule = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(TradingAdvisorModule)
        from TradingAdvisorModule import TradingAdvisorModule
    except ImportError as e:
        print(f"导入 TradingAdvisorModule 失败: {e}")
        sys.exit(1)

import pandas as pd
import numpy as np
from config.config import cfg
from config.logging_config import get_logger

logger = get_logger("BacktestDay")

# ---------- 缓存 ----------
CACHE_FILE = os.path.join(cfg.DATA_DIR, 'stat_cache.json')

# ---------- 工具 ----------
def load_cache(symbol, plugin_name):
    if not os.path.exists(CACHE_FILE):
        return None
    with open(CACHE_FILE) as f:
        return json.load(f).get(symbol, {}).get(plugin_name)

def save_cache(symbol, plugin_name, params):
    db = {}
    if os.path.exists(CACHE_FILE):
        with open(CACHE_FILE) as f:
            db = json.load(f)
    db.setdefault(symbol, {})[plugin_name] = params
    with open(CACHE_FILE, 'w') as f:
        json.dump(db, f, indent=2)

def iter_stat_plugins():
    for fp in glob.glob(os.path.join(BASE_DIR, 'statistics', '*.py')):
        if os.path.basename(fp) in ('base.py', '__init__.py'):
            continue
        spec = importlib.util.spec_from_file_location("stat_mod", fp)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        for obj in vars(mod).values():
            if (isinstance(obj, type) and obj is not StatPluginBase and
                issubclass(obj, StatPluginBase)):
                yield obj

# ---------- 算法热插拔 ----------
class AlgoHotPlug:
    def __init__(self, algo_dir: str):
        self.algo_dir = algo_dir
        os.makedirs(algo_dir, exist_ok=True)

    def list_algorithms(self):
        return {os.path.basename(f)[:-3]: f for f in glob.glob(f"{self.algo_dir}/**/*.py", recursive=True)}

    def load_predict_func(self, name: str):
        path = self.list_algorithms()[name]
        spec = importlib.util.spec_from_file_location(name, path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        if not hasattr(mod, "algo_predict"):
            raise RuntimeError(f"{name} 未定义 algo_predict 函数")
        return mod.algo_predict

    def get_default_algo(self, params: dict):
        def adaptive(df_daily, df_minute, meta):
            strategy_engine = StrategyEngine()
            c = df_daily['收盘'].iloc[-1]
            atr20 = strategy_engine.atr(df_daily, 20).iloc[-1]
            k_val = params.get('k')
            if k_val is None:
                k_val = 2.0
            width = params.get('width')
            if width is None:
                width = k_val * atr20
            return {"pred_low": c - width * 0.5,
                    "pred_high": c + width * 0.5,
                    "volatility": meta.get("volatility", 0.02),
                    "trend_strength": meta.get("trend_strength", 0.0),
                    "market_regime": meta.get("market_regime", "normal")}
        return adaptive

# ---------- 持仓配置读取 ----------
def load_backtest_positions():
    """加载回测持仓配置"""
    temp_file = os.path.join(cfg.DATA_DIR, "backtest_temp_positions.json")
    print(f"📁 尝试读取持仓文件: {temp_file}")
    print(f"📁 文件是否存在: {os.path.exists(temp_file)}")
    
    if os.path.exists(temp_file):
        try:
            with open(temp_file, 'r', encoding='utf-8') as f:
                config = json.load(f)
            positions = config.get('backtest_positions', {})
            print(f"📊 读取到持仓数据: {positions}")
            return positions
        except Exception as e:
            logger.warning(f"读取回测持仓配置失败: {e}")
    else:
        print("❌ 持仓文件不存在，请检查文件路径和名称")
    return {}

def validate_position_data(position_info, symbol):
    """验证持仓数据的完整性"""
    if not position_info:
        print(f"❌ 未找到股票 {symbol} 的持仓配置")
        return False
    
    required_fields = ['shares', 'cost_price']
    for field in required_fields:
        if field not in position_info:
            print(f"❌ 持仓数据缺少必要字段: {field}")
            return False
    
    print(f"✅ 持仓数据验证通过: {position_info['shares']}股 @ {position_info['cost_price']:.2f}")
    return True

# ---------- 文件大小检查函数 ----------
def check_and_trim_file(file_path, max_size_mb=1):
    """检查文件大小，如果超过限制则清理最早的记录"""
    if not os.path.exists(file_path):
        return
    
    file_size = os.path.getsize(file_path)
    max_size_bytes = max_size_mb * 1024 * 1024  # 1MB
    
    if file_size > max_size_bytes:
        try:
            # 读取文件内容
            if file_path.endswith('_curve.csv'):
                # 曲线文件：保留最近50%的数据
                df = pd.read_csv(file_path)
                if len(df) > 10:  # 至少有10条记录才清理
                    keep_count = max(5, len(df) // 2)  # 至少保留5条，最多保留50%
                    df_trimmed = df.tail(keep_count)
                    df_trimmed.to_csv(file_path, index=False, encoding='utf-8-sig')
                    print(f"📁 曲线文件超过{max_size_mb}MB，已清理最早记录，保留{keep_count}条")
            elif file_path.endswith('_trades.csv'):
                # 交易文件：保留最近100条交易记录
                df = pd.read_csv(file_path)
                if len(df) > 50:  # 至少有50条记录才清理
                    keep_count = min(100, len(df) // 2)  # 最多保留100条
                    df_trimmed = df.tail(keep_count)
                    df_trimmed.to_csv(file_path, index=False, encoding='utf-8-sig')
                    print(f"📁 交易文件超过{max_size_mb}MB，已清理最早记录，保留{keep_count}条")
        except Exception as e:
            print(f"⚠️ 文件清理失败: {e}")

# ---------- 增强图表生成 ----------
def generate_enhanced_charts(symbol, curve_series, trades, price_data, metrics, plot=False):
    """Generate enhanced charts with performance metrics"""
    if not plot:
        return
        
    try:
        print("\n🎨 Generating enhanced charts...")
        
        # Import chart generator
        try:
            from backtest_modules.chart_generator import ChartGenerator
        except ImportError as e:
            print(f"❌ Chart generator import failed: {e}")
            return False
        
        # Initialize chart generator
        chart_generator = ChartGenerator(
            portfolio_curve=curve_series,
            trades=trades,
            price_data=price_data,
            symbol=symbol,
            results_dir=cfg.BACKTEST_DIR
        )
        
        # Generate charts with metrics
        chart_generator.generate_all_charts(backtest_metrics=metrics)
        
        print("✅ Enhanced charts generated successfully!")
        return True
        
    except Exception as e:
        print(f"❌ Chart generation failed: {e}")
        return False

# ---------- 策略展示 & 用户选择 ----------
STRATEGY_DB_PATH = os.path.join(cfg.DATA_DIR, "strategy_db.json")

def display_strategy_results(plugin_results, baseline):
    """展示所有策略结果排序，返回用户选择"""
    # 1. 按综合评分排序
    sorted_plugins = sorted(
        plugin_results.items(),
        key=lambda x: x[1].get('evaluation', {}).get('composite_score', 0),
        reverse=True
    )
    
    # 2. 输出表格
    print("\n" + "="*95)
    print("📊 策略回测结果（按综合评分排序）")
    print("="*95)
    print(f"{'序号':<6} {'策略名称':<22} {'k/参数':<8} {'夏普比率':<10} {'年化收益%':<10} {'交易次数':<10} {'最大回撤%':<10} {'综合评分':<10}")
    print("-"*95)
    for i, (pname, r) in enumerate(sorted_plugins, 1):
        ev = r.get('evaluation', {})
        sharpe = r.get('sharpe', 0)
        ret = r.get('total_return', 0) * 100
        trades = r.get('n_trades', 0)
        dd = r.get('max_drawdown', 0)
        score = ev.get('composite_score', 0)
        # 提取k值或参数描述
        p = r.get('params', {})
        if p.get('type') == 'trend':
            k_desc = f"MA{p.get('fast_ma','?')}/{p.get('slow_ma','?')}"
        else:
            k_desc = f"k={p.get('k', '?')}"
        print(f"{i:<6} {pname:<22} {k_desc:<8} {sharpe:<10.3f} {ret:<10.2f} {trades:<10} {dd:<10.2f} {score:<10.3f}")
    print("="*95)
    
    # 3. 用户选择
    print("\n请选择操作：")
    print(f"  1-{len(sorted_plugins)}: 选择对应策略 → 记录到策略库，用于实盘预警")
    print(f"  0: Skip → 启动在线学习，生成新策略")
    while True:
        try:
            choice = input(f"\n请选择 (0-{len(sorted_plugins)}): ").strip()
            if choice == "0":
                return {"action": "skip", "sorted_plugins": sorted_plugins}
            elif choice.isdigit() and 1 <= int(choice) <= len(sorted_plugins):
                idx = int(choice) - 1
                return {
                    "action": "select",
                    "plugin_name": sorted_plugins[idx][0],
                    "plugin_result": sorted_plugins[idx][1],
                    "sorted_plugins": sorted_plugins
                }
            print("❌ 无效选择，请重试")
        except (KeyboardInterrupt, EOFError):
            print("\n⚠️ 用户取消，退出回测")
            return {"action": "cancel"}

def save_strategy_to_db(symbol, plugin_name, result, source="backtest"):
    """保存选中的策略到策略数据库"""
    from datetime import datetime
    db = {}
    if os.path.exists(STRATEGY_DB_PATH):
        try:
            with open(STRATEGY_DB_PATH, 'r', encoding='utf-8') as f:
                db = json.load(f)
        except Exception as e:
            logger.warning(f"读取策略数据库失败: {e}")
    
    params = result.get('params', {})
    ev = result.get('evaluation', {})
    
    db[symbol] = {
        "active_strategy": plugin_name,
        "params": params,
        "metrics": {
            "sharpe": result.get('sharpe', 0),
            "annual_return": result.get('total_return', 0),
            "max_drawdown": result.get('max_drawdown', 0),
            "n_trades": result.get('n_trades', 0),
            "composite_score": ev.get('composite_score', 0)
        },
        "updated": datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        "source": source
    }
    
    try:
        os.makedirs(os.path.dirname(STRATEGY_DB_PATH), exist_ok=True)
        with open(STRATEGY_DB_PATH, 'w', encoding='utf-8') as f:
            json.dump(db, f, ensure_ascii=False, indent=2)
        return True
    except Exception as e:
        logger.error(f"保存策略到数据库失败: {e}")
        return False

def run_online_learning_and_verify(symbol, df):
    """在线学习：2/3训练 + 1/3验证，返回新策略和结果"""
    logger.info(f"🚀 开始在线学习流程: {symbol}")
    print(f"\n🚀 启动在线学习（2/3数据训练 + 1/3数据验证）...")
    
    try:
        split_idx = int(len(df) * 2 / 3)
        train_data = df.iloc[:split_idx]
        test_data = df.iloc[split_idx:]
        
        print(f"📊 训练数据: {len(train_data)} 条, 验证数据: {len(test_data)} 条")
        
        # 使用参数网格搜索，对所有插件尝试不同的k值
        engine = StrategyEngine()
        evaluator = DataDrivenEvaluator()
        
        # k值搜索范围：0.5~4.0，步长0.5（9个值）
        K_GRID = [0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0]
        
        best_result = None
        best_score = -999
        best_label = ""
        n_plugins = len(list(iter_stat_plugins()))
        
        print(f"\n🔍 参数网格搜索: {n_plugins}个策略 × {len(K_GRID)}个k值 = {n_plugins * len(K_GRID)}种组合")
        print(f"   k值范围: {K_GRID}")
        print(f"   {'─'*60}")
        
        for Plugin in iter_stat_plugins():
            pl = Plugin()
            csv_path = os.path.join(cfg.DATA_DIR, f"CCB_{symbol}_daily.csv")
            base_params = pl.fit(csv_path, {"symbol": symbol})
            if base_params is None:
                continue
            
            # 记录该插件在不同k值下的最佳结果
            plugin_best_score = -999
            plugin_best_k = None
            
            for k in K_GRID:
                params = base_params.copy()
                params['k'] = k
                try:
                    result = engine.quick_score(train_data, params, symbol)
                    score = result.get('evaluation', {}).get('composite_score', 0)
                    
                    if score > plugin_best_score:
                        plugin_best_score = score
                        plugin_best_k = k
                    
                    if score > best_score:
                        best_score = score
                        best_result = result
                        best_label = f"{pl.name}(k={k:.1f})"
                        
                except ValueError as e:
                    continue
            
            if plugin_best_k is not None:
                print(f"   {pl.name:<16} 最佳k={plugin_best_k:<4.1f}  评分={plugin_best_score:.3f}")
        
        # ====== 趋势策略搜索 ======
        TREND_GRID = [(5, 20), (5, 30), (10, 30), (10, 60), (20, 60)]
        for fast, slow in TREND_GRID:
            params_trend = {
                'type': 'trend', 'fast_ma': fast, 'slow_ma': slow,
                'plugin': 'trend_follow', 'k': None
            }
            try:
                result = engine.quick_score(train_data, params_trend, symbol)
                score = result.get('evaluation', {}).get('composite_score', 0)
                print(f"   {'trend_follow':<16} MA{fast}/{slow:<6} 评分={score:.3f}")
                if score > best_score:
                    best_score = score
                    best_result = result
                    best_label = f"trend_MA{fast}_{slow}"
            except ValueError as e:
                continue
        
        if best_result is None:
            print("❌ 网格搜索未找到有效策略")
            return None, None
        
        print(f"   {'─'*60}")
        print(f"✅ 最优: {best_label}, 训练评分={best_score:.3f}")
        print(f"📊 开始在验证集上验证...")
        
        verify_result = engine.quick_score(test_data, best_result['params'], symbol)
        verify_score = verify_result.get('evaluation', {}).get('composite_score', 0)
        
        print(f"\n📋 验证结果:")
        print(f"   夏普比率: {verify_result['sharpe']:.3f}")
        print(f"   年化收益: {verify_result['total_return']*100:.2f}%")
        print(f"   交易次数: {verify_result['n_trades']}")
        print(f"   最大回撤: {verify_result['max_drawdown']:.2f}%")
        print(f"   综合评分: {verify_score:.3f}")
        
        return best_result, verify_result
        
    except Exception as e:
        logger.error(f"在线学习失败: {e}")
        print(f"❌ 在线学习失败: {e}")
        return None, None

# ---------- 主回测 ----------
def run_backtest(symbol: str, start: str, end: str, cash: float = 100_000,
                 slip: float = 0.000, tune: bool = False, plot: bool = False):
    # 数据准备 - 直接从本地文件读取
    daily_file = os.path.join(cfg.DATA_DIR, f"CCB_{symbol}_daily.csv")
    if not os.path.exists(daily_file):
        print(f"❌ 股票历史数据文件不存在: {daily_file}")
        print("💡 请先下载股票历史数据到data目录")
        return
    
    try:
        df = pd.read_csv(daily_file)
        df['日期'] = pd.to_datetime(df['日期'])
        df = df.sort_values('日期').reset_index(drop=True)
        print(f"✅ 成功加载数据: {len(df)} 条记录")
    except Exception as e:
        print(f"❌ 读取数据文件失败: {e}")
        return

    # 初始化引擎和评估器
    strategy_engine = StrategyEngine()
    evaluator = DataDrivenEvaluator()
    
    # 插件选择 - 数据驱动版本
    params = None
    plugin_name = "未评估"  # 用于工作日志
    _log_results = {}  # 记录所有策略评估结果
    if not tune:
        for Plugin in iter_stat_plugins():
            pl = Plugin()
            old = load_cache(symbol, pl.name)
            if old and not pl.is_better(df, old):
                params = old
                plugin_name = pl.name
                logger.info(f"使用缓存参数 {pl.name} {params}")
                break
                
    if params is None:
        logger.info("开始数据驱动的策略网格评估 ...")
        plugin_results = {}
        eval_errors = []
        
        # 定义搜索网格
        K_GRID = [0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0]
        TREND_GRID = [(5, 20), (5, 30), (10, 30), (10, 60), (20, 60)]
        
        print(f"\n🔍 策略网格评估:")
        print(f"   - 通道策略: {len(list(iter_stat_plugins()))}个插件 × {len(K_GRID)}个k值")
        print(f"   - 趋势策略: MA交叉 × {len(TREND_GRID)}组参数")
        
        # ====== 通道策略网格搜索 ======
        for Plugin in iter_stat_plugins():
            pl = Plugin()
            old = load_cache(symbol, pl.name)
            if old and not pl.is_better(df, old):
                candidate_params = old
                try:
                    result = strategy_engine.quick_score(df, candidate_params, symbol)
                    plugin_results[f"{pl.name}(k={candidate_params.get('k','?')})"] = result
                except ValueError as e:
                    eval_errors.append(f"  ⚠️ {pl.name}: {e}")
                continue
            
            csv_path = daily_file
            base_params = pl.fit(csv_path, {"symbol": symbol})
            if base_params is None:
                continue
            
            # 对该插件尝试不同k值，只保留最优
            plugin_best_score = -999
            plugin_best_result = None
            plugin_best_k = None
            for k in K_GRID:
                params_k = base_params.copy()
                params_k['k'] = k
                try:
                    result = strategy_engine.quick_score(df, params_k, symbol)
                    score = result.get('evaluation', {}).get('composite_score', -999)
                    if score > plugin_best_score:
                        plugin_best_score = score
                        plugin_best_result = result
                        plugin_best_k = k
                except ValueError as e:
                    continue
            if plugin_best_result is not None:
                label = f"{pl.name}(k={plugin_best_k})"
                plugin_results[label] = plugin_best_result
        
        # ====== 趋势策略（MA交叉，适合ETF） ======
        for fast, slow in TREND_GRID:
            params_trend = {
                'type': 'trend',
                'fast_ma': fast,
                'slow_ma': slow,
                'plugin': 'trend_follow',
                'k': None
            }
            try:
                result = strategy_engine.quick_score(df, params_trend, symbol)
                label = f"trend_MA{fast}_{slow}"
                plugin_results[label] = result
            except ValueError as e:
                eval_errors.append(f"  ⚠️ trend_MA{fast}_{slow}: {e}")
                continue
        
        # 捕获所有策略评估结果（用于工作日志）
        _log_results = {k: v for k, v in plugin_results.items()}
        
        # 检查是否有任何策略成功评估
        if not plugin_results:
            print("\n" + "="*60)
            print("❌ 回测中止：数据不足以完成任何策略的评估")
            print("="*60)
            for err in eval_errors:
                print(err)
            print("\n💡 请先下载足够的交易历史数据（至少1年，约250个交易日）后再运行回测")
            print("="*60 + "\n")
            logger.error("数据不足，回测中止")
            return
        
        # ========== [新流程] 展示结果 + 用户选择 ==========
        baseline = evaluator.calculate_market_baseline(df, symbol)
        
        # 展示所有策略结果，让用户选择
        user_choice = display_strategy_results(plugin_results, baseline)
        
        if user_choice["action"] == "cancel":
            logger.info("用户取消回测")
            return
        elif user_choice["action"] == "select":
            # [选择1] 用户选定了某个策略
            plugin_name = user_choice["plugin_name"]
            result = user_choice["plugin_result"]
            params = result['params']
            save_cache(symbol, plugin_name, params)
            
            # 记录到策略数据库
            if save_strategy_to_db(symbol, plugin_name, result, source="backtest"):
                logger.info(f"✅ 策略 [{plugin_name}] 已保存到策略数据库")
                print(f"\n✅ 策略 [{plugin_name}] 已保存到策略数据库")
                print(f"📋 实盘监测将自动调用该策略进行交易预警")
            print(f"\n🚀 使用策略 [{plugin_name}] 进行回测...")
            
        elif user_choice["action"] == "skip":
            # [选择2] 用户选择 Skip → 在线学习
            print("\n🔄 用户选择 Skip，启动在线学习...")
            best_result, verify_result = run_online_learning_and_verify(symbol, df)
            
            if best_result is None or verify_result is None:
                print("❌ 在线学习失败，无法生成新策略")
                print("💡 可尝试获取更多数据后重试")
                return
            
            # 使用在线学习生成的最佳策略
            params = best_result['params']
            
            # 记录到策略数据库
            plugin_name = f"online_learned_{datetime.now().strftime('%H%M%S')}"
            if save_strategy_to_db(symbol, plugin_name, verify_result, source="online_learning"):
                logger.info(f"✅ 在线学习策略 [{plugin_name}] 已保存到策略数据库")
                print(f"\n✅ 在线学习策略 [{plugin_name}] 已保存到策略数据库")
                print(f"📋 实盘监测将自动调用该策略进行交易预警")
            print(f"\n🚀 使用在线学习策略 [{plugin_name}] 进行回测...")

    # 预测函数 - 使用自适应算法处理插件/在线学习的参数
    # [修复] 始终使用 get_default_algo，因为插件参数和在线学习策略
    # 都针对自适应算法设计（k、width等），不使用 algorithms/ 下的模块
    plug = AlgoHotPlug(cfg.ALGO_DIR)
    pred_func = plug.get_default_algo(params)
    algo_name = "adaptive"

    # ========== 新增：全局资金和持仓管理 ==========
    # 获取回测持仓配置
    backtest_positions = load_backtest_positions()
    position_info = backtest_positions.get(symbol, {})
    
    # 初始化交易建议模块
    advisor = TradingAdvisorModule()
    
    # 获取全局资金信息
    global_capital = advisor.get_capital_info()
    logger.info(f"💰 全局资金: 可用{global_capital['available_cash']:.2f}")

    # 设置持仓和资金
    if position_info and validate_position_data(position_info, symbol):
        shares = position_info['shares']
        cost_price = position_info['cost_price']
        stock_name = position_info.get('stock_name', cfg.STOCK_NAME)
        
        advisor.update_holding_info(symbol, stock_name, 
                                  shares=shares, cost_price=cost_price,
                                  aggressive_factor=0.5, t_position_ratio=0.2)
        
        # 调整初始现金（扣除已投资金）
        initial_investment = shares * cost_price
        adjusted_cash = global_capital['available_cash'] - initial_investment
        
        # 设置回测期间的资金状态
        advisor.set_capital_info(adjusted_cash, global_capital['total_capital'])
        
        logger.info(f"💰 使用回测持仓: {shares}股 @ {cost_price:.2f}")
        logger.info(f"💰 调整后现金: {adjusted_cash:.2f} (已扣除持仓价值 {initial_investment:.2f})")
    else:
        # 无配置，使用默认空仓
        advisor.update_holding_info(symbol, cfg.STOCK_NAME, shares=0, cost_price=0)
        adjusted_cash = global_capital['available_cash']
        advisor.set_capital_info(adjusted_cash, global_capital['total_capital'])
        logger.info("💰 使用默认空仓设置")

    # 使用调整后的现金开始回测
    cash = adjusted_cash
    shares = advisor.holding_info['shares'] if advisor.holding_info else 0
    
    print(f"🎯 回测初始状态 - 股数: {shares}, 现金: {cash:.2f}")
    # ========== 全局资金和持仓管理结束 ==========

    df['atr20'] = strategy_engine.atr(df, 20)
    
    # 使用调整后的现金
    cash0, curve, trades = cash, [], []
    
    # ========== 新增：初始化回测记录文件（追加模式） ==========
    os.makedirs(cfg.BACKTEST_DIR, exist_ok=True)
    curve_path = os.path.join(cfg.BACKTEST_DIR, f"{symbol}_curve.csv")
    trades_path = os.path.join(cfg.BACKTEST_DIR, f"{symbol}_trades.csv")

    # 初始化曲线文件（覆盖模式，写入表头）
    with open(curve_path, 'w', encoding='utf-8-sig') as f:
        f.write("date,portfolio_value\n")

    # 初始化交易文件（覆盖模式，写入表头）
    trades_df_header = pd.DataFrame(columns=['date', 'type', 'price', 'shares'])
    trades_df_header.to_csv(trades_path, index=False, encoding='utf-8-sig')

    print(f"💾 初始化回测记录文件:")
    print(f"   曲线文件: {curve_path}")
    print(f"   交易文件: {trades_path}")

    # ========== 新增：进度显示 ==========
    total_days = len(df) - 21
    print(f"📊 开始回测，总天数: {total_days}")
    
    # 回测主循环
    for i in range(20, len(df) - 1):
        # 进度显示
        current_day = i - 19
        if current_day % 100 == 0 or current_day == total_days:
            progress = (current_day / total_days) * 100
            print(f"⏳ 回测进度: {progress:.1f}% ({current_day}/{total_days})")
        
        date, close, open_n = df.loc[i, '日期'], df.loc[i, '收盘'], df.loc[i + 1, '开盘']
        sub = df.iloc[:i + 1]
        meta = {"symbol": symbol, "current_price": close,
                "volatility": sub['收盘'].pct_change().std() * np.sqrt(250),
                "trend_strength": (close / sub['收盘'].iloc[-20] - 1) / 20,
                "market_regime": "normal"}
        pred = pred_func(sub, None, meta)
        
        # 生成交易建议
        advice = advisor.generate_trading_advice(close, pred["pred_low"], pred["pred_high"],
                                                 sub, None, pred["volatility"])
        
        # 调试输出
        if i % 100 == 0 or advice:  # 每100天或当有建议时输出
            pl_data = advisor.calculate_profit_loss(close)
            current_total = cash + shares * close
            capital_info = advisor.get_capital_info()
            print(f"📅 {date} - 价格: {close:.2f}, 持仓: {shares}股, 现金: {cash:.2f}, 总资产: {current_total:.2f}")
            print(f"   📈 盈亏: {pl_data.get('net_profit_pct', 0):.2f}%")
            print(f"   💰 模块资金: 可用{capital_info['available_cash']:.2f}, 总资金{capital_info['total_capital']:.2f}")
            if advice:
                print(f"   💡 交易建议: {[r['type'] for r in advice['recommendations']]}")
        
        # 处理交易建议
        if advice:
            for rec in advice['recommendations']:
                typ = rec['type']
                
                if typ == '止盈' and shares > 0:
                    rev = shares * open_n * (1 - slip) - advisor.calculate_sell_fees(shares * open_n)
                    trade_record = {"date": date, "type": "sell", "price": open_n, "shares": shares}
                    trades.append(trade_record)
                    # ========== 修改：立即追加交易记录 ==========
                    # 先检查文件大小，如果超过1MB则清理
                    check_and_trim_file(trades_path)
                    new_trade_df = pd.DataFrame([trade_record])
                    new_trade_df.to_csv(trades_path, mode='a', header=False, index=False, encoding='utf-8-sig')
                    
                    cash += rev
                    shares = 0
                    # 更新交易建议模块的资金状态
                    advisor.update_cash_after_trade('sell', rev, open_n, shares)
                    # 更新持仓信息
                    advisor.update_holding_info(symbol, cfg.STOCK_NAME, shares=0, cost_price=0)
                    
                elif typ == '止损' and shares > 0:
                    rev = shares * open_n * (1 - slip) - advisor.calculate_sell_fees(shares * open_n)
                    trade_record = {"date": date, "type": "sell", "price": open_n, "shares": shares}
                    trades.append(trade_record)
                    # ========== 修改：立即追加交易记录 ==========
                    # 先检查文件大小，如果超过1MB则清理
                    check_and_trim_file(trades_path)
                    new_trade_df = pd.DataFrame([trade_record])
                    new_trade_df.to_csv(trades_path, mode='a', header=False, index=False, encoding='utf-8-sig')
                    
                    cash += rev
                    shares = 0
                    # 更新交易建议模块的资金状态
                    advisor.update_cash_after_trade('sell', rev, open_n, shares)
                    # 更新持仓信息
                    advisor.update_holding_info(symbol, cfg.STOCK_NAME, shares=0, cost_price=0)
                    
                elif typ == '加仓' and cash > 0:
                    buy_shares = int(cash / (open_n * 1.01))
                    if buy_shares > 0:
                        cost = buy_shares * open_n * (1 + slip) + advisor.calculate_buy_fees(buy_shares * open_n)
                        cash -= cost
                        shares += buy_shares
                        trade_record = {"date": date, "type": "buy", "price": open_n, "shares": buy_shares}
                        trades.append(trade_record)
                        # ========== 修改：立即追加交易记录 ==========
                        # 先检查文件大小，如果超过1MB则清理
                        check_and_trim_file(trades_path)
                        new_trade_df = pd.DataFrame([trade_record])
                        new_trade_df.to_csv(trades_path, mode='a', header=False, index=False, encoding='utf-8-sig')
                        
                        # 更新交易建议模块的资金状态
                        advisor.update_cash_after_trade('buy', cost, open_n, buy_shares)
                        
                        # 更新持仓信息
                        new_cost = (advisor.holding_info['shares'] * advisor.holding_info['cost_price'] +
                                    buy_shares * open_n) / (advisor.holding_info['shares'] + buy_shares)
                        advisor.update_holding_info(symbol, cfg.STOCK_NAME,
                                                    shares=advisor.holding_info['shares'] + buy_shares,
                                                    cost_price=new_cost, aggressive_factor=0.5,
                                                    t_position_ratio=0.2)
                        
                elif typ == '做T' and shares > 0:
                    t_shares = int(advisor.holding_info['shares'] * 0.2)
                    if t_shares == 0:
                        continue
                    buy_cost = t_shares * open_n * (1 + slip) + advisor.calculate_buy_fees(t_shares * open_n)
                    sell_rev = t_shares * df.loc[i + 1, '收盘'] * (1 - slip) - advisor.calculate_sell_fees(
                        t_shares * df.loc[i + 1, '收盘'])
                    cash += sell_rev - buy_cost
                    trade_record = {"date": date, "type": "T", "price": open_n, "shares": t_shares}
                    trades.append(trade_record)
                    # ========== 修改：立即追加交易记录 ==========
                    # 先检查文件大小，如果超过1MB则清理
                    check_and_trim_file(trades_path)
                    new_trade_df = pd.DataFrame([trade_record])
                    new_trade_df.to_csv(trades_path, mode='a', header=False, index=False, encoding='utf-8-sig')
                    
                    # 更新交易建议模块的资金状态（做T的净收益）
                    net_t_profit = sell_rev - buy_cost
                    advisor.set_capital_info(cash, global_capital['total_capital'])
                
                # ========== 新增：开仓买入建议处理 ==========
                elif typ == '开仓买入' and shares == 0 and cash > 0:
                    # 执行开仓买入
                    buy_shares = int(cash * 0.5 / (open_n * 1.01))  # 使用50%资金
                    if buy_shares > 0:
                        cost = buy_shares * open_n * (1 + slip) + advisor.calculate_buy_fees(buy_shares * open_n)
                        cash -= cost
                        shares += buy_shares
                        trade_record = {"date": date, "type": "buy", "price": open_n, "shares": buy_shares}
                        trades.append(trade_record)
                        # ========== 修改：立即追加交易记录 ==========
                        # 先检查文件大小，如果超过1MB则清理
                        check_and_trim_file(trades_path)
                        new_trade_df = pd.DataFrame([trade_record])
                        new_trade_df.to_csv(trades_path, mode='a', header=False, index=False, encoding='utf-8-sig')
                        
                        # 更新交易建议模块的资金状态
                        advisor.update_cash_after_trade('buy', cost, open_n, buy_shares)
                        
                        # 更新持仓信息
                        advisor.update_holding_info(symbol, cfg.STOCK_NAME,
                                                  shares=shares, cost_price=open_n,
                                                  aggressive_factor=0.5, t_position_ratio=0.2)
                        logger.info(f"🔔 执行开仓买入: {buy_shares}股 @ {open_n:.2f}")
        
        # ========== 修改：每天追加曲线数据 ==========
        current_portfolio_value = cash + shares * df.loc[i + 1, '收盘']
        curve.append(current_portfolio_value)
        
        # 先检查文件大小，如果超过1MB则清理
        check_and_trim_file(curve_path)
        # 追加到曲线文件
        with open(curve_path, 'a', encoding='utf-8-sig') as f:
            f.write(f"{date},{current_portfolio_value}\n")

    # 结果分析
    metrics_calc = MetricsCalculator()
    curve_series = pd.Series(curve, index=df['日期'].iloc[20:-1])
    
    ret = (curve_series.iloc[-1] / cash0 - 1) * 100
    bh = (df['收盘'].iloc[-1] / df['收盘'].iloc[20] - 1) * 100
    sharpe = metrics_calc.calculate_sharpe(curve)
    maxdd = metrics_calc.calculate_max_drawdown(curve)
    
    logger.info(f"📊 最终回测结果 - 算法: {algo_name}")
    logger.info(f"   策略收益: {ret:.2f}% | 持有收益: {bh:.2f}%")
    logger.info(f"   夏普比率: {sharpe:.2f} | 最大回撤: {maxdd:.2f}%")
    logger.info(f"   总交易次数: {len(trades)}")

    # ========== 修改：移除一次性保存代码，因为已经实时追加了 ==========
    print(f"✅ 回测完成!")
    print(f"📈 策略收益: {ret:.2f}% | 夏普比率: {sharpe:.2f}")
    print(f"🔢 交易次数: {len(trades)}")
    print(f"💾 文件实时保存完成:")
    print(f"   曲线文件: {curve_path}")
    print(f"   交易文件: {trades_path}")
    
    logger.info("✅ 回测完成，结果已实时保存")
    
    # ========== 新增：性能指标计算和增强图表生成 ==========
    # Calculate performance metrics
    performance_metrics = {
        'strategy_return': ret,
        'benchmark_return': bh,
        'sharpe': sharpe,
        'max_drawdown': maxdd,
        'total_trades': len(trades),
        'algo_name': algo_name
    }

    # Generate enhanced charts
    generate_enhanced_charts(symbol, curve_series, trades, df, performance_metrics, plot=plot)

    # 记录回测结果到每日工作日志
    log_backtest(cfg.DATA_DIR, symbol, cfg.STOCK_NAME,
                 plugin_name, performance_metrics, _log_results, params)

# ---------- 入口 ----------
if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("symbol", nargs="?", default=cfg.STOCK_SYMBOL)
    parser.add_argument("start", nargs="?", default="20170101")
    parser.add_argument("end", nargs="?", default="20241231")
    parser.add_argument("--tune", action="store_true", help="强制重新遍历插件")
    parser.add_argument("--plot", action="store_true", help="生成回测图表")
    args = parser.parse_args()
    run_backtest(args.symbol, args.start, args.end, tune=args.tune, plot=args.plot)