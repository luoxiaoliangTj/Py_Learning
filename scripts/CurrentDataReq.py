#!/usr/bin/env python3
"""
CurrentDataReq.py - 拉取最近 1 天 1 分钟线
保存：data/CCB_${code}_minute_${date}.csv
"""
#!/usr/bin/env python3
"""
CurrentDataReq.py - 拉取最近 1 天 1 分钟线
"""
import os, sys
BASE_DIR = os.path.dirname(os.path.dirname(__file__))
sys.path.insert(0, BASE_DIR)

import akshare as ak
import pandas as pd
from datetime import datetime   # ← 新增
from config.config import cfg
from config.logging_config import get_logger


logger = get_logger("CurrentDataReq")

def download_minute(symbol: str):
    os.makedirs(cfg.DATA_DIR, exist_ok=True)
    today = datetime.now().strftime("%Y%m%d")
    file_path = os.path.join(cfg.DATA_DIR, f"CCB_{symbol}_minute_{today}.csv")

# scripts/CurrentDataReq.py 约第 28 行
df = ak.stock_zh_a_hist(symbol=symbol, period="1", adjust="qfq")
if df.empty:
    logger.error("akshare 返回空数据")
    return
    df.rename(columns=str.lower, inplace=True)
    # 确保这一行缩进正确
    df.to_csv(file_path, index=False, encoding="utf-8-sig")
    logger.info(f"分钟线已保存 -> {file_path}")
    if __name__ == "__main__":
        download_minute(cfg.STOCK_SYMBOL)
