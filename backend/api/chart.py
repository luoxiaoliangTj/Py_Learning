"""
API Routes - Chart generation endpoint.
回测图表生成接口，执行回测后生成权益曲线、交易点、对比图。
"""
import os
import glob
import pandas as pd
from datetime import datetime
from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse
from models import ChartRequest
from core.data_loader import load_daily_data
from core.strategy_engine import quick_score
from core.config import BACKTEST_DIR

router = APIRouter(prefix="/api/chart", tags=["图表"])


def _check_matplotlib():
    """Check if matplotlib is available, return (available: bool, error_msg: str)."""
    try:
        import matplotlib  # noqa: F401
        return True, ""
    except ImportError:
        return False, (
            "matplotlib 未安装，无法生成图表。"
            "请运行: pip install matplotlib"
        )


def _generate_charts(symbol: str, df: pd.DataFrame, result: dict) -> dict:
    """
    Generate backtest charts using matplotlib.
    Returns dict with chart file paths and metrics.
    """
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import numpy as np

    portfolio_values = result["portfolio_values"]
    trades = result["trades"]
    params = result["params"]

    # Build results directory
    results_dir = str(BACKTEST_DIR)
    charts_dir = os.path.join(results_dir, "charts")
    os.makedirs(charts_dir, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    chart_paths = {}

    # Build portfolio curve series
    date_col = "日期" if "日期" in df.columns else None
    if date_col:
        curve_index = pd.to_datetime(df[date_col].iloc[:len(portfolio_values)])
    else:
        curve_index = pd.RangeIndex(len(portfolio_values))
    portfolio_curve = pd.Series(portfolio_values, index=curve_index)

    # ---- Chart 1: Equity Curve + Drawdown ----
    try:
        fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))

        # Equity curve
        ax1.plot(portfolio_curve.index, portfolio_curve.values,
                 label='Strategy Equity', color='blue', linewidth=2)

        # Benchmark (buy & hold)
        initial_cash = portfolio_curve.iloc[0]
        price_col = "收盘"
        if price_col in df.columns:
            start_idx = min(20, len(df) - 1)
            price_start = df[price_col].iloc[start_idx]
            end_idx = min(len(df) - 1, len(portfolio_curve) - 1)
            benchmark_prices = df[price_col].iloc[start_idx:end_idx + 1]
            if date_col:
                bench_index = pd.to_datetime(df[date_col].iloc[start_idx:end_idx + 1])
            else:
                bench_index = benchmark_prices.index
            benchmark_curve = (benchmark_prices.values / price_start * initial_cash)
            ax1.plot(bench_index, benchmark_curve,
                     label='Buy & Hold', color='red', linewidth=2, alpha=0.7)

        strategy_type = params.get("type", "channel")
        algo_name = f"{strategy_type}"
        ax1.set_title(f'{symbol} - Equity Curve\nAlgorithm: {algo_name}',
                      fontsize=14, fontweight='bold')
        ax1.set_ylabel('Portfolio Value', fontsize=12)
        ax1.legend()
        ax1.grid(True, alpha=0.3)
        ax1.xaxis.set_major_locator(plt.MaxNLocator(8))

        # Metrics text box
        total_return = result["total_return"]
        sharpe = result["sharpe"]
        max_dd = result["max_drawdown"]
        n_trades = result["n_trades"]
        metrics_text = (
            f"Strategy Return: {total_return * 100:.2f}%\n"
            f"Sharpe Ratio: {sharpe:.2f}\n"
            f"Max Drawdown: {max_dd * 100:.2f}%\n"
            f"Total Trades: {n_trades}\n"
            f"Algorithm: {algo_name}"
        )
        ax1.text(0.02, 0.98, metrics_text, transform=ax1.transAxes,
                 verticalalignment='top',
                 bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.8),
                 fontfamily='monospace', fontsize=9)

        # Drawdown subplot
        rolling_max = portfolio_curve.expanding().max()
        drawdown = (portfolio_curve - rolling_max) / rolling_max * 100
        ax2.fill_between(drawdown.index, drawdown.values, 0,
                         color='red', alpha=0.3, label='Drawdown')
        ax2.plot(drawdown.index, drawdown.values, color='red', linewidth=1)
        ax2.axhline(y=0, color='black', linestyle='-', alpha=0.5)
        max_dd_val = drawdown.min()
        ax2.axhline(y=max_dd_val, color='darkred', linestyle='--',
                    label=f'Max DD: {max_dd_val:.2f}%')
        ax2.set_title('Drawdown Analysis', fontsize=12)
        ax2.set_xlabel('Date', fontsize=12)
        ax2.set_ylabel('Drawdown %', fontsize=12)
        ax2.legend()
        ax2.grid(True, alpha=0.3)
        ax2.xaxis.set_major_locator(plt.MaxNLocator(8))

        plt.tight_layout()
        equity_path = os.path.join(charts_dir, f"{symbol}_equity_curve_{timestamp}.png")
        fig.savefig(equity_path, dpi=150, bbox_inches='tight', facecolor='white')
        plt.close()
        chart_paths["equity_curve"] = equity_path
    except Exception as e:
        chart_paths["equity_curve_error"] = str(e)

    # ---- Chart 2: Trade Points ----
    try:
        if trades:
            fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))

            # Price curve
            price_col = "收盘"
            if date_col and price_col in df.columns:
                offset = min(20, len(df) - 1)
                price_dates = pd.to_datetime(df[date_col].iloc[offset:])
                prices = df[price_col].iloc[offset:]
                # Trim to match portfolio length
                max_len = min(len(price_dates), len(portfolio_curve))
                price_dates = price_dates.iloc[:max_len]
                prices = prices.iloc[:max_len]
            else:
                prices = df[price_col] if price_col in df.columns else pd.Series()
                price_dates = prices.index

            ax1.plot(price_dates, prices.values, label='Price',
                     color='black', linewidth=1.5)

            # Plot trade markers
            buy_dates, buy_prices = [], []
            sell_dates, sell_prices = [], []
            t_dates, t_prices = [], []

            for trade in trades:
                t_type = trade["type"]
                t_price = trade.get("price", 0)
                # Find nearest date in df for this trade
                t_date = price_dates.iloc[-1] if len(price_dates) > 0 else None
                if t_date:
                    if t_type in ("buy", "开仓买入"):
                        buy_dates.append(t_date)
                        buy_prices.append(t_price)
                    elif t_type == "sell":
                        sell_dates.append(t_date)
                        sell_prices.append(t_price)
                    elif t_type == "T":
                        t_dates.append(t_date)
                        t_prices.append(t_price)

            if buy_dates:
                ax1.scatter(buy_dates, buy_prices, color='green', marker='^',
                            s=120, label='Buy', zorder=5,
                            edgecolors='darkgreen', linewidth=1.5)
            if sell_dates:
                ax1.scatter(sell_dates, sell_prices, color='red', marker='v',
                            s=120, label='Sell', zorder=5,
                            edgecolors='darkred', linewidth=1.5)
            if t_dates:
                ax1.scatter(t_dates, t_prices, color='blue', marker='o',
                            s=100, label='Trading', zorder=5, alpha=0.8,
                            edgecolors='darkblue', linewidth=1.5)

            ax1.set_title(f'{symbol} - Trade Points\nB=Buy, S=Sell, T=Trading',
                          fontsize=14, fontweight='bold')
            ax1.set_ylabel('Price', fontsize=12)
            ax1.legend()
            ax1.grid(True, alpha=0.3)
            ax1.xaxis.set_major_locator(plt.MaxNLocator(8))

            # Trade frequency bar chart
            trades_df = pd.DataFrame(trades)
            if not trades_df.empty and date_col:
                try:
                    trades_df["date"] = pd.to_datetime(trades_df.get("date", pd.NaT))
                    buy_counts = trades_df[trades_df["type"].isin(["buy", "开仓买入"])].groupby("date").size()
                    sell_counts = trades_df[trades_df["type"] == "sell"].groupby("date").size()
                    t_counts = trades_df[trades_df["type"] == "T"].groupby("date").size()
                    all_dates = sorted(set(buy_counts.index) | set(sell_counts.index) | set(t_counts.index))
                    b_vals = [buy_counts.get(d, 0) for d in all_dates]
                    s_vals = [sell_counts.get(d, 0) for d in all_dates]
                    t_vals = [t_counts.get(d, 0) for d in all_dates]

                    ax2.bar(all_dates, b_vals, color='green', alpha=0.7, label='Buy Trades')
                    ax2.bar(all_dates, s_vals, bottom=b_vals, color='red', alpha=0.7, label='Sell Trades')
                    ax2.bar(all_dates, t_vals, bottom=[i + j for i, j in zip(b_vals, s_vals)],
                            color='blue', alpha=0.7, label='Trading Trades')
                    ax2.set_title('Daily Trade Frequency by Type', fontsize=12)
                    ax2.set_xlabel('Date', fontsize=12)
                    ax2.set_ylabel('Number of Trades', fontsize=12)
                    ax2.legend()
                    ax2.grid(True, alpha=0.3)
                    ax2.xaxis.set_major_locator(plt.MaxNLocator(8))
                except Exception:
                    pass

            plt.tight_layout()
            trade_path = os.path.join(charts_dir, f"{symbol}_trade_points_{timestamp}.png")
            fig.savefig(trade_path, dpi=150, bbox_inches='tight', facecolor='white')
            plt.close()
            chart_paths["trade_points"] = trade_path
    except Exception as e:
        chart_paths["trade_points_error"] = str(e)

    # ---- Chart 3: Performance Comparison ----
    try:
        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))

        # Cumulative returns
        strategy_returns = portfolio_curve.pct_change().dropna()
        cum_strategy = (1 + strategy_returns).cumprod() - 1

        price_col = "收盘"
        bench_returns = None
        if price_col in df.columns:
            offset = min(20, len(df) - 1)
            end_idx = min(len(df) - 1, len(portfolio_curve) - 1)
            benchmark_prices = df[price_col].iloc[offset:end_idx + 1]
            bench_returns = benchmark_prices.pct_change().dropna()
            cum_benchmark = (1 + bench_returns).cumprod() - 1

            # Align indices for plotting
            min_len = min(len(cum_strategy), len(cum_benchmark))
            ax1.plot(cum_strategy.index[:min_len], cum_strategy.values[:min_len] * 100,
                     label='Strategy', color='blue', linewidth=2)
            ax1.plot(cum_benchmark.index[:min_len], cum_benchmark.values[:min_len] * 100,
                     label='Benchmark', color='red', linewidth=2, alpha=0.7)
        else:
            ax1.plot(cum_strategy.index, cum_strategy.values * 100,
                     label='Strategy', color='blue', linewidth=2)

        ax1.set_title(f'Cumulative Returns (%)\nAlgorithm: {strategy_type}',
                      fontsize=14, fontweight='bold')
        ax1.set_ylabel('Cumulative Return %', fontsize=12)
        ax1.legend()
        ax1.grid(True, alpha=0.3)
        ax1.xaxis.set_major_locator(plt.MaxNLocator(8))

        # Returns distribution
        ax2.hist(strategy_returns * 100, bins=50, alpha=0.7,
                 label='Strategy Returns', color='blue', density=True)
        if bench_returns is not None:
            ax2.hist(bench_returns * 100, bins=50, alpha=0.7,
                     label='Benchmark Returns', color='red', density=True)
            benchmark_mean = bench_returns.mean() * 100
            ax2.axvline(x=benchmark_mean, color='darkred', linestyle='--',
                        label=f'Benchmark Mean: {benchmark_mean:.2f}%')

        strategy_mean = strategy_returns.mean() * 100
        ax2.axvline(x=strategy_mean, color='darkblue', linestyle='--',
                    label=f'Strategy Mean: {strategy_mean:.2f}%')
        ax2.set_title('Return Distribution (%)', fontsize=14, fontweight='bold')
        ax2.set_xlabel('Daily Return %', fontsize=12)
        ax2.set_ylabel('Density', fontsize=12)
        ax2.legend()
        ax2.grid(True, alpha=0.3)

        plt.tight_layout()
        perf_path = os.path.join(charts_dir, f"{symbol}_performance_comparison_{timestamp}.png")
        fig.savefig(perf_path, dpi=150, bbox_inches='tight', facecolor='white')
        plt.close()
        chart_paths["performance_comparison"] = perf_path
    except Exception as e:
        chart_paths["performance_comparison_error"] = str(e)

    return chart_paths


@router.post("/generate", summary="生成回测图表")
async def generate_chart(request: ChartRequest):
    """
    执行回测并生成图表。
    - 支持通道策略（channel）和趋势策略（trend）
    - 生成三种图表：equity_curve, trade_points, performance_comparison
    - 图表保存到 backtest_results/charts/ 目录
    - 返回图表文件路径和回测指标
    """
    # Check matplotlib availability
    available, error_msg = _check_matplotlib()
    if not available:
        raise HTTPException(status_code=503, detail=error_msg)

    # Load data
    df = load_daily_data(request.symbol)
    if df is None or df.empty:
        raise HTTPException(
            status_code=404,
            detail=f"未找到股票 {request.symbol} 的日线数据"
        )

    # Run backtest
    try:
        params = request.params or {}
        params.setdefault("type", request.strategy_type)
        result = quick_score(df, params, request.symbol)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"回测执行失败: {str(e)}")

    # Generate charts
    try:
        chart_paths = _generate_charts(request.symbol, df, result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"图表生成失败: {str(e)}")

    # Build metrics
    metrics = {
        "sharpe_ratio": float(result["sharpe"]),
        "total_return": float(result["total_return"]),
        "max_drawdown": float(result["max_drawdown"]),
        "total_trades": int(result["n_trades"]),
        "final_cash": float(result["final_cash"]),
        "final_shares": int(result["final_shares"]),
        "strategy_type": request.strategy_type,
        "params": params,
    }

    return {
        "code": 200,
        "message": "生成成功",
        "data": {
            "chart_paths": chart_paths,
            "metrics": metrics,
        },
    }


@router.get("/{symbol}", summary="获取图表")
async def get_chart(symbol: str, chart_type: str = "all"):
    """
    获取已生成的回测图表。

    - chart_type: all | equity_curve | trade_points | performance_comparison
    - 返回图表文件列表或指定类型的图表文件
    """
    charts_dir = os.path.join(str(BACKTEST_DIR), "charts")
    if not os.path.isdir(charts_dir):
        return {"code": 404, "message": "图表目录不存在", "data": None}

    pattern = f"{symbol}_*.png"
    all_charts = sorted(glob.glob(os.path.join(charts_dir, pattern)))

    if not all_charts:
        return {
            "code": 404,
            "message": f"未找到股票 {symbol} 的图表",
            "data": None,
        }

    if chart_type == "all":
        # 返回所有图表文件路径列表
        chart_files = []
        for ch in all_charts:
            fname = os.path.basename(ch)
            # 解析图表类型
            parts = fname.replace(f"{symbol}_", "").replace(".png", "")
            # 移除时间戳后缀
            type_name = "_".join(parts.split("_")[:-2]) if "_" in parts else parts
            chart_files.append({
                "type": type_name,
                "filename": fname,
                "path": ch,
                "size": os.path.getsize(ch),
            })
        return {"code": 200, "message": "获取成功", "data": {"charts": chart_files}}

    # 按类型筛选
    type_map = {
        "equity_curve": f"{symbol}_equity_curve_",
        "trade_points": f"{symbol}_trade_points_",
        "performance_comparison": f"{symbol}_performance_comparison_",
    }

    prefix = type_map.get(chart_type)
    if not prefix:
        raise HTTPException(
            status_code=400,
            detail=f"不支持的图表类型: {chart_type}。支持: {list(type_map.keys())}",
        )

    matched = [ch for ch in all_charts if os.path.basename(ch).startswith(prefix)]
    if not matched:
        return {
            "code": 404,
            "message": f"未找到类型为 {chart_type} 的图表",
            "data": None,
        }

    # 返回最新的图表文件
    latest = max(matched, key=os.path.getmtime)
    return FileResponse(
        latest,
        media_type="image/png",
        filename=os.path.basename(latest),
    )
