# algorithms/data_sources/tester.py
"""
数据源测试器 - 专注真实实时数据，无模拟替代版本
"""
import time
import threading
from typing import List, Dict, Optional, Any
from concurrent.futures import ThreadPoolExecutor, as_completed

from algorithms.data_sources.base import DataSource
from algorithms.data_sources.sina_source import SinaDataSource
from algorithms.data_sources.sohu_source import SohuDataSource
from algorithms.data_sources.netease_source import NeteaseDataSource  # 修正导入
from algorithms.exceptions import DataSourceException


class DataSourceTester:
    """数据源测试器 - 仅测试真实数据源"""
    
    def __init__(self):
        self.data_sources: List[DataSource] = [
            SinaDataSource(),        # 新浪数据源
            NeteaseDataSource(),     # 网易数据源（使用正确的类名）
            SohuDataSource(),        # 搜狐数据源
            # 注意：不再包含 LocalFallbackSource
        ]
        self.test_results = {}
        
    def test_all_sources(self, symbol: str, test_duration: int = 30) -> Dict:
        """
        测试所有数据源
        
        Args:
            symbol: 测试的股票代码
            test_duration: 测试持续时间（秒）
            
        Returns:
            测试结果字典
        """
        print(f"🔍 开始测试实时数据源，股票: {symbol}，持续时间: {test_duration}秒")
        print("⚠️  注意：系统已禁用模拟数据，仅使用真实实时数据源")
        
        # 初始连接测试
        self._test_initial_connection()
        
        # 持续性能测试
        self._test_performance(symbol, test_duration)
        
        # 评估并选择最优数据源
        best_source = self._select_best_source()
        
        return {
            'best_source': best_source,
            'all_results': self.test_results,
            'symbol': symbol
        }
    
    def _test_initial_connection(self):
        """初始连接测试"""
        print("📡 进行初始连接测试...")
        
        with ThreadPoolExecutor(max_workers=len(self.data_sources)) as executor:
            future_to_source = {
                executor.submit(source.test_connection): source 
                for source in self.data_sources
            }
            
            for future in as_completed(future_to_source):
                source = future_to_source[future]
                try:
                    result = future.result(timeout=10)
                    status = "✅ 可用" if result else "❌ 不可用"
                    print(f"  {source.name}: {status}")
                except Exception as e:
                    print(f"  {source.name}: ❌ 测试失败 - {e}")
                    source.is_available = False
    
    def _test_performance(self, symbol: str, duration: int):
        """性能测试"""
        print("⚡ 进行性能测试...")
        
        # 为每个数据源创建测试记录
        for source in self.data_sources:
            if source.is_available:
                self.test_results[source.name] = {
                    'success_count': 0,
                    'error_count': 0,
                    'total_requests': 0,
                    'avg_response_time': 0,
                    'total_response_time': 0,
                    'stability_score': 0,
                    'has_valid_data': False,  # 标记是否返回过有效数据
                    'last_valid_price': None,  # 记录最后一次有效价格
                    'data_quality_score': 0.0  # 数据质量评分
                }
        
        # 性能测试循环
        start_time = time.time()
        test_count = 0
        
        while time.time() - start_time < duration:
            test_count += 1
            print(f"  第{test_count}轮测试...")
            
            with ThreadPoolExecutor(max_workers=len(self.data_sources)) as executor:
                future_to_source = {}
                
                for source in self.data_sources:
                    if source.is_available:
                        future = executor.submit(self._test_source_performance, source, symbol)
                        future_to_source[future] = source
                
                for future in as_completed(future_to_source):
                    source = future_to_source[future]
                    try:
                        result = future.result(timeout=15)
                        self._record_test_result(source.name, result)
                    except Exception as e:
                        self._record_test_result(source.name, {
                            'success': False,
                            'response_time': 15,
                            'error': str(e),
                            'data': None,
                            'is_valid': False
                        })
            
            # 短暂休息
            time.sleep(2)
    
    def _test_source_performance(self, source: DataSource, symbol: str) -> Dict:
        """测试单个数据源性能"""
        start_time = time.time()
        
        try:
            data = source.fetch_realtime_data(symbol)
            response_time = time.time() - start_time
            
            # 严格验证数据有效性
            is_valid = self._validate_data_quality(data, symbol)
            
            if is_valid:
                print(f"    {source.name}: ✅ 返回有效数据 {data.price}")
            else:
                print(f"    {source.name}: ⚠️  返回无效数据")
            
            return {
                'success': True,
                'response_time': response_time,
                'data': data,
                'is_valid': is_valid,
                'price': data.price if data else None
            }
        except Exception as e:
            response_time = time.time() - start_time
            print(f"    {source.name}: ❌ 获取失败 - {str(e)[:50]}")
            return {
                'success': False,
                'response_time': response_time,
                'error': str(e),
                'data': None,
                'is_valid': False
            }
    
    def _validate_data_quality(self, data, symbol: str) -> bool:
        """严格验证数据质量"""
        if not data or not data.valid:
            return False
            
        # 价格合理性检查
        if data.price <= 0:
            return False
            
        # 涨跌幅合理性检查（A股涨跌停为±10%，留有一定余量）
        if abs(data.change_pct) > 11:
            return False
            
        # 成交量合理性检查
        if data.volume < 0:
            return False
            
        # 股票代码匹配检查
        if data.symbol != symbol:
            return False
            
        # 时间戳检查（数据不应太旧）
        current_time = time.time()
        data_time = data.timestamp.timestamp() if hasattr(data.timestamp, 'timestamp') else data.timestamp
        if current_time - data_time > 300:  # 5分钟
            return False
            
        return True
    
    def _record_test_result(self, source_name: str, result: Dict):
        """记录测试结果"""
        if source_name not in self.test_results:
            return
            
        stats = self.test_results[source_name]
        stats['total_requests'] += 1
        
        if result['success']:
            stats['success_count'] += 1
            stats['total_response_time'] += result['response_time']
            stats['avg_response_time'] = stats['total_response_time'] / stats['success_count']
            
            # 记录是否有有效数据
            if result.get('is_valid', False):
                stats['has_valid_data'] = True
                stats['last_valid_price'] = result.get('price')
                
                # 计算数据质量评分
                data_quality = self._calculate_data_quality(result['data'])
                stats['data_quality_score'] = 0.7 * stats['data_quality_score'] + 0.3 * data_quality
        else:
            stats['error_count'] += 1
        
        # 计算稳定性评分
        if stats['total_requests'] > 0:
            success_rate = stats['success_count'] / stats['total_requests']
            # 响应时间评分（越快越好）
            time_score = max(0, 1 - stats['avg_response_time'] / 5.0) if stats['success_count'] > 0 else 0
            # 综合评分：成功率权重70%，响应时间权重20%，数据质量权重10%
            stability_score = (
                success_rate * 0.7 + 
                time_score * 0.2 + 
                stats['data_quality_score'] * 0.1
            )
            stats['stability_score'] = stability_score
    
    def _calculate_data_quality(self, data) -> float:
        """计算数据质量评分"""
        if not data:
            return 0.0
            
        quality_score = 1.0
        
        # 价格合理性
        if data.price <= 0:
            quality_score *= 0.3
            
        # 涨跌幅合理性
        if abs(data.change_pct) > 20:
            quality_score *= 0.5
        elif abs(data.change_pct) > 10:
            quality_score *= 0.8
            
        # 成交量合理性
        if data.volume < 0:
            quality_score *= 0.3
            
        return quality_score
    
    def _select_best_source(self) -> Optional[DataSource]:
        """选择最优数据源 - 仅限真实有效数据源"""
        available_sources = [s for s in self.data_sources if s.is_available]
        
        if not available_sources:
            raise DataSourceException("❌ 没有可用的实时数据源")
        
        # 只选择返回过有效真实数据的数据源
        valid_sources = []
        for source in available_sources:
            source_name = source.name
            if (source_name in self.test_results and 
                self.test_results[source_name].get('has_valid_data', False)):
                valid_sources.append(source)
        
        if not valid_sources:
            raise DataSourceException("❌ 所有数据源都未能返回有效实时数据")
        
        # 选择综合评分最高的有效数据源
        best_source = max(valid_sources, key=lambda s: self.test_results[s.name]['stability_score'])
        best_score = self.test_results[best_source.name]['stability_score']
        
        print(f"\n✅ 选择实时数据源: {best_source.name} (综合评分: {best_score:.3f})")
        
        # 打印详细结果
        print("\n📊 实时数据源测试结果汇总:")
        print("=" * 50)
        for source_name, results in self.test_results.items():
            success_rate = results['success_count'] / results['total_requests'] if results['total_requests'] > 0 else 0
            has_valid = results.get('has_valid_data', False)
            data_quality = results.get('data_quality_score', 0)
            
            print(f"  {source_name}:")
            print(f"    连接状态: {'✅ 可用' if success_rate > 0 else '❌ 不可用'}")
            print(f"    成功率: {success_rate:.1%}")
            print(f"    平均响应: {results.get('avg_response_time', 0):.2f}s")
            print(f"    有效数据: {'✅' if has_valid else '❌'}")
            print(f"    数据质量: {data_quality:.3f}")
            print(f"    综合评分: {results.get('stability_score', 0):.3f}")
            if has_valid:
                print(f"    最后有效价格: {results.get('last_valid_price')}")
            print()
        
        return best_source