"""
API Routes - Backtest endpoint.
"""
from fastapi import APIRouter, HTTPException
from models import BacktestRequest
from core.data_loader import load_daily_data
from core.strategy_engine import quick_score

router = APIRouter(prefix="/api", tags=["回测"])


@router.post("/backtest", summary="策略回测")
async def backtest_endpoint(request: BacktestRequest):
    """
    执行策略回测。
    - 支持通道策略（channel）和趋势策略（trend）
    - 返回夏普比率、收益率、最大回撤等指标
    """
    try:
        df = load_daily_data(request.symbol)
        if df is None or df.empty:
            raise HTTPException(status_code=404, detail=f"未找到股票 {request.symbol} 的日线数据")

        params = request.params or {}
        params.setdefault("type", request.strategy_type)

        result = quick_score(df, params, request.symbol)

        # Convert trades to serializable format
        trades = []
        for t in result.get("trades", []):
            trades.append({
                "type": t["type"],
                "price": round(t["price"], 2),
                "shares": t["shares"],
            })

        return {
            "success": True,
            "data": {
                "symbol": request.symbol,
                "strategy_type": request.strategy_type,
                "params": result["params"],
                "sharpe": result["sharpe"],
                "total_return": result["total_return"],
                "max_drawdown": result["max_drawdown"],
                "n_trades": result["n_trades"],
                "final_cash": result["final_cash"],
                "final_shares": result["final_shares"],
                "trades": trades,
            },
        }
    except HTTPException:
        raise
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"回测失败: {str(e)}")
