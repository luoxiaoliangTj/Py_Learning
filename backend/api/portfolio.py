"""
API Routes - Portfolio endpoints (holdings & capital).
投资组合接口，响应字段对齐 Android 端 PortfolioSummary / PortfolioItem。
"""
from fastapi import APIRouter, HTTPException
from models import CapitalUpdateRequest, PortfolioImportRequest, PositionUpdateRequest
from core.position_manager import get_all_positions, get_position, update_position, delete_position, clear_all_positions
from core.capital_manager import get_capital_info, update_capital

router = APIRouter(prefix="/api/portfolio", tags=["投资组合"])


@router.get("/holdings", summary="持仓信息")
async def get_holdings():
    """
    获取所有持仓信息。
    响应字段对齐 Android PortfolioSummary:
      total_market_value, total_cost, total_profit_loss, total_profit_loss_percent, items
    其中 items 中每个 PortfolioItem 包含:
      code, name, shares, avg_cost, current_price, market_value, profit_loss, profit_loss_percent
    """
    try:
        positions = get_all_positions()
        items = []
        total_market_value = 0.0
        total_cost = 0.0

        for symbol, info in positions.items():
            shares = info.get("shares", 0)
            avg_cost = info.get("cost_price", 0.0)
            cost = shares * avg_cost
            # 暂无实时行情，market_value 使用成本价计算
            market_value = cost
            profit_loss = market_value - cost
            profit_loss_percent = (profit_loss / cost * 100) if cost > 0 else 0.0

            items.append({
                "code": symbol,
                "name": info.get("stock_name", ""),
                "shares": shares,
                "avg_cost": avg_cost,
                "current_price": 0.0,  # 需实时行情，暂为0
                "market_value": round(market_value, 2),
                "profit_loss": round(profit_loss, 2),
                "profit_loss_percent": round(profit_loss_percent, 2),
            })
            total_market_value += market_value
            total_cost += cost

        total_profit_loss = total_market_value - total_cost
        total_profit_loss_percent = (total_profit_loss / total_cost * 100) if total_cost > 0 else 0.0

        response_data = {
            "total_market_value": round(total_market_value, 2),
            "total_cost": round(total_cost, 2),
            "total_profit_loss": round(total_profit_loss, 2),
            "total_profit_loss_percent": round(total_profit_loss_percent, 2),
            "items": items,
        }
        return {"code": 200, "message": "获取成功", "data": response_data}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取持仓失败: {str(e)}")


@router.get("/capital", summary="资金信息")
async def get_capital():
    """获取当前资金配置信息。"""
    try:
        info = get_capital_info()
        return {"code": 200, "message": "获取成功", "data": info}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取资金信息失败: {str(e)}")


@router.post("/capital", summary="更新资金配置")
async def update_capital_endpoint(request: CapitalUpdateRequest):
    """更新资金配置。"""
    try:
        info = update_capital(
            available_cash=request.available_cash,
            total_capital=request.total_capital,
        )
        return {"code": 200, "message": "更新成功", "data": info}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"更新资金失败: {str(e)}")


@router.post("/import", summary="导入持仓")
async def import_portfolio(request: PortfolioImportRequest):
    """
    从 .md 文件解析后导入持仓数据。
    接收解析后的持仓列表和资金信息，更新 real_positions.json 和 global_capital.json。
    
    逻辑对齐原始 position_manager_tool.py:
    1. 清除旧持仓
    2. 写入新持仓
    3. 同步资金信息
    """
    try:
        # 清除旧持仓
        positions = get_all_positions()
        for symbol in list(positions.keys()):
            delete_position(symbol)

        # 写入新持仓
        imported_count = 0
        for item in request.holdings:
            symbol = item.symbol
            if not symbol:
                continue
            update_position(
                symbol=symbol,
                shares=item.shares,
                cost_price=item.cost_price,
                stock_name=item.name or "",
            )
            imported_count += 1

        # 更新资金信息
        capital_info = None
        if request.capital:
            capital_info = update_capital(
                available_cash=request.capital.available_cash,
                total_capital=request.capital.total_capital,
            )

        return {
            "code": 200,
            "message": f"成功导入 {imported_count} 条持仓",
            "data": {
                "imported_count": imported_count,
                "capital": capital_info,
            },
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"导入持仓失败: {str(e)}")


@router.post("/position", summary="添加/修改单个持仓")
async def add_or_update_position(request: PositionUpdateRequest):
    """添加或修改单个持仓"""
    try:
        result = update_position(
            symbol=request.symbol,
            shares=request.shares,
            cost_price=request.cost_price,
            stock_name=request.stock_name,
        )
        return {"code": 200, "message": "保存成功", "data": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"保存持仓失败: {str(e)}")


@router.delete("/position/{symbol}", summary="删除持仓")
async def remove_position(symbol: str):
    """删除指定股票的持仓"""
    try:
        success = delete_position(symbol)
        if success:
            return {"code": 200, "message": "删除成功", "data": None}
        else:
            return {"code": 404, "message": "持仓不存在", "data": None}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"删除持仓失败: {str(e)}")


@router.delete("/positions", summary="清空所有持仓")
async def clear_positions():
    """清空所有持仓"""
    try:
        count = clear_all_positions()
        return {"code": 200, "message": f"已清空 {count} 条持仓", "data": {"cleared_count": count}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"清空持仓失败: {str(e)}")
