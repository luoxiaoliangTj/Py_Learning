"""
Position manager - reads/writes position data.
Ported from Tools/position_manager_tool.py
"""
import json
import os
from datetime import datetime
from .config import DATA_DIR


POSITIONS_FILE = DATA_DIR / "real_positions.json"


def get_all_positions() -> dict:
    """Get all positions."""
    if POSITIONS_FILE.exists():
        try:
            with open(POSITIONS_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def get_position(symbol: str) -> dict:
    """Get position for a specific symbol."""
    positions = get_all_positions()
    return positions.get(symbol, {
        "shares": 0,
        "cost_price": 0,
        "stock_name": "",
        "last_updated": None,
    })


def update_position(symbol: str, shares: int, cost_price: float, stock_name: str = None) -> dict:
    """Update a position."""
    positions = get_all_positions()
    if shares == 0 and symbol in positions:
        existing = positions[symbol]
        final_name = stock_name or existing.get("stock_name", f"股票{symbol}")
        final_cost = existing.get("cost_price", 0.0)
    else:
        final_name = stock_name or f"股票{symbol}"
        final_cost = cost_price

    positions[symbol] = {
        "shares": shares,
        "cost_price": final_cost,
        "stock_name": final_name,
        "last_updated": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    }

    os.makedirs(DATA_DIR, exist_ok=True)
    with open(POSITIONS_FILE, "w", encoding="utf-8") as f:
        json.dump(positions, f, ensure_ascii=False, indent=2)
    return positions[symbol]


def delete_position(symbol: str) -> bool:
    """Delete a position."""
    positions = get_all_positions()
    if symbol in positions:
        del positions[symbol]
        with open(POSITIONS_FILE, "w", encoding="utf-8") as f:
            json.dump(positions, f, ensure_ascii=False, indent=2)
        return True
    return False


def clear_all_positions() -> int:
    """Clear all positions. Returns the number of positions removed."""
    positions = get_all_positions()
    count = len(positions)
    if count > 0:
        os.makedirs(DATA_DIR, exist_ok=True)
        with open(POSITIONS_FILE, "w", encoding="utf-8") as f:
            json.dump({}, f, ensure_ascii=False, indent=2)
    return count
