"""
Backend configuration - reads from the same data files as the main project.
"""
import os
import json
from pathlib import Path

# Project paths
BACKEND_DIR = Path(__file__).resolve().parent.parent
PROJECT_DIR = BACKEND_DIR.parent
DATA_DIR = PROJECT_DIR / "data"
BACKTEST_DIR = PROJECT_DIR / "backtest_results"
LOG_DIR = PROJECT_DIR / "logs"
RESULT_DIR = PROJECT_DIR / "realtime_results"
TOKEN_DIR = PROJECT_DIR / "token"

# Ensure directories exist
for d in [DATA_DIR, BACKTEST_DIR, LOG_DIR, RESULT_DIR]:
    d.mkdir(parents=True, exist_ok=True)


def get_current_stock() -> dict:
    """Read current stock selection from persistent config."""
    current_stock_file = DATA_DIR / "current_stock.json"
    if current_stock_file.exists():
        try:
            with open(current_stock_file, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"symbol": "000001", "name": "平安银行", "index_symbol": "000001", "index_name": "上证指数"}


def get_tushare_token() -> str:
    """Load tushare token from file."""
    token_file = TOKEN_DIR / "tushare_token.txt"
    if token_file.exists():
        try:
            return token_file.read_text(encoding="utf-8").strip()
        except Exception:
            pass
    return ""


CURRENT_STOCK = get_current_stock()
TUSHARE_TOKEN = get_tushare_token()
