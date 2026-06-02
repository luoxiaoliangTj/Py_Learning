"""
交易建议生成 - 持仓管理、动态止盈止损、交易建议
对齐原始代码: TradingAdvisorModule.py -> TradingAdvisorModule
"""
import json
import os
import numpy as np
import pandas as pd
from datetime import datetime
from core.config import DATA_DIR


# 持仓文件路径
HOLDINGS_FILE = DATA_DIR / "stock_holdings.json"
CAPITAL_FILE = DATA_DIR / "global_capital.json"

# 费率配置
BUY_COMMISSION_RATE = 0.000131    # 买入佣金率：万分之1.31
SELL_COMMISSION_RATE = 0.000131   # 卖出佣金率：万分之1.31
STAMP_DUTY_RATE = 0.0005          # 印花税率：0.05%（仅卖出）
OTHER_FEES_RATE = 0.00001         # 其他费用率：万分之0.1
MIN_COMMISSION = 5.00             # 最低佣金：5元


def _ensure_holdings_file():
    """确保持仓文件存在"""
    os.makedirs(DATA_DIR, exist_ok=True)
    if not HOLDINGS_FILE.exists():
        with open(HOLDINGS_FILE, 'w', encoding='utf-8') as f:
            json.dump({}, f, ensure_ascii=False, indent=2)


def _load_holdings() -> dict:
    """加载所有持仓"""
    _ensure_holdings_file()
    try:
        with open(HOLDINGS_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception:
        return {}


def _save_holdings(data: dict):
    """保存持仓"""
    _ensure_holdings_file()
    with open(HOLDINGS_FILE, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def _load_capital() -> dict:
    """加载全局资金"""
    try:
        if CAPITAL_FILE.exists():
            with open(CAPITAL_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
    except Exception:
        pass
    return {"available_cash": 100000.0, "total_capital": 100000.0}


def _save_capital(data: dict):
    """保存全局资金"""
    os.makedirs(DATA_DIR, exist_ok=True)
    with open(CAPITAL_FILE, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def _get_holding(symbol: str) -> dict:
    """获取单只股票持仓"""
    holdings = _load_holdings()
    return holdings.get(symbol, None)


def _calculate_buy_fees(amount: float) -> float:
    """计算买入费用"""
    commission = max(amount * BUY_COMMISSION_RATE, MIN_COMMISSION)
    other_fees = amount * OTHER_FEES_RATE
    return commission + other_fees


def _calculate_sell_fees(amount: float) -> float:
    """计算卖出费用"""
    commission = max(amount * SELL_COMMISSION_RATE, MIN_COMMISSION)
    stamp_duty = amount * STAMP_DUTY_RATE
    other_fees = amount * OTHER_FEES_RATE
    return commission + stamp_duty + other_fees


def _calculate_profit_loss(holding_info: dict, current_price: float) -> dict:
    """计算盈亏情况"""
    if holding_info is None:
        holding_info = {'shares': 0, 'cost_price': 0}

    shares = holding_info.get('shares', 0)
    cost_price = holding_info.get('cost_price', 0)

    if shares == 0:
        return {
            'gross_profit': 0, 'gross_profit_pct': 0,
            'net_profit': 0, 'net_profit_pct': 0,
            'current_value': 0, 'cost_value': 0,
            'sell_fees': 0, 'no_position': True
        }

    gross_profit = (current_price - cost_price) * shares
    gross_profit_pct = (current_price / cost_price - 1) * 100
    sell_amount = current_price * shares
    sell_fees = _calculate_sell_fees(sell_amount)
    net_profit = gross_profit - sell_fees
    net_profit_pct = (net_profit / (cost_price * shares)) * 100

    return {
        'gross_profit': round(gross_profit, 2),
        'gross_profit_pct': round(gross_profit_pct, 2),
        'net_profit': round(net_profit, 2),
        'net_profit_pct': round(net_profit_pct, 2),
        'current_value': round(sell_amount, 2),
        'cost_value': round(cost_price * shares, 2),
        'sell_fees': round(sell_fees, 2),
        'no_position': False
    }


def _analyze_trend_strength(stock_data) -> float:
    """分析趋势强度"""
    if stock_data is None or len(stock_data) < 20:
        return 0.0

    close_col = "收盘" if "收盘" in stock_data.columns else None
    if close_col is None:
        return 0.0

    closes = stock_data[close_col].values
    if len(closes) < 20:
        return 0.0

    short_ma = np.mean(closes[-5:])
    long_ma = np.mean(closes[-20:])
    trend_strength = (short_ma / long_ma - 1) * 100
    normalized_strength = max(min(trend_strength / 100, 0.03), -0.03)
    return normalized_strength


def _calculate_dynamic_thresholds(current_price, predicted_low, predicted_high,
                                  intraday_volatility, trend_strength) -> tuple:
    """计算动态止盈止损阈值"""
    base_profit_threshold = 0.03
    base_loss_threshold = 0.02

    vol_adjustment = intraday_volatility * 10
    profit_threshold = base_profit_threshold + vol_adjustment
    loss_threshold = base_loss_threshold + vol_adjustment

    if trend_strength > 0.01:
        profit_threshold += 0.02
        loss_threshold -= 0.01
    elif trend_strength < -0.01:
        profit_threshold -= 0.01
        loss_threshold += 0.02

    profit_threshold = max(profit_threshold, 0.015)
    loss_threshold = max(loss_threshold, 0.01)

    return profit_threshold, loss_threshold


def generate_advice(symbol: str, current_price: float, holding_info: dict = None) -> dict:
    """
    生成交易建议。
    对齐原始代码: TradingAdvisorModule.py -> TradingAdvisorModule.generate_trading_advice

    Args:
        symbol: 股票代码
        current_price: 当前价格
        holding_info: 持仓信息 {shares, cost_price, aggressive_factor, t_position_ratio}
                      如果 None，从文件加载

    Returns:
        dict: {
            "symbol": str,
            "current_price": float,
            "holding": dict,
            "profit_loss": dict,
            "thresholds": dict,
            "recommendations": list[str],
            "urgency": str,  # low / medium / high
            "timestamp": str,
            "has_position": bool,
        }
    """
    # 加载持仓信息
    if holding_info is None:
        holding_info = _get_holding(symbol)

    if holding_info is None:
        holding_info = {'shares': 0, 'cost_price': 0}

    aggressive_factor = holding_info.get('aggressive_factor', 0.5)
    t_position_ratio = holding_info.get('t_position_ratio', 0.0)

    # 计算盈亏
    pl_data = _calculate_profit_loss(holding_info, current_price)

    # 动态阈值
    shares = holding_info.get('shares', 0)
    cost_price = holding_info.get('cost_price', 0)

    # 使用默认预测参数
    intraday_volatility = 0.02
    trend_strength = 0.0
    predicted_low = current_price * 0.98
    predicted_high = current_price * 1.02

    profit_threshold_pct, loss_threshold_pct = _calculate_dynamic_thresholds(
        current_price, predicted_low, predicted_high,
        intraday_volatility, trend_strength
    )

    recommendations = []
    urgency_level = 'low'

    has_position = shares > 0

    if not has_position:
        # 空仓：开仓建议
        predicted_range = predicted_high - predicted_low
        range_ratio = predicted_range / current_price if current_price > 0 else 0

        if range_ratio > 0.01 and current_price <= predicted_low * 1.03:
            recommendations.append({
                'type': '开仓买入',
                'reason': '价格接近支撑位，预测区间较宽，适合建立仓位',
                'details': f'预测区间: {predicted_low:.2f}-{predicted_high:.2f} | 区间宽度: {range_ratio * 100:.1f}%',
                'urgency': 'medium'
            })
            urgency_level = 'medium'
    else:
        # 有持仓：止盈止损建议
        current_net_profit_pct = pl_data['net_profit_pct']
        profit_threshold_price = cost_price * (1 + profit_threshold_pct)
        loss_threshold_price = cost_price * (1 - loss_threshold_pct)

        # 止盈建议
        if current_price >= profit_threshold_price and current_net_profit_pct > 0:
            if current_net_profit_pct > 8 or trend_strength < -0.01:
                urgency = 'high'
                action = "强烈建议止盈"
            elif current_net_profit_pct > 5:
                urgency = 'medium'
                action = "建议止盈"
            else:
                urgency = 'low'
                action = "考虑止盈"

            recommendations.append({
                'type': '止盈',
                'reason': f'{action}，当前价格达到动态止盈阈值',
                'details': f'动态阈值: {profit_threshold_pct * 100:.1f}% | 当前盈利: {current_net_profit_pct:.1f}%',
                'urgency': urgency
            })
            urgency_level = urgency

        # 止损建议
        elif current_price <= loss_threshold_price and current_net_profit_pct < -1:
            if current_net_profit_pct < -8 or trend_strength < -0.02:
                urgency = 'high'
                action = "强烈建议止损"
            elif current_net_profit_pct < -5:
                urgency = 'medium'
                action = "建议止损"
            else:
                urgency = 'low'
                action = "考虑止损"

            recommendations.append({
                'type': '止损',
                'reason': f'{action}，当前价格达到动态止损阈值',
                'details': f'动态阈值: {loss_threshold_pct * 100:.1f}% | 当前亏损: {current_net_profit_pct:.1f}%',
                'urgency': urgency
            })
            urgency_level = urgency

        # 加仓建议
        if (current_price <= cost_price * 0.97 and
                trend_strength > -0.01 and
                current_net_profit_pct > -8):
            recommendations.append({
                'type': '加仓',
                'reason': '价格接近强支撑位，可考虑分批加仓',
                'details': f'当前低于成本{abs(current_net_profit_pct):.1f}% | 趋势强度: {trend_strength:.4f}',
                'urgency': 'low'
            })

    return {
        "symbol": symbol,
        "current_price": current_price,
        "holding": {
            "shares": shares,
            "cost_price": cost_price,
            "aggressive_factor": aggressive_factor,
            "t_position_ratio": t_position_ratio,
        },
        "profit_loss": pl_data,
        "thresholds": {
            "profit_threshold_pct": round(profit_threshold_pct * 100, 2),
            "loss_threshold_pct": round(loss_threshold_pct * 100, 2),
            "profit_threshold_price": round(cost_price * (1 + profit_threshold_pct), 2) if cost_price > 0 else 0,
            "loss_threshold_price": round(cost_price * (1 - loss_threshold_pct), 2) if cost_price > 0 else 0,
        },
        "recommendations": recommendations,
        "urgency": urgency_level,
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "has_position": has_position,
    }


def update_holding(symbol: str, stock_name: str, shares: int = None,
                   cost_price: float = None, aggressive_factor: float = None,
                   t_position_ratio: float = None) -> dict:
    """更新持仓信息"""
    holdings = _load_holdings()

    if symbol not in holdings:
        holdings[symbol] = {
            'stock_name': stock_name,
            'shares': 0,
            'cost_price': 0,
            'aggressive_factor': 0.5,
            't_position_ratio': 0.0,
        }

    if shares is not None:
        holdings[symbol]['shares'] = shares
    if cost_price is not None:
        holdings[symbol]['cost_price'] = cost_price
    if aggressive_factor is not None:
        holdings[symbol]['aggressive_factor'] = aggressive_factor
    if t_position_ratio is not None:
        holdings[symbol]['t_position_ratio'] = t_position_ratio

    holdings[symbol]['stock_name'] = stock_name
    holdings[symbol]['last_updated'] = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    _save_holdings(holdings)
    return holdings[symbol]


def get_capital_info() -> dict:
    """获取资金信息"""
    capital = _load_capital()
    holdings = _load_holdings()

    holding_value = 0
    for sym, h in holdings.items():
        holding_value += h.get('shares', 0) * h.get('cost_price', 0)

    available_cash = capital.get('available_cash', 100000.0)
    total_capital = capital.get('total_capital', available_cash)

    return {
        'available_cash': available_cash,
        'total_capital': total_capital,
        'holding_value': round(holding_value, 2),
        'cash_ratio': round(available_cash / total_capital * 100, 2) if total_capital > 0 else 0,
    }


def update_capital(available_cash: float = None, total_capital: float = None) -> dict:
    """更新资金信息"""
    capital = _load_capital()
    if available_cash is not None:
        capital['available_cash'] = available_cash
    if total_capital is not None:
        capital['total_capital'] = total_capital
    capital['last_updated'] = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    _save_capital(capital)
    return capital
