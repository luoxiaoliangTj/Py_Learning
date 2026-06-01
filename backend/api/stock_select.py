"""
股票切换 API - 保存/加载 current_stock.json
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from core.current_stock import load_current_stock, save_current_stock

router = APIRouter(prefix="/api/stock", tags=["股票切换"])


class StockSelectRequest(BaseModel):
    """股票选择请求"""
    symbol: str
    name: str = ""
    index_symbol: str = ""
    index_name: str = ""


class StockSelectResponse(BaseModel):
    """股票选择响应"""
    symbol: str
    name: str
    index_symbol: str
    index_name: str


@router.get("/current", summary="获取当前选择的股票")
async def get_current_stock():
    """获取当前选择的股票配置"""
    try:
        stock = load_current_stock()
        return {"code": 200, "message": "获取成功", "data": stock}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取当前股票失败: {str(e)}")


@router.post("/select", summary="选择股票")
async def select_stock(request: StockSelectRequest):
    """
    选择股票并保存配置。
    自动判断市场（6开头=上证，其他=深证）。
    """
    try:
        symbol = request.symbol
        name = request.name or f"股票{symbol}"

        # 自动判断市场
        if not request.index_symbol:
            if symbol.startswith("6"):
                index_symbol = "000001"
                index_name = "上证指数"
            else:
                index_symbol = "399001"
                index_name = "深圳成指"
        else:
            index_symbol = request.index_symbol
            index_name = request.index_name

        success = save_current_stock(symbol, name, index_symbol, index_name)
        if success:
            return {
                "code": 200,
                "message": "保存成功",
                "data": {
                    "symbol": symbol,
                    "name": name,
                    "index_symbol": index_symbol,
                    "index_name": index_name,
                },
            }
        else:
            raise HTTPException(status_code=500, detail="保存失败")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"选择股票失败: {str(e)}")
