# algorithms/data_models.py
"""
数据模型定义
"""
from dataclasses import dataclass
from datetime import datetime
from typing import Optional, Dict, Any

@dataclass
class RealtimeData:
    """实时数据容器"""
    symbol: str
    timestamp: datetime
    price: float
    volume: int
    change: float
    change_pct: float
    valid: bool = True

@dataclass  
class StrategyState:
    """策略状态容器"""
    current_strategy: Dict[str, Any]
    previous_strategy: Optional[Dict[str, Any]]
    change_reason: Optional[str]
    confidence: float
    last_updated: datetime

@dataclass
class PredictionContext:
    """预测上下文容器"""
    symbol: str
    daily_plan: Dict[str, Any]
    realtime_data: Optional[RealtimeData]
    current_strategy: StrategyState
    start_time: datetime