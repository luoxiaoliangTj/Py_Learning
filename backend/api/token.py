"""
Token management API - 让用户在 APP 端输入 TUSHARE_TOKEN。

流程：
1. APP 启动时 GET /api/token/status 检查是否有 token
2. 没有 token → 弹出输入框让用户填
3. 用户输入后 POST /api/token 保存
4. 没有 token 也能用，后端 fallback 到内置数据源
"""
from fastapi import APIRouter
from pydantic import BaseModel
from core.config import get_tushare_token, save_tushare_token

router = APIRouter(prefix="/api/token", tags=["token"])


class TokenRequest(BaseModel):
    token: str


@router.get("/status")
async def token_status():
    token = get_tushare_token()
    return {
        "code": 200,
        "message": "ok",
        "data": {
            "has_token": bool(token),
            "source": "env" if token else "none",
        },
    }


@router.post("/")
async def set_token(req: TokenRequest):
    success = save_tushare_token(req.token)
    if success:
        return {"code": 200, "message": "token 已保存", "data": None}
    return {"code": 500, "message": "保存失败", "data": None}


@router.delete("/")
async def clear_token():
    success = save_tushare_token("")
    if success:
        return {"code": 200, "message": "token 已清除", "data": None}
    return {"code": 500, "message": "清除失败", "data": None}
