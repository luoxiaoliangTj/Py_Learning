 
import numpy as np
from typing import List, Tuple, Optional

class LMSRMarket:
    """
    对数市场评分规则 (LMSR) 定价引擎。
    对应文档: 《Logarithmic Market Scoring Rule (LMSR): Pricing Mechanism & Inefficiency Detection》
    """
    
    def __init__(self, n_outcomes: int, liquidity_param_b: float):
        """
        初始化市场。
        
        Args:
            n_outcomes: 互斥结果的数量 (例如，二元市场 n=2)。
            liquidity_param_b: 流动性参数 b。b 越大，市场越深，价差越小，做市商最大损失越高。
        """
        self.n = n_outcomes
        self.b = liquidity_param_b
        # q: 当前各结果的持仓数量向量，初始通常设为0或很小的值。
        self.q = np.zeros(n_outcomes)
        
    def cost_function(self, q: np.ndarray) -> float:
        """
        计算给定持仓向量 q 下的成本函数值 C(q)。
        公式: C(q) = b * ln( sum_i exp(q_i / b) )
        """
        return self.b * np.log(np.sum(np.exp(q / self.b)))
    
    def price_function(self, q: Optional[np.ndarray] = None) -> np.ndarray:
        """
        计算当前市场各结果的瞬时价格向量 p。
        公式: p_i = exp(q_i / b) / sum_j exp(q_j / b)
        这是 Softmax 函数。
        
        Args:
            q: 可选，指定一个持仓向量来计算价格。如果为None，则使用当前市场状态 self.q。
        Returns:
            形状为 (n_outcomes,) 的价格数组，所有元素之和为1。
        """
        if q is None:
            q = self.q
        exponents = np.exp(q / self.b)
        total = np.sum(exponents)
        return exponents / total
    
    def trade_cost(self, outcome_index: int, delta: float) -> float:
        """
        计算对某个结果进行交易（改变其持仓量）所需的成本。
        公式: Cost = C(q_after) - C(q_before)
        
        Args:
            outcome_index: 要交易的结果索引 (0-based)。
            delta: 持仓变化量。正数表示买入/增加该结果的持仓。
        Returns:
            需要支付的成本（如果为正，表示需要付钱；为负则表示获得退款）。
        """
        old_cost = self.cost_function(self.q)
        new_q = self.q.copy()
        new_q[outcome_index] += delta
        new_cost = self.cost_function(new_q)
        return new_cost - old_cost
    
    def execute_trade(self, outcome_index: int, delta: float) -> Tuple[float, np.ndarray]:
        """
        执行一笔交易，更新市场状态并返回成本和新的价格。
        
        Args:
            outcome_index: 交易的结果索引。
            delta: 持仓变化量。
        Returns:
            cost: 本次交易的成本。
            new_prices: 交易后的新价格向量。
        """
        cost = self.trade_cost(outcome_index, delta)
        self.q[outcome_index] += delta
        new_prices = self.price_function()
        return cost, new_prices
    
    @property
    def max_maker_loss(self) -> float:
        """计算做市商的最大可能损失 L_max = b * ln(n)。"""
        return self.b * np.log(self.n)


class BayesianTradingAgent:
    """
    实时贝叶斯信号处理智能体。
    对应文档: 《Real-Time Bayesian Signal Processing Agent Decision Architecture》
    """
    
    def __init__(self, n_outcomes: int, prior_belief: Optional[np.ndarray] = None):
        """
        初始化智能体。
        
        Args:
            n_outcomes: 结果数量，须与市场一致。
            prior_belief: 先验信念，形状为 (n_outcomes,) 的概率数组。如果为None，则使用均匀先验。
        """
        self.n = n_outcomes
        if prior_belief is None:
            self.prior = np.ones(n_outcomes) / n_outcomes  # 均匀先验
        else:
            assert len(prior_belief) == n_outcomes, "先验信念维度不匹配"
            assert np.allclose(np.sum(prior_belief), 1.0), "先验信念之和必须为1"
            self.prior = prior_belief.copy()
        # 当前后验信念，初始等于先验
        self.current_belief = self.prior.copy()
        
    def sequential_bayesian_update(self, likelihoods: np.ndarray) -> np.ndarray:
        """
        序贯贝叶斯更新（在对数空间中进行，保证数值稳定）。
        公式: log P(H|D) = log P(H) + Σ log P(D_k|H) - log Z
        
        Args:
            likelihoods: 形状为 (n_outcomes,) 的数组，表示新数据 D 在当前信念下各结果的似然 P(D|H_i)。
                        注意：这里的似然是给定假设H_i时观察到数据D的概率。
        Returns:
            更新后的后验信念 (已归一化)。
        """
        # 对数空间计算
        log_prior = np.log(self.current_belief + 1e-10)  # 加一个小值防止log(0)
        log_likelihood = np.log(likelihoods + 1e-10)
        log_posterior = log_prior + log_likelihood
        # 归一化 (减去 log Z)
        log_z = np.log(np.sum(np.exp(log_posterior)))
        log_posterior -= log_z
        # 转换回概率空间
        self.current_belief = np.exp(log_posterior)
        return self.current_belief
    
    def expected_value(self, market_prices: np.ndarray) -> np.ndarray:
        """
        计算针对每个结果的预期价值 (EV)。
        公式: EV_i = belief_i - market_price_i
        文档公式 (4): EV = ṗ - p
        
        Args:
            market_prices: 市场当前价格向量，形状为 (n_outcomes,)。
        Returns:
            每个结果的预期价值向量。
        """
        return self.current_belief - market_prices
    
    def decide_trade(self, ev_vector: np.ndarray, threshold: float = 0.01) -> Optional[Tuple[int, float]]:
        """
        一个简单的交易决策逻辑示例。
        策略：找到预期价值绝对值最大的结果，如果其绝对值超过阈值，则进行交易。
        交易方向：如果 EV > 0，则买入该结果 (认为市场低估)；如果 EV < 0，则卖出 (认为市场高估)。
        注：这是一个非常简化的示例。实际策略会更复杂，涉及头寸规模、风险控制等。
        
        Args:
            ev_vector: 预期价值向量。
            threshold: 触发交易的最小 |EV| 阈值。
        Returns:
            None 如果不交易，否则返回 (outcome_index, delta_sign)。
            delta_sign 只是方向指示 (1 或 -1)，具体交易量需另算。
        """
        abs_ev = np.abs(ev_vector)
        best_idx = np.argmax(abs_ev)
        max_abs_ev = abs_ev[best_idx]
        
        if max_abs_ev > threshold:
            direction = 1.0 if ev_vector[best_idx] > 0 else -1.0
            # 这里返回索引和方向，实际交易量可以基于 EV 大小、账户资金等因素动态计算
            return best_idx, direction
        return None


def simulate_one_cycle(market: LMSRMarket, agent: BayesianTradingAgent,
                       new_likelihoods: np.ndarray, trade_threshold: float = 0.02) -> dict:
    """
    模拟一个完整的智能体决策周期（简化版，不包括网络延迟）。
    步骤：
    1. 智能体接收新数据（似然），更新信念。
    2. 智能体获取市场当前价格。
    3. 智能体计算预期价值 (EV)。
    4. 智能体根据 EV 做出交易决策。
    5. 如果决定交易，则在市场上执行。
    
    Args:
        market: LMSR 市场实例。
        agent: 贝叶斯交易智能体实例。
        new_likelihoods: 本轮接收到的新数据的似然向量。
        trade_threshold: 交易触发阈值。
    Returns:
        包含周期内各步骤信息的字典。
    """
    cycle_info = {}
    
    # 1. 贝叶斯更新
    updated_belief = agent.sequential_bayesian_update(new_likelihoods)
    cycle_info['belief'] = updated_belief.copy()
    
    # 2. 获取市场价格
    market_prices = market.price_function()
    cycle_info['market_prices'] = market_prices.copy()
    
    # 3. 计算预期价值
    ev = agent.expected_value(market_prices)
    cycle_info['ev'] = ev.copy()
    
    # 4. & 5. 决策与交易
    decision = agent.decide_trade(ev, threshold=trade_threshold)
    cycle_info['decision'] = decision
    
    if decision is not None:
        outcome_idx, direction = decision
        # 简化的交易量计算：固定单位量乘以方向。实际应用中，这里会是更复杂的头寸规模模型。
        fixed_delta = 1.0 * direction
        cost, new_prices_post_trade = market.execute_trade(outcome_idx, fixed_delta)
        cycle_info['trade_executed'] = True
        cycle_info['traded_outcome'] = outcome_idx
        cycle_info['trade_delta'] = fixed_delta
        cycle_info['trade_cost'] = cost
        cycle_info['new_market_prices'] = new_prices_post_trade.copy()
    else:
        cycle_info['trade_executed'] = False
        
    return cycle_info


# ==================== 示例用法 ====================
if __name__ == "__main__":
    print("===== 高频预测市场交易算法原型 =====")
    
    # 1. 初始化一个二元市场 (n=2)，流动性参数 b=100
    b_param = 100.0
    market = LMSRMarket(n_outcomes=2, liquidity_param_b=b_param)
    print(f"市场初始化: {market.n} 个结果, b={market.b}")
    print(f"做市商最大潜在损失 L_max: {market.max_maker_loss:.2f}")
    initial_prices = market.price_function()
    print(f"初始市场价格: {initial_prices}")
    print()
    
    # 2. 初始化智能体，假设先验略微偏向结果0
    prior = np.array([0.55, 0.45])
    agent = BayesianTradingAgent(n_outcomes=2, prior_belief=prior)
    print(f"智能体初始化，先验信念: {agent.prior}")
    print()
    
    # 3. 模拟几轮数据更新和决策
    np.random.seed(42)  # 可重复性
    n_cycles = 5
    
    for cycle in range(1, n_cycles + 1):
        print(f"\n--- 决策周期 #{cycle} ---")
        
        # 模拟新数据：生成一个随机的似然向量（实际中来自数据管道）
        # 例如，数据可能暗示结果0更有可能
        likelihoods = np.random.dirichlet([3.0, 1.0])  # 结果0的似然更高
        print(f"接收到新数据，似然向量: {likelihoods}")
        
        # 运行一个完整周期
        info = simulate_one_cycle(market, agent, likelihoods, trade_threshold=0.03)
        
        print(f"智能体更新后信念: {info['belief']}")
        print(f"当前市场价格: {info['market_prices']}")
        print(f"预期价值 (EV): {info['ev']}")
        
        if info['trade_executed']:
            print(f"决策: 交易！ 方向={'买入' if info['trade_delta'] > 0 else '卖出'} 结果{info['traded_outcome']}")
            print(f"交易成本: {info['trade_cost']:.4f}")
            print(f"交易后新价格: {info['new_market_prices']}")
        else:
            print("决策: 暂无交易 (EV未达阈值)")
            
        print(f"市场当前持仓向量 q: {market.q}")
 