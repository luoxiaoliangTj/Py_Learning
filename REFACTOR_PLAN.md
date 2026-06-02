# 去后端化重构执行计划

## 目标
完全移除 FastAPI 后端依赖，所有功能在 Android App 本地实现。

## 已完成
- [x] 持仓管理本地JSON存储（real_positions.json + global_capital.json）
- [x] 持仓.md文件导入 + 5级代码匹配
- [x] 后端地址默认改为127.0.0.1（临时方案，最终删除）

## Phase 1: 数据获取层（最高优先级）
### W1: 实时数据获取模块
- 文件: `android/app/src/main/java/com/tangtang/stockadvisor/data/remote/RealtimeDataSource.kt`
- 功能: 调用新浪/网易/搜狐API获取实时行情
- 新浪: `http://hq.sinajs.cn/list=sh600036` → JS文本解析
- 网易: `http://api.money.126.net/data/feed/0600036` → JSONP解析
- 搜狐: 复用网易接口
- 三级降级: 新浪→网易→搜狐
- 参考: `NewProjectV2402/algorithms/data_sources/sina_source.py` 等

### W2: 历史K线下载模块
- 文件: `android/app/src/main/java/com/tangtang/stockadvisor/data/remote/HistoricalDataDownloader.kt`
- 功能: 下载历史日线数据并保存为CSV
- 新浪: `CN_MarketData.getKLineData` → JSON
- 搜狐: `q.stock.sohu.com/hisHq` → JSON
- Tushare: 可选（需要token）
- CSV存储: `filesDir/data/ccb_{code}_daily.csv`
- 参考: `NewProjectV2402/Tools/daily_downloader.py`

### W3: 股票列表模块
- 文件: `android/app/src/main/java/com/tangtang/stockadvisor/data/remote/StockListProvider.kt`
- 功能: 获取A股股票列表（用于首页显示和搜索）
- 方案: 本地硬编码常用股票 + 支持手动添加
- 或: 从新浪/网易API获取

## Phase 2: 策略引擎层
### W4: 技术指标计算库
- 文件: `android/app/src/main/java/com/tangtang/stockadvisor/engine/indicators/TechnicalIndicators.kt`
- 功能: MA/EMA/RSI/MACD/ATR/布林带等指标计算
- 参考: `NewProjectV2402/statistics/*.py`

### W5: 通道策略引擎
- 文件: `android/app/src/main/java/com/tangtang/stockadvisor/engine/ChannelStrategy.kt`
- 功能: 11个统计插件的width计算 + 通道信号判断
- 参考: `NewProjectV2402/statistics/base.py` + 各插件

### W6: 趋势策略引擎
- 文件: `android/app/src/main/java/com/tangtang/stockadvisor/engine/TrendStrategy.kt`
- 功能: MA交叉信号判断
- 参考: `NewProjectV2402/scripts/backtest_modules/strategy_engine.py`

## Phase 3: 回测引擎层
### W7: 回测主循环
- 文件: `android/app/src/main/java/com/tangtang/stockadvisor/engine/BacktestEngine.kt`
- 功能: 逐日模拟交易 + 绩效指标计算
- 参考: `NewProjectV2402/scripts/backtest_day.py`

### W8: 交易建议生成
- 文件: `android/app/src/main/java/com/tangtang/stockadvisor/engine/TradingAdvisor.kt`
- 功能: 动态止盈止损 + 5种交易建议
- 参考: `NewProjectV2402/TradingAdvisorModule.py`

## Phase 4: UI集成
### W9: 首页集成实时行情
- 修改: HomeScreen + HomeViewModel
- 功能: 首页显示持仓股票的实时行情
- 去掉: 所有后端API调用

### W10: 预测页面集成策略引擎
- 修改: PredictScreen + PredictViewModel
- 功能: 选择股票后本地计算预测区间

### W11: 回测页面集成
- 修改: BacktestScreen + BacktestViewModel
- 功能: 选择股票+策略后本地回测

### W12: 移除所有后端依赖
- 删除: ApiClient.kt 中所有后端相关方法
- 删除: Settings 页面中的后端地址配置
- 修改: 所有 ViewModel 去掉后端调用

## 执行顺序
Phase 1 → Phase 2 → Phase 3 → Phase 4

## 质量检查
每个Phase完成后：
1. 编译验证（CI通过）
2. 功能测试（真机验证）
3. 代码审查（检查死代码/重复代码）
