# algorithms/data_sources/sohu_source.py
"""
搜狐数据源实现 - 修复硬编码问题
"""
import time
import requests
import pandas as pd
from datetime import datetime
from typing import Optional

from algorithms.data_sources.base import DataSource
from algorithms.data_models import RealtimeData
from algorithms.exceptions import DataSourceException


class SohuDataSource(DataSource):
    """搜狐财经数据源"""
    
    def __init__(self):
        super().__init__("搜狐财经")
        self.base_url = "http://q.stock.sohu.com/hisHq"
        self.realtime_url = "http://api.money.126.net/data/feed/{symbol}"
        
    def test_connection(self) -> bool:
        """测试连接"""
        try:
            # 测试接口连通性
            test_params = {
                "code": "cn_000001",
                "start": "20240101", 
                "end": "20240110",
                "stat": "1",
                "order": "D",
                "period": "d"
            }
            response = requests.get(self.base_url, params=test_params, timeout=10)
            return response.status_code == 200
        except Exception as e:
            self.is_available = False
            return False
    
    def fetch_realtime_data(self, symbol: str) -> Optional[RealtimeData]:
        """获取实时数据"""
        try:
            start_time = time.time()
            
            # 尝试使用网易财经接口获取实时数据
            market_symbol = self._convert_symbol_163(symbol)
            if not market_symbol:
                raise DataSourceException("不支持的股票代码格式")
                
            url = self.realtime_url.format(symbol=market_symbol)
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            
            # 解析网易财经数据格式
            data_str = response.text
            data = self._parse_163_data(data_str, symbol)
            
            response_time = time.time() - start_time
            self.update_quality_score(True, response_time)
            
            return data
            
        except Exception as e:
            response_time = time.time() - start_time if 'start_time' in locals() else 10
            self.update_quality_score(False, response_time)
            raise DataSourceException(f"搜狐数据源获取失败: {e}")
    
    def _convert_symbol_163(self, symbol: str) -> str:
        """转换股票代码为网易财经格式"""
        if symbol.startswith('6'):
            return f"0{symbol}"  # 网易财经格式：0+代码
        elif symbol.startswith('0') or symbol.startswith('3'):
            return f"1{symbol}"  # 网易财经格式：1+代码
        else:
            return None
    
    def _parse_163_data(self, data_str: str, symbol: str) -> RealtimeData:
        """解析网易财经数据格式"""
        try:
            # 网易财经数据格式：_ntes_quote_callback({...});
            # 去除回调函数包装
            if data_str.startswith('_ntes_quote_callback('):
                data_str = data_str[len('_ntes_quote_callback('):-2]
            
            import json
            data_json = json.loads(data_str)
            
            # 获取股票数据
            market_symbol = self._convert_symbol_163(symbol)
            if market_symbol not in data_json:
                raise ValueError("股票数据不存在")
                
            stock_data = data_json[market_symbol]
            
            current_price = float(stock_data.get('price', 0))
            prev_close = float(stock_data.get('yestclose', current_price))
            change = current_price - prev_close
            change_pct = (change / prev_close) * 100 if prev_close != 0 else 0
            
            return RealtimeData(
                symbol=symbol,
                timestamp=datetime.now(),
                price=round(current_price, 2),
                volume=int(stock_data.get('volume', 0)),
                change=round(change, 2),
                change_pct=round(change_pct, 2),
                valid=True
            )
            
        except Exception as e:
            raise DataSourceException(f"网易财经数据解析失败: {e}")
    
    def fetch_historical_data(self, symbol: str, days: int = 30) -> Optional[pd.DataFrame]:
        """获取历史数据"""
        try:
            # 转换股票代码
            market_symbol = self._convert_symbol(symbol)
            if not market_symbol:
                return None
                
            # 构建请求参数
            end_date = datetime.now().strftime("%Y%m%d")
            start_date = (datetime.now() - pd.Timedelta(days=days)).strftime("%Y%m%d")
            
            params = {
                "code": market_symbol,
                "start": start_date,
                "end": end_date,
                "stat": "1",
                "order": "D",
                "period": "d"
            }
            
            response = requests.get(self.base_url, params=params, timeout=10)
            response.raise_for_status()
            
            data = response.json()
            if not data or 'hq' not in data[0]:
                return None
                
            # 解析历史数据
            df_data = []
            for item in data[0]['hq']:
                df_data.append({
                    'date': item[0],
                    'open': float(item[1]),
                    'close': float(item[2]),
                    'change': float(item[3]),
                    'change_pct': float(item[4].strip('%')),
                    'low': float(item[5]),
                    'high': float(item[6]),
                    'volume': int(item[7]),
                    'amount': float(item[8])
                })
            
            return pd.DataFrame(df_data)
            
        except Exception as e:
            raise DataSourceException(f"搜狐历史数据获取失败: {e}")
    
    def _convert_symbol(self, symbol: str) -> str:
        """转换股票代码为搜狐格式"""
        if symbol.startswith('6'):
            return f"cn_{symbol}"
        elif symbol.startswith('0') or symbol.startswith('3'):
            return f"cn_{symbol}"
        else:
            return None