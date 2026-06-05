# [file name]: scripts/backtest_modules/strategy_engine.py
#!/usr/bin/env python3
"""
strategy_engine.py - 策略执行引擎（LMSR评估集成版）
保持原有架构，只添加LMSR评估维度
"""

import pandas as pd
import numpy as np
from config.logging_config import get_logger
from scripts.backtest_modules.metrics import MetricsCalculator
from scripts.backtest_modules.data_driven_evaluator import DataDrivenEvaluator
from scripts.backtest_modules.lmsr_evaluator import LMSEvaluator  # LMSR评估器作为附加维度

logger = get_logger("StrategyEngine")

# 交易成本参数
COMMISSION_RATE = 0.00025    # 佣金万2.5
STAMP_TAX_RATE = 0.001       # 印花税万10（卖出时收）
SLIPPAGE_RATE = 0.0005       # 滑点万5


class StrategyEngine:
    def __init__(self):
        self.slip = 0.000  # 滑点
        self.metrics_calc = MetricsCalculator()
        self.evaluator = DataDrivenEvaluator()
        # LMSR评估器作为策略的附加评估维度（非独立策略）
        self.lmsr_evaluator = LMSEvaluator()

    def atr(self, df: pd.DataFrame, n: int = 20) -> pd.Series:
        """计算 ATR"""
        df = df.copy()
        df['h-l'] = df['最高'] - df['最低']
        df['h-pc'] = np.abs(df['最高'] - df['收盘'].shift(1))
        df['l-pc'] = np.abs(df['最低'] - df['收盘'].shift(1))
        df['tr'] = df[['h-l', 'h-pc', 'l-pc']].max(axis=1)
        return df['tr'].rolling(n).mean()

    def quick_score(self, df: pd.DataFrame, params: dict, symbol: str = None):
        """快速评分 - 返回完整评估结果（支持通道/趋势策略模式 + 交易成本）"""
        # 计算市场基准
        baseline = self.evaluator.calculate_market_baseline(df, symbol or "unknown")

        # [修改] 使用全量数据，不再限制最近2年
        sub = df.copy()
        if sub.empty or len(sub) < 50:
            raise ValueError(
                f"数据不足：当前交易数据仅{len(sub)}条，不足以进行策略评分。\n"
                f"请下载至少1年的完整交易记录（约250个交易日）后重试。")

        strategy_type = params.get('type', 'channel')
        cash, shares, trades = 100_000, 0, []
        portfolio_values = [100_000]

        if strategy_type == 'trend':
            # ========== 趋势跟随模式（MA交叉，适合ETF） ==========
            fast = int(params.get('fast_ma', 10))
            slow = int(params.get('slow_ma', 30))
            if fast >= slow or fast < 2:
                raise ValueError(f"无效MA参数: fast={fast}, slow={slow}")
            sub['ma_fast'] = sub['收盘'].rolling(fast).mean()
            sub['ma_slow'] = sub['收盘'].rolling(slow).mean()

            for i in range(slow, len(sub)):
                row = sub.iloc[i]
                close = row['收盘']
                prev_fast = sub['ma_fast'].iloc[i - 1]
                prev_slow = sub['ma_slow'].iloc[i - 1]
                cur_fast = sub['ma_fast'].iloc[i]
                cur_slow = sub['ma_slow'].iloc[i]

                # 金叉买入
                if prev_fast <= prev_slow and cur_fast > cur_slow and shares == 0:
                    buy_price = close * 1.01
                    buy_cost = COMMISSION_RATE + SLIPPAGE_RATE
                    shares = int(cash / (buy_price * (1 + buy_cost)))
                    if shares > 0:
                        cost = shares * buy_price * (1 + buy_cost)
                        cash -= cost
                        trades.append({'type': 'buy', 'price': buy_price, 'shares': shares})

                # 死叉卖出
                elif prev_fast >= prev_slow and cur_fast < cur_slow and shares > 0:
                    sell_price = close * 0.99
                    sell_cost = COMMISSION_RATE + STAMP_TAX_RATE + SLIPPAGE_RATE
                    revenue = shares * sell_price * (1 - sell_cost)
                    cash += revenue
                    trades.append({'type': 'sell', 'price': sell_price, 'shares': shares})
                    shares = 0

                daily_value = cash + shares * close
                portfolio_values.append(daily_value)

        else:
            # ========== 通道策略模式（默认） ==========
            sub['atr20'] = self.atr(sub, 20)

            for i in range(20, len(sub)):
                row = sub.iloc[i]
                k_value = params.get('k', 2.0)
                if k_value is None:
                    k_value = 2.0
                width = params.get('width', k_value * row['atr20'])
                if pd.isna(width) or width <= 0:
                    width = 0.02 * row['收盘']

                close = row['收盘']
                ph, pl = close + width / 2, close - width / 2

                # 交易逻辑（含交易成本）
                if row['最高'] > ph and shares == 0:
                    buy_cost = COMMISSION_RATE + SLIPPAGE_RATE
                    shares = int(cash / (ph * (1 + buy_cost + 0.01)))  # 1%安全边际
                    if shares > 0:
                        cash -= shares * ph * (1 + buy_cost + self.slip)
                        trades.append({'type': 'buy', 'price': ph, 'shares': shares})
                elif row['最低'] < pl and shares > 0:
                    sell_cost = COMMISSION_RATE + STAMP_TAX_RATE + SLIPPAGE_RATE
                    cash += shares * pl * (1 - sell_cost - self.slip)
                    trades.append({'type': 'sell', 'price': pl, 'shares': shares})
                    shares = 0

                daily_value = cash + shares * close
                portfolio_values.append(daily_value)

        if shares > 0:
            cash += shares * sub.iloc[-1]['收盘']

        # 计算完整指标
        total_return = cash / 100_000 - 1
        sharpe = self.metrics_calc.calculate_sharpe(portfolio_values)
        max_drawdown = self.metrics_calc.calculate_max_drawdown(portfolio_values)
        n_trades = len([t for t in trades if t['type'] in ['buy', 'sell']])

        result = {
            'params': params,
            'sharpe': sharpe,
            'total_return': total_return,
            'max_drawdown': max_drawdown,
            'n_trades': n_trades,
            'portfolio_values': portfolio_values,
            'trades': trades,
            'final_cash': cash,
            'final_shares': shares
        }

        # 基于市场基准评估
        evaluation = self.evaluator.evaluate_plugin_performance(
            sharpe, n_trades, total_return, max_drawdown, baseline
        )
        result['evaluation'] = evaluation

        label = params.get('plugin', 'plugin')
        if strategy_type == 'trend':
            label += f"(MA{fast}/{slow})"
        else:
            label += f"(k={params.get('k', '?')})"
        logger.info(f"{label}: "
                   f"夏普={sharpe:.3f}, 收益={total_return:.3f}, "
                   f"交易={n_trades}, 综合评分={evaluation['composite_score']:.3f}")

        return result

    def quick_score_with_lmsr(self, df: pd.DataFrame, params: dict, symbol: str = None):
        """
        带LMSR评估的快速评分（新增方法）
        LMSR作为策略评估维度，不改变原有策略类型
        """
        # 原有的快速评分逻辑（完全保留）
        result = self.quick_score(df, params, symbol)

        # 新增LMSR评估维度（基于真实价格数据）
        try:
            # 从价格数据计算LMSR评估（禁止硬编码）
            lmsr_results = self.lmsr_evaluator.evaluate_strategy(
                symbol or "unknown",
                params.get('stock_name', '未知股票'),
                result,
                df['收盘'].values,
                result.get('trades', [])
            )

            # 将LMSR结果合并到原有结果中（平等地位）
            result.update(lmsr_results)

        except Exception as e:
            logger.warning(f"LMSR评估失败: {e}")
            # 不影响原有功能，设置默认值
            result.update({
                'lmsr_avg_probability': 0.5,
                'lmsr_confidence': 0.0,
                'lmsr_evaluation': "评估失败"
            })

        return result

    def execute_trade(self, current_price, advice, holdings, slip=0.000):
        """执行交易建议"""
        cash, shares = holdings['cash'], holdings['shares']
        trades = []

        for rec in advice.get('recommendations', []):
            if rec['type'] in ['止盈', '止损'] and shares > 0:
                rev = shares * current_price * (1 - slip)
                trades.append({
                    "type": "sell",
                    "price": current_price,
                    "shares": shares,
                    "reason": rec['type']
                })
                cash += rev
                shares = 0

            elif rec['type'] == '加仓' and cash > 0:
                buy_shares = int(cash * 0.5 / (current_price * 1.01))
                if buy_shares > 0:
                    cost = buy_shares * current_price * (1 + slip)
                    cash -= cost
                    shares += buy_shares
                    trades.append({
                        "type": "buy",
                        "price": current_price,
                        "shares": buy_shares,
                        "reason": "加仓"
                    })

            elif rec['type'] == '做T' and shares > 10:
                t_shares = int(shares * 0.2)
                buy_cost = t_shares * current_price * (1 + slip)
                sell_price = current_price * 1.01
                sell_rev = t_shares * sell_price * (1 - slip)
                net_profit = sell_rev - buy_cost

                if net_profit > 0:
                    cash += net_profit
                    trades.append({
                        "type": "T",
                        "price": current_price,
                        "shares": t_shares,
                        "profit": net_profit,
                        "reason": "做T"
                    })

        return {
            'cash': cash,
            'shares': shares,
            'trades': trades
        }