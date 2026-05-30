"""
daily_logger.py - 每日工作日志记录器
仅记录关键操作结果，不记录过程细节
"""
import os
from datetime import datetime
from config.logging_config import get_logger

logger = get_logger("DailyLogger")

def _log_path(data_dir):
    today = datetime.now().strftime("%Y-%m-%d")
    log_dir = os.path.join(data_dir, "daily_logs")
    os.makedirs(log_dir, exist_ok=True)
    return os.path.join(log_dir, f"work_log_{today}.md")

def append(data_dir, content):
    """追加内容到当日日志文件"""
    path = _log_path(data_dir)
    try:
        with open(path, 'a', encoding='utf-8') as f:
            f.write(content + "\n\n")
        logger.info(f"📝 已记录工作日志: {path}")
    except Exception as e:
        logger.error(f"写入工作日志失败: {e}")

def _build_plugin_table(plugin_results):
    """将插件评估结果转为Markdown表格"""
    if not plugin_results:
        return ""
    
    sorted_items = sorted(
        plugin_results.items(),
        key=lambda x: x[1].get('evaluation', {}).get('composite_score', 0),
        reverse=True
    )

    lines = ["| 策略 | k/参数 | 夏普比率 | 年化收益 | 交易次数 | 最大回撤 | 综合评分 |"]
    lines.append("|:----|:-----:|:-------:|:-------:|:-------:|:-------:|:-------:|")

    for pname, r in sorted_items:
        ev = r.get('evaluation', {})
        p = r.get('params', {})
        if p.get('type') == 'trend':
            k_desc = f"MA{p.get('fast_ma','?')}/{p.get('slow_ma','?')}"
        else:
            k_desc = f"k={p.get('k', '?')}"
        sharpe = f"{r.get('sharpe', 0):.2f}"
        ret = f"{r.get('total_return', 0)*100:.1f}%"
        trades = str(r.get('n_trades', 0))
        dd = f"{r.get('max_drawdown', 0):.1f}%" if isinstance(r.get('max_drawdown'), (int, float)) and not isinstance(r.get('max_drawdown'), bool) else "0.0%"
        score = f"{ev.get('composite_score', 0):.3f}"
        lines.append(f"| {pname} | {k_desc} | {sharpe} | {ret} | {trades} | {dd} | {score} |")
    
    return "\n".join(lines)

def _build_trade_table(trades, symbol, data_dir):
    """将交易记录转为Markdown表格"""
    if not trades:
        return "_本轮回测无交易记录_"
    
    trades_sorted = sorted(trades, key=lambda x: str(x.get('date', '')))
    lines = ["| 序号 | 日期 | 类型 | 价格 | 股数 |"]
    lines.append("|:----|:-----|:-----:|------:|-----:|")
    
    for i, trade in enumerate(trades_sorted, 1):
        t_date = trade.get('date', '')
        t_type = trade.get('type', '')
        t_price = trade.get('price', 0)
        t_shares = trade.get('shares', trade.get('quantity', 0))
        lines.append(f"| {i} | {t_date} | {t_type} | {t_price:.2f} | {t_shares} |")
    
    symbol_trade_path = os.path.join(data_dir, f"{symbol}_trades.csv")
    lines.append(f"\n📁 交易记录保存于: {symbol_trade_path}")
    return "\n".join(lines)

def log_backtest(data_dir, symbol, stock_name, plugin_name, results, plugin_results=None, params=None):
    """记录回测结果到当日日志 — 统一表格 + 结论 + 交易记录"""
    ts = datetime.now().strftime("%H:%M")

    final_params = params if params else {}
    final_type = final_params.get('type', 'channel')

    entry_parts = [f"### [{ts}] 回测 - {symbol} {stock_name}\n"]

    # ====== 统一表格（含最终选用的策略） ======
    if plugin_results:
        display_results = dict(plugin_results)
        star_label = f"⭐ {plugin_name}"
        display_results[star_label] = {
            'params': final_params,
            'sharpe': results.get('sharpe', 0),
            'total_return': results.get('strategy_return', 0) / 100 if abs(results.get('strategy_return', 0)) > 1 else results.get('strategy_return', 0),
            'n_trades': results.get('total_trades', 0),
            'max_drawdown': results.get('max_drawdown', 0),
            'evaluation': {'composite_score': 0}
        }

        entry_parts.append("**策略评估结果（按综合评分排序，⭐=最终选用）：**\n")
        entry_parts.append(_build_plugin_table(display_results))
        entry_parts.append("")

    # ====== 结论 ======
    if plugin_results:
        sorted_items = sorted(
            plugin_results.items(),
            key=lambda x: x[1].get('evaluation', {}).get('composite_score', 0),
            reverse=True
        )
        best_name = sorted_items[0][0]
        best_data = sorted_items[0][1]
        best_params = best_data.get('params', {})
        best_type = best_params.get('type', 'channel')
        best_sharpe = best_data.get('sharpe', 0)
        best_return = best_data.get('total_return', 0) * 100
    else:
        best_name = plugin_name
        best_type = final_type
        best_params = final_params
        best_sharpe = results.get('sharpe', 0)
        best_return = results.get('strategy_return', 0)

    entry_parts.append("**📋 结论与参数说明**\n")

    if best_type == 'trend':
        fast = best_params.get('fast_ma', '?')
        slow = best_params.get('slow_ma', '?')
        entry_parts.append(f"**推荐策略：** 趋势跟随（MA交叉）  |  **参数：** 快线MA{fast} / 慢线MA{slow}")
        entry_parts.append(f"**表现：** 夏普比率 {best_sharpe:.2f}，年化收益 {best_return:.1f}%\n")
        entry_parts.append("**参数含义：**")
        entry_parts.append(f"- `MA{fast}`（快线）= 过去{fast}个交易日的平均收盘价，反应短期趋势")
        entry_parts.append(f"- `MA{slow}`（慢线）= 过去{slow}个交易日的平均收盘价，反应长期趋势")
        entry_parts.append("")
        entry_parts.append("**买卖规则：**")
        entry_parts.append(f"- 🟢 **金叉买入**：MA{fast} 上穿 MA{slow} → 短期趋势强于长期 → **买入持有**")
        entry_parts.append(f"- 🔴 **死叉卖出**：MA{fast} 下穿 MA{slow} → 短期趋势弱于长期 → **卖出离场**")
        entry_parts.append(f"- 持仓期间不受短期波动干扰，适合趋势明显的品种（如ETF、指数）")
        entry_parts.append("")
        entry_parts.append("**适用场景：** 单边上涨/下跌行情，震荡市会频繁假信号")
    else:
        k_val = best_params.get('k', '?')
        entry_parts.append(f"**推荐策略：** 自适应通道突破  |  **参数：** k={k_val}")
        entry_parts.append(f"**表现：** 夏普比率 {best_sharpe:.2f}，年化收益 {best_return:.1f}%\n")
        entry_parts.append("**参数含义：**")
        entry_parts.append(f"- `k`（通道宽度系数）= {k_val}，控制通道的宽窄")
        entry_parts.append(f"- `ATR20` = 过去20日的平均真实波幅，衡量股票的日常波动大小")
        entry_parts.append(f"- 通道宽度 = k × ATR20，k越大通道越宽，信号越少但越可靠")
        entry_parts.append("")
        entry_parts.append("**买卖规则：**")
        entry_parts.append(f"- 上轨 = 收盘价 + ({k_val}×ATR20)÷2")
        entry_parts.append(f"- 下轨 = 收盘价 - ({k_val}×ATR20)÷2")
        entry_parts.append(f"- 🟢 **买入**：当日最高价突破上轨 → 多头强势 → **买入**")
        entry_parts.append(f"- 🔴 **卖出**：当日最低价跌破下轨 → 空头强势 → **卖出**")
        entry_parts.append(f"- 通道宽度随ATR20动态调整：波动大时自动加宽，波动小时自动收窄")
        entry_parts.append("")
        entry_parts.append("**适用场景：** 震荡行情，有趋势但回调明显的个股")

    # 交易成本说明
    entry_parts.append("")
    entry_parts.append("**交易成本已计入：** 佣金万2.5 + 印花税万10(卖出) + 滑点万5")

    # ====== 📋 模拟交易记录 ======
    trades = results.get('trades', [])
    entry_parts.append("\n**📋 模拟交易记录：**")
    entry_parts.append(_build_trade_table(trades, symbol, data_dir))

    append(data_dir, "\n".join(entry_parts))
