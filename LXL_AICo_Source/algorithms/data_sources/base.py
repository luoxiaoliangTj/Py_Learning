# algorithms/data_sources/base.py
"""
数据源基类和接口定义
"""
from abc import ABC, abstractmethod
from datetime import datetime
from typing import List, Optional, Dict, Any
import pandas as pd

from algorithms.data_models import RealtimeData
from algorithms.exceptions import DataSourceException


class DataSource(ABC):
    """数据源基类"""
    
    def __init__(self, name: str):
        self.name = name
        self.is_available = True
        self.last_check = None
        self.quality_score = 0.0
        
    @abstractmethod
    def fetch_realtime_data(self, symbol: str) -> Optional[RealtimeData]:
        """获取实时数据"""
        pass
    
    @abstractmethod
    def fetch_historical_data(self, symbol: str, days: int = 30) -> Optional[pd.DataFrame]:
        """获取历史数据用于验证"""
        pass
    
    @abstractmethod
    def test_connection(self) -> bool:
        """测试连接"""
        pass
    
    def update_quality_score(self, success: bool, response_time: float):
        """更新质量评分"""
        if success:
            # 成功请求，根据响应时间评分
            time_score = max(0, 1 - response_time / 10.0)  # 10秒内为满分
            self.quality_score = 0.7 * self.quality_score + 0.3 * time_score
        else:
            # 失败请求，降低评分
            self.quality_score *= 0.5
            
        self.last_check = datetime.now()