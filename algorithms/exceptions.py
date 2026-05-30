# algorithms/exceptions.py
"""
在线预测异常类定义
"""

class OnlinePredictException(Exception):
    """在线预测基础异常"""
    pass

class DataSourceException(OnlinePredictException):
    """数据源异常"""
    pass

class StrategyException(OnlinePredictException):
    """策略异常"""
    pass

class UserInterruptException(OnlinePredictException):
    """用户中断异常"""
    pass

class DataInterruptException(OnlinePredictException):
    """数据中断异常"""
    pass