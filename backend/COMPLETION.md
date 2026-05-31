# 后端项目完成文档 (COMPLETION.md)

## 项目概述

基于 FastAPI 构建的股票策略回测与预测系统后端服务，为 Android 端 StockAdvisor 应用提供 RESTful API 接口。

**核心功能：**
- 股票日线数据下载（新浪财经 / 搜狐财经）
- 策略回测（通道策略 / 趋势策略）
- 日线价格预测区间生成
- 实时价格预测（LMSR 策略）
- 策略参数优化（网格搜索）
- 多维度策略绩效评估
- 投资组合管理（持仓 & 资金）
- 股票列表管理与切换

**技术栈：**
- Python 3.13+
- FastAPI (REST API 框架)
- Pandas + NumPy (数据处理与计算)
- Pydantic (请求/响应数据验证)
- Uvicorn (ASGI 服务器)

---

## API 端点列表（11个端点）

### 1. `GET /` - 服务状态

**响应示例：**
```json
{
  "code": 200,
  "message": "服务运行正常",
  "data": {
    "service": "股票策略回测与预测系统 API",
    "version": "1.0.0",
    "docs": "/docs"
  }
}
```

### 2. `GET /health` - 健康检查

**响应示例：**
```json
{
  "code": 200,
  "message": "healthy",
  "data": null
}
```

### 3. `GET /api/stock/list` - 股票列表

**响应示例：**
```json
{
  "code": 200,
  "message": "获取成功",
  "data": [
    {
      "code": "000001",
      "name": "000001",
      "current_price": 0.0,
      "change_percent": 0.0,
      "change_amount": 0.0,
      "volume": 0,
      "turnover": 0.0,
      "high": 0.0,
      "low": 0.0,
      "open": 0.0,
      "prev_close": 0.0,
      "market_cap": 0.0,
      "pe_ratio": 0.0
    }
  ]
}
```

### 4. `POST /api/stock/select` - 切换股票

**请求体：**
```json
{
  "symbol": "000001",
  "name": "平安银行",
  "index_symbol": "000001",
  "index_name": "上证指数"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "已切换到 000001",
  "data": {
    "symbol": "000001",
    "name": "平安银行",
    "index_symbol": "000001",
    "index_name": "上证指数"
  }
}
```

### 5. `POST /api/predict/daily` - 日线预测

**请求体：**
```json
{
  "symbol": "000001"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "预测成功",
  "data": {
    "code": "000001",
    "name": "000001",
    "current_price": 12.50,
    "predicted_high": 13.20,
    "predicted_low": 11.80,
    "predicted_close": 12.50,
    "confidence": 0.72,
    "strategies": [],
    "timestamp": "2025-01-15T10:30:00.000000"
  }
}
```

### 6. `POST /api/predict/realtime` - 在线预测

**请求体：**
```json
{
  "symbol": "000001",
  "prev_close": 12.45
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "实时预测成功",
  "data": {
    "code": "000001",
    "name": "000001",
    "current_price": 12.50,
    "predicted_price": 12.55,
    "confidence": 0.85,
    "signals": [
      {
        "name": "LMSR",
        "signal": "BUY",
        "weight": 0.85,
        "value": 12.55
      }
    ],
    "update_time": "2025-01-15T10:30:00.000000"
  }
}
```

### 7. `POST /api/backtest` - 策略回测

**请求体：**
```json
{
  "symbol": "000001",
  "strategy_type": "channel",
  "params": {"type": "channel", "k": 2.0}
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "回测成功",
  "data": {
    "code": "000001",
    "name": "000001",
    "start_date": "2020-01-02",
    "end_date": "2024-12-31",
    "initial_capital": 100000.0,
    "final_capital": 125000.0,
    "total_return": 0.25,
    "annual_return": 0.0572,
    "max_drawdown": -0.1523,
    "sharpe_ratio": 1.2345,
    "win_rate": 0.5,
    "total_trades": 42,
    "equity_curve": [
      {"date": "2020-01-02", "value": 100000.0},
      {"date": "2020-01-03", "value": 100150.0}
    ],
    "trades": [
      {
        "date": "2020-01-15",
        "type": "BUY",
        "price": 12.50,
        "shares": 1000,
        "amount": 12500.0,
        "pnl": 0.0
      }
    ]
  }
}
```

### 8. `POST /api/strategy/optimize` - 参数优化

**请求体：**
```json
{
  "symbol": "000001",
  "strategy_type": "channel"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "优化完成",
  "data": {
    "code": "000001",
    "name": "000001",
    "strategy_type": "channel",
    "best_params": {"type": "channel", "k": 2.0},
    "best_sharpe": 1.2345,
    "best_return": 0.25,
    "all_results": [...]
  }
}
```

### 9. `POST /api/strategy/evaluate` - 策略评估

**请求体：**
```json
{
  "symbol": "000001",
  "strategy_type": "channel"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "评估完成",
  "data": {
    "code": "000001",
    "name": "000001",
    "strategy_type": "channel",
    "evaluation": {
      "composite_score": 0.65,
      "detailed_metrics": {...},
      "strengths": ["优秀的风险调整收益"],
      "weaknesses": ["交易样本不足"],
      "improvement_suggestions": ["策略表现良好，继续保持当前参数"]
    }
  }
}
```

### 10. `GET /api/portfolio/holdings` - 持仓信息

**响应示例：**
```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "total_market_value": 50000.0,
    "total_cost": 48000.0,
    "total_profit_loss": 2000.0,
    "total_profit_loss_percent": 4.17,
    "items": [
      {
        "code": "000001",
        "name": "平安银行",
        "shares": 1000,
        "avg_cost": 12.5,
        "current_price": 0.0,
        "market_value": 12500.0,
        "profit_loss": 0.0,
        "profit_loss_percent": 0.0
      }
    ]
  }
}
```

### 11. `GET /api/portfolio/capital` / `POST /api/portfolio/capital` - 资金信息

**GET 响应示例：**
```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "available_cash": 100000.0,
    "total_capital": 100000.0,
    "last_updated": "2025-01-15 10:30:00",
    "note": "默认资金配置"
  }
}
```

### 12. `POST /api/tools/download` - 下载日线数据

**请求体：**
```json
{
  "symbol": "000001",
  "years": 8,
  "source": "auto"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "下载成功",
  "data": {
    "success": true,
    "message": "新浪财经下载成功 | 数据质量验证通过",
    "rows": 2000,
    "file": "/path/to/data/ccb_000001_daily.csv",
    "source": "sina"
  }
}
```

### 13. `GET /api/tools/download/status/{symbol}` - 检查数据状态

**响应示例：**
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "exists": true,
    "data_points": 2000,
    "date_range": "2020-01-01 ~ 2024-12-31",
    "message": "数据量: 2000条"
  }
}
```

---

## 目录结构说明

```
backend/
├── main.py                 # FastAPI 应用入口，注册路由 + CORS
├── requirements.txt        # Python 依赖
├── Dockerfile              # Docker 构建文件
├── COMPLETION.md           # 本文档
├── test_api.py             # API 测试脚本
├── core/                   # 核心逻辑层（不修改）
│   ├── config.py           # 项目路径与配置
│   ├── data_loader.py      # 数据加载工具
│   ├── predictor.py        # 预测引擎（日线/实时）
│   ├── strategy_engine.py  # 策略引擎（回测/评分/优化）
│   ├── performance_analyzer.py  # 绩效分析器
│   ├── capital_manager.py  # 资金管理
│   ├── position_manager.py # 持仓管理
│   ├── downloader.py       # 数据下载器
│   └── lmsr_engine.py      # LMSR 做市引擎
├── api/                    # API 路由层
│   ├── predict.py          # 预测相关路由
│   ├── backtest.py         # 回测路由
│   ├── stock.py            # 股票管理路由
│   ├── portfolio.py        # 投资组合路由
│   ├── strategy.py         # 策略管理路由
│   └── tools.py            # 工具路由（数据下载）
└── models/                 # Pydantic 数据模型
    └── __init__.py         # 请求/响应模型定义
```

---

## 如何运行

### 本地运行

```bash
# 1. 进入后端目录
cd ~/Py_Learning/backend

# 2. 创建虚拟环境（推荐）
python -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate   # Windows

# 3. 安装依赖
pip install -r requirements.txt

# 4. 启动服务
uvicorn main:app --host 0.0.0.0 --port 8000 --reload

# 5. 访问 API 文档
# Swagger UI: http://localhost:8000/docs
# ReDoc:      http://localhost:8000/redoc
```

### Docker 运行

```bash
# 1. 构建镜像
cd ~/Py_Learning/backend
docker build -t stock-backend .

# 2. 运行容器
docker run -d -p 8000:8000 --name stock-backend stock-backend

# 3. 访问
# http://localhost:8000/docs
```

---

## 与原 Python 项目的对应关系

| 后端模块 | 原始项目文件 | 说明 |
|---------|------------|------|
| `core/config.py` | `main.py` (全局变量) | 项目路径配置 |
| `core/data_loader.py` | `core/data_loader.py` | CSV 数据加载 |
| `core/predictor.py` | `algorithms/TradePlanAlgo.py` + `algorithms/online_predictor.py` | 预测逻辑 |
| `core/strategy_engine.py` | `scripts/backtest_modules/strategy_engine.py` | 回测引擎 |
| `core/performance_analyzer.py` | `scripts/backtest_modules/performance_analyzer.py` | 绩效分析 |
| `core/capital_manager.py` | `Tools/capital_manager_tool.py` | 资金管理 |
| `core/position_manager.py` | `Tools/position_manager_tool.py` | 持仓管理 |
| `core/downloader.py` | `Tools/daily_downloader.py` | 数据下载 |
| `core/lmsr_engine.py` | `lmsr.py` | LMSR 做市引擎 |

---

## 已知限制

1. **实时行情缺失**：后端无实时行情数据源，`StockInfo` 中的 `current_price`、`change_percent` 等字段暂返回 0，需后续接入实时行情 API。
2. **股票名称映射**：后端使用股票代码作为名称（`name = symbol`），无中文名称映射表。
3. **胜率计算简化**：回测结果中的 `win_rate` 固定返回 0.5，未实现真实胜率统计。
4. **单用户设计**：无用户认证系统，所有用户共享同一份持仓和资金数据。
5. **数据目录依赖**：依赖 `~/Py_Learning/data/` 目录结构，需先下载数据才能使用回测/预测功能。
6. **无持久化数据库**：使用 JSON 文件存储持仓和资金信息，不适合高并发场景。
7. **预测策略信号为空**：`PredictionResult.strategies` 返回空数组，待后续实现多策略信号聚合。
8. **权益曲线日期简化**：回测结果中 `equity_curve` 的日期字段为简化处理，可能与实际交易日不完全对应。
