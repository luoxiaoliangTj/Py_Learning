"""
Prediction engine - daily and realtime prediction logic.
Ported from algorithms/TradePlanAlgo.py and algorithms/online_predictor.py
"""
import pandas as pd
import numpy as np
from typing import Optional
from .data_loader import load_daily_data, get_price_column
from .config import DATA_DIR, BACKTEST_DIR, CURRENT_STOCK
import json
import os
from datetime import datetime


def predict_daily(symbol: str) -> dict:
    """
    Daily price range prediction based on backtest results.
    Equivalent to TradePlanAlgo.algo_predict().
    """
    try:
        df = load_daily_data(symbol)
        if df is None or df.empty:
            return {"error": f"未找到股票 {symbol} 的日线数据，请先下载数据"}

        price_col = get_price_column(df)
        current_price = float(df[price_col].iloc[-1])

        # Calculate volatility
        returns = df[price_col].pct_change().dropna()
        volatility = float(returns.rolling(20).std().iloc[-1]) if len(returns) >= 20 else float(returns.std())
        if pd.isna(volatility) or volatility == 0:
            volatility = 0.02

        # Calculate trend strength
        if len(df) >= 20:
            price_20 = float(df[price_col].iloc[-20])
            trend_strength = (current_price / price_20 - 1) / 20 if price_20 > 0 else 0.0
        else:
            trend_strength = 0.0

        # Check backtest results
        backtest_stats = _load_backtest_stats(symbol)

        # Predict price range
        if backtest_stats.get("is_initial_data", True):
            base_range = current_price * volatility * 3.0
            pred_low = current_price - base_range
            pred_high = current_price + base_range
        else:
            sharpe = backtest_stats.get("sharpe_ratio", 0)
            max_dd = backtest_stats.get("max_drawdown", 0)
            sharpe_adj = 0.8 if sharpe > 1.0 else 1.3 if sharpe < 0.3 else 1.0
            dd_adj = 1.2 if max_dd > 0.3 else 1.0
            base_range = current_price * volatility * 2.5 * sharpe_adj * dd_adj
            if abs(trend_strength) > 0.01:
                base_range *= 1 + abs(trend_strength) * 10
            min_range = current_price * 0.03
            max_range = current_price * 0.15
            adjusted_range = max(min(base_range, max_range), min_range)
            pred_low = current_price - adjusted_range / 2
            pred_high = current_price + adjusted_range / 2

        # Apply price limits (±10%)
        limit_up = current_price * 1.10
        limit_down = current_price * 0.90
        pred_low = max(pred_low, limit_down)
        pred_high = min(pred_high, limit_up)

        confidence = max(0.3, min(0.95, 0.7 - volatility * 2))

        return {
            "symbol": symbol,
            "current_price": round(current_price, 2),
            "pred_low": round(pred_low, 2),
            "pred_high": round(pred_high, 2),
            "volatility": round(volatility, 4),
            "trend_strength": round(trend_strength, 4),
            "confidence": round(confidence, 4),
            "backtest_stats": backtest_stats,
            "generated_at": datetime.now().isoformat(),
        }
    except Exception as e:
        return {"error": str(e)}


def predict_realtime(symbol: str, prev_close: Optional[float] = None) -> dict:
    """
    Realtime prediction using LMSR-like strategy.
    Equivalent to OnlinePredictor.run_prediction().
    """
    try:
        df = load_daily_data(symbol)
        if df is None or df.empty:
            return {"error": f"未找到股票 {symbol} 的数据"}

        price_col = get_price_column(df)
        current_price = float(df[price_col].iloc[-1])

        # Use last close as prev_close if not provided
        if prev_close is None or prev_close <= 0:
            if len(df) >= 2:
                prev_close = float(df[price_col].iloc[-2])
            else:
                prev_close = current_price

        price_volatility = current_price * 0.05
        low_price = round(current_price - price_volatility, 2)
        high_price = round(current_price + price_volatility, 2)

        # Apply price limits
        limit_up = round(prev_close * 1.10, 2)
        limit_down = round(prev_close * 0.90, 2)
        low_price = max(low_price, limit_down)
        high_price = min(high_price, limit_up)

        confidence = max(0.6, min(0.95, 0.85 - abs(price_volatility / current_price)))

        # Generate recommendation
        middle = (low_price + high_price) / 2
        if current_price < low_price:
            recommendation = "buy"
        elif current_price > high_price:
            recommendation = "sell"
        elif current_price < middle:
            recommendation = "hold_buy"
        elif current_price > middle:
            recommendation = "hold_sell"
        else:
            recommendation = "hold"

        return {
            "symbol": symbol,
            "current_price": round(current_price, 2),
            "prev_close": round(prev_close, 2),
            "prediction_range": {"low": low_price, "high": high_price},
            "confidence": round(confidence, 4),
            "recommendation": recommendation,
            "strategy": "LMSR",
            "price_limits_applied": True,
            "generated_at": datetime.now().isoformat(),
        }
    except Exception as e:
        return {"error": str(e)}


def _load_backtest_stats(symbol: str) -> dict:
    """Load backtest results for a symbol."""
    try:
        backtest_dir = BACKTEST_DIR
        if not backtest_dir.exists():
            return {"is_initial_data": True, "message": "未回测模拟结果"}

        # Find files case-insensitively
        curve_file = None
        trades_file = None
        for f in backtest_dir.iterdir():
            fl = f.name.lower()
            sl = symbol.lower()
            if f"{sl}_curve.csv" in fl:
                curve_file = f
            elif f"{sl}_trades.csv" in fl:
                trades_file = f

        if not curve_file or not trades_file:
            return {"is_initial_data": True, "message": "未回测模拟结果"}

        curve_df = pd.read_csv(curve_file)
        trades_df = pd.read_csv(trades_file)

        # Check if initial
        if "note" in curve_df.columns and len(curve_df) > 0:
            if curve_df["note"].iloc[0] == "initial_backtest_file":
                return {"is_initial_data": True, "message": "未回测模拟结果"}

        if "portfolio_value" in curve_df.columns and len(curve_df) > 1:
            pv = curve_df["portfolio_value"]
            returns = pv.pct_change().dropna()
            sharpe = float(returns.mean() / returns.std() * np.sqrt(252)) if returns.std() != 0 else 0
            peak = pv.expanding().max()
            drawdown = (pv - peak) / peak
            max_dd = float(drawdown.min())
        else:
            sharpe = 0
            max_dd = 0

        real_trades = []
        if "type" in trades_df.columns:
            real_trades = trades_df[trades_df["type"].isin(["buy", "sell"])].to_dict("records")

        return {
            "sharpe_ratio": round(sharpe, 3),
            "max_drawdown": round(abs(max_dd), 3),
            "win_rate": 0.0,
            "total_trades": len(real_trades),
            "curve_length": len(curve_df),
            "is_initial_data": False,
        }
    except Exception:
        return {"is_initial_data": True, "message": "回测数据加载失败"}
