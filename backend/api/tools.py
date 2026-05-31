"""
API Routes - Tools endpoints (data download).
"""
from fastapi import APIRouter, HTTPException
from models import DownloadRequest
from core.downloader import download_daily_data, check_existing_data

router = APIRouter(prefix="/api/tools", tags=["工具"])


@router.post("/download", summary="下载日线数据")
async def download_data(request: DownloadRequest):
    """
    下载股票日线数据。
    - 支持新浪财经（主）和搜狐财经（备）数据源
    - 自动保存为CSV格式
    """
    try:
        result = download_daily_data(
            symbol=request.symbol,
            years=request.years,
            source=request.source,
        )
        if result["success"]:
            return {"success": True, "data": result}
        else:
            raise HTTPException(status_code=400, detail=result["message"])
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"下载失败: {str(e)}")


@router.get("/download/status/{symbol}", summary="检查数据状态")
async def check_data_status(symbol: str):
    """
    检查指定股票的数据状态。
    """
    try:
        result = check_existing_data(symbol)
        return {"success": True, "data": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
