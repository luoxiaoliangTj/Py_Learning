# [file name]: scripts/backtest_modules/__init__.py
#[file content begin]
"""
backtest_modules - 回测模块包
包含策略引擎、指标计算、数据驱动评估、在线学习等核心组件
"""

from .strategy_engine import StrategyEngine
from .metrics import MetricsCalculator
from .data_driven_evaluator import DataDrivenEvaluator
from .online_learning_module import OnlineLearningModule
from .market_state_detector import MarketStateDetector
from .performance_analyzer import PerformanceAnalyzer
from .parameter_optimizer import ParameterOptimizer

__all__ = [
    'StrategyEngine', 
    'MetricsCalculator', 
    'DataDrivenEvaluator',
    'OnlineLearningModule',
    'MarketStateDetector', 
    'PerformanceAnalyzer',
    'ParameterOptimizer'
]
#[file content end]