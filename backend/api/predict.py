"""
API Routes - Prediction endpoints.
"""
from fastapi import APIRouter, HTTPException
from models import PredictDailyRequest, PredictRealtimeRequest
from core.predictor import predict_daily, predict_realtime

router = APIRouter(prefix="/api/predict", tags=["预测"])


@router.post("/daily", summary="日线预测")
async def predict_daily_endpoint(request: PredictDailyRequest):
    """
    基于回测结果生成日线价格预测区间。
    - 读取历史日线数据
    - 分析回测性能指标
    - 生成预测区间和交易建议
    """
    try:
        result = predict_daily(request.symbol)
        if "error" in result:
            raise HTTPException(status_code=400, detail=result["error"])
        return {"success": True, "data": result}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"预测失败: {str(e)}")


@router.post("/realtime", summary="在线预测")
async def predict_realtime_endpoint(request: PredictRealtimeRequest):
    """
    实时价格预测（LMSR策略）。
    - 基于最新价格数据
    - 应用涨跌幅限制（±10%）
    - 生成交易建议: buy/sell/hold/hold_buy/hold_sell
    """
    try:
        result = predict_realtime(request.symbol, request.prev_close)
        if "error" in result:
            raise HTTPException(status_code=400, detail=result["error"])
        return {"success": True, "data": result}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"实时预测失败: {str(e)}")
