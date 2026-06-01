"""
工具箱 API - 列出和运行 Tools/ 目录下的工具
"""
import os
import glob
import importlib.util
import subprocess
import sys
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter(prefix="/api/tools", tags=["工具箱"])

TOOLS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "Tools")


class ToolRunRequest(BaseModel):
    """工具运行请求"""
    tool_name: str
    args: list[str] = []


@router.get("/list", summary="列出所有工具")
async def list_tools():
    """
    列出 Tools/ 目录下所有可用工具。
    扫描包含 run_tool() 函数的 .py 文件。
    """
    try:
        tools = []
        if os.path.exists(TOOLS_DIR):
            for py_file in glob.glob(os.path.join(TOOLS_DIR, "*.py")):
                name = os.path.basename(py_file)[:-3]
                if name.startswith("__"):
                    continue
                # 检查是否有 run_tool 函数
                try:
                    spec = importlib.util.spec_from_file_location(name, py_file)
                    mod = importlib.util.module_from_spec(spec)
                    spec.loader.exec_module(mod)
                    if hasattr(mod, "run_tool"):
                        tools.append({
                            "name": name,
                            "path": py_file,
                            "description": getattr(mod, "__doc__", "") or "",
                        })
                except Exception:
                    pass
        return {"code": 200, "message": "获取成功", "data": tools}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取工具列表失败: {str(e)}")


@router.post("/run", summary="运行工具")
async def run_tool(request: ToolRunRequest):
    """
    运行指定工具。
    通过子进程运行 Tools/ 目录下的脚本。
    """
    try:
        tool_path = os.path.join(TOOLS_DIR, f"{request.tool_name}.py")
        if not os.path.exists(tool_path):
            raise HTTPException(status_code=404, detail=f"工具不存在: {request.tool_name}")

        # 运行工具脚本
        cmd = [sys.executable, tool_path] + request.args
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=300,  # 5分钟超时
            cwd=os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
        )

        return {
            "code": 200 if result.returncode == 0 else 500,
            "message": "执行成功" if result.returncode == 0 else "执行失败",
            "data": {
                "stdout": result.stdout,
                "stderr": result.stderr,
                "returncode": result.returncode,
            },
        }
    except subprocess.TimeoutExpired:
        raise HTTPException(status_code=408, detail="工具执行超时")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"运行工具失败: {str(e)}")
