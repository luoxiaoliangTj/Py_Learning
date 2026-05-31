"""
Daily data downloader - wraps the download logic.
Ported from Tools/daily_downloader.py
"""
import os
import sys
import time
import random
import requests
import pandas as pd
from datetime import datetime, timedelta
from typing import Optional
from .config import DATA_DIR, TUSHARE_TOKEN

STANDARD_COLUMNS = ["日期", "开盘", "最高", "最低", "收盘", "成交量", "成交额"]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


def download_daily_data(symbol: str, years: int = 8, source: str = "auto") -> dict:
    """
    Download daily data for a symbol.
    Returns {"success": bool, "message": str, "rows": int, "file": str}
    """
    if not symbol or not symbol.isdigit() or len(symbol) != 6:
        return {"success": False, "message": "股票代码必须是6位数字", "rows": 0, "file": ""}

    sources = ["sina", "sohu"] if source == "auto" else [source]

    for src in sources:
        if src == "sina":
            success, df, msg = _download_sina(symbol, years)
        elif src == "sohu":
            success, df, msg = _download_sohu(symbol, years)
        else:
            continue

        if success and df is not None and not df.empty:
            fpath = _save_data(df, symbol)
            if fpath:
                return {
                    "success": True,
                    "message": f"{msg} | 数据质量验证通过",
                    "rows": len(df),
                    "file": str(fpath),
                    "source": src,
                }

    return {"success": False, "message": "所有数据源均失败", "rows": 0, "file": ""}


def _download_sina(symbol: str, years: int):
    """Download from Sina Finance."""
    try:
        sina_symbol = f"sh{symbol}" if symbol.startswith("6") else f"sz{symbol}"
        url = "http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData"
        params = {
            "symbol": sina_symbol,
            "scale": 240,
            "ma": "no",
            "datalen": years * 250,
        }
        headers = {
            "User-Agent": random.choice(USER_AGENTS),
            "Referer": "http://finance.sina.com.cn",
        }
        time.sleep(0.5)
        resp = requests.get(url, params=params, headers=headers, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        if not data:
            return False, None, "新浪财经返回空数据"

        records = []
        for item in data:
            record = {
                "日期": pd.to_datetime(item["day"]),
                "开盘": float(item["open"]),
                "最高": float(item["high"]),
                "最低": float(item["low"]),
                "收盘": float(item["close"]),
                "成交量": int(float(item["volume"])) * 100,
                "成交额": float(item.get("amount", 0)),
            }
            if record["成交额"] == 0:
                record["成交额"] = record["收盘"] * record["成交量"]
            records.append(record)

        df = pd.DataFrame(records)
        if not df.empty:
            df = df.sort_values("日期").reset_index(drop=True)
        return True, df, "新浪财经下载成功"
    except Exception as e:
        return False, None, f"新浪财经下载失败: {e}"


def _download_sohu(symbol: str, years: int):
    """Download from Sohu Finance."""
    try:
        url = "http://q.stock.sohu.com/hisHq"
        params = {
            "code": f"cn_{symbol}",
            "start": (datetime.now() - timedelta(days=years * 365)).strftime("%Y%m%d"),
            "end": datetime.now().strftime("%Y%m%d"),
            "stat": "1",
            "order": "D",
            "period": "d",
        }
        headers = {
            "User-Agent": random.choice(USER_AGENTS),
            "Referer": "http://q.stock.sohu.com/",
        }
        resp = requests.get(url, params=params, headers=headers, timeout=15)
        resp.raise_for_status()
        data = resp.json()
        if not data or "hq" not in data[0]:
            return False, None, "搜狐财经返回空数据"

        records = []
        for item in data[0]["hq"]:
            record = {
                "日期": pd.to_datetime(item[0]),
                "开盘": float(item[1]),
                "收盘": float(item[2]),
                "最低": float(item[5]),
                "最高": float(item[6]),
                "成交量": int(float(item[7])),
                "成交额": float(item[8]),
            }
            records.append(record)

        df = pd.DataFrame(records)
        if not df.empty:
            df = df.sort_values("日期").reset_index(drop=True)
        return True, df, "搜狐财经下载成功"
    except Exception as e:
        return False, None, f"搜狐财经下载失败: {e}"


def _save_data(df: pd.DataFrame, symbol: str) -> Optional[str]:
    """Save data to CSV file."""
    os.makedirs(DATA_DIR, exist_ok=True)
    fpath = DATA_DIR / f"ccb_{symbol}_daily.csv"
    try:
        df_out = df.copy()
        if "日期" in df_out.columns:
            df_out["日期"] = pd.to_datetime(df_out["日期"]).dt.strftime("%Y-%m-%d")
        df_out.to_csv(fpath, index=False, encoding="utf-8-sig")
        return str(fpath)
    except Exception:
        return None


def check_existing_data(symbol: str) -> dict:
    """Check if data exists for a symbol."""
    fpath = DATA_DIR / f"ccb_{symbol}_daily.csv"
    if not fpath.exists():
        return {"exists": False, "message": "数据文件不存在"}
    try:
        df = pd.read_csv(fpath, encoding="utf-8-sig")
        if df.empty:
            return {"exists": False, "message": "数据文件为空"}
        if "日期" in df.columns:
            df["日期"] = pd.to_datetime(df["日期"])
            return {
                "exists": True,
                "data_points": len(df),
                "date_range": f"{df['日期'].min().strftime('%Y-%m-%d')} ~ {df['日期'].max().strftime('%Y-%m-%d')}",
                "message": f"数据量: {len(df)}条",
            }
        return {"exists": False, "message": "数据格式错误"}
    except Exception as e:
        return {"exists": False, "message": f"读取失败: {e}"}
