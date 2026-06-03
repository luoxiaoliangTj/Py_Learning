# 原始代码 (RunUI.py) 用户操作流程

## 启动流程
1. 运行 `python RunUI.py`
2. 自动加载 `current_stock.json`（上次选择的股票）
3. 显示主菜单

## 主菜单 (6个选项)
```
========================================
       股票预测控制台 v6.5
       当前股票: 600519 - 贵州茅台
========================================
1 - 日线预测
2 - 在线预测
3 - 日线回测
4 - 工具箱
5 - 切换股票
0 - 退出
```

## 操作 5: 切换股票 (核心入口)
1. 显示当前股票
2. 从 `real_positions.json` 加载持仓列表
3. 显示有效持仓（shares > 0）编号列表
4. 用户输入编号选择持仓股票
5. 自动判断市场（6开头→上证指数，其他→深证成指）
6. 保存到 `current_stock.json`（持久化）
7. **自动调用 daily_downloader.py 下载该股票日线数据**

## 操作 1: 日线预测
1. 检查当前股票是否已选择
2. 检查数据文件 `data/CCB_{symbol}_daily.csv` 是否存在
3. 从 `strategy_db.json` 加载该股票的策略
4. 调用 `algorithms/` 目录下的算法插件
5. 输出预测结果（pred_low, pred_high, confidence）
6. 记录到每日工作日志

## 操作 2: 在线预测
1. 调用 `algorithms/online_predictor.py`
2. 实时监控模式

## 操作 3: 日线回测
1. 选项：仅回测 / 回测+图表
2. 运行 `scripts/backtest_day.py`
3. 可选生成PNG图表

## 操作 4: 工具箱
1. 扫描 `Tools/` 目录下的 .py 文件
2. 显示工具列表
3. 用户选择后延迟加载并执行 `run_tool()`

## 关键数据流
```
.md 持仓文件
  ↓ (position_manager_tool.py 导入)
real_positions.json (持仓数据)
global_capital.json (资金数据)
  ↓ (RunUI 读取)
持仓列表 → 用户选择 → current_stock.json
  ↓ (自动调用)
daily_downloader.py → data/CCB_{symbol}_daily.csv
  ↓ (预测/回测使用)
算法插件 / backtest_day.py
```

## 与当前 App 的对比

| 原始代码 | 当前 App | 差距 |
|---------|---------|------|
| 主菜单6个选项 | 底部Tab导航 | App缺少"切换股票"入口 |
| 选股票→预测/回测 | 独立的Predict/Backtest Screen | App没有"选股票"这个前置步骤 |
| 自动下载日线数据 | 无此功能 | App没有自动下载 |
| 策略库 (strategy_db.json) | 无 | App没有策略库 |
| 工具箱 (Tools/) | 无 | App没有工具箱 |
| 在线预测 (online_predictor) | 无 | App没有 |
| 回测+图表 | BacktestScreen存在但半成品 | 需要完善 |
| 持仓导入 (.md文件) | ✅ 已有 | 已对齐 |
| 持仓显示 | ✅ 已有 | 已对齐 |
