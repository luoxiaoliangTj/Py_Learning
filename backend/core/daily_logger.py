"""
每日日志管理 - 读写 data/daily_logs/work_log_{date}.md
"""
import os
from datetime import datetime
from pathlib import Path

from .config import DATA_DIR

LOG_DIR = DATA_DIR / "daily_logs"


def get_log_path(date_str: str = None) -> Path:
    """获取日志文件路径"""
    if not date_str:
        date_str = datetime.now().strftime("%Y-%m-%d")
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    return LOG_DIR / f"work_log_{date_str}.md"


def append_log(content: str, date_str: str = None):
    """追加内容到日志"""
    path = get_log_path(date_str)
    with open(path, 'a', encoding='utf-8') as f:
        f.write(content + "\n\n")


def read_log(date_str: str = None) -> str:
    """读取日志内容"""
    path = get_log_path(date_str)
    if path.exists():
        with open(path, 'r', encoding='utf-8') as f:
            return f.read()
    return ""


def list_logs() -> list:
    """列出所有日志文件"""
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    files = sorted(LOG_DIR.glob("work_log_*.md"), reverse=True)
    return [f.name for f in files]


def delete_log(date_str: str) -> bool:
    """删除指定日期的日志"""
    path = get_log_path(date_str)
    if path.exists():
        path.unlink()
        return True
    return False
