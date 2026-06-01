"""
股票切换管理 - 读写 data/current_stock.json
"""
import json
import os
from datetime import datetime
from pathlib import Path

from .config import DATA_DIR

CURRENT_STOCK_FILE = DATA_DIR / "current_stock.json"


def load_current_stock() -> dict:
    """加载当前选择的股票"""
    if CURRENT_STOCK_FILE.exists():
        try:
            with open(CURRENT_STOCK_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception:
            pass
    return {
        "symbol": "",
        "name": "",
        "index_symbol": "000001",
        "index_name": "上证指数",
        "last_updated": None,
    }


def save_current_stock(symbol: str, name: str, index_symbol: str, index_name: str) -> bool:
    """保存当前选择的股票"""
    try:
        DATA_DIR.mkdir(parents=True, exist_ok=True)
        data = {
            "symbol": symbol,
            "name": name,
            "index_symbol": index_symbol,
            "index_name": index_name,
            "last_updated": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }
        with open(CURRENT_STOCK_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        return True
    except Exception:
        return False
