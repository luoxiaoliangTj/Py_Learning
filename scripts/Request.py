#!/usr/bin/env python3
"""
Request.py - 双数据源日线下载器
1. 优先 akshare（新浪）
2. 网络断开 → 自动回落到「新浪财经备用接口」
3. 仍然失败 → 提示用户手动放 CSV
保存：data/CCB_${code}_daily.csv
"""
import os, sys, time, requests
from datetime import datetime, timedelta

BASE_DIR = os.path.dirname(os.path.dirname(__file__))
sys.path.insert(0, BASE_DIR)

import pandas as pd
from config.config import cfg
from config.logging_config import get_logger

logger = get_logger("Request")

# 备用接口：新浪财经（网页版）
BACKUP_URL = "https://finance.sina.com.cn/realstock/company/{code}/hisdata/keladata.txt"

def fetch_sina_backup(symbol: str, start_date: str) -> pd.DataFrame:
    """
    新浪财经备用接口（网页版），自动添加市场前缀
    """
    import re
    # 添加市场前缀
    if symbol.startswith('6'):
        prefix = 'sh'
    else:
        prefix = 'sz'
    market_code = prefix + symbol
    url = BACKUP_URL.format(code=market_code)
    params = {"start": start_date, "end": datetime.now().strftime("%Y-%m-%d")}
    headers = {"User-Agent": "Mozilla/5.0"}
    r = requests.get(url, params=params, headers=headers, timeout=10)
    r.raise_for_status()
    txt = r.text.strip()
    if not txt:
        return pd.DataFrame()
    lines = txt.splitlines()[1:]          # 跳过表头
    records = []
    for line in lines:
        if not line:
            continue
        arr = line.split("\t")
        if len(arr) < 6:
            continue
        records.append({
            "date": arr[0],
            "open": float(arr[1]),
            "high": float(arr[2]),
            "low": float(arr[3]),
            "close": float(arr[4]),
            "volume": int(arr[5])
        })
    df = pd.DataFrame(records)
    df['date'] = pd.to_datetime(df['date'])
    return df

def download_daily(symbol: str, years: int = 8, max_retry: int = 3):
    os.makedirs(cfg.DATA_DIR, exist_ok=True)
    start_date = (datetime.now() - timedelta(days=years*365)).strftime("%Y%m%d")
    file_path = os.path.join(cfg.DATA_DIR, f"CCB_{symbol}_daily.csv")

    # ① 优先 akshare
    for attempt in range(max_retry):
        try:
            import akshare as ak
            df = ak.stock_zh_a_hist(symbol=symbol, period="daily", start_date=start_date, adjust="")
            if df.empty or df.shape[0] < 10:
                logger.warning(f"akshare 数据过少（尝试 {attempt+1}）")
                time.sleep(2)
                continue

            # 检查必要列是否存在
            required_cols = ['日期', '开盘', '收盘', '最高', '最低', '成交量', '成交额']
            for col in required_cols:
                if col not in df.columns:
                    raise ValueError(f"缺少必要列: {col}")

            # 构建标准顺序的 DataFrame（日期,开盘,最高,最低,收盘,成交量,成交额）
            df_std = pd.DataFrame()
            df_std['日期'] = df['日期']
            df_std['开盘'] = df['开盘']
            df_std['最高'] = df['最高']
            df_std['最低'] = df['最低']
            df_std['收盘'] = df['收盘']
            df_std['成交量'] = df['成交量']
            df_std['成交额'] = df['成交额']

            # 确保数值类型正确
            for col in ['开盘', '最高', '最低', '收盘', '成交量', '成交额']:
                df_std[col] = pd.to_numeric(df_std[col], errors='coerce')

            logger.info(f"akshare 下载成功，共 {len(df_std)} 条数据")
            df = df_std
            break
        except Exception as e:
            logger.warning(f"akshare 失败（尝试 {attempt+1}）: {e}")
            time.sleep(3)
    else:
        # 备用接口（目前已知失效，可以暂时保留，但建议使用其他备选或提示手动下载）
        logger.error("所有数据源均失败，请手动下载 CSV 放入 data 目录")
        return

    # 保存
    df.to_csv(file_path, index=False, encoding="utf-8-sig")
    logger.info(f"日线已保存 -> {file_path} （{start_date} 至今，{len(df)} 条）")

if __name__ == "__main__":
    download_daily(cfg.STOCK_SYMBOL)
