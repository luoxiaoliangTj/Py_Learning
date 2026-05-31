"""
FastAPI 应用入口 - 股票策略回测与预测系统后端服务。

注册所有路由模块，配置 CORS 中间件供 Android 端调用。
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# 导入所有路由模块
from api.predict import router as predict_router
from api.backtest import router as backtest_router
from api.stock import router as stock_router
from api.portfolio import router as portfolio_router
from api.strategy import router as strategy_router
from api.tools import router as tools_router

# 创建 FastAPI 应用实例
app = FastAPI(
    title="股票策略回测与预测系统",
    description="基于 FastAPI 的股票策略回测、预测与投资组合管理后端 API",
    version="1.0.0",
)

# 配置 CORS 中间件 - 允许所有来源（供 Android 端调用）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # 允许所有来源
    allow_credentials=True,
    allow_methods=["*"],          # 允许所有 HTTP 方法
    allow_headers=["*"],          # 允许所有请求头
)

# 注册所有路由
app.include_router(predict_router)
app.include_router(backtest_router)
app.include_router(stock_router)
app.include_router(portfolio_router)
app.include_router(strategy_router)
app.include_router(tools_router)


@app.get("/", summary="服务状态", tags=["根路径"])
async def root():
    """根路径 - 返回服务状态信息。"""
    return {
        "code": 200,
        "message": "服务运行正常",
        "data": {
            "service": "股票策略回测与预测系统 API",
            "version": "1.0.0",
            "docs": "/docs",
        },
    }


@app.get("/health", summary="健康检查", tags=["根路径"])
async def health_check():
    """健康检查端点。"""
    return {"code": 200, "message": "healthy", "data": None}
