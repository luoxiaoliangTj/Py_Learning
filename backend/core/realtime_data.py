"""
实时数据获取 - 多数据源自动降级
数据源优先级: 新浪(主) → 网易(备) → 搜狐(备)
对齐原始代码: algorithms/data_sources/
"""
import time
import json
import random
import requests
from datetime import datetime
from typing import Optional

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


def _convert_symbol_sina(symbol: str) -> Optional[str]:
    """新浪格式: sh600519 / sz000001"""
    if symbol.startswith("6"):
        return f"sh{symbol}"
    elif symbol.startswith("0") or symbol.startswith("3"):
        return f"sz{symbol}"
    return None


def _convert_symbol_netease(symbol: str) -> Optional[str]:
    """网易格式: 0600519 / 1000001"""
    if symbol.startswith("6"):
        return f"0{symbol}"
    elif symbol.startswith("0") or symbol.startswith("3"):
        return f"1{symbol}"
    return None


def fetch_realtime_sina(symbol: str) -> Optional[dict]:
    """
    新浪财经实时数据
    对齐原始代码: algorithms/data_sources/sina_source.py -> SinaDataSource._parse_sina_data
    """
    try:
        sina_symbol = _convert_symbol_sina(symbol)
        if not sina_symbol:
            return None

        url = "http://hq.sinajs.cn/list=" + sina_symbol
        headers = {
            "User-Agent": random.choice(USER_AGENTS),
            "Referer": "http://finance.sina.com.cn/",
            "Accept": "*/*",
            "Accept-Encoding": "gzip, deflate",
            "Connection": "keep-alive",
        }
        resp = requests.get(url, headers=headers, timeout=10)
        resp.raise_for_status()

        # 解析: var hq_str_sh600519="贵州茅台,1780.00,1775.00,..."
        text = resp.text
        if '="' not in text:
            return None

        data_content = text.split('="')[1].rstrip('";')
        fields = data_content.split(",")

        if len(fields) < 32:
            return None

        # 新浪字段说明 (对齐原始代码注释):
        # 0: 股票名称, 1: 今日开盘价, 2: 昨日收盘价, 3: 当前价格
        # 4: 今日最高价, 5: 今日最低价, 6: 竞买价(买一), 7: 竞卖价(卖一)
        # 8: 成交股数, 9: 成交金额
        # 30: 日期, 31: 时间
        stock_name = fields[0]
        current_price = float(fields[3])
        prev_close = float(fields[2])
        volume = int(fields[8])
        change = current_price - prev_close
        change_pct = (change / prev_close) * 100 if prev_close != 0 else 0

        date_str = fields[30]
        time_str = fields[31]

        return {
            "symbol": symbol,
            "name": stock_name,
            "price": round(current_price, 2),
            "prev_close": round(prev_close, 2),
            "change": round(change, 2),
            "change_pct": round(change_pct, 2),
            "volume": volume,
            "date": date_str,
            "time": time_str,
            "source": "sina",
            "valid": True,
        }
    except Exception as e:
        return None


def fetch_realtime_netease(symbol: str) -> Optional[dict]:
    """
    网易财经实时数据
    对齐原始代码: algorithms/data_sources/netease_source.py -> NeteaseDataSource._parse_netease_data
    """
    try:
        netease_symbol = _convert_symbol_netease(symbol)
        if not netease_symbol:
            return None

        url = f"http://api.money.126.net/data/feed/{netease_symbol}"
        headers = {
            "User-Agent": random.choice(USER_AGENTS),
            "Referer": "http://quotes.money.163.com/",
        }
        resp = requests.get(url, headers=headers, timeout=10)
        resp.raise_for_status()

        # 解析: _ntes_quote_callback({...});
        text = resp.text
        if text.startswith("_ntes_quote_callback("):
            text = text[len("_ntes_quote_callback("):-2]

        data_json = json.loads(text)
        if netease_symbol not in data_json:
            return None

        stock_data = data_json[netease_symbol]
        current_price = float(stock_data.get("price", 0))
        prev_close = float(stock_data.get("yestclose", current_price))
        change = current_price - prev_close
        change_pct = (change / prev_close) * 100 if prev_close != 0 else 0

        return {
            "symbol": symbol,
            "name": stock_data.get("name", ""),
            "price": round(current_price, 2),
            "prev_close": round(prev_close, 2),
            "change": round(change, 2),
            "change_pct": round(change_pct, 2),
            "volume": int(stock_data.get("volume", 0)),
            "date": datetime.now().strftime("%Y-%m-%d"),
            "time": datetime.now().strftime("%H:%M:%S"),
            "source": "netease",
            "valid": True,
        }
    except Exception as e:
        return None


def fetch_realtime_sohu(symbol: str) -> Optional[dict]:
    """
    搜狐财经实时数据 (第三备用)
    对齐原始代码: algorithms/data_sources/sohu_source.py -> SohuDataSource._parse_163_data

    注: 原始代码中搜狐数据源实际使用网易财经接口(api.money.126.net)获取实时数据，
    此处严格对齐原始代码的解析逻辑。
    """
    try:
        # 搜狐数据源使用网易财经接口获取实时数据 (对齐原始代码)
        sohu_symbol = _convert_symbol_netease(symbol)
        if not sohu_symbol:
            return None

        url = f"http://api.money.126.net/data/feed/{sohu_symbol}"
        headers = {
            "User-Agent": random.choice(USER_AGENTS),
            "Referer": "http://quotes.money.163.com/",
        }
        resp = requests.get(url, headers=headers, timeout=10)
        resp.raise_for_status()

        # 解析: _ntes_quote_callback({...});
        text = resp.text
        if text.startswith("_ntes_quote_callback("):
            text = text[len("_ntes_quote_callback("):-2]

        data_json = json.loads(text)
        if sohu_symbol not in data_json:
            return None

        stock_data = data_json[sohu_symbol]
        current_price = float(stock_data.get("price", 0))
        prev_close = float(stock_data.get("yestclose", current_price))
        change = current_price - prev_close
        change_pct = (change / prev_close) * 100 if prev_close != 0 else 0

        return {
            "symbol": symbol,
            "name": stock_data.get("name", ""),
            "price": round(current_price, 2),
            "prev_close": round(prev_close, 2),
            "change": round(change, 2),
            "change_pct": round(change_pct, 2),
            "volume": int(stock_data.get("volume", 0)),
            "date": datetime.now().strftime("%Y-%m-%d"),
            "time": datetime.now().strftime("%H:%M:%S"),
            "source": "sohu",
            "valid": True,
        }
    except Exception as e:
        return None


def fetch_realtime_data(symbol: str) -> dict:
    """
    获取实时数据，多数据源自动降级
    优先级: 新浪(主) → 网易(备) → 搜狐(备)
    对齐原始代码: algorithms/real_time_service.py -> RealTimeDataService.get_realtime_data
    """
    if not symbol or not symbol.isdigit() or len(symbol) != 6:
        return {
            "symbol": symbol or "",
            "name": "",
            "price": 0.0,
            "prev_close": 0.0,
            "change": 0.0,
            "change_pct": 0.0,
            "volume": 0,
            "date": "",
            "time": "",
            "source": "",
            "valid": False,
        }

    # 1. 尝试新浪 (主数据源)
    result = fetch_realtime_sina(symbol)
    if result and result.get("valid"):
        return result

    # 2. 尝试网易 (备用数据源)
    result = fetch_realtime_netease(symbol)
    if result and result.get("valid"):
        return result

    # 3. 尝试搜狐 (第三备用数据源)
    result = fetch_realtime_sohu(symbol)
    if result and result.get("valid"):
        return result

    # 所有数据源均失败
    return {
        "symbol": symbol,
        "name": "",
        "price": 0.0,
        "prev_close": 0.0,
        "change": 0.0,
        "change_pct": 0.0,
        "volume": 0,
        "date": "",
        "time": "",
        "source": "",
        "valid": False,
    }
