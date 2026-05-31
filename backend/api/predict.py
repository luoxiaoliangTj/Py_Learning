"""
API Routes - Prediction endpoints.
日线预测与在线预测接口，响应字段对齐 Android 端 PredictionResult / OnlinePredictionResult。
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

    响应字段对齐 Android PredictionResult:
      code, name, current_price, predicted_high, predicted_low, predicted_close,
      confidence, strategies, timestamp
    """
    try:
        result = predict_daily(request.symbol)
        if "error" in result:
            raise HTTPException(status_code=400, detail=result["error"])

        # 对齐 Android PredictionResult 字段命名
        response_data = {
            "code": result["symbol"],
            "name": result["symbol"],  # 后端暂无股票名称映射，使用代码代替
            "current_price": result["current_price"],
            "predicted_high": result["pred_high"],
            "predicted_low": result["pred_low"],
            "predicted_close": result["current_price"],  # 预测收盘价使用当前价作为基准
            "confidence": result["confidence"],
            "strategies": [],  # 暂无策略信号列表，返回空数组
            "timestamp": result["generated_at"],
        }
        return {"code": 200, "message": "预测成功", "data": response_data}
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

    响应字段对齐 Android OnlinePredictionResult:
      code, name, current_price, predicted_price, confidence, signals, update_time
    """
    try:
        result = predict_realtime(request.symbol, request.prev_close)
        if "error" in result:
            raise HTTPException(status_code=400, detail=result["error"])

        # 对齐 Android OnlinePredictionResult 字段命名
        pred_range = result["prediction_range"]
        predicted_price = (pred_range["low"] + pred_range["high"]) / 2.0

        # 将推荐信号转换为 StrategySignal 列表
        signals = [
            {
                "name": result.get("strategy", "LMSR"),
                "signal": _map_recommendation(result["recommendation"]),
                "weight": result["confidence"],
                "value": predicted_price,
            }
        ]

        response_data = {
            "code": result["symbol"],
            "name": result["symbol"],
            "current_price": result["current_price"],
            "predicted_price": round(predicted_price, 2),
            "confidence": result["confidence"],
            "signals": signals,
            "update_time": result["generated_at"],
        }
        return {"code": 200, "message": "实时预测成功", "data": response_data}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"实时预测失败: {str(e)}")


def _map_recommendation(rec: str) -> str:
    """将后端推荐信号映射为 Android 端的 BUY/SELL/HOLD。"""
    mapping = {
        "buy": "BUY",
        "hold_buy": "BUY",
        "sell": "SELL",
        "hold_sell": "SELL",
        "hold": "HOLD",
    }
    return mapping.get(rec, "HOLD")
