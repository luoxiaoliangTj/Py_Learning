# tools/capital_manager_tool.py
#!/usr/bin/env python3
"""
全局资金管理工具 - 独立工具模块
管理全局可用资金配置，与回测系统集成
"""

import os
import json
import sys
from datetime import datetime

# 添加项目路径
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)

from config.config import cfg

class CapitalManager:
    """全局资金管理器"""
    
    def __init__(self):
        self.capital_file = os.path.join(cfg.DATA_DIR, "global_capital.json")
        self.capital_data = {}
        self.load_capital()
    
    def load_capital(self):
        """加载全局资金配置"""
        try:
            if os.path.exists(self.capital_file):
                with open(self.capital_file, 'r', encoding='utf-8') as f:
                    self.capital_data = json.load(f)
                print(f"✅ 已加载全局资金配置")
                return True
            else:
                # 创建默认配置
                self.capital_data = {
                    'available_cash': 100000.0,
                    'total_capital': 100000.0,
                    'last_updated': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                    'note': '全局资金配置，适用于所有股票回测'
                }
                self.save_capital()
                print("💰 已创建默认全局资金: 100,000.00")
                return True
        except Exception as e:
            print(f"❌ 加载资金配置失败: {e}")
            return False
    
    def save_capital(self):
        """保存全局资金配置"""
        try:
            os.makedirs(cfg.DATA_DIR, exist_ok=True)
            with open(self.capital_file, 'w', encoding='utf-8') as f:
                json.dump(self.capital_data, f, ensure_ascii=False, indent=2)
            print("✅ 资金配置已保存")
            return True
        except Exception as e:
            print(f"❌ 保存资金配置失败: {e}")
            return False
    
    def get_capital_info(self):
        """获取当前资金信息"""
        return self.capital_data
    
    def update_capital(self, available_cash=None, total_capital=None):
        """更新全局资金配置"""
        try:
            if available_cash is not None:
                if available_cash < 0:
                    print("❌ 可用资金不能为负数")
                    return False
                self.capital_data['available_cash'] = float(available_cash)
            
            if total_capital is not None:
                if total_capital < 0:
                    print("❌ 总资金不能为负数")
                    return False
                self.capital_data['total_capital'] = float(total_capital)
            
            # 如果只设置了可用资金，自动设置总资金为相同值
            if available_cash is not None and total_capital is None:
                self.capital_data['total_capital'] = self.capital_data['available_cash']
            
            self.capital_data['last_updated'] = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            
            if self.save_capital():
                print(f"✅ 全局资金已更新")
                print(f"   可用资金: {self.capital_data['available_cash']:,.2f} 元")
                print(f"   总资金: {self.capital_data['total_capital']:,.2f} 元")
                return True
            return False
            
        except ValueError:
            print("❌ 输入格式错误，请确保输入数字")
            return False
    
    def reset_to_default(self):
        """重置为默认资金"""
        self.capital_data = {
            'available_cash': 100000.0,
            'total_capital': 100000.0,
            'last_updated': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'note': '全局资金配置，适用于所有股票回测'
        }
        return self.save_capital()

def run_tool():
    """工具主函数 - 资金管理界面"""
    manager = CapitalManager()
    
    while True:
        print("\n" + "="*50)
        print("           全局资金管理系统")
        print("="*50)
        
        # 显示当前资金状态
        capital_info = manager.get_capital_info()
        print(f"💰 当前资金配置:")
        print(f"   可用资金: {capital_info.get('available_cash', 0):,.2f} 元")
        print(f"   总资金: {capital_info.get('total_capital', 0):,.2f} 元")
        print(f"   最后更新: {capital_info.get('last_updated', '未知')}")
        print("-" * 50)
        
        print("1 - 修改可用资金")
        print("2 - 修改总资金") 
        print("3 - 同时修改可用和总资金")
        print("4 - 重置为默认资金 (100,000元)")
        print("0 - 返回主菜单")
        
        choice = input("请选择: ").strip()
        
        if choice == '1':
            modify_available_cash(manager)
        elif choice == '2':
            modify_total_capital(manager)
        elif choice == '3':
            modify_both_capital(manager)
        elif choice == '4':
            reset_to_default(manager)
        elif choice == '0':
            print("返回主菜单...")
            break
        else:
            print("❌ 无效选择")

def modify_available_cash(manager):
    """修改可用资金"""
    print("\n💰 修改可用资金")
    print("💡 注意: 可用资金是回测时实际可用的现金")
    
    current_info = manager.get_capital_info()
    current_cash = current_info.get('available_cash', 0)
    print(f"当前可用资金: {current_cash:,.2f} 元")
    
    try:
        new_cash = input("请输入新的可用资金 (元): ").strip()
        if not new_cash:
            print("❌ 输入不能为空")
            return
        
        new_cash = float(new_cash)
        if new_cash < 0:
            print("❌ 可用资金不能为负数")
            return
        
        if manager.update_capital(available_cash=new_cash):
            print("✅ 可用资金修改成功")
        else:
            print("❌ 修改失败")
            
    except ValueError:
        print("❌ 输入格式错误，请确保输入数字")

def modify_total_capital(manager):
    """修改总资金"""
    print("\n💰 修改总资金")
    print("💡 注意: 总资金 = 可用资金 + 持仓价值")
    
    current_info = manager.get_capital_info()
    current_total = current_info.get('total_capital', 0)
    print(f"当前总资金: {current_total:,.2f} 元")
    
    try:
        new_total = input("请输入新的总资金 (元): ").strip()
        if not new_total:
            print("❌ 输入不能为空")
            return
        
        new_total = float(new_total)
        if new_total < 0:
            print("❌ 总资金不能为负数")
            return
        
        if manager.update_capital(total_capital=new_total):
            print("✅ 总资金修改成功")
        else:
            print("❌ 修改失败")
            
    except ValueError:
        print("❌ 输入格式错误，请确保输入数字")

def modify_both_capital(manager):
    """同时修改可用资金和总资金"""
    print("\n💰 同时修改可用资金和总资金")
    
    current_info = manager.get_capital_info()
    current_cash = current_info.get('available_cash', 0)
    current_total = current_info.get('total_capital', 0)
    print(f"当前可用资金: {current_cash:,.2f} 元")
    print(f"当前总资金: {current_total:,.2f} 元")
    
    try:
        new_cash = input("请输入新的可用资金 (元): ").strip()
        new_total = input("请输入新的总资金 (元): ").strip()
        
        if not new_cash or not new_total:
            print("❌ 输入不能为空")
            return
        
        new_cash = float(new_cash)
        new_total = float(new_total)
        
        if new_cash < 0 or new_total < 0:
            print("❌ 资金不能为负数")
            return
        
        if new_cash > new_total:
            print("⚠️  警告: 可用资金大于总资金，这通常不合理")
            confirm = input("确认继续? (y/N): ").strip().lower()
            if confirm != 'y':
                return
        
        if manager.update_capital(available_cash=new_cash, total_capital=new_total):
            print("✅ 资金配置修改成功")
        else:
            print("❌ 修改失败")
            
    except ValueError:
        print("❌ 输入格式错误，请确保输入数字")

def reset_to_default(manager):
    """重置为默认资金"""
    print("\n🔄 重置为默认资金")
    print("💡 将恢复为 100,000 元初始资金")
    
    confirm = input("确认重置为默认资金? (y/N): ").strip().lower()
    if confirm == 'y':
        if manager.reset_to_default():
            print("✅ 已重置为默认资金: 100,000.00 元")
        else:
            print("❌ 重置失败")

if __name__ == "__main__":
    run_tool()