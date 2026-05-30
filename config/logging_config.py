import logging, os
from .config import cfg

def get_logger(name: str) -> logging.Logger:
    os.makedirs(cfg.LOG_DIR, exist_ok=True)
    logger = logging.getLogger(name)
    if logger.hasHandlers():          # 避免重复
        return logger
    logger.setLevel(cfg.LOG_LEVEL)
    handler = logging.FileHandler(os.path.join(cfg.LOG_DIR, f"{name}.log"), encoding="utf-8")
    handler.setFormatter(logging.Formatter(
        "%(asctime)s | %(levelname)s | %(name)s | %(message)s"))
    logger.addHandler(handler)
    # 同时输出到控制台
    console = logging.StreamHandler()
    console.setFormatter(logging.Formatter("%(message)s"))
    logger.addHandler(console)
    return logger
