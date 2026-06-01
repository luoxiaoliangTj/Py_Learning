"""
策略库管理 API
"""
from fastapi import APIRouter, HTTPException
from models import StrategySaveRequest
from core.strategy_db import (
    get_stock_strategy,
    save_stock_strategy,
    delete_stock_strategy,
    list_all_strategies,
)

router = APIRouter(prefix="/api/strategy", tags=["策略库"])


@router.get("/list", summary="列出所有策略")
async def list_strategies():
    """列出所有股票的策略配置"""
    try:
        strategies = list_all_strategies()
        return {"code": 200, "message": "获取成功", "data": strategies}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取策略列表失败: {str(e)}")


@router.get("/{symbol}", summary="获取指定股票策略")
async def get_strategy(symbol: str):
    """获取指定股票的策略配置"""
    try:
        strategy = get_stock_strategy(symbol)
        if not strategy:
            return {"code": 404, "message": "策略不存在", "data": None}
        return {"code": 200, "message": "获取成功", "data": strategy}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取策略失败: {str(e)}")


@router.post("/{symbol}", summary="保存指定股票策略")
async def save_strategy(symbol: str, request: StrategySaveRequest):
    """保存指定股票的策略配置"""
    try:
        strategy_info = {
            "active_strategy": request.active_strategy,
            "params": request.params,
            "metrics": request.metrics,
            "source": request.source,
            "last_updated": request.last_updated or __import__("datetime").datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }
        save_stock_strategy(symbol, strategy_info)
        return {"code": 200, "message": "保存成功", "data": strategy_info}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"保存策略失败: {str(e)}")


@router.delete("/{symbol}", summary="删除指定股票策略")
async def remove_strategy(symbol: str):
    """删除指定股票的策略配置"""
    try:
        success = delete_stock_strategy(symbol)
        if success:
            return {"code": 200, "message": "删除成功", "data": None}
        else:
            return {"code": 404, "message": "策略不存在", "data": None}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"删除策略失败: {str(e)}")
