"""
Pydantic models for request/response validation.
"""
from pydantic import BaseModel, Field
from typing import Optional


class PredictDailyRequest(BaseModel):
    symbol: str = Field(..., description="股票代码，如 000001", min_length=6, max_length=6)


class PredictRealtimeRequest(BaseModel):
    symbol: str = Field(..., description="股票代码，如 000001", min_length=6, max_length=6)
    prev_close: Optional[float] = Field(None, description="前收盘价（可选）")


class BacktestRequest(BaseModel):
    symbol: str = Field(..., description="股票代码，如 000001", min_length=6, max_length=6)
    strategy_type: str = Field("channel", description="策略类型: channel 或 trend")
    params: Optional[dict] = Field(None, description="策略参数，如 {\"k\": 2.0}")


class StockSelectRequest(BaseModel):
    symbol: str = Field(..., description="股票代码", min_length=6, max_length=6)
    name: Optional[str] = Field(None, description="股票名称")
    index_symbol: Optional[str] = Field(None, description="指数代码")
    index_name: Optional[str] = Field(None, description="指数名称")


class DownloadRequest(BaseModel):
    symbol: str = Field(..., description="股票代码", min_length=6, max_length=6)
    years: int = Field(8, description="数据年限", ge=1, le=20)
    source: str = Field("auto", description="数据源: auto, sina, sohu")


class OptimizeRequest(BaseModel):
    symbol: str = Field(..., description="股票代码", min_length=6, max_length=6)
    strategy_type: str = Field("channel", description="策略类型: channel 或 trend")


class CapitalUpdateRequest(BaseModel):
    available_cash: Optional[float] = Field(None, ge=0, description="可用资金")
    total_capital: Optional[float] = Field(None, ge=0, description="总资金")


class PositionUpdateRequest(BaseModel):
    symbol: str = Field(..., description="股票代码", min_length=6, max_length=6)
    shares: int = Field(..., ge=0, description="持仓股数")
    cost_price: float = Field(..., description="成本价")
    stock_name: Optional[str] = Field(None, description="股票名称")


class PortfolioImportItem(BaseModel):
    """单条持仓数据（从 .md 文件解析后）"""
    symbol: str = Field("", description="股票代码（6位数字）")
    name: str = Field("", description="股票名称")
    shares: int = Field(0, ge=0, description="持仓股数")
    cost_price: float = Field(0.0, description="成本价")


class PortfolioImportCapital(BaseModel):
    """资金信息"""
    available_cash: Optional[float] = Field(None, ge=0, description="可用资金")
    total_capital: Optional[float] = Field(None, ge=0, description="总资金")


class PortfolioImportRequest(BaseModel):
    """持仓导入请求"""
    holdings: list[PortfolioImportItem] = Field(default_factory=list, description="持仓列表")
    capital: Optional[PortfolioImportCapital] = Field(None, description="资金信息（可选）")


class StrategySaveRequest(BaseModel):
    """策略保存请求"""
    active_strategy: str = Field(..., description="策略名称")
    params: dict = Field(default_factory=dict, description="策略参数")
    metrics: dict = Field(default_factory=dict, description="策略指标（夏普、收益等）")
    source: str = Field("手动", description="来源：回测、优化、手动")
    last_updated: Optional[str] = Field(None, description="最后更新时间")


class StrategyInfo(BaseModel):
    """策略信息"""
    active_strategy: str = Field(..., description="策略名称")
    params: dict = Field(default_factory=dict, description="策略参数")
    metrics: dict = Field(default_factory=dict, description="策略指标")
    source: str = Field("手动", description="来源")
    last_updated: Optional[str] = Field(None, description="最后更新时间")
