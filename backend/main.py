"""
FastAPI Backend - Stock Strategy Analysis API
Entry point: creates the FastAPI app and includes all routers.
"""
import sys
from pathlib import Path

# Ensure backend/ is on the Python path so `from models import ...` and `from core import ...` work
BACKEND_DIR = Path(__file__).resolve().parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from api.stock import router as stock_router
from api.portfolio import router as portfolio_router
from api.tools import router as tools_router
from api.backtest import router as backtest_router
from api.predict import router as predict_router
from api.strategy import router as strategy_router

app = FastAPI(
    title="Stock Strategy Analysis API",
    description="股票策略分析后端 API — 支持日线数据下载、策略回测、参数优化、价格预测等功能",
    version="1.0.0",
)

# CORS middleware — allow all origins for development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include all routers
app.include_router(stock_router)
app.include_router(portfolio_router)
app.include_router(tools_router)
app.include_router(backtest_router)
app.include_router(predict_router)
app.include_router(strategy_router)


@app.get("/", summary="健康检查")
async def root():
    """API health check / 服务状态检查."""
    return {
        "status": "ok",
        "message": "Stock Strategy Analysis API is running",
        "endpoints": [
            "/api/stock/list",
            "/api/stock/select",
            "/api/portfolio/holdings",
            "/api/portfolio/capital",
            "/api/tools/download",
            "/api/tools/download/status/{symbol}",
            "/api/backtest",
            "/api/predict/daily",
            "/api/predict/realtime",
            "/api/strategy/list",
            "/api/strategy/optimize",
            "/api/strategy/evaluate",
        ],
    }
