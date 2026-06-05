#!/usr/bin/env python3
"""
diagnose_algo.py - 独立算法监测器
只干三件事：读 CSV → 预测 → 打印
用法：python tools/diagnose_algo.py
"""
import os, sys, pandas as pd
from datetime import datetime

BASE_DIR = os.path.dirname(os.path.dirname(__file__))
sys.path.insert(0, BASE_DIR)

from config.config import cfg
from scripts.backtest_day import AlgoHotPlug   # 复用你的热插拔

# ---------------- 监测入口 ----------------
def monitor():
    symbol = cfg.STOCK_SYMBOL
    csv_path = os.path.join(cfg.DATA_DIR, f"CCB_{symbol}_daily.csv")

    if not os.path.exists(csv_path):
        print(f"❌ 找不到文件：{csv_path}")
        return

    df = pd.read_csv(csv_path)
    cn2en = {"日期": "date", "开盘": "open", "收盘": "close", "最高": "high", "最低": "low", "成交量": "volume"}
    df.rename(columns=cn2en, inplace=True)
    df.rename(columns=str.lower, inplace=True)
    df['date'] = pd.to_datetime(df['date'])

    plug = AlgoHotPlug(cfg.ALGO_DIR)
    # [修复] 始终使用自适应算法，避免加载 algorithms/ 下无 algo_predict 的模块
    pred_func = plug.get_default_algo({})

    print(f"\n===== 监测 {symbol} 算法输出 =====")
    for i in range(20, len(df)):
        sub = df.iloc[:i+1]          # 历史数据
        c     = sub['close'].iloc[-1]
        meta  = {"symbol": symbol, "current_price": c,
                 "volatility": sub['close'].pct_change().std() * (250**0.5),
                 "trend_strength": (c / sub['close'].iloc[-20] - 1) / 20,
                 "market_regime": "normal"}

        pred  = pred_func(sub, None, meta)
        low   = pred["pred_low"]
        high  = pred["pred_high"]
        width = (high - low) / c * 100

        # 简单信号：当前价 vs 预测区间
        signal = []
        if c <= low * 1.01:   signal.append("接近预测低点")
        if c >= high * 0.99:  signal.append("接近预测高点")
        if not signal:        signal.append("区间内")

        print(f"{sub['date'].iloc[-1]:%Y-%m-%d}  "
              f"当前={c:7.2f}  "
              f"预测={low:7.2f}-{high:7.2f}  "
              f"宽度={width:5.1f}%  "
              f"信号：{', '.join(signal)}")

    print("\n===== 监测结束 =====")

# ---------------- 入口 ----------------
if __name__ == "__main__":
    monitor()
