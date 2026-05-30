# algorithms/real_time_service.py
"""
实时数据服务 - 完整修复版
"""
import time
from typing import Optional, Dict, Any
from datetime import datetime, timedelta

from algorithms.data_sources.base import DataSource
from algorithms.data_sources.tester import DataSourceTester
from algorithms.data_models import RealtimeData
from algorithms.exceptions import DataSourceException, DataInterruptException


class RealTimeDataService:
    """实时数据服务"""
    
    def __init__(self, symbol: str):
        self.symbol = symbol
        self.current_source: Optional[DataSource] = None
        self.tester = DataSourceTester()
        self.last_success_time = None
        self.consecutive_failures = 0
        self.max_consecutive_failures = 3
        
    def initialize(self, test_duration: int = 20) -> bool:
        """初始化数据服务"""
        print("📡 初始化实时数据服务...")
        
        try:
            # 测试并选择最优数据源
            test_results = self.tester.test_all_sources(self.symbol, test_duration)
            self.current_source = test_results['best_source']
            
            if not self.current_source:
                raise DataSourceException("无法找到可用的数据源")
            
            print(f"✅ 实时数据服务初始化完成，使用数据源: {self.current_source.name}")
            return True
            
        except Exception as e:
            print(f"❌ 实时数据服务初始化失败: {e}")
            return False
    
    def get_realtime_data(self) -> RealtimeData:
        """获取实时数据"""
        if not self.current_source:
            raise DataInterruptException("数据服务未初始化")
        
        try:
            data = self.current_source.fetch_realtime_data(self.symbol)
            
            # 更新成功状态
            self.last_success_time = datetime.now()
            self.consecutive_failures = 0
            
            return data
            
        except Exception as e:
            # 记录失败
            self.consecutive_failures += 1
            print(f"⚠️ 数据获取失败 ({self.consecutive_failures}/{self.max_consecutive_failures}): {e}")
            
            # 检查是否超过最大失败次数
            if self.consecutive_failures >= self.max_consecutive_failures:
                raise DataInterruptException(f"连续{self.consecutive_failures}次数据获取失败")
            
            # 检查数据源是否长时间不可用
            if (self.last_success_time and 
                datetime.now() - self.last_success_time > timedelta(minutes=5)):
                raise DataInterruptException("数据源长时间不可用")
            
            # 返回无效数据，但允许重试
            return RealtimeData(
                symbol=self.symbol,
                timestamp=datetime.now(),
                price=0.0,
                volume=0,
                change=0.0,
                change_pct=0.0,
                valid=False
            )
    
    def switch_data_source(self) -> bool:
        """切换数据源"""
        print("🔄 尝试切换数据源...")
        
        try:
            # 快速重新测试
            test_results = self.tester.test_all_sources(self.symbol, test_duration=10)
            new_source = test_results['best_source']
            
            if new_source and new_source != self.current_source:
                print(f"🔄 切换数据源: {self.current_source.name} -> {new_source.name}")
                self.current_source = new_source
                self.consecutive_failures = 0
                return True
            else:
                print("⚠️ 没有更好的数据源可用")
                return False
                
        except Exception as e:
            print(f"❌ 数据源切换失败: {e}")
            return False
    
    def get_service_status(self) -> Dict[str, Any]:
        """获取服务状态"""
        return {
            'current_source': self.current_source.name if self.current_source else None,
            'last_success': self.last_success_time,
            'consecutive_failures': self.consecutive_failures,
            'is_healthy': self.consecutive_failures < self.max_consecutive_failures
        }


# ---------- 策略数据库工具函数 ----------

def load_strategy_from_db(symbol):
    """从策略数据库加载指定股票的策略配置
    
    在实盘监测时调用，获取该股票已保存的最优策略。
    如果尚未有回测策略，返回 None，使用默认策略。
    """
    import json
    import os
    from config.config import cfg
    
    db_path = os.path.join(cfg.DATA_DIR, "strategy_db.json")
    if not os.path.exists(db_path):
        return None
    
    try:
        with open(db_path, 'r', encoding='utf-8') as f:
            db = json.load(f)
        strategy = db.get(symbol)
        if strategy:
            print(f"📋 已加载 {symbol} 的策略: {strategy['active_strategy']} (来源: {strategy.get('source', '未知')})")
        return strategy
    except Exception as e:
        print(f"⚠️ 读取策略数据库失败: {e}")
        return None