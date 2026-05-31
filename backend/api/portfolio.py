"""
API Routes - Portfolio endpoints (holdings & capital).
"""
from fastapi import APIRouter, HTTPException
from models import CapitalUpdateRequest
from core.position_manager import get_all_positions, get_position
from core.capital_manager import get_capital_info, update_capital

router = APIRouter(prefix="/api/portfolio", tags=["投资组合"])


@router.get("/holdings", summary="持仓信息")
async def get_holdings():
    """
    获取所有持仓信息。
    """
    try:
        positions = get_all_positions()
        holdings = []
        for symbol, info in positions.items():
            holdings.append({
                "symbol": symbol,
                "stock_name": info.get("stock_name", ""),
                "shares": info.get("shares", 0),
                "cost_price": info.get("cost_price", 0),
                "last_updated": info.get("last_updated"),
            })
        return {"success": True, "data": holdings, "count": len(holdings)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取持仓失败: {str(e)}")


@router.get("/capital", summary="资金信息")
async def get_capital():
    """
    获取当前资金配置信息。
    """
    try:
        info = get_capital_info()
        return {"success": True, "data": info}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取资金信息失败: {str(e)}")


@router.post("/capital", summary="更新资金配置")
async def update_capital_endpoint(request: CapitalUpdateRequest):
    """
    更新资金配置。
    """
    try:
        info = update_capital(
            available_cash=request.available_cash,
            total_capital=request.total_capital,
        )
        return {"success": True, "data": info}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"更新资金失败: {str(e)}")
