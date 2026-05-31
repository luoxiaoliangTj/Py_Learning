"""
API Routes - Backtest endpoint.
策略回测接口，响应字段对齐 Android 端 BacktestResult。
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

    响应字段对齐 Android BacktestResult:
      code, name, start_date, end_date, initial_capital, final_capital,
      total_return, annual_return, max_drawdown, sharpe_ratio, win_rate,
      total_trades, equity_curve, trades
    """
    try:
        df = load_daily_data(request.symbol)
        if df is None or df.empty:
            raise HTTPException(status_code=404, detail=f"未找到股票 {request.symbol} 的日线数据")

        params = request.params or {}
        params.setdefault("type", request.strategy_type)

        result = quick_score(df, params, request.symbol)

        # 计算日期范围
        date_col = "日期" if "日期" in df.columns else None
        start_date = ""
        end_date = ""
        if date_col:
            start_date = str(df[date_col].iloc[0])[:10]
            end_date = str(df[date_col].iloc[-1])[:10]

        initial_capital = 100000.0
        final_capital = float(result["final_cash"])
        total_return = float(result["total_return"])

        # 计算年化收益率
        n_days = len(df)
        if n_days > 0:
            annual_return = (1 + total_return) ** (252.0 / n_days) - 1
        else:
            annual_return = 0.0

        # 构建权益曲线（简化版：使用 portfolio_values）
        equity_curve = []
        pv = result.get("portfolio_values", [])
        for i, val in enumerate(pv):
            point_date = ""
            if date_col and i < len(df):
                point_date = str(df[date_col].iloc[i])[:10]
            equity_curve.append({
                "date": point_date,
                "value": round(float(val), 2),
            })

        # 构建交易记录（对齐 Android TradeRecord）
        trades = []
        for t in result.get("trades", []):
            trade_date = ""
            if date_col and len(trades) < len(df):
                trade_date = end_date  # 简化处理
            trades.append({
                "date": trade_date,
                "type": t["type"].upper(),  # BUY / SELL
                "price": round(float(t["price"]), 2),
                "shares": int(t["shares"]),
                "amount": round(float(t["price"]) * int(t["shares"]), 2),
                "pnl": 0.0,  # 简化处理，暂不计算单笔盈亏
            })

        n_trades = result["n_trades"]

        # 对齐 Android BacktestResult 字段命名
        response_data = {
            "code": request.symbol,
            "name": request.symbol,
            "start_date": start_date,
            "end_date": end_date,
            "initial_capital": initial_capital,
            "final_capital": final_capital,
            "total_return": total_return,
            "annual_return": round(annual_return, 4),
            "max_drawdown": float(result["max_drawdown"]),
            "sharpe_ratio": float(result["sharpe"]),
            "win_rate": 0.5,  # 简化处理，暂不计算真实胜率
            "total_trades": n_trades,
            "equity_curve": equity_curve,
            "trades": trades,
        }
        return {"code": 200, "message": "回测成功", "data": response_data}
    except HTTPException:
        raise
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"回测失败: {str(e)}")
