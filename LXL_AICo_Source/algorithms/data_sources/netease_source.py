# algorithms/data_sources/netease_source.py
"""
网易财经数据源 - 备用数据源
"""
import time
import requests
import json
import pandas as pd
from datetime import datetime
from typing import Optional

from algorithms.data_sources.base import DataSource
from algorithms.data_models import RealtimeData
from algorithms.exceptions import DataSourceException


class NeteaseDataSource(DataSource):  # 注意：类名是 NeteaseDataSource
    """网易财经数据源"""
    
    def __init__(self):
        super().__init__("网易财经")
        self.base_url = "http://api.money.126.net/data/feed/{symbol}"
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Referer': 'http://quotes.money.163.com/'
        }
        
    def test_connection(self) -> bool:
        """测试连接"""
        try:
            # 测试获取上证指数
            test_symbol = "0000001"  # 网易格式：0+代码
            url = self.base_url.format(symbol=test_symbol)
            response = requests.get(url, headers=self.headers, timeout=10)
            return response.status_code == 200 and '_ntes_quote_callback' in response.text
        except Exception as e:
            print(f"❌ 网易连接测试失败: {e}")
            self.is_available = False
            return False
    
    def fetch_realtime_data(self, symbol: str) -> Optional[RealtimeData]:
        """获取实时数据"""
        try:
            start_time = time.time()
            
            market_symbol = self._convert_symbol(symbol)
            if not market_symbol:
                raise DataSourceException(f"不支持的股票代码格式: {symbol}")
                
            url = self.base_url.format(symbol=market_symbol)
            response = requests.get(url, headers=self.headers, timeout=10)
            response.raise_for_status()
            
            data = self._parse_netease_data(response.text, symbol)
            
            response_time = time.time() - start_time
            self.update_quality_score(True, response_time)
            
            return data
            
        except Exception as e:
            response_time = time.time() - start_time if 'start_time' in locals() else 10
            self.update_quality_score(False, response_time)
            raise DataSourceException(f"网易数据源获取失败: {e}")
    
    def _convert_symbol(self, symbol: str) -> str:
        """转换股票代码为网易格式"""
        if symbol.startswith('6'):
            return f"0{symbol}"  # 上海股票：0+代码
        elif symbol.startswith('0') or symbol.startswith('3'):
            return f"1{symbol}"  # 深圳股票：1+代码
        else:
            return None
    
    def _parse_netease_data(self, data_str: str, symbol: str) -> RealtimeData:
        """解析网易财经数据格式"""
        try:
            # 网易数据格式：_ntes_quote_callback({...});
            if data_str.startswith('_ntes_quote_callback('):
                data_str = data_str[len('_ntes_quote_callback('):-2]
            
            data_json = json.loads(data_str)
            
            market_symbol = self._convert_symbol(symbol)
            if market_symbol not in data_json:
                raise ValueError(f"股票数据不存在: {market_symbol}")
                
            stock_data = data_json[market_symbol]
            
            current_price = float(stock_data.get('price', 0))
            prev_close = float(stock_data.get('yestclose', current_price))
            change = current_price - prev_close
            change_pct = (change / prev_close) * 100 if prev_close != 0 else 0
            
            return RealtimeData(
                symbol=symbol,
                timestamp=datetime.now(),  # 网易数据中没有明确的时间戳
                price=round(current_price, 2),
                volume=int(stock_data.get('volume', 0)),
                change=round(change, 2),
                change_pct=round(change_pct, 2),
                valid=True
            )
            
        except Exception as e:
            raise DataSourceException(f"网易数据解析失败: {e} - 原始数据: {data_str[:100]}")
    
    def fetch_historical_data(self, symbol: str, days: int = 30) -> Optional[pd.DataFrame]:
        """获取历史数据"""
        # 网易也主要提供实时数据
        return None