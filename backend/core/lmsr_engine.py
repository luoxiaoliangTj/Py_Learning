"""
LMSR market maker engine.
Ported from lmsr.py
"""
import numpy as np
from typing import Optional, Tuple


class LMSRMarket:
    """Logarithmic Market Scoring Rule pricing engine."""

    def __init__(self, n_outcomes: int, liquidity_param_b: float):
        self.n = n_outcomes
        self.b = liquidity_param_b
        self.q = np.zeros(n_outcomes)

    def cost_function(self, q: np.ndarray) -> float:
        return float(self.b * np.log(np.sum(np.exp(q / self.b))))

    def price_function(self, q: Optional[np.ndarray] = None) -> np.ndarray:
        if q is None:
            q = self.q
        exponents = np.exp(q / self.b)
        total = np.sum(exponents)
        return exponents / total

    def trade_cost(self, outcome_index: int, delta: float) -> float:
        old_cost = self.cost_function(self.q)
        new_q = self.q.copy()
        new_q[outcome_index] += delta
        new_cost = self.cost_function(new_q)
        return new_cost - old_cost

    def execute_trade(self, outcome_index: int, delta: float) -> Tuple[float, np.ndarray]:
        cost = self.trade_cost(outcome_index, delta)
        self.q[outcome_index] += delta
        new_prices = self.price_function()
        return cost, new_prices

    @property
    def max_maker_loss(self) -> float:
        return float(self.b * np.log(self.n))


class BayesianTradingAgent:
    """Real-time Bayesian signal processing agent."""

    def __init__(self, n_outcomes: int, prior_belief: Optional[np.ndarray] = None):
        self.n = n_outcomes
        if prior_belief is None:
            self.prior = np.ones(n_outcomes) / n_outcomes
        else:
            self.prior = prior_belief.copy()
        self.current_belief = self.prior.copy()

    def sequential_bayesian_update(self, likelihoods: np.ndarray) -> np.ndarray:
        log_prior = np.log(self.current_belief + 1e-10)
        log_likelihood = np.log(likelihoods + 1e-10)
        log_posterior = log_prior + log_likelihood
        log_z = np.log(np.sum(np.exp(log_posterior)))
        log_posterior -= log_z
        self.current_belief = np.exp(log_posterior)
        return self.current_belief

    def expected_value(self, market_prices: np.ndarray) -> np.ndarray:
        return self.current_belief - market_prices

    def decide_trade(self, ev_vector: np.ndarray, threshold: float = 0.01):
        abs_ev = np.abs(ev_vector)
        best_idx = int(np.argmax(abs_ev))
        if abs_ev[best_idx] > threshold:
            direction = 1.0 if ev_vector[best_idx] > 0 else -1.0
            return best_idx, direction
        return None
