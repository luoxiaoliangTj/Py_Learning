"""
实时数据 API
"""
from fastapi import APIRouter, HTTPException
from core.realtime_data import fetch_realtime_data

router = APIRouter(prefix="/api/realtime", tags=["实时数据"])


@router.get("/{symbol}", summary="获取实时数据")
async def get_realtime(symbol: str):
    """
    获取股票实时数据。
    多数据源自动降级: 新浪 → 网易 → 搜狐
    """
    try:
        result = fetch_realtime_data(symbol)
        if result.get("valid"):
            return {"code": 200, "message": "获取成功", "data": result}
        else:
            return {"code": 404, "message": result.get("message", "获取失败"), "data": None}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取实时数据失败: {str(e)}")
