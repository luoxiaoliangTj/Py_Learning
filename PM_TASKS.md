# PM 任务分配 - Phase 5 功能补全

## 已完成
- 后端持仓 CRUD API ✅
- 后端策略库 API ✅
- 后端每日日志 API ✅
- 后端股票切换 API ✅
- 后端持仓导入逻辑（清仓+资金同步）✅

## 待完成

### Worker 1: 后端下载器补全
**任务：** 补全 backend/core/downloader.py，添加 Tushare 数据源
**原始代码参考：**
- `Tools/daily_downloader.py` 第 18-24 行：Tushare 导入逻辑
- `backend/core/config.py` 第 33-43 行：TUSHARE_TOKEN 读取
- 当前后端 downloader.py 只有 sina + sohu，需要加 tushare

### Worker 2: 后端实时数据 API
**任务：** 创建 backend/api/realtime.py，提供实时行情查询
**原始代码参考：**
- `algorithms/data_sources/sina_source.py`：新浪实时数据
- `algorithms/data_sources/netease_source.py`：网易实时数据
- `algorithms/data_sources/sohu_source.py`：搜狐实时数据
- `algorithms/real_time_service.py`：多数据源自动切换

### Worker 3: App 持仓管理页
**任务：** 创建完整持仓管理页面（查看/添加/修改/删除/清空）
**后端 API 参考：**
- `GET /api/portfolio/holdings`
- `POST /api/portfolio/position`
- `DELETE /api/portfolio/position/{symbol}`
- `DELETE /api/portfolio/positions`

### Worker 4: App 策略库页 + 日志页
**任务：** 创建策略库页面和日志页面
**后端 API 参考：**
- `GET /api/strategy/list`
- `GET /api/strategy/{symbol}`
- `POST /api/strategy/{symbol}`
- `GET /api/logs/list`
- `GET /api/logs/{date}`

## 当前问题
- Worker 1 的 Tushare 集成需要确认 token 管理逻辑
- Worker 2 的实时数据需要确认数据源优先级
- Worker 3/4 的 UI 设计需要参考原始代码的交互逻辑

## 下一步
1. 先启动 Worker 1 和 Worker 3（后端+App 并行）
2. Worker 2 等 Worker 1 完成后启动（需要数据源测试）
3. Worker 4 等 Worker 3 完成后启动（共享 UI 框架）
