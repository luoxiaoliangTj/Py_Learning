"""
Daily data downloader - wraps the download logic.
Ported from Tools/daily_downloader.py

数据源优先级: 新浪(主) → 搜狐(备) → Tushare(备)
无 Tushare Token 时自动降级到新浪+搜狐
"""
import os
import sys
import time
import random
import requests
import pandas as pd
from datetime import datetime, timedelta
from typing import Optional
from .config import DATA_DIR

# 尝试导入Tushare
try:
    import tushare as ts
    TUSHARE_AVAILABLE = True
except ImportError:
    TUSHARE_AVAILABLE = False

STANDARD_COLUMNS = ["日期", "开盘", "最高", "最低", "收盘", "成交量", "成交额"]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


def _get_tushare_token() -> str:
    """从 token/tushare_token.txt 加载 Tushare Token（对齐原始代码逻辑）"""
    token_file = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "token", "tushare_token.txt")
    if os.path.exists(token_file):
        with open(token_file, 'r', encoding='utf-8') as f:
            token = f.read().strip()
            if token:
                return token
    return ""


def download_daily_data(symbol: str, years: int = 8, source: str = "auto", max_retries: int = 3) -> dict:
    """
    Download daily data for a symbol.
    数据源自动降级: 新浪 → 搜狐 → Tushare
    Returns {"success": bool, "message": str, "rows": int, "file": str, "source": str}
    """
    if not symbol or not symbol.isdigit() or len(symbol) != 6:
        return {"success": False, "message": "股票代码必须是6位数字", "rows": 0, "file": "", "source": ""}

    # 构建数据源列表
    sources = []
    if source == "auto":
        sources = ["sina", "sohu"]
        # Tushare 作为第三优先级（如果有 token）
        tushare_token = _get_tushare_token()
        if TUSHARE_AVAILABLE and tushare_token:
            sources.append("tushare")
    else:
        if source == "tushare" and (not TUSHARE_AVAILABLE or not _get_tushare_token()):
            return {"success": False, "message": "Tushare 不可用或未配置 Token", "rows": 0, "file": "", "source": ""}
        sources = [source]

    last_error = ""
    for src in sources:
        for attempt in range(max_retries):
            try:
                if src == "sina":
                    success, df, msg = _download_sina(symbol, years)
                elif src == "sohu":
                    success, df, msg = _download_sohu(symbol, years)
                elif src == "tushare":
                    success, df, msg = _download_tushare(symbol, years)
                else:
                    continue

                if success and df is not None and not df.empty:
                    # 数据清洗
                    df = _clean_data(df)
                    # 数据验证
                    valid, validate_msg = _validate_data(df, symbol)
                    if not valid:
                        last_error = f"{src} 数据验证失败: {validate_msg}"
                        continue
                    # 保存
                    fpath = _save_data(df, symbol)
                    if fpath:
                        return {
                            "success": True,
                            "message": f"{msg} | 数据验证通过",
                            "rows": len(df),
                            "file": str(fpath),
                            "source": src,
                        }
                last_error = msg
            except Exception as e:
                last_error = f"{src} 尝试 {attempt+1}/{max_retries} 失败: {e}"
            time.sleep(1)

    return {"success": False, "message": f"所有数据源均失败: {last_error}", "rows": 0, "file": "", "source": ""}


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


def _download_tushare(symbol: str, years: int):
    """Download from Tushare (备用数据源，需要 token)."""
    if not TUSHARE_AVAILABLE:
        return False, None, "Tushare 未安装"
    token = _get_tushare_token()
    if not token:
        return False, None, "未配置 Tushare Token"
    try:
        import tushare as ts_local
        ts_local.set_token(token)
        pro = ts_local.pro_api()
        ts_code = f"{symbol}.SH" if symbol.startswith("6") else f"{symbol}.SZ"
        end_date = datetime.now().strftime("%Y%m%d")
        start_date = (datetime.now() - timedelta(days=years * 365)).strftime("%Y%m%d")
        df = pro.daily(ts_code=ts_code, start_date=start_date, end_date=end_date)
        if df is None or df.empty:
            return False, None, "Tushare 返回空数据"
        # 列名映射
        column_mapping = {
            "trade_date": "日期", "open": "开盘", "high": "最高",
            "low": "最低", "close": "收盘", "vol": "成交量", "amount": "成交额",
        }
        df = df.rename(columns=column_mapping)
        df["日期"] = pd.to_datetime(df["日期"], format="%Y%m%d")
        available = [c for c in STANDARD_COLUMNS if c in df.columns]
        df = df[available].copy()
        df = df.sort_values("日期").reset_index(drop=True)
        return True, df, "Tushare 下载成功"
    except Exception as e:
        return False, None, f"Tushare 下载失败: {e}"


def _clean_data(df: pd.DataFrame) -> pd.DataFrame:
    """数据清洗：处理缺失值和异常值（对齐原始代码 DailyDataDownloader._clean_data）."""
    if df.empty:
        return df
    df = df.ffill().bfill()
    # 3σ 原则处理价格异常值
    for col in ["开盘", "最高", "最低", "收盘"]:
        if col in df.columns:
            mean = df[col].mean()
            std = df[col].std()
            if std > 0:
                df[col] = df[col].apply(
                    lambda x: mean if (x < mean - 3 * std or x > mean + 3 * std) else x
                )
    # IQR 处理成交量异常值
    if "成交量" in df.columns:
        q1 = df["成交量"].quantile(0.25)
        q3 = df["成交量"].quantile(0.75)
        iqr = q3 - q1
        if iqr > 0:
            lower = q1 - 1.5 * iqr
            upper = q3 + 1.5 * iqr
            median_vol = df["成交量"].median()
            df["成交量"] = df["成交量"].apply(
                lambda x: median_vol if (x < lower or x > upper) else x
            )
    return df


def _validate_data(df: pd.DataFrame, symbol: str):
    """数据验证（对齐原始代码 DailyDataDownloader.validate_data_quality）."""
    checks = []
    checks.append(("数据量", len(df) > 0, "没有数据"))
    checks.append(("列完整性", set(STANDARD_COLUMNS).issubset(set(df.columns)), "缺少必要列"))
    if "日期" in df.columns and len(df) > 1:
        df_sorted = df.sort_values("日期")
        date_gaps = (df_sorted["日期"].diff().dt.days > 5).sum()
        checks.append(("日期连续性", date_gaps < len(df) * 0.5, f"发现 {date_gaps} 个较大日期间隔"))
    if len(df) > 0:
        for col in ["开盘", "最高", "最低", "收盘"]:
            if col in df.columns:
                checks.append((f"{col}合理性", (df[col] > 0).all(), f"存在无效{col}数据"))
    passed = len(df) > 0 and all(c[1] for c in checks if c[0] != "数据量")
    details = [f"{name}: {'通过' if ok else '失败 - ' + msg}" for name, ok, msg in checks]
    return passed, "; ".join(details)


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
