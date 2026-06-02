# StockAdvisor 持仓加载修复方案

## 一、问题根因分析

### 当前 App 的问题
1. **持仓导入走后端 API**：用户选择 .md 文件 → App 解析 → 发到后端 → 后端写 JSON
   - 问题：后端是云端/本地服务器，不是手机本地存储
   - 用户说"存到手机卡"，应该是存到手机本地文件系统

2. **loadPortfolio() 从后端读取**：每次启动都依赖网络请求
   - 问题：持仓数据应该从本地 JSON 读取，不需要网络

3. **数据下载依赖 tushare**：没有 tushare key 就失败
   - 原始代码：tushare → 新浪 → 搜狐 三级降级

4. **实时数据只有新浪**：没有降级方案
   - 原始代码：新浪 → 网易 → 搜狐 三级降级

## 二、原始代码架构（NewProjectV2402）

### 数据存储
```
项目根目录/data/
├── real_positions.json      ← 持仓明细
├── global_capital.json      ← 资金信息
├── current_stock.json       ← 当前选中股票
├── name_code_cache.json     ← 名称→代码缓存
├── stock_list_cache.json    ← 全量股票列表缓存
├── strategy_db.json         ← 策略数据库
└── ccb_{symbol}_daily.csv   ← 日线数据
```

### 数据下载降级链
```
daily_downloader.py:
  1. tushare（如果 token 存在）
  2. 新浪财经（主数据源）
  3. 搜狐财经（备用）
  
  无 tushare token → 自动禁用 tushare，用新浪+搜狐
```

### 实时数据降级链
```
real_time_service.py:
  DataSourceTester 测试所有数据源：
  1. 新浪财经（SinaDataSource）
  2. 网易财经（NeteaseDataSource）
  3. 搜狐财经（SohuDataSource）
  
  选择综合评分最高的数据源
  连续失败3次 → 切换数据源
```

### 持仓导入流程
```
position_manager_tool.py:
  1. 扫描 /storage/emulated/0/Documents/mindmaps/炒股/*.md
  2. 按文件名日期排序，取最新
  3. 解析 .md 文件（表格 → 持仓列表）
  4. 5级代码匹配：
     ① 文件中的股票代码列
     ② 现有持仓精确匹配名称
     ③ 现有持仓模糊匹配
     ④ 本地缓存（name_code_cache.json / stock_list_cache.json）
     ⑤ 跳过
  5. 清仓处理（数据库有但文件没有的股票 → shares=0）
  6. 同步资金（总资产/可用资金 → global_capital.json）
  7. 保存 real_positions.json
```

## 三、App 端修复方案

### 3.1 持仓本地存储

**存储路径**：`Context.getFilesDir()/data/`（Android 标准内部存储）
```
/data/data/com.tangtang.stockadvisor/files/data/
├── real_positions.json
├── global_capital.json
└── current_stock.json
```

**修改文件**：
- `PortfolioViewModel.kt`：
  - `importPortfolioFromUri()`：解析后直接写本地 JSON，不发网络
  - `loadPortfolio()`：从本地 JSON 读取，不发网络
  - 新增 `savePositionsToLocal()` / `loadPositionsFromLocal()`
  - 新增 `saveCapitalToLocal()` / `loadCapitalFromLocal()`

- `PortfolioScreen.kt`：
  - 保留 SAF 文件选择器（用户选择 .md 文件）
  - 选择后触发解析 + 本地存储
  - 显示导入结果（成功/失败/跳过数量）

- `StockRepository.kt`：
  - 持仓相关方法改为操作本地 JSON
  - 不再调用后端 API

### 3.2 数据下载降级

**后端已有**：`backend/core/downloader.py`（需要确认是否有降级逻辑）

**检查点**：
- 是否有 tushare token 检查
- 无 token 时是否自动降级到新浪/搜狐
- 下载的数据格式是否正确

### 3.3 实时数据降级

**后端已有**：需要确认是否有新浪/网易/搜狐降级

**检查点**：
- 是否只有新浪一个数据源
- 是否有自动切换逻辑

## 四、Worker 任务拆分

### Worker A：持仓本地存储修复
**文件**：PortfolioViewModel.kt, PortfolioScreen.kt, StockRepository.kt
**参考**：position_manager_tool.py, capital_manager_tool.py
**要点**：
1. 解析 .md 后直接写本地 JSON（不发网络）
2. loadPortfolio 从本地 JSON 读取
3. 5级代码匹配逻辑
4. 清仓处理
5. 资金同步

### Worker B：数据下载降级修复
**文件**：backend/core/downloader.py
**参考**：Tools/daily_downloader.py
**要点**：
1. 检查 tushare token
2. 无 token → 新浪 → 搜狐 降级
3. 数据质量验证

### Worker C：实时数据降级修复
**文件**：后端实时数据模块
**参考**：algorithms/data_sources/, algorithms/real_time_service.py
**要点**：
1. 新浪 → 网易 → 搜狐 三级降级
2. 数据源质量评分
3. 自动切换

## 五、质量检查清单

- [ ] 持仓导入：选择 .md → 解析 → 写本地 JSON → 重新加载显示
- [ ] 持仓持久化：App 重启后从本地 JSON 读取
- [ ] 5级代码匹配：文件代码列 → 精确匹配 → 模糊匹配 → 缓存 → 跳过
- [ ] 清仓处理：文件没有的股票 shares=0
- [ ] 资金同步：总资产/可用资金写入 global_capital.json
- [ ] 数据下载：无 tushare key 时自动降级
- [ ] 实时数据：新浪失败时自动切换网易/搜狐
- [ ] CI 编译通过
