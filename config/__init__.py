#!/usr/bin/env python3
"""
config/__init__.py - 统一配置 & 单例
"""
import os
from dataclasses import dataclass

# --------------- 配置定义 ---------------
@dataclass
class Config:
    # 路径
    BASE_DIR        = os.path.dirname(os.path.dirname(__file__))
    DATA_DIR        = os.path.join(BASE_DIR, "data")
    LOG_DIR         = os.path.join(BASE_DIR, "logs")
    RESULT_DIR      = os.path.join(BASE_DIR, "realtime_results")
    BACKTEST_DIR    = os.path.join(BASE_DIR, "backtest_results")
    ALGO_DIR        = os.path.join(BASE_DIR, "algorithms")

    # 默认股票 & 指数
    STOCK_SYMBOL    = os.getenv("STOCK_SYMBOL", "000001")
    STOCK_NAME      = os.getenv("STOCK_NAME", "平安银行")
    INDEX_SYMBOL    = os.getenv("INDEX_SYMBOL", "000001")
    INDEX_NAME      = os.getenv("INDEX_NAME", "上证指数")

    # 日志级别
    LOG_LEVEL       = os.getenv("LOG_LEVEL", "INFO").upper()

# --------------- 单例导出 ---------------
cfg = Config()
