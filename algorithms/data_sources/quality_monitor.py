# algorithms/data_sources/quality_monitor.py
"""
实时数据源质量监控
"""
import time
from datetime import datetime, timedelta
from typing import Dict, List

class DataSourceQualityMonitor:
    """数据源质量监控器"""
    
    def __init__(self):
        self.performance_stats: Dict[str, Dict] = {}
        self.last_check_time = datetime.now()
        
    def validate_data_quality(self, data, source_name: str) -> bool:
        """验证数据质量"""
        if not data or not data.valid:
            return False
            
        # 价格合理性检查
        if data.price <= 0:
            return False
            
        # 涨跌幅合理性检查（A股涨跌停为±10%）
        if abs(data.change_pct) > 11:
            return False
            
        # 成交量合理性检查
        if data.volume < 0:
            return False
            
        # 时间戳检查（数据不应太旧）
        if datetime.now() - data.timestamp > timedelta(minutes=5):
            return False
            
        return True
    
    def record_performance(self, source_name: str, success: bool, response_time: float):
        """记录性能指标"""
        if source_name not in self.performance_stats:
            self.performance_stats[source_name] = {
                'total_requests': 0,
                'success_count': 0,
                'total_response_time': 0,
                'last_success': None
            }
        
        stats = self.performance_stats[source_name]
        stats['total_requests'] += 1
        
        if success:
            stats['success_count'] += 1
            stats['total_response_time'] += response_time
            stats['last_success'] = datetime.now()
    
    def get_source_health(self, source_name: str) -> Dict:
        """获取数据源健康状态"""
        if source_name not in self.performance_stats:
            return {'health': 'unknown', 'success_rate': 0}
        
        stats = self.performance_stats[source_name]
        success_rate = stats['success_count'] / stats['total_requests'] if stats['total_requests'] > 0 else 0
        
        if success_rate > 0.8:
            health = 'healthy'
        elif success_rate > 0.5:
            health = 'degraded'
        else:
            health = 'unhealthy'
            
        return {
            'health': health,
            'success_rate': success_rate,
            'avg_response_time': stats['total_response_time'] / stats['success_count'] if stats['success_count'] > 0 else 0,
            'last_success': stats['last_success']
        }