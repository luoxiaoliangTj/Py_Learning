"""
API Routes - Stock management endpoints.
股票列表与切换接口，响应字段对齐 Android 端 StockInfo / ApiResponse。
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

    响应字段对齐 Android StockInfo:
      code, name, current_price, change_percent, change_amount, volume,
      turnover, high, low, open, prev_close, market_cap, pe_ratio
    """
    try:
        stocks = get_available_stocks()
        # 对齐 Android StockInfo 字段命名
        response_list = []
        for s in stocks:
            response_list.append({
                "code": s["symbol"],
                "name": s["name"],
                "current_price": 0.0,       # 需实时行情数据，暂为0
                "change_percent": 0.0,
                "change_amount": 0.0,
                "volume": 0,
                "turnover": 0.0,
                "high": 0.0,
                "low": 0.0,
                "open": 0.0,
                "prev_close": 0.0,
                "market_cap": 0.0,
                "pe_ratio": 0.0,
            })
        return {"code": 200, "message": "获取成功", "data": response_list}
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
        return {"code": 200, "message": f"已切换到 {request.symbol}", "data": data}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"切换股票失败: {str(e)}")
