#!/usr/bin/env python3
import os
import sys

# 添加项目路径（复制 position_manager_tool.py 的设置）
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)

print(f"BASE_DIR: {BASE_DIR}")
print(f"sys.path: {sys.path}")

# 尝试导入
try:
    from tools.capital_manager_tool import CapitalManager
    print("✅ 导入成功!")
    # 测试创建对象
    mgr = CapitalManager()
    print("✅ CapitalManager 对象创建成功!")
except ImportError as e:
    print(f"❌ 导入失败: {e}")
    
# 列出 tools 目录内容
tools_dir = os.path.join(BASE_DIR, "tools")
if os.path.exists(tools_dir):
    print(f"\ntools/ 目录内容: {os.listdir(tools_dir)}")
else:
    print(f"\n❌ 目录不存在: {tools_dir}")
