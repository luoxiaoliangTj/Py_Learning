"""
API Routes - Strategy management endpoints.
策略管理与参数优化接口，响应格式对齐 Android 端 ApiResponse。
"""
from fastapi import APIRouter, HTTPException
from models import OptimizeRequest
from core.data_loader import load_daily_data
from core.strategy_engine import optimize_params, quick_score
from core.performance_analyzer import PerformanceAnalyzer

router = APIRouter(prefix="/api/strategy", tags=["策略"])


STRATEGY_LIST = [
    {
        "id": "channel",
        "name": "通道策略",
        "description": "基于ATR通道的均值回归策略，适合震荡市场",
        "default_params": {"type": "channel", "k": 2.0},
    },
    {
        "id": "trend",
        "name": "趋势策略",
        "description": "基于MA交叉的趋势跟随策略，适合趋势市场",
        "default_params": {"type": "trend", "fast_ma": 10, "slow_ma": 30},
    },
    {
        "id": "lmsr",
        "name": "LMSR策略",
        "description": "对数市场评分规则做市策略",
        "default_params": {"type": "lmsr", "b": 100.0},
    },
]


@router.get("/list", summary="策略列表")
async def list_strategies():
    """
    获取所有可用策略列表。
    """
    return {"code": 200, "message": "获取成功", "data": STRATEGY_LIST}


@router.post("/optimize", summary="参数优化")
async def optimize_strategy(request: OptimizeRequest):
    """
    对指定策略进行参数优化（网格搜索）。
    - 通道策略：搜索最优 k 值
    - 趋势策略：搜索最优 MA 组合
    """
    try:
        df = load_daily_data(request.symbol)
        if df is None or df.empty:
            raise HTTPException(status_code=404, detail=f"未找到股票 {request.symbol} 的日线数据")

        result = optimize_params(df, request.strategy_type)

        # Clean up non-serializable data
        if result.get("best"):
            result["best"].pop("portfolio_values", None)
            result["best"].pop("trades", None)

        response_data = {
            "code": request.symbol,
            "name": request.symbol,
            "strategy_type": request.strategy_type,
            "best_params": result["best"]["params"] if result.get("best") else None,
            "best_sharpe": result["best"]["sharpe"] if result.get("best") else None,
            "best_return": result["best"]["total_return"] if result.get("best") else None,
            "all_results": result["all_results"],
        }
        return {"code": 200, "message": "优化完成", "data": response_data}
    except HTTPException:
        raise
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"参数优化失败: {str(e)}")


@router.post("/evaluate", summary="策略评估")
async def evaluate_strategy(request: OptimizeRequest):
    """
    对策略进行多维度绩效评估。
    """
    try:
        df = load_daily_data(request.symbol)
        if df is None or df.empty:
            raise HTTPException(status_code=404, detail=f"未找到股票 {request.symbol} 的日线数据")

        # Find matching strategy default params
        params = None
        for s in STRATEGY_LIST:
            if s["id"] == request.strategy_type:
                params = s["default_params"]
                break
        if not params:
            params = {"type": request.strategy_type}

        analyzer = PerformanceAnalyzer()
        evaluation = analyzer.quick_evaluate(df, params)

        response_data = {
            "code": request.symbol,
            "name": request.symbol,
            "strategy_type": request.strategy_type,
            "evaluation": evaluation,
        }
        return {"code": 200, "message": "评估完成", "data": response_data}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"策略评估失败: {str(e)}")
