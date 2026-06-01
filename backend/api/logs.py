"""
每日日志 API
"""
from fastapi import APIRouter, HTTPException, Query
from typing import Optional
from core.daily_logger import append_log, read_log, list_logs, delete_log

router = APIRouter(prefix="/api/logs", tags=["每日日志"])


@router.get("/list", summary="列出所有日志")
async def list_all_logs():
    """列出所有日志文件名"""
    try:
        logs = list_logs()
        return {"code": 200, "message": "获取成功", "data": logs}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取日志列表失败: {str(e)}")


@router.get("/{date}", summary="读取指定日期日志")
async def get_log(date: str):
    """
    读取指定日期的工作日志。
    date 格式: YYYY-MM-DD
    """
    try:
        content = read_log(date)
        return {"code": 200, "message": "获取成功", "data": content}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"读取日志失败: {str(e)}")


@router.post("/{date}", summary="追加日志")
async def add_log(date: str, content: str):
    """
    追加内容到指定日期的日志。
    date 格式: YYYY-MM-DD
    """
    try:
        append_log(content, date)
        return {"code": 200, "message": "追加成功", "data": None}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"追加日志失败: {str(e)}")


@router.delete("/{date}", summary="删除日志")
async def remove_log(date: str):
    """
    删除指定日期的日志。
    date 格式: YYYY-MM-DD
    """
    try:
        success = delete_log(date)
        if success:
            return {"code": 200, "message": "删除成功", "data": None}
        else:
            return {"code": 404, "message": "日志不存在", "data": None}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"删除日志失败: {str(e)}")
