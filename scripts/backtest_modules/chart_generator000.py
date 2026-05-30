# backtest_modules/chart_generator.py
import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
from datetime import datetime
import matplotlib.dates as mdates

class ChartGenerator:
    """Chart Generator - Enhanced version with plugin info and better time axis"""
    
    def __init__(self, portfolio_curve, trades, price_data, symbol, results_dir):
        self.portfolio_curve = portfolio_curve
        self.trades = trades
        self.price_data = price_data
        self.symbol = symbol
        self.results_dir = results_dir
        self.charts_dir = os.path.join(results_dir, "charts")
        
        # Create charts directory
        os.makedirs(self.charts_dir, exist_ok=True)
        
        print(f"📊 Enhanced chart generator initialized")
    
    def generate_all_charts(self, backtest_metrics=None):
        """Generate all charts - Enhanced version"""
        try:
            print("🖼️  Generating enhanced charts...")
            
            # Generate equity curve with plugin info
            self._generate_enhanced_equity_curve(backtest_metrics)
            
            # Generate improved trade points chart
            if self.trades:
                self._generate_enhanced_trade_points()
            
            # Generate performance comparison
            self._generate_performance_comparison(backtest_metrics)
            
            print("✅ Enhanced charts generated successfully!")
            
        except Exception as e:
            print(f"❌ Chart generation failed: {e}")
    
    def _generate_enhanced_equity_curve(self, metrics=None):
        """Generate equity curve with plugin info and better time axis"""
        try:
            fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))
            
            # Equity curve
            ax1.plot(self.portfolio_curve.index, self.portfolio_curve.values, label='Strategy Equity', color='blue', linewidth=2)
            # Benchmark (buy & hold)
            initial_cash = self.portfolio_curve.iloc[0]
            price_start = self.price_data['收盘'].iloc[20]
            benchmark_curve = (self.price_data['收盘'] / price_start * initial_cash).iloc[20:-1]
            
            ax1.plot(benchmark_curve.index, benchmark_curve.values, 
                   label='Buy & Hold', color='red', linewidth=2, alpha=0.7)
            
            # Enhanced title with plugin info
            algo_name = metrics.get('algo_name', 'Unknown') if metrics else 'Unknown'
            ax1.set_title(f'{self.symbol} - Equity Curve\nAlgorithm: {algo_name}', 
                         fontsize=14, fontweight='bold')
            ax1.set_ylabel('Portfolio Value', fontsize=12)
            ax1.legend()
            ax1.grid(True, alpha=0.3)
            
            # Optimize time axis for better readability
            self._optimize_time_axis(ax1, self.portfolio_curve.index)
            
            # Enhanced performance metrics text box
            if metrics:
                metrics_text = (
                    f"Strategy Return: {metrics.get('strategy_return', 0):.2f}%\n"
                    f"Benchmark Return: {metrics.get('benchmark_return', 0):.2f}%\n"
                    f"Sharpe Ratio: {metrics.get('sharpe', 0):.2f}\n"
                    f"Max Drawdown: {metrics.get('max_drawdown', 0):.2f}%\n"
                    f"Total Trades: {metrics.get('total_trades', 0)}\n"
                    f"Algorithm: {metrics.get('algo_name', 'Unknown')}"
                )
                ax1.text(0.02, 0.98, metrics_text, transform=ax1.transAxes, 
                        verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.8),
                        fontfamily='monospace', fontsize=9)
            
            # Drawdown subplot
            rolling_max = self.portfolio_curve.expanding().max()
            drawdown = (self.portfolio_curve - rolling_max) / rolling_max * 100
            
            ax2.fill_between(drawdown.index, drawdown.values, 0, 
                           color='red', alpha=0.3, label='Drawdown')
            ax2.plot(drawdown.index, drawdown.values, color='red', linewidth=1)
            ax2.axhline(y=0, color='black', linestyle='-', alpha=0.5)
            
            max_dd = drawdown.min()
            ax2.axhline(y=max_dd, color='darkred', linestyle='--', 
                       label=f'Max DD: {max_dd:.2f}%')
            
            ax2.set_title('Drawdown Analysis', fontsize=12)
            ax2.set_xlabel('Date', fontsize=12)
            ax2.set_ylabel('Drawdown %', fontsize=12)
            ax2.legend()
            ax2.grid(True, alpha=0.3)
            
            # Optimize time axis for drawdown chart
            self._optimize_time_axis(ax2, drawdown.index)
            
            plt.tight_layout()
            self._save_chart(fig, 'enhanced_equity_curve')
            plt.close()
            
        except Exception as e:
            print(f"❌ Enhanced equity curve generation failed: {e}")
    
    def _generate_enhanced_trade_points(self):
        """Generate enhanced trade points chart with clear B/S/T markers"""
        try:
            if not self.trades:
                return
                
            fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))
            
            # Price curve with enhanced trade points
            price_dates = self.price_data['日期'].iloc[20:-1]
            prices = self.price_data['收盘'].iloc[20:-1]
            
            ax1.plot(price_dates, prices, label='Price', color='black', linewidth=1.5)
            
            # Enhanced trade classification with clear B/S/T markers
            buy_dates, buy_prices, buy_details = [], [], []
            sell_dates, sell_prices, sell_details = [], [], []
            t_dates, t_prices, t_details = [], [], []
            
            for trade in self.trades:
                trade_type = trade['type']
                shares = trade.get('shares', 0)
                price = trade.get('price', 0)
                
                # Enhanced labeling with trade details
                if trade_type in ['buy', '开仓买入']:
                    buy_dates.append(trade['date'])
                    buy_prices.append(price)
                    buy_details.append(f"B:{shares}@{price:.2f}")
                elif trade_type == 'sell':
                    sell_dates.append(trade['date'])
                    sell_prices.append(price)
                    sell_details.append(f"S:{shares}@{price:.2f}")
                elif trade_type == 'T':
                    t_dates.append(trade['date'])
                    t_prices.append(price)
                    t_details.append(f"T:{shares}@{price:.2f}")
            
            # Enhanced buy markers with detailed annotation
            if buy_dates:
                ax1.scatter(buy_dates, buy_prices, color='green', marker='^', 
                          s=120, label='Buy (B)', zorder=5, edgecolors='darkgreen', linewidth=1.5)
                # Enhanced B labels with trade details
                for i, (date, price, detail) in enumerate(zip(buy_dates, buy_prices, buy_details)):
                    ax1.annotate(detail, (date, price), textcoords="offset points", 
                               xytext=(0,15), ha='center', fontweight='bold', 
                               color='green', fontsize=8, bbox=dict(boxstyle="round,pad=0.3", 
                                                                   facecolor="lightgreen", alpha=0.7))
            
            # Enhanced sell markers with detailed annotation
            if sell_dates:
                ax1.scatter(sell_dates, sell_prices, color='red', marker='v', 
                          s=120, label='Sell (S)', zorder=5, edgecolors='darkred', linewidth=1.5)
                # Enhanced S labels with trade details
                for i, (date, price, detail) in enumerate(zip(sell_dates, sell_prices, sell_details)):
                    ax1.annotate(detail, (date, price), textcoords="offset points", 
                               xytext=(0,-20), ha='center', fontweight='bold', 
                               color='red', fontsize=8, bbox=dict(boxstyle="round,pad=0.3", 
                                                                 facecolor="lightcoral", alpha=0.7))
            
            # Enhanced trading markers with detailed annotation
            if t_dates:
                ax1.scatter(t_dates, t_prices, color='blue', marker='o', 
                          s=100, label='Trading (T)', zorder=5, alpha=0.8,
                          edgecolors='darkblue', linewidth=1.5)
                # Enhanced T labels with trade details
                for i, (date, price, detail) in enumerate(zip(t_dates, t_prices, t_details)):
                    ax1.annotate(detail, (date, price), textcoords="offset points", 
                               xytext=(15,0), ha='left', fontweight='bold', 
                               color='blue', fontsize=8, bbox=dict(boxstyle="round,pad=0.3", 
                                                                  facecolor="lightblue", alpha=0.7))
            
            ax1.set_title(f'{self.symbol} - Enhanced Trade Points\nB=Buy, S=Sell, T=Trading', 
                         fontsize=14, fontweight='bold')
            ax1.set_ylabel('Price', fontsize=12)
            ax1.legend()
            ax1.grid(True, alpha=0.3)
            
            # Optimize time axis
            self._optimize_time_axis(ax1, price_dates)
            
            # Enhanced trade frequency analysis
            trades_df = pd.DataFrame(self.trades)
            trades_df['date'] = pd.to_datetime(trades_df['date'])
            
            # Create separate series for each trade type
            buy_trades = trades_df[trades_df['type'].isin(['buy', '开仓买入'])].groupby('date').size()
            sell_trades = trades_df[trades_df['type'] == 'sell'].groupby('date').size()
            t_trades = trades_df[trades_df['type'] == 'T'].groupby('date').size()
            
            # Plot stacked bar chart
            dates = sorted(set(buy_trades.index) | set(sell_trades.index) | set(t_trades.index))
            buy_counts = [buy_trades.get(date, 0) for date in dates]
            sell_counts = [sell_trades.get(date, 0) for date in dates]
            t_counts = [t_trades.get(date, 0) for date in dates]
            
            ax2.bar(dates, buy_counts, color='green', alpha=0.7, label='Buy Trades')
            ax2.bar(dates, sell_counts, bottom=buy_counts, color='red', alpha=0.7, label='Sell Trades')
            ax2.bar(dates, t_counts, bottom=[i+j for i,j in zip(buy_counts, sell_counts)], 
                   color='blue', alpha=0.7, label='Trading Trades')
            
            ax2.set_title('Daily Trade Frequency by Type', fontsize=12)
            ax2.set_xlabel('Date', fontsize=12)
            ax2.set_ylabel('Number of Trades', fontsize=12)
            ax2.legend()
            ax2.grid(True, alpha=0.3)
            
            # Optimize time axis for frequency chart
            self._optimize_time_axis(ax2, dates)
            
            plt.tight_layout()
            self._save_chart(fig, 'enhanced_trade_points')
            plt.close()
            
        except Exception as e:
            print(f"❌ Enhanced trade points generation failed: {e}")
    
    def _generate_performance_comparison(self, metrics=None):
        """Generate performance comparison charts with optimized time axis"""
        try:
            fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))
            
            # Calculate returns
            strategy_returns = self.portfolio_curve.pct_change().dropna()
            price_start = self.price_data['收盘'].iloc[20]
            benchmark_prices = self.price_data['收盘'].iloc[20:-1]
            benchmark_returns = benchmark_prices.pct_change().dropna()
            
            # Cumulative returns comparison
            cum_strategy = (1 + strategy_returns).cumprod() - 1
            cum_benchmark = (1 + benchmark_returns).cumprod() - 1
            
            ax1.plot(cum_strategy.index, cum_strategy.values * 100,
                    label='Strategy', color='blue', linewidth=2)
            ax1.plot(cum_benchmark.index, cum_benchmark.values * 100,label='Benchmark', color='red', linewidth=2, alpha=0.7)
            
            # Add algorithm info to title if available
            algo_name = metrics.get('algo_name', 'Unknown') if metrics else 'Unknown'
            ax1.set_title(f'Cumulative Returns (%)\nAlgorithm: {algo_name}', 
                         fontsize=14, fontweight='bold')
            ax1.set_ylabel('Cumulative Return %', fontsize=12)
            ax1.legend()
            ax1.grid(True, alpha=0.3)
            
            # Optimize time axis
            self._optimize_time_axis(ax1, cum_strategy.index)
            
            # Returns distribution
            ax2.hist(strategy_returns * 100, bins=50, alpha=0.7, 
                    label='Strategy Returns', color='blue', density=True)
            ax2.hist(benchmark_returns * 100, bins=50, alpha=0.7,
                    label='Benchmark Returns', color='red', density=True)
            
            # Add mean lines
            strategy_mean = strategy_returns.mean() * 100
            benchmark_mean = benchmark_returns.mean() * 100
            ax2.axvline(x=strategy_mean, color='darkblue', linestyle='--', 
                       label=f'Strategy Mean: {strategy_mean:.2f}%')
            ax2.axvline(x=benchmark_mean, color='darkred', linestyle='--', 
                       label=f'Benchmark Mean: {benchmark_mean:.2f}%')
            
            ax2.set_title('Return Distribution (%)', fontsize=14, fontweight='bold')
            ax2.set_xlabel('Daily Return %', fontsize=12)
            ax2.set_ylabel('Density', fontsize=12)
            ax2.legend()
            ax2.grid(True, alpha=0.3)
            
            plt.tight_layout()
            self._save_chart(fig, 'performance_comparison')
            plt.close()
            
        except Exception as e:
            print(f"❌ Performance comparison generation failed: {e}")
    
    def _optimize_time_axis(self, ax, dates):
        """Optimize time axis for better readability"""
        try:
            # Convert to datetime if needed
            if not isinstance(dates, pd.DatetimeIndex):
                dates = pd.to_datetime(dates)
            
            # Set major locator and formatter
            if len(dates) > 365:  # More than 1 year of data
                # For long time series, show quarterly or yearly ticks
                if len(dates) > 1000:
                    ax.xaxis.set_major_locator(mdates.YearLocator())
                    ax.xaxis.set_major_formatter(mdates.DateFormatter('%Y'))
                else:
                    ax.xaxis.set_major_locator(mdates.MonthLocator(interval=3))
                    ax.xaxis.set_major_formatter(mdates.DateFormatter('%Y-%m'))
            elif len(dates) > 90:  # 3 months to 1 year
                # Show monthly ticks
                ax.xaxis.set_major_locator(mdates.MonthLocator())
                ax.xaxis.set_major_formatter(mdates.DateFormatter('%Y-%m'))
            else:  # Less than 3 months
                # Show weekly or daily ticks
                if len(dates) > 30:
                    ax.xaxis.set_major_locator(mdates.WeekdayLocator(interval=2))
                    ax.xaxis.set_major_formatter(mdates.DateFormatter('%m-%d'))
                else:
                    ax.xaxis.set_major_locator(mdates.WeekdayLocator())
                    ax.xaxis.set_major_formatter(mdates.DateFormatter('%m-%d'))
            
            # Rotate x-axis labels for better readability
            plt.setp(ax.xaxis.get_majorticklabels(), rotation=45, ha='right')
            
            # Adjust layout to prevent label cutoff
            plt.tight_layout()
            
        except Exception as e:
            print(f"⚠️ Time axis optimization failed: {e}")
            # Fallback: use default formatting
            plt.gcf().autofmt_xdate()
    
    def _save_chart(self, fig, chart_type):
        """Save chart to file"""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        
        # PNG format
        png_path = os.path.join(self.charts_dir, f"{self.symbol}_{chart_type}_{timestamp}.png")
        fig.savefig(png_path, dpi=150, bbox_inches='tight', facecolor='white')
        
        print(f"   ✅ Generated: {chart_type} -> {os.path.basename(png_path)}")