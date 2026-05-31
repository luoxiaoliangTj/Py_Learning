"""
Capital manager - reads/writes global capital configuration.
Ported from Tools/capital_manager_tool.py
"""
import json
import os
from datetime import datetime
from .config import DATA_DIR


CAPITAL_FILE = DATA_DIR / "global_capital.json"


def get_capital_info() -> dict:
    """Get current capital information."""
    if CAPITAL_FILE.exists():
        try:
            with open(CAPITAL_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {
        "available_cash": 100000.0,
        "total_capital": 100000.0,
        "last_updated": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "note": "默认资金配置",
    }


def update_capital(available_cash: float = None, total_capital: float = None) -> dict:
    """Update capital configuration."""
    info = get_capital_info()
    if available_cash is not None:
        if available_cash < 0:
            raise ValueError("可用资金不能为负数")
        info["available_cash"] = float(available_cash)
    if total_capital is not None:
        if total_capital < 0:
            raise ValueError("总资金不能为负数")
        info["total_capital"] = float(total_capital)
    if available_cash is not None and total_capital is None:
        info["total_capital"] = info["available_cash"]
    info["last_updated"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    os.makedirs(DATA_DIR, exist_ok=True)
    with open(CAPITAL_FILE, "w", encoding="utf-8") as f:
        json.dump(info, f, ensure_ascii=False, indent=2)
    return info
