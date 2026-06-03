# StockAdvisor App 重构计划

## 一、原始代码架构分析

### 核心流程
```
RunUI.py (主界面)
  ├── 显示持仓列表 (position_manager_tool.py)
  │     └── 从 .md 文件导入 → real_positions.json + global_capital.json
  ├── 用户选择某只股票
  │     └── 进入单股操作界面，可选：
  │           ├── ① 下载日线数据 (daily_downloader.py)
  │           ├── ② 回测 (backtest_day.py / scripts/backtest_modules/)
  │           ├── ③ 预测 (TradingAdvisorModule.py → generate_trading_advice)
  │           └── ④ 实时监测 (real_time_service.py)
  └── 交易建议 (TradingAdvisorModule.py)
        ├── 加载持仓信息 (stock_holdings.json)
        ├── 计算盈亏 (calculate_profit_loss)
        ├── 动态止盈止损 (calculate_dynamic_thresholds)
        └── 生成建议 (generate_trading_advice)
```

### 关键数据文件
| 文件 | 用途 | 当前App |
|------|------|---------|
| `real_positions.json` | 持仓数据 (代码→{名称,股数,成本价}) | ✅ 已有 |
| `global_capital.json` | 全局资金 (可用资金,总资金) | ✅ 已有 |
| `stock_holdings.json` | 单股持仓 (含aggressive_factor等) | ❌ 未用 |
| `name_code_cache.json` | 名称→代码映射 | ✅ 新增 |
| `stock_list.json` | 全量股票列表 | ❌ 未用 |

### 原始代码功能清单
1. **持仓管理** — 从.md导入、显示、清仓 ✅ App已有
2. **选股** — 从持仓中选择单只股票操作 ❌ App无此流程
3. **下载日线数据** — 新浪/搜狐/Tushare三级降级 ✅ App有但独立
4. **回测** — 通道策略/趋势策略，93种参数组合网格评估 ✅ App有但简化
5. **预测** — 动态止盈止损+6种交易建议 ⚠️ App有TradingAdvisor但未接入流程
6. **实时监测** — 新浪/网易/搜狐实时行情 ✅ App有

## 二、当前App架构问题

### 严重问题
1. **没有"选股→操作"流程** — 原始代码核心是"从持仓中选一只股票，然后对其操作"，App缺少这个入口
2. **TradingAdvisor 孤立** — 有代码但没接入任何UI流程
3. **回测/预测功能隐藏太深** — 没有从持仓选股的上下文
4. **缺少单股详情页** — 原始代码有详细的单股操作界面

### 架构差异
| 原始代码 | 当前App | 问题 |
|---------|---------|------|
| RunUI主界面→持仓列表→选股→操作 | 底部Tab导航→持仓Tab | 没有选股入口 |
| 单股操作界面(下载/回测/预测/监测) | 独立的Backtest/Predict Screen | 缺少持仓上下文 |
| TradingAdvisor.generate_trading_advice() | TradingAdvisor.kt存在但未调用 | 功能孤立 |
| 实时监测是独立功能 | RealtimeViewModel存在但无UI | 功能孤立 |

## 三、重构计划 (Phase 6)

### 6.1 新增"选股"入口
- 持仓列表中每只股票可点击 → 进入单股详情页
- 或在持仓列表顶部加"选择股票"搜索框

### 6.2 新增"单股详情页" (StockDetailScreen)
- 显示：股票名称、代码、持仓股数、成本价、现价、盈亏
- 四个操作按钮：
  - 📥 下载日线数据
  - 📊 回测
  - 🔮 预测/交易建议
  - 📡 实时监测

### 6.3 接入 TradingAdvisor
- 在"预测"功能中调用 TradingAdvisor.generateTradingAdvice()
- 传入：currentPrice, predictedLow, predictedHigh, stockData, intradayVolatility
- 显示：止盈止损阈值、交易建议列表、做T建议

### 6.4 完善回测功能
- 当前BacktestViewModel是半成品
- 需要：选择策略类型 → 下载K线 → 运行回测 → 显示结果

### 6.5 完善实时监测
- 当前RealtimeViewModel存在但无实际UI
- 需要：实时价格刷新、分时数据、涨跌幅

### 6.6 数据层完善
- stock_holdings.json (单股持仓详情)
- 回测结果缓存
- 预测结果缓存

## 四、执行顺序

### Step 1: 选股入口 + 单股详情页框架
### Step 2: 接入 TradingAdvisor (预测功能)
### Step 3: 完善回测功能
### Step 4: 完善实时监测
### Step 5: 数据层完善 + 缓存
### Step 6: 整体联调 + 编译测试
