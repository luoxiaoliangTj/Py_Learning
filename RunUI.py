#!/usr/bin/env python3
"""
Run_UI.py - 股票预测 & 回测 主控制台
算法/工具 即插即用，扁平菜单，统一日志
版本: 6.5（增强股票选择功能 + 在线预测升级）
"""
import os
import sys
import glob
import json
import importlib.util
import subprocess
from datetime import datetime

# ------------- 路径插入 -------------
BASE_DIR = os.path.dirname(__file__)
sys.path.insert(0, BASE_DIR)
sys.path.insert(0, os.path.join(BASE_DIR, 'NewProjectV23'))

from config.config import cfg
from config.logging_config import get_logger

logger = get_logger("RunUI")

# ------------- 持久化股票配置 -------------
def load_current_stock():
    """启动时加载持久化的股票配置"""
    current_stock_file = os.path.join(BASE_DIR, "data", "current_stock.json")
    if os.path.exists(current_stock_file):
        try:
            with open(current_stock_file, 'r', encoding='utf-8') as f:
                current_stock = json.load(f)
            
            os.environ["STOCK_SYMBOL"] = current_stock.get("symbol", cfg.STOCK_SYMBOL)
            os.environ["STOCK_NAME"] = current_stock.get("name", cfg.STOCK_NAME)
            os.environ["INDEX_SYMBOL"] = current_stock.get("index_symbol", cfg.INDEX_SYMBOL)
            os.environ["INDEX_NAME"] = current_stock.get("index_name", cfg.INDEX_NAME)
            
            logger.info(f"已加载持久化股票配置: {current_stock['symbol']} - {current_stock['name']}")
        except Exception as e:
            logger.warning(f"加载持久化股票配置失败: {e}")

def save_current_stock(symbol, name, index_symbol, index_name):
    """保存当前股票配置到文件"""
    current_stock_file = os.path.join(BASE_DIR, "data", "current_stock.json")
    current_stock = {
        "symbol": symbol,
        "name": name,
        "index_symbol": index_symbol,
        "index_name": index_name,
        "last_updated": datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    }
    
    try:
        os.makedirs(os.path.dirname(current_stock_file), exist_ok=True)
        with open(current_stock_file, 'w', encoding='utf-8') as f:
            json.dump(current_stock, f, ensure_ascii=False, indent=2)
        return True
    except Exception as e:
        logger.error(f"保存股票配置失败: {e}")
        return False

# ------------- 热插拔通用方法 -------------
def scan_plugins(folder: str, func_name: str):
    """扫描目录下所有 .py 插件，返回 {name: path}（延迟加载）"""
    plugins = {}
    
    # 构建绝对路径：基于 BASE_DIR
    folder_path = os.path.join(BASE_DIR, folder)
    
    # 确保目录存在
    if not os.path.exists(folder_path):
        logger.warning(f"目录不存在: {folder_path}")
        return plugins
        
    for py in glob.glob(f"{folder_path}/**/*.py", recursive=True):
        name = os.path.basename(py)[:-3]
        
        # 跳过 __init__.py 等特殊文件
        if name.startswith('__') or name.startswith('.'):
            continue
            
        # 只检查文件是否存在，不实际导入
        plugins[name] = py
        logger.info(f"发现工具文件: {name}")
            
    return plugins

def load_tool_module(tool_path, func_name):
    """延迟加载单个工具模块"""
    name = os.path.basename(tool_path)[:-3]
    try:
        spec = importlib.util.spec_from_file_location(name, tool_path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        
        if hasattr(mod, func_name):
            return getattr(mod, func_name)
        else:
            logger.warning(f"工具 {name} 缺少 {func_name} 函数")
            return None
    except Exception as e:
        logger.error(f"加载工具 {name} 失败: {e}")
        return None

def run_sub(script: str, timeout: int = 120):
    """运行 scripts/ 下子进程（强制工作目录=项目根）"""
    path = os.path.join(BASE_DIR, "scripts", script)
    if not os.path.exists(path):
        logger.error(f"脚本不存在: {path}")
        return False
    try:
        # 👇👇👇 关键：cwd=BASE_DIR
        subprocess.run([sys.executable, path], cwd=BASE_DIR, check=True, timeout=timeout)
        return True
    except subprocess.TimeoutExpired:
        logger.error(f"{script} 超时")
        return False
    except subprocess.CalledProcessError as e:
        logger.error(f"{script} 失败: {e}")
        return False

# ------------- 算法/工具菜单 -------------
def algo_menu() -> dict:
    return scan_plugins(cfg.ALGO_DIR, "algo_predict")

def tool_menu() -> dict:
    """返回工具名称和路径的映射，不实际加载模块"""
    return scan_plugins("Tools", "run_tool")

# ------------- 核心流程 -------------
def daily_predict():
    """1. 日线预测 → 落地日线区间"""
    algos = algo_menu()
    if not algos:
        logger.error("没有可用算法，退出")
        return
    # 获取当前选择的股票代码（与回测一致）
    symbol = os.environ.get("STOCK_SYMBOL", cfg.STOCK_SYMBOL)
    if not symbol:
        print("❌ 请先使用菜单5选择股票")
        return
    
    # 检查数据文件是否存在（与回测一致）
    data_file = os.path.join(BASE_DIR, "data", f"CCB_{symbol}_daily.csv")
    if not os.path.exists(data_file):
        print(f"❌ 数据文件不存在: {data_file}")
        print("请确保已在data目录放置对应的CSV文件")
        return
    
    print(f"📊 使用股票: {symbol}")
    
    # ====== [P2] 从策略库加载当前股票的策略 ======
    strategy_info = None
    strategy_name = "默认算法"
    db_path = os.path.join(BASE_DIR, "data", "strategy_db.json")
    if os.path.exists(db_path):
        try:
            with open(db_path, 'r', encoding='utf-8') as f:
                db = json.load(f)
            if symbol in db:
                s = db[symbol]
                metrics = s.get('metrics', {})
                params = s.get('params', {})
                # 判断策略类型，显示对应参数
                if params.get('type') == 'trend':
                    fast = params.get('fast_ma', '?')
                    slow = params.get('slow_ma', '?')
                    strategy_name = f"趋势跟随(MA{fast}/{slow})"
                    print(f"✅ 已加载策略库: {strategy_name}")
                else:
                    k = params.get('k', '?')
                    strategy_name = f"{s['active_strategy']}(k={k})"
                    print(f"✅ 已加载策略库: {strategy_name}")
                print(f"   来源: {s.get('source', '未知')} "
                      f"| 夏普: {metrics.get('sharpe', 0):.2f} "
                      f"| 收益: {metrics.get('annual_return', 0)*100:.1f}%")
                strategy_info = s
        except Exception as e:
            logger.warning(f"读取策略库失败: {e}")
    
    name, path = next(iter(algos.items()))
    algo_func = load_tool_module(path, "algo_predict")
    
    if algo_func:
        try:
            # 调用算法，传入股票代码和策略信息
            meta = {"symbol": symbol}
            if strategy_info:
                meta['strategy'] = strategy_info
            result = algo_func(df_daily=None, df_minute=None, meta=meta)
            logger.info(f"使用算法 {name} 完成预测")
            print("✅ 日线预测完成（使用本地数据）")
            
            # 记录预测结果到每日工作日志
            try:
                from scripts.daily_logger import append
                pred_low = result.get('pred_low', 0)
                pred_high = result.get('pred_high', 0)
                confidence = result.get('trade_plan', {}).get('confidence', 0.5)
                stock_name = os.environ.get("STOCK_NAME", cfg.STOCK_NAME)
                ts = datetime.now().strftime("%H:%M")
                log_line = f"{ts} | {symbol} {stock_name} | {strategy_name} | {pred_low:.2f}({confidence*100:.0f}%)～{pred_high:.2f}"
                append(cfg.DATA_DIR, log_line)
            except Exception as e:
                logger.warning(f"写入预测日志失败: {e}")
        except Exception as e:
            logger.error(f"算法执行失败: {e}")
            print(f"❌ 预测失败: {e}")
    else:
        logger.error(f"无法加载算法: {name}")

def realtime_predict():
    """2. 在线预测 - 基于日线策略和实时数据的自适应预测（完全替换版）"""
    print("\n🚀 启动在线预测...")
    
    try:
        # 动态导入在线预测模块
        online_predictor_path = os.path.join(BASE_DIR, "algorithms", "online_predictor.py")
        if not os.path.exists(online_predictor_path):
            print("❌ 在线预测模块不存在，请先完成第一阶段开发")
            return
            
        # 导入在线预测
        spec = importlib.util.spec_from_file_location("online_predictor", online_predictor_path)
        online_predictor_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(online_predictor_module)
        
        from algorithms.online_predictor import OnlinePredictor
        
        predictor = OnlinePredictor()
        if predictor.initialize():
            print("✅ 初始化完成，开始实时监控...")
            print("💡 提示: 输入 'q' + 回车 可随时退出监控")
            predictor.start_monitoring()
        else:
            print("❌ 在线预测初始化失败")
            
    except ImportError as e:
        print("❌ 在线预测模块导入失败")
        print(f"详细错误: {e}")
        print("💡 请确保已完成第一阶段的基础框架开发")
    except Exception as e:
        print(f"❌ 在线预测运行失败: {e}")

def backtest():
    """3. 日线回测 - 增强版，支持图表生成"""
    # 询问是否生成图表
    print("\n📊 日线回测选项:")
    print("1 - 仅回测（不生成图表）")
    print("2 - 回测并生成图表")
    print("0 - 返回主菜单")
    
    choice = input("请选择: ").strip()
    
    if choice == "0":
        return
    elif choice not in ["1", "2"]:
        print("❌ 无效选择")
        return
    
    # 根据选择构建命令
    script = os.path.join(BASE_DIR, "scripts", "backtest_day.py")
    cmd = [sys.executable, script]
    
    if choice == "2":
        cmd.append("--plot")
        print("🖼️  将生成回测图表")
    else:
        print("📈 仅执行回测，不生成图表")
    
    # 运行回测
    print("🚀 开始回测...")
    try:
        subprocess.run(cmd, cwd=BASE_DIR)
        
        # 如果生成了图表，显示提示信息
        if choice == "2":
            charts_dir = os.path.join(cfg.BACKTEST_DIR, "charts")
            print(f"\n📁 回测图表已生成到: {charts_dir}")
            print("💡 你可以使用文件管理器查看生成的PNG图表文件")
            
    except Exception as e:
        print(f"❌ 回测执行失败: {e}")

def tool_runner():
    """4. 运行工具 - 延迟加载机制"""
    tools = tool_menu()  # 这里只返回名称和路径，不加载模块
    if not tools:
        print("❌ 未发现任何工具")
        print("请检查 Tools/ 目录下是否有包含 run_tool() 函数的 .py 文件")
        return
    
    print("\n=== 可用工具 ===")
    tool_list = list(tools.items())
    for idx, (name, path) in enumerate(tool_list, 1):
        print(f"{idx} - {name}")
    
    choice = input("选择编号 (回车返回): ").strip()
    if not choice.isdigit() or not (1 <= int(choice) <= len(tool_list)):
        return
    
    name, path = tool_list[int(choice) - 1]
    print(f"🔧 运行工具: {name}")
    
    # 只有在这里才实际加载和运行工具
    tool_func = load_tool_module(path, "run_tool")
    if tool_func:
        try:
            tool_func()
        except Exception as e:
            logger.error(f"工具 {name} 执行失败: {e}")
            print(f"❌ 工具执行出错: {e}")
    else:
        print(f"❌ 无法加载工具: {name}")

def select_stock():
    """5. 重新选择股票 - 增强版"""
    # 1. 显示当前股票
    current_symbol = os.environ.get("STOCK_SYMBOL", cfg.STOCK_SYMBOL)
    current_name = os.environ.get("STOCK_NAME", cfg.STOCK_NAME)
    print(f"\n📈 当前默认股票: {current_symbol} - {current_name}")
    
    # 2. 加载真实持仓信息（从 real_positions.json）
    real_positions_file = os.path.join(BASE_DIR, "data", "real_positions.json")
    holdings = {}
    
    if os.path.exists(real_positions_file):
        try:
            with open(real_positions_file, 'r', encoding='utf-8') as f:
                holdings = json.load(f)
            print(f"✅ 已加载真实持仓信息")
        except Exception as e:
            print(f"❌ 加载真实持仓信息失败: {e}")
    else:
        print("📭 暂无真实持仓记录")
    
    # 3. 过滤有效持仓 (shares > 0)
    valid_holdings = {code: info for code, info in holdings.items() 
                     if info.get('shares', 0) > 0}
    
    # 4. 显示菜单
    print(f"\n📊 真实持仓股票 ({len(valid_holdings)} 支):")
    if valid_holdings:
        holdings_list = list(valid_holdings.items())
        for idx, (code, info) in enumerate(holdings_list, 1):
            print(f"  {idx} - {code} - {info.get('stock_name', '未知名称')} "
                  f"(持仓: {info.get('shares', 0):,}股)")
    
    print("\n💡 选择方式:")
    if valid_holdings:
        print("  输入编号选择持仓股票")
    print("  输入股票代码选择新股票")
    print("  直接回车返回")
    
    choice = input("\n请选择或输入股票代码: ").strip()
    
    if not choice:
        print("取消选择")
        return
    
    selected_symbol = None
    selected_name = None
    
    # 5. 处理选择
    if choice.isdigit() and 1 <= int(choice) <= len(valid_holdings):
        # 选择持仓股票
        code, info = holdings_list[int(choice) - 1]
        selected_symbol = code
        selected_name = info.get('stock_name', '未知名称')
        print(f"✅ 选择持仓股票: {selected_symbol} - {selected_name}")
    else:
        # 手动输入股票代码
        selected_symbol = choice
        # 尝试从持仓中获取股票名称，如果没有则让用户输入
        if selected_symbol in holdings and holdings[selected_symbol].get('stock_name'):
            selected_name = holdings[selected_symbol]['stock_name']
            print(f"✅ 自动识别股票: {selected_symbol} - {selected_name}")
        else:
            selected_name = input("请输入股票名称: ").strip() or f"股票{selected_symbol}"
            print(f"✅ 输入新股票: {selected_symbol} - {selected_name}")
    
    if selected_symbol:
        # 6. 自动判断市场
        if selected_symbol.startswith('6'):
            index_symbol = "000001"
            index_name = "上证指数"
        else:
            index_symbol = "399001" 
            index_name = "深圳成指"
        
        # 7. 更新环境变量
        os.environ["STOCK_SYMBOL"] = selected_symbol
        os.environ["STOCK_NAME"] = selected_name
        os.environ["INDEX_SYMBOL"] = index_symbol
        os.environ["INDEX_NAME"] = index_name
        
        # 8. 持久化到文件
        if save_current_stock(selected_symbol, selected_name, index_symbol, index_name):
            print(f"✅ 已切换至: {selected_symbol} - {selected_name}")
            print(f"   对应指数: {index_name} ({index_symbol})")
            print(f"   配置已保存，下次启动自动加载")
            
            # 9. 自动调用daily_downloader下载该股票数据
            import subprocess
            try:
                print(f"\\n🚀 正在自动下载 {selected_symbol} 的日线数据...")
                
                # 使用绝对路径确保找到正确的daily_downloader.py
                base_dir = os.path.dirname(__file__)
                daily_downloader_path = os.path.join(base_dir, 'NewProjectV23', 'Tools', 'daily_downloader.py')
                
                result = subprocess.run(
                    ['python', daily_downloader_path, selected_symbol],
                    capture_output=True,
                    text=True,
                    timeout=120  # 2分钟超时
                )
                if result.returncode == 0:
                    print("✅ 数据下载完成！")
                    # 检查是否有新数据产生
                    data_file = os.path.join(base_dir, "data", f"ccb_{selected_symbol}_daily.csv")
                    if os.path.exists(data_file) and os.path.getsize(data_file) > 100:
                        print(f"📊 数据已保存到: {data_file}")
                    else:
                        print("⚠️  未检测到新的数据文件")
                else:
                    print(f"❌ 数据下载失败: {result.stderr}")
            except subprocess.TimeoutExpired:
                print("⏰ 数据下载超时，请稍后手动下载")
            except Exception as e:
                print(f"❌ 调用下载工具失败: {e}")
        else:
            print(f"❌ 配置保存失败，但环境变量已临时设置")

# ------------- 主菜单 -------------
def main_menu():
    menu = {
        "1": ("日线预测", daily_predict),
        "2": ("在线预测", realtime_predict),
        "3": ("日线回测", backtest),
        "4": ("工具箱", tool_runner),
        "5": ("切换股票", select_stock),
        "0": ("退出", lambda: sys.exit(0)),
    }
    while True:
        # 显示当前股票信息
        current_symbol = os.environ.get("STOCK_SYMBOL", cfg.STOCK_SYMBOL)
        current_name = os.environ.get("STOCK_NAME", cfg.STOCK_NAME)
        
        print("\n" + "="*40)
        print(f"       股票预测控制台 v6.5")
        print(f"       当前股票: {current_symbol} - {current_name}")
        print("="*40)
        for k, (desc, _) in menu.items():
            print(f"{k} - {desc}")
        choice = input("请选择: ").strip()
        if choice not in menu:
            print("❌ 无效选择")
            continue
        _, func = menu[choice]
        func()

# ------------- 入口 -------------
def main():
    # 启动时加载持久化配置
    load_current_stock()
    
    current_symbol = os.environ.get("STOCK_SYMBOL", cfg.STOCK_SYMBOL)
    current_name = os.environ.get("STOCK_NAME", cfg.STOCK_NAME)
    
    print("🎯 股票预测控制台（算法/工具 即插即用）")
    print(f"📈 当前股票: {current_symbol} - {current_name}")
    print("算法目录:", cfg.ALGO_DIR)
    print("工具目录: Tools/")
    main_menu()

if __name__ == "__main__":
    main()