"""
交易建议 API
"""
import os
from fastapi import APIRouter, HTTPException, Query
from core.trading_advisor import generate_advice, get_capital_info, update_capital, _get_holding, _save_holdings, HOLDINGS_FILE
from core.config import BACKTEST_DIR

router = APIRouter(prefix="/api/advice", tags=["交易建议"])


@router.get("/{symbol}", summary="获取交易建议")
async def get_advice(
    symbol: str,
    current_price: float = Query(None, description="当前价格（可选，不提供则尝试获取实时数据）"),
    predicted_low: float = Query(None, description="预测低点（可选）"),
    predicted_high: float = Query(None, description="预测高点（可选）"),
    intraday_volatility: float = Query(0.02, description="日内波动率（可选，默认0.02）"),
):
    """
    获取股票交易建议。

    - 自动读取持仓信息
    - 计算动态止盈止损阈值
    - 生成交易建议（止盈/止损/加仓/开仓）
    - 支持有持仓和空仓两种场景
    """
    try:
        # 获取持仓信息
        holding_info = _get_holding(symbol)

        # 如果没有提供当前价格，尝试从实时数据获取
        if current_price is None:
            try:
                from core.realtime_data import fetch_realtime_data
                rt = fetch_realtime_data(symbol)
                if rt and rt.get("valid"):
                    current_price = rt.get("price", 0)
            except Exception:
                pass

        if current_price is None or current_price <= 0:
            return {
                "code": 400,
                "message": "无法获取当前价格，请提供 current_price 参数",
                "data": None,
            }

        # 生成建议
        advice = generate_advice(symbol, current_price, holding_info)

        return {
            "code": 200,
            "message": "获取成功",
            "data": advice,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取交易建议失败: {str(e)}")


@router.get("/{symbol}/holding", summary="获取持仓信息")
async def get_holding(symbol: str):
    """获取单只股票持仓信息"""
    try:
        holding = _get_holding(symbol)
        if holding:
            return {"code": 200, "message": "获取成功", "data": holding}
        else:
            return {"code": 404, "message": "无持仓信息", "data": None}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{symbol}/capital", summary="获取资金信息")
async def get_capital():
    """获取全局资金信息"""
    try:
        capital = get_capital_info()
        return {"code": 200, "message": "获取成功", "data": capital}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
