#!/usr/bin/env python3
"""
PM工作脚本 - 用于管理StockAdvisor项目开发
功能：
1. 监控workers状态
2. 分配任务
3. 检查代码质量
4. 发现问题并提供指导
"""
import subprocess
import json
import os
from datetime import datetime

WORKSPACE = "/data/data/com.termux/files/home/Py_Learning"
ORIGINAL_CODE = "/data/data/com.termux/files/home/code_scan/NewProjectV2402"

def get_workers_status():
    """获取workers状态"""
    try:
        result = subprocess.run(
            ["hermes", "profile", "list"],
            capture_output=True,
            text=True,
            timeout=10
        )
        return result.stdout
    except Exception as e:
        return f"Error getting workers status: {e}"

def check_kanban_tasks():
    """检查kanban任务状态"""
    try:
        result = subprocess.run(
            ["hermes", "kanban", "list"],
            capture_output=True,
            text=True,
            timeout=10
        )
        return result.stdout
    except Exception as e:
        return f"Error checking kanban tasks: {e}"

def check_project_status():
    """检查项目状态"""
    status = {
        "backend_api": [],
        "app_screens": [],
        "missing_features": []
    }
    
    # 检查后端API
    backend_api_dir = os.path.join(WORKSPACE, "backend/api")
    if os.path.exists(backend_api_dir):
        for f in os.listdir(backend_api_dir):
            if f.endswith(".py"):
                status["backend_api"].append(f)
    
    # 检查App页面
    app_screen_dir = os.path.join(WORKSPACE, "android/app/src/main/java/com/tangtang/stockadvisor/ui/screen")
    if os.path.exists(app_screen_dir):
        for f in os.listdir(app_screen_dir):
            if f.endswith(".kt"):
                status["app_screens"].append(f)
    
    # 检查缺失功能
    required_apis = [
        "portfolio.py", "strategy_db.py", "logs.py", "tools.py",
        "predict.py", "backtest.py", "stock.py", "token.py"
    ]
    for api in required_apis:
        if api not in status["backend_api"]:
            status["missing_features"].append(f"Missing API: {api}")
    
    required_screens = [
        "PortfolioScreen.kt", "SettingsScreen.kt", "StockSearchScreen.kt",
        "PredictionScreen.kt", "BacktestScreen.kt", "StrategyScreen.kt"
    ]
    for screen in required_screens:
        if screen not in status["app_screens"]:
            status["missing_features"].append(f"Missing Screen: {screen}")
    
    return status

def generate_pm_report():
    """生成PM报告"""
    report = []
    report.append("=" * 60)
    report.append("StockAdvisor PM Report")
    report.append(f"Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report.append("=" * 60)
    
    # Workers状态
    report.append("\n## Workers Status")
    report.append(get_workers_status())
    
    # Kanban任务
    report.append("\n## Kanban Tasks")
    report.append(check_kanban_tasks())
    
    # 项目状态
    report.append("\n## Project Status")
    status = check_project_status()
    report.append(f"Backend APIs: {len(status['backend_api'])}")
    report.append(f"App Screens: {len(status['app_screens'])}")
    if status["missing_features"]:
        report.append("\n### Missing Features")
        for feature in status["missing_features"]:
            report.append(f"- {feature}")
    
    return "\n".join(report)

if __name__ == "__main__":
    print(generate_pm_report())