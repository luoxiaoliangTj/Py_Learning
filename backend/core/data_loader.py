"""
Data loading utilities - reads CSV data files used by the strategy engine.
"""
import pandas as pd
import os
from pathlib import Path
from typing import Optional
from .config import DATA_DIR, CURRENT_STOCK


def load_daily_data(symbol: str) -> Optional[pd.DataFrame]:
    """
    Load daily CSV data for a given symbol.
    Tries multiple file naming conventions.
    """
    candidates = [
        DATA_DIR / f"ccb_{symbol}_daily.csv",
        DATA_DIR / f"ccb_{symbol.lower()}_daily.csv",
        DATA_DIR / f"{symbol}_daily.csv",
    ]

    # Also try glob pattern
    if symbol:
        pattern_files = list(DATA_DIR.glob(f"*{symbol.lower()}*_daily*.csv"))
        for pf in pattern_files:
            if pf not in candidates:
                candidates.append(pf)

    for fpath in candidates:
        if fpath.exists():
            try:
                df = pd.read_csv(fpath, encoding="utf-8-sig")
                if "日期" in df.columns:
                    df["日期"] = pd.to_datetime(df["日期"])
                    df = df.sort_values("日期").reset_index(drop=True)
                return df
            except Exception:
                continue
    return None


def get_available_stocks() -> list[dict]:
    """
    Scan data directory for available stock CSV files.
    Returns list of {symbol, name, data_points, date_range}.
    """
    stocks = []
    seen_symbols = set()

    for fpath in DATA_DIR.glob("*_daily*.csv"):
        try:
            df = pd.read_csv(fpath, encoding="utf-8-sig")
            if df.empty:
                continue

            # Extract symbol from filename
            stem = fpath.stem  # e.g. ccb_000001_daily
            parts = stem.split("_")
            symbol = None
            for i, p in enumerate(parts):
                if p.isdigit() and len(p) == 6:
                    symbol = p
                    break
            if not symbol:
                # Try first numeric part
                for p in parts:
                    if p.isdigit():
                        symbol = p
                        break
            if not symbol or symbol in seen_symbols:
                continue

            seen_symbols.add(symbol)

            name = symbol  # Default name
            data_points = len(df)
            date_range = ""
            if "日期" in df.columns:
                df["日期"] = pd.to_datetime(df["日期"])
                date_range = f"{df['日期'].min().strftime('%Y-%m-%d')} ~ {df['日期'].max().strftime('%Y-%m-%d')}"

            stocks.append({
                "symbol": symbol,
                "name": name,
                "data_points": data_points,
                "date_range": date_range,
                "file": str(fpath.name),
            })
        except Exception:
            continue

    return stocks


def get_price_column(df: pd.DataFrame) -> str:
    """Find the price column name in a dataframe."""
    for col in ["收盘", "STOCK_Close", "close", "Close", "CLOSE"]:
        if col in df.columns:
            return col
    raise ValueError("无法找到价格列")
