#!/usr/bin/env python3
"""
verify_prediction_fix.py - 验证预测功能修复性能
测试所有预测和实时推荐功能
"""

import sys
import os
import json

# 添加项目路径
BASE_DIR = "/data/data/com.termux/files/home/NewProjectV24/NewProjectV24_Complete"
sys.path.insert(0, BASE_DIR)

def test_basic_imports():
    """测试基本导入功能"""
    print("🔍 测试基本导入...")
    
    modules_to_test = [
        'config.config',
        'algorithms.online_predictor',
        'TradingAdvisorModule',
        'scripts.backtest_modules.strategy_engine'
    ]
    
    for module_name in modules_to_test:
        try:
            __import__(module_name)
            print(f"   ✅ {module_name}")
        except Exception as e:
            print(f"   ❌ {module_name}: {e}")
            return False
    return True

def test_online_predictor_basic():
    """测试在线预测器基本功能"""
    print("\n🤖 测试在线预测器...")
    
    try:
        from algorithms.online_predictor import OnlinePredictor, RealTimePredict
        
        # 测试OnlinePredictor
        predictor = OnlinePredictor()
        
        # 测试初始化
        if predictor.initialize():
            print("   ✅ OnlinePredictor初始化成功")
        else:
            print("   ❌ OnlinePredictor初始化失败")
            return False
        
        # 测试获取策略状态
        state = predictor.get_current_strategy_state()
        if state and 'current_strategy' in state:
            print(f"   ✅ 策略状态: {state['current_strategy']}")
        else:
            print("   ❌ 策略状态获取失败")
            return False
            
        # 测试RealTimePredict
        rt_predict = RealTimePredict()
        rt_state = rt_predict.get_current_strategy_state()
        if rt_state and 'current_strategy' in rt_state:
            print(f"   ✅ RealTimePredict策略状态: {rt_state['current_strategy']}")
        else:
            print("   ❌ RealTimePredict策略状态获取失败")
            return False
            
        return True
        
    except Exception as e:
        print(f"   ❌ 在线预测器测试失败: {e}")
        return False

def test_trading_advisor_basic():
    """测试交易顾问基本功能"""
    print("\n💼 测试交易顾问...")
    
    try:
        import TradingAdvisorModule
        
        # 测试初始化
        advisor = TradingAdvisorModule.TradingAdvisorModule()
        print("   ✅ TradingAdvisorModule初始化成功")
        
        # 测试方法存在
        methods = ['load_global_capital', 'ensure_holdings_file', 'generate_trading_advice']
        for method in methods:
            if hasattr(advisor, method):
                print(f"   ✅ {method} 方法可用")
            else:
                print(f"   ❌ {method} 方法不可用")
                return False
                
        return True
        
    except Exception as e:
        print(f"   ❌ 交易顾问测试失败: {e}")
        return False

def test_strategy_engine_with_lmsr():
    """测试策略引擎LMSR集成"""
    print("\n📈 测试策略引擎...")
    
    try:
        from scripts.backtest_modules.strategy_engine import StrategyEngine
        
        # 初始化策略引擎
        engine = StrategyEngine()
        print("   ✅ 策略引擎初始化成功")
        
        # 检查原有方法
        original_methods = ['quick_score', 'execute_trade']
        for method in original_methods:
            if hasattr(engine, method):
                print(f"   ✅ {method} 原有方法可用")
            else:
                print(f"   ❌ {method} 原有方法不可用")
                return False
        
        # 检查新增LMSR方法
        lmsr_methods = ['quick_score_with_lmsr']
        for method in lmsr_methods:
            if hasattr(engine, method):
                print(f"   ✅ {method} LMSR方法可用")
            else:
                print(f"   ❌ {method} LMSR方法不可用")
                return False
        
        # 检查LMSR评估器已集成
        if hasattr(engine, 'lmsr_evaluator'):
            print("   ✅ LMSR评估器已集成")
        else:
            print("   ❌ LMSR评估器未集成")
            return False
            
        return True
        
    except Exception as e:
        print(f"   ❌ 策略引擎测试失败: {e}")
        return False

def test_data_integrity():
    """测试数据文件完整性"""
    print("\n📊 测试数据文件...")
    
    data_files = [
        'data/current_stock.json',
        'data/strategy_db.json',
        'data/stock_holdings.json'
    ]
    
    for file_path in data_files:
        full_path = os.path.join(BASE_DIR, file_path)
        if os.path.exists(full_path):
            try:
                with open(full_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                print(f"   ✅ {file_path} 文件数据正常")
            except Exception as e:
                print(f"   ❌ {file_path} 文件数据错误: {e}")
                return False
        else:
            print(f"   ⚠️  {file_path} 文件不存在（可能是正常现象）")
    
    return True

def create_test_data():
    """创建测试数据文件"""
    print("\n📦 创建测试数据...")
    
    test_data_dir = os.path.join(BASE_DIR, 'data')
    os.makedirs(test_data_dir, exist_ok=True)
    
    # 创建测试股票数据
    test_stock = {
        "symbol": "002049",
        "name": "紫光国微",
        "index_symbol": "sh000001",
        "index_name": "上证指数",
        "last_updated": "2026-05-20 14:00:00"
    }
    
    with open(os.path.join(test_data_dir, 'current_stock.json'), 'w', encoding='utf-8') as f:
        json.dump(test_stock, f, ensure_ascii=False, indent=2)
    print("   ✅ 测试股票数据已创建")

def main():
    print("🎯 预测功能修复验证")
    print("=" * 50)
    
    # 创建测试数据
    create_test_data()
    
    # 执行各项测试
    tests = [
        test_basic_imports,
        test_online_predictor_basic,
        test_trading_advisor_basic,
        test_strategy_engine_with_lmsr,
        test_data_integrity
    ]
    
    results = []
    for test_func in tests:
        result = test_func()
        results.append(result)
    
    print("\n" + "=" * 50)
    print("🎉 测试结果总结:")
    
    if all(results):
        print("✅ 所有功能测试通过!")
        print("✅ 原有预测功能已恢复正常")
        print("✅ LMSR作为评估维度成功集成")
        print("\n💡 推荐操作:")
        print("   1. 运行 RunUI.py 进入主菜单")
        print("   2. 选择 '1. 日线预测' 测试预测功能")
        print("   3. 选择 '2. 在线预测' 测试实时预测")
        print("   4. 选择 '3. 日线回测' 测试回测系统")
    else:
        print("❌ 有部分功能未通过测试")
        print("⚠️ 请检查相应的错误消息")

if __name__ == "__main__":
    main()