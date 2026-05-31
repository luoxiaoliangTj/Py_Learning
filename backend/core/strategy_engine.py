"""
Strategy engine - backtesting and scoring logic.
Ported from scripts/backtest_modules/strategy_engine.py
"""
import pandas as pd
import numpy as np
from typing import Optional

# Trading cost parameters
COMMISSION_RATE = 0.00025
STAMP_TAX_RATE = 0.001
SLIPPAGE_RATE = 0.0005


def atr(df: pd.DataFrame, n: int = 20) -> pd.Series:
    """Calculate ATR."""
    df = df.copy()
    df["h-l"] = df["最高"] - df["最低"]
    df["h-pc"] = np.abs(df["最高"] - df["收盘"].shift(1))
    df["l-pc"] = np.abs(df["最低"] - df["收盘"].shift(1))
    df["tr"] = df[["h-l", "h-pc", "l-pc"]].max(axis=1)
    return df["tr"].rolling(n).mean()


def quick_score(df: pd.DataFrame, params: dict, symbol: str = "unknown") -> dict:
    """
    Quick backtest scoring.
    Supports 'channel' (default) and 'trend' strategy types.
    """
    sub = df.copy()
    if sub.empty or len(sub) < 50:
        raise ValueError(f"数据不足：当前{len(sub)}条，需要至少50条")

    strategy_type = params.get("type", "channel")
    cash, shares, trades = 100_000, 0, []
    portfolio_values = [100_000]

    if strategy_type == "trend":
        fast = int(params.get("fast_ma", 10))
        slow = int(params.get("slow_ma", 30))
        if fast >= slow or fast < 2:
            raise ValueError(f"无效MA参数: fast={fast}, slow={slow}")
        sub["ma_fast"] = sub["收盘"].rolling(fast).mean()
        sub["ma_slow"] = sub["收盘"].rolling(slow).mean()

        for i in range(slow, len(sub)):
            row = sub.iloc[i]
            close = row["收盘"]
            prev_fast = sub["ma_fast"].iloc[i - 1]
            prev_slow = sub["ma_slow"].iloc[i - 1]
            cur_fast = sub["ma_fast"].iloc[i]
            cur_slow = sub["ma_slow"].iloc[i]

            if prev_fast <= prev_slow and cur_fast > cur_slow and shares == 0:
                buy_price = close * 1.01
                buy_cost = COMMISSION_RATE + SLIPPAGE_RATE
                shares = int(cash / (buy_price * (1 + buy_cost)))
                if shares > 0:
                    cost = shares * buy_price * (1 + buy_cost)
                    cash -= cost
                    trades.append({"type": "buy", "price": buy_price, "shares": shares})
            elif prev_fast >= prev_slow and cur_fast < cur_slow and shares > 0:
                sell_price = close * 0.99
                sell_cost = COMMISSION_RATE + STAMP_TAX_RATE + SLIPPAGE_RATE
                revenue = shares * sell_price * (1 - sell_cost)
                cash += revenue
                trades.append({"type": "sell", "price": sell_price, "shares": shares})
                shares = 0

            daily_value = cash + shares * close
            portfolio_values.append(daily_value)
    else:
        # Channel strategy (default)
        sub["atr20"] = atr(sub, 20)
        for i in range(20, len(sub)):
            row = sub.iloc[i]
            k_value = float(params.get("k", 2.0))
            width = params.get("width", k_value * row["atr20"])
            if pd.isna(width) or width <= 0:
                width = 0.02 * row["收盘"]

            close = row["收盘"]
            ph, pl = close + width / 2, close - width / 2

            if row["最高"] > ph and shares == 0:
                buy_cost = COMMISSION_RATE + SLIPPAGE_RATE
                shares = int(cash / (ph * (1 + buy_cost + 0.01)))
                if shares > 0:
                    cash -= shares * ph * (1 + buy_cost)
                    trades.append({"type": "buy", "price": ph, "shares": shares})
            elif row["最低"] < pl and shares > 0:
                sell_cost = COMMISSION_RATE + STAMP_TAX_RATE + SLIPPAGE_RATE
                cash += shares * pl * (1 - sell_cost)
                trades.append({"type": "sell", "price": pl, "shares": shares})
                shares = 0

            daily_value = cash + shares * close
            portfolio_values.append(daily_value)

    if shares > 0:
        cash += shares * sub.iloc[-1]["收盘"]

    total_return = cash / 100_000 - 1
    sharpe = _calc_sharpe(portfolio_values)
    max_drawdown = _calc_max_drawdown(portfolio_values)
    n_trades = len([t for t in trades if t["type"] in ["buy", "sell"]])

    return {
        "params": params,
        "sharpe": round(sharpe, 4),
        "total_return": round(total_return, 4),
        "max_drawdown": round(max_drawdown, 4),
        "n_trades": n_trades,
        "portfolio_values": portfolio_values,
        "trades": trades,
        "final_cash": round(cash, 2),
        "final_shares": shares,
    }


def _calc_sharpe(portfolio_values: list) -> float:
    """Calculate annualized Sharpe ratio."""
    s = pd.Series(portfolio_values)
    returns = s.pct_change().dropna()
    if len(returns) == 0 or returns.std() == 0:
        return 0.0
    return float(returns.mean() / returns.std() * np.sqrt(252))


def _calc_max_drawdown(portfolio_values: list) -> float:
    """Calculate max drawdown as a negative percentage."""
    s = pd.Series(portfolio_values)
    peak = s.expanding().max()
    drawdown = (s - peak) / peak
    return float(drawdown.min())


def optimize_params(df: pd.DataFrame, strategy_type: str = "channel") -> dict:
    """
    Simple parameter optimization via grid search.
    """
    best_result = None
    best_score = -999

    if strategy_type == "trend":
        param_grid = [
            {"type": "trend", "fast_ma": f, "slow_ma": s}
            for f in [5, 10, 15, 20]
            for s in [20, 30, 40, 60]
            if f < s
        ]
    else:
        param_grid = [
            {"type": "channel", "k": round(k, 1)}
            for k in [1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0]
        ]

    results = []
    for params in param_grid:
        try:
            r = quick_score(df, params)
            score = r["sharpe"]
            results.append({"params": params, "sharpe": r["sharpe"], "total_return": r["total_return"],
                            "max_drawdown": r["max_drawdown"], "n_trades": r["n_trades"]})
            if score > best_score:
                best_score = score
                best_result = {"params": params, **r}
        except Exception:
            continue

    return {
        "best": best_result,
        "all_results": results,
        "strategy_type": strategy_type,
    }
