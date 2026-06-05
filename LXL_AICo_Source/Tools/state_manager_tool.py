# tools/state_manager_tool.py
"""
状态管理工具 - 用于手动管理策略状态
"""
import os
import sys
import json
from datetime import datetime

# 添加模块路径
BASE_DIR = os.path.dirname(os.path.dirname(__file__))
sys.path.insert(0, BASE_DIR)

from algorithms.strategy.state_manager import StrategyStateManager

def list_states(symbol=None):
    """列出所有状态"""
    base_dir = os.path.join(BASE_DIR, "data", "strategy_states")
    
    if not os.path.exists(base_dir):
        print("❌ 状态目录不存在")
        return
    
    if symbol:
        # 显示指定股票的状态
        print(f"\n📂 股票 {symbol} 的状态:")
        
        # 日线状态
        daily_file = os.path.join(base_dir, "daily", f"{symbol}.json")
        if os.path.exists(daily_file):
            with open(daily_file, 'r', encoding='utf-8') as f:
                daily_state = json.load(f)
            print(f"📅 日线策略:")
            print(f"  时间: {daily_state.get('timestamp')}")
            print(f"  原因: {daily_state.get('reason')}")
            print(f"  交易日: {daily_state.get('trading_date')}")
        else:
            print("📅 日线策略: 无")
        
        # 盘中状态
        intraday_file = os.path.join(base_dir, "intraday", f"{symbol}.json")
        if os.path.exists(intraday_file):
            with open(intraday_file, 'r', encoding='utf-8') as f:
                intraday_state = json.load(f)
            print(f"\n📊 盘中调整:")
            print(f"  时间: {intraday_state.get('timestamp')}")
            print(f"  原因: {intraday_state.get('reason')}")
            print(f"  交易日: {intraday_state.get('trading_date')}")
        else:
            print("\n📊 盘中调整: 无")
    else:
        # 显示所有股票状态
        print("\n📂 所有股票状态:")
        
        # 日线策略
        daily_dir = os.path.join(base_dir, "daily")
        if os.path.exists(daily_dir):
            daily_files = [f for f in os.listdir(daily_dir) if f.endswith('.json')]
            print(f"📅 日线策略 ({len(daily_files)}):")
            for file in daily_files:
                symbol = file.replace('.json', '')
                filepath = os.path.join(daily_dir, file)
                try:
                    with open(filepath, 'r', encoding='utf-8') as f:
                        state = json.load(f)
                    print(f"  {symbol}: {state.get('reason')} ({state.get('timestamp')})")
                except:
                    print(f"  {symbol}: 读取失败")
        
        # 盘中调整
        intraday_dir = os.path.join(base_dir, "intraday")
        if os.path.exists(intraday_dir):
            intraday_files = [f for f in os.listdir(intraday_dir) if f.endswith('.json') and not f.endswith('_history.json')]
            print(f"\n📊 盘中调整 ({len(intraday_files)}):")
            for file in intraday_files:
                symbol = file.replace('.json', '')
                filepath = os.path.join(intraday_dir, file)
                try:
                    with open(filepath, 'r', encoding='utf-8') as f:
                        state = json.load(f)
                    print(f"  {symbol}: {state.get('reason')} ({state.get('timestamp')})")
                except:
                    print(f"  {symbol}: 读取失败")

def clear_state(symbol, state_type=None):
    """清理状态"""
    manager = StrategyStateManager(symbol)
    
    if state_type == 'daily':
        manager.clear_all_states(symbol)
        print(f"✅ 已清理 {symbol} 的所有状态")
    elif state_type == 'intraday':
        manager.clear_intraday_state(symbol)
        print(f"✅ 已清理 {symbol} 的盘中状态")
    elif state_type == 'all':
        manager.clear_all_states(symbol)
        print(f"✅ 已清理 {symbol} 的所有状态")
    else:
        print("❌ 请指定状态类型: daily, intraday, all")

def show_state(symbol):
    """显示状态详情"""
    manager = StrategyStateManager(symbol)
    state = manager.load_latest_strategy()
    
    if state:
        print(f"\n📋 股票 {symbol} 的当前状态:")
        print(f"类型: {manager.state_type}")
        print(f"恢复时间: {state.get('_generated_at', 'N/A')}")
        print(f"信心度: {state.get('feasibility_score', 'N/A')}")
        print(f"风险等级: {state.get('risk_level', 'N/A')}")
        
        # 显示价格区间
        pred_range = state.get('prediction_range', {})
        if pred_range:
            print(f"价格区间: {pred_range.get('low', 'N/A')} - {pred_range.get('high', 'N/A')}")
        
        # 显示备注
        if state.get('note'):
            print(f"备注: {state.get('note')}")
    else:
        print(f"❌ 未找到 {symbol} 的状态")

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="策略状态管理工具")
    subparsers = parser.add_subparsers(dest='command', help='命令')
    
    # list 命令
    list_parser = subparsers.add_parser('list', help='列出状态')
    list_parser.add_argument('--symbol', help='股票代码')
    
    # clear 命令
    clear_parser = subparsers.add_parser('clear', help='清理状态')
    clear_parser.add_argument('symbol', help='股票代码')
    clear_parser.add_argument('type', choices=['daily', 'intraday', 'all'], help='状态类型')
    
    # show 命令
    show_parser = subparsers.add_parser('show', help='显示状态详情')
    show_parser.add_argument('symbol', help='股票代码')
    
    args = parser.parse_args()
    
    if args.command == 'list':
        list_states(args.symbol)
    elif args.command == 'clear':
        clear_state(args.symbol, args.type)
    elif args.command == 'show':
        show_state(args.symbol)
    else:
        parser.print_help()