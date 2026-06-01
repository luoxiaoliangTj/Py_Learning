"""
FastAPI 应用入口 - 股票策略回测与预测系统后端服务。
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.predict import router as predict_router
from api.backtest import router as backtest_router
from api.stock import router as stock_router
from api.portfolio import router as portfolio_router
from api.strategy import router as strategy_router
from api.tools import router as tools_router
from api.token import router as token_router
from api.strategy_db import router as strategy_db_router
from api.logs import router as logs_router

app = FastAPI(
    title="股票策略回测与预测系统",
    description="基于 FastAPI 的股票策略回测、预测与投资组合管理后端 API",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(predict_router)
app.include_router(backtest_router)
app.include_router(stock_router)
app.include_router(portfolio_router)
app.include_router(strategy_router)
app.include_router(tools_router)
app.include_router(token_router)
app.include_router(strategy_db_router)
app.include_router(logs_router)


@app.get("/", summary="服务状态", tags=["根路径"])
async def root():
    return {
        "code": 200,
        "message": "服务运行正常",
        "data": {"service": "股票策略回测与预测系统 API", "version": "1.0.0", "docs": "/docs"},
    }


@app.get("/health", summary="健康检查", tags=["根路径"])
async def health_check():
    return {"code": 200, "message": "healthy", "data": None}
