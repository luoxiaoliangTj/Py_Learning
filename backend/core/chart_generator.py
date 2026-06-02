"""
回测图表生成 - 多图表类型支持
对齐原始代码: scripts/backtest_modules/chart_generator.py -> ChartGenerator
"""
import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import pandas as pd
import numpy as np
from datetime import datetime
from core.config import BACKTEST_DIR


def generate_backtest_chart(symbol: str, backtest_result: dict) -> dict:
    """
    生成回测图表。
    对齐原始代码: scripts/backtest_modules/chart_generator.py -> ChartGenerator.generate_all_charts

    生成三种图表:
    1. 权益曲线图 (equity_curve) - 含回撤分析
    2. 交易点图 (trade_points) - 含买卖T标记和交易频率
    3. 性能对比图 (performance_comparison) - 累计收益和收益分布

    Args:
        symbol: 股票代码
        backtest_result: 回测结果字典，需包含:
            - portfolio_values: list[float] 权益曲线值列表
            - trades: list[dict] 交易记录列表
            - df: pd.DataFrame 日线数据
            - sharpe: float 夏普比率
            - total_return: float 总收益率
            - max_drawdown: float 最大回撤
            - n_trades: int 交易次数
            - params: dict 策略参数

    Returns:
        dict: {chart_type: file_path, ...}
    """
    try:
        portfolio_values = backtest_result.get("portfolio_values", [])
        trades = backtest_result.get("trades", [])
        df = backtest_result.get("df")
        params = backtest_result.get("params", {})
        strategy_type = params.get("type", "channel")

        results_dir = str(BACKTEST_DIR)
        charts_dir = os.path.join(results_dir, "charts")
        os.makedirs(charts_dir, exist_ok=True)

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        chart_paths = {}

        # Build portfolio curve
        date_col = "日期" if df is not None and "日期" in df.columns else None
        if date_col and df is not None:
            curve_index = pd.to_datetime(df[date_col].iloc[:len(portfolio_values)])
        else:
            curve_index = pd.RangeIndex(len(portfolio_values))
        portfolio_curve = pd.Series(portfolio_values, index=curve_index)

        # ---- Chart 1: Equity Curve + Drawdown ----
        try:
            fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))

            ax1.plot(portfolio_curve.index, portfolio_curve.values,
                     label='Strategy Equity', color='blue', linewidth=2)

            # Benchmark (buy & hold)
            initial_cash = portfolio_curve.iloc[0] if len(portfolio_curve) > 0 else 0
            if df is not None and "收盘" in df.columns and len(df) > 20:
                price_start = df["收盘"].iloc[20]
                end_idx = min(len(df) - 1, len(portfolio_curve) - 1)
                benchmark_prices = df["收盘"].iloc[20:end_idx + 1]
                if date_col:
                    bench_index = pd.to_datetime(df[date_col].iloc[20:end_idx + 1])
                else:
                    bench_index = benchmark_prices.index
                benchmark_curve = (benchmark_prices.values / price_start * initial_cash)
                ax1.plot(bench_index, benchmark_curve,
                         label='Buy & Hold', color='red', linewidth=2, alpha=0.7)

            algo_name = strategy_type
            ax1.set_title(f'{symbol} - Equity Curve\nAlgorithm: {algo_name}',
                          fontsize=14, fontweight='bold')
            ax1.set_ylabel('Portfolio Value', fontsize=12)
            ax1.legend()
            ax1.grid(True, alpha=0.3)
            ax1.xaxis.set_major_locator(plt.MaxNLocator(8))

            # Metrics text box
            sharpe = backtest_result.get("sharpe", 0)
            total_return = backtest_result.get("total_return", 0)
            max_dd = backtest_result.get("max_drawdown", 0)
            n_trades = backtest_result.get("n_trades", 0)
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
            if trades and df is not None:
                fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))

                price_col = "收盘"
                if date_col and price_col in df.columns:
                    offset = min(20, len(df) - 1)
                    price_dates = pd.to_datetime(df[date_col].iloc[offset:])
                    prices = df[price_col].iloc[offset:]
                    max_len = min(len(price_dates), len(portfolio_curve))
                    price_dates = price_dates.iloc[:max_len]
                    prices = prices.iloc[:max_len]
                else:
                    prices = df[price_col] if price_col in df.columns else pd.Series()
                    price_dates = prices.index

                ax1.plot(price_dates, prices.values, label='Price',
                         color='black', linewidth=1.5)

                buy_dates, buy_prices, buy_details = [], [], []
                sell_dates, sell_prices, sell_details = [], [], []
                t_dates, t_prices, t_details = [], [], []

                for trade in trades:
                    trade_type = trade.get("type", "")
                    shares = trade.get("shares", 0)
                    price = trade.get("price", 0)
                    trade_date = trade.get("date")
                    if trade_date:
                        try:
                            t_date = pd.to_datetime(trade_date)
                        except Exception:
                            t_date = price_dates.iloc[-1] if len(price_dates) > 0 else None
                    else:
                        t_date = price_dates.iloc[-1] if len(price_dates) > 0 else None

                    if t_date:
                        if trade_type in ("buy", "开仓买入"):
                            buy_dates.append(t_date)
                            buy_prices.append(price)
                            buy_details.append(f"B:{shares}@{price:.2f}")
                        elif trade_type == "sell":
                            sell_dates.append(t_date)
                            sell_prices.append(price)
                            sell_details.append(f"S:{shares}@{price:.2f}")
                        elif trade_type == "T":
                            t_dates.append(t_date)
                            t_prices.append(price)
                            t_details.append(f"T:{shares}@{price:.2f}")

                if buy_dates:
                    ax1.scatter(buy_dates, buy_prices, color='green', marker='^',
                                s=120, label='Buy (B)', zorder=5,
                                edgecolors='darkgreen', linewidth=1.5)
                    for date, price_val, detail in zip(buy_dates, buy_prices, buy_details):
                        ax1.annotate(detail, (date, price_val), textcoords="offset points",
                                     xytext=(0, 15), ha='center', fontweight='bold',
                                     color='green', fontsize=8,
                                     bbox=dict(boxstyle="round,pad=0.3",
                                               facecolor="lightgreen", alpha=0.7))

                if sell_dates:
                    ax1.scatter(sell_dates, sell_prices, color='red', marker='v',
                                s=120, label='Sell (S)', zorder=5,
                                edgecolors='darkred', linewidth=1.5)
                    for date, price_val, detail in zip(sell_dates, sell_prices, sell_details):
                        ax1.annotate(detail, (date, price_val), textcoords="offset points",
                                     xytext=(0, -20), ha='center', fontweight='bold',
                                     color='red', fontsize=8,
                                     bbox=dict(boxstyle="round,pad=0.3",
                                               facecolor="lightcoral", alpha=0.7))

                if t_dates:
                    ax1.scatter(t_dates, t_prices, color='blue', marker='o',
                                s=100, label='Trading (T)', zorder=5, alpha=0.8,
                                edgecolors='darkblue', linewidth=1.5)
                    for date, price_val, detail in zip(t_dates, t_prices, t_details):
                        ax1.annotate(detail, (date, price_val), textcoords="offset points",
                                     xytext=(15, 0), ha='left', fontweight='bold',
                                     color='blue', fontsize=8,
                                     bbox=dict(boxstyle="round,pad=0.3",
                                               facecolor="lightblue", alpha=0.7))

                ax1.set_title(f'{symbol} - Trade Points\nB=Buy, S=Sell, T=Trading',
                              fontsize=14, fontweight='bold')
                ax1.set_ylabel('Price', fontsize=12)
                ax1.legend()
                ax1.grid(True, alpha=0.3)
                ax1.xaxis.set_major_locator(plt.MaxNLocator(8))

                # Trade frequency bar chart
                trades_df = pd.DataFrame(trades)
                if not trades_df.empty:
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

            strategy_returns = portfolio_curve.pct_change().dropna()
            cum_strategy = (1 + strategy_returns).cumprod() - 1

            bench_returns = None
            if df is not None and "收盘" in df.columns and len(df) > 20:
                offset = min(20, len(df) - 1)
                end_idx = min(len(df) - 1, len(portfolio_curve) - 1)
                benchmark_prices = df["收盘"].iloc[offset:end_idx + 1]
                bench_returns = benchmark_prices.pct_change().dropna()
                cum_benchmark = (1 + bench_returns).cumprod() - 1

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

    except Exception as e:
        return {"error": str(e)}
