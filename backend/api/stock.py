"""
API Routes - Stock management endpoints.
"""
import json
import os
from fastapi import APIRouter, HTTPException
from models import StockSelectRequest
from core.config import DATA_DIR, CURRENT_STOCK
from core.data_loader import get_available_stocks

router = APIRouter(prefix="/api/stock", tags=["股票"])


@router.get("/list", summary="股票列表")
async def list_stocks():
    """
    获取所有可用的股票列表（扫描数据目录中的CSV文件）。
    """
    try:
        stocks = get_available_stocks()
        return {"success": True, "data": stocks, "count": len(stocks)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取股票列表失败: {str(e)}")


@router.post("/select", summary="切换股票")
async def select_stock(request: StockSelectRequest):
    """
    切换当前选中的股票，保存到持久化配置。
    """
    try:
        current_stock_file = DATA_DIR / "current_stock.json"
        data = {
            "symbol": request.symbol,
            "name": request.name or request.symbol,
            "index_symbol": request.index_symbol or CURRENT_STOCK.get("index_symbol", "000001"),
            "index_name": request.index_name or CURRENT_STOCK.get("index_name", "上证指数"),
        }
        os.makedirs(DATA_DIR, exist_ok=True)
        with open(current_stock_file, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        return {"success": True, "data": data, "message": f"已切换到 {request.symbol}"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"切换股票失败: {str(e)}")
