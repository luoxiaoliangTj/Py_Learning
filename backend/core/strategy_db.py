"""
策略库管理 - 读写 data/strategy_db.json
"""
import json
import os
from datetime import datetime
from pathlib import Path

from .config import DATA_DIR

STRATEGY_DB_FILE = DATA_DIR / "strategy_db.json"


def load_strategy_db() -> dict:
    """加载策略库"""
    if STRATEGY_DB_FILE.exists():
        try:
            with open(STRATEGY_DB_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def save_strategy_db(db: dict):
    """保存策略库"""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with open(STRATEGY_DB_FILE, 'w', encoding='utf-8') as f:
        json.dump(db, f, ensure_ascii=False, indent=2)


def get_stock_strategy(symbol: str) -> dict | None:
    """获取指定股票的策略"""
    db = load_strategy_db()
    return db.get(symbol)


def save_stock_strategy(symbol: str, strategy_info: dict):
    """保存指定股票的策略"""
    db = load_strategy_db()
    db[symbol] = strategy_info
    save_strategy_db(db)


def delete_stock_strategy(symbol: str) -> bool:
    """删除指定股票的策略"""
    db = load_strategy_db()
    if symbol in db:
        del db[symbol]
        save_strategy_db(db)
        return True
    return False


def list_all_strategies() -> dict:
    """列出所有策略"""
    return load_strategy_db()
