"""
Price Chart Visualization Module - Real-time display with English labels and fixed style
"""
from typing import List, Dict, Optional, Tuple
from datetime import datetime
import numpy as np
from collections import deque
import os
import sys

# Add matplotlib imports for real-time chart
import matplotlib.pyplot as plt
import matplotlib.animation as animation
from matplotlib import style
import threading
import matplotlib

class PriceChartVisualizer:
    """Price Chart Visualizer - Support real-time charts with English labels"""
    
    def __init__(self, symbol: str, max_points: int = 100):
        self.symbol = symbol
        self.max_points = max_points
        
        # Set correct style (fixed)
        try:
            style.use('seaborn-v0_8-darkgrid')  # Use correct style name
            print(f"✅ Using style: seaborn-v0_8-darkgrid")
        except Exception as e:
            try:
                style.use('default')  # Fallback to default
                print(f"✅ Using style: default (fallback)")
            except:
                print(f"⚠️ Could not set style: {e}")
        
        # Data storage for text chart
        self.timestamps = deque(maxlen=max_points)
        self.actual_prices = deque(maxlen=max_points)
        self.predicted_prices = deque(maxlen=max_points)
        self.prediction_ranges = deque(maxlen=max_points)
        
        # Data storage for real-time chart
        self.realtime_timestamps = deque(maxlen=50)
        self.realtime_actual = deque(maxlen=50)
        self.realtime_predicted = deque(maxlen=50)
        
        # Prediction quality statistics
        self.prediction_errors = deque(maxlen=100)
        self.total_predictions = 0
        self.accurate_predictions = 0
        
        # Display configuration
        self.chart_width = 80
        self.chart_height = 20
        self.show_confidence_band = True
        
        # Real-time chart objects
        self.fig = None
        self.ax = None
        self.actual_line = None
        self.predicted_line = None
        self.anim = None
        self.is_chart_running = False
        self.chart_lock = threading.Lock()
        
        # Chart mode
        self.enable_realtime_chart = True  # Set to True for real-time chart
        self.realtime_chart_initialized = False
        
        # File saving (optional)
        self.save_to_file = False
        self.data_file = None
        
        if self.save_to_file:
            self._setup_data_file()
    
    def _setup_data_file(self):
        """Setup data file"""
        data_dir = os.path.join(
            os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
            "data", "price_charts"
        )
        os.makedirs(data_dir, exist_ok=True)
        
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        self.data_file = os.path.join(data_dir, f"{self.symbol}_{timestamp}.csv")
        
        with open(self.data_file, 'w', encoding='utf-8') as f:
            f.write("timestamp,actual_price,predicted_price,pred_low,pred_high\n")
    
    def add_data_point(self, 
                      timestamp: datetime,
                      actual_price: float,
                      predicted_price: Optional[float] = None,
                      prediction_range: Optional[Dict] = None):
        """Add data point to both text and real-time charts"""
        # Store for text chart
        self.timestamps.append(timestamp)
        self.actual_prices.append(actual_price)
        
        # Predicted price
        if predicted_price is not None:
            self.predicted_prices.append(predicted_price)
        elif self.predicted_prices:
            self.predicted_prices.append(self.predicted_prices[-1])
        else:
            self.predicted_prices.append(actual_price)
        
        # Prediction range
        if prediction_range:
            self.prediction_ranges.append(prediction_range)
        elif self.prediction_ranges:
            self.prediction_ranges.append(self.prediction_ranges[-1])
        else:
            self.prediction_ranges.append({
                'low': actual_price * 0.95,
                'high': actual_price * 1.05
            })
        
        # Store for real-time chart
        with self.chart_lock:
            self.realtime_timestamps.append(timestamp)
            self.realtime_actual.append(actual_price)
            self.realtime_predicted.append(predicted_price if predicted_price is not None else actual_price)
        
        # Calculate prediction error
        if len(self.actual_prices) > 1 and len(self.predicted_prices) > 1:
            actual_prev = list(self.actual_prices)[-2]
            actual_curr = list(self.actual_prices)[-1]
            predicted_prev = list(self.predicted_prices)[-2]
            
            # Calculate direction accuracy
            actual_dir = 1 if actual_curr > actual_prev else (-1 if actual_curr < actual_prev else 0)
            predicted_dir = 1 if predicted_prev > actual_prev else (-1 if predicted_prev < actual_prev else 0)
            
            if actual_dir == predicted_dir and actual_dir != 0:
                self.accurate_predictions += 1
            
            self.total_predictions += 1
            
            # Calculate price error
            error = abs(actual_curr - predicted_prev)
            self.prediction_errors.append(error)
        
        # Save to file (if enabled)
        if self.save_to_file and self.data_file:
            self._save_to_file(timestamp, actual_price, predicted_price, prediction_range)
    
    def _save_to_file(self, timestamp: datetime, actual_price: float, 
                     predicted_price: Optional[float], prediction_range: Optional[Dict]):
        """Save data to file"""
        pred_low = prediction_range.get('low', 0) if prediction_range else 0
        pred_high = prediction_range.get('high', 0) if prediction_range else 0
        pred_price = predicted_price if predicted_price is not None else 0
        
        with open(self.data_file, 'a', encoding='utf-8') as f:
            f.write(f"{timestamp.isoformat()},{actual_price},{pred_price},{pred_low},{pred_high}\n")
    
    def init_realtime_chart(self):
        """Initialize real-time matplotlib chart"""
        if not self.enable_realtime_chart or self.realtime_chart_initialized:
            return
        
        try:
            # Use Agg backend for better compatibility
            matplotlib.use('Agg')
            
            # Create figure and axes
            self.fig, self.ax = plt.subplots(figsize=(10, 6))
            
            # Initialize lines
            self.actual_line, = self.ax.plot([], [], 
                                            label='Actual Price', 
                                            color='blue',
                                            linewidth=2,
                                            marker='o',
                                            markersize=4)
            
            self.predicted_line, = self.ax.plot([], [], 
                                               label='Predicted Price', 
                                               color='red',
                                               linewidth=2,
                                               linestyle='--',
                                               alpha=0.8)
            
            # Set chart properties (English labels)
            self.ax.set_title(f'{self.symbol} - Real-time Price Prediction', 
                             fontsize=14, fontweight='bold')
            self.ax.set_xlabel('Time')
            self.ax.set_ylabel('Price')
            self.ax.legend()
            self.ax.grid(True, alpha=0.3)
            
            # Format x-axis
            plt.xticks(rotation=45)
            plt.tight_layout()
            
            self.realtime_chart_initialized = True
            
            print(f"✅ Real-time chart initialized for {self.symbol}")
            
        except Exception as e:
            print(f"❌ Failed to initialize real-time chart: {e}")
            self.enable_realtime_chart = False
    
    def start_animation(self, interval=1000):
        """Start real-time chart animation"""
        if not self.enable_realtime_chart or self.anim is not None or not self.fig:
            return
        
        try:
            self.anim = animation.FuncAnimation(
                self.fig,
                self._update_realtime_chart,
                interval=interval,  # Update every second
                blit=False,
                cache_frame_data=False
            )
            self.is_chart_running = True
            
            print(f"✅ Animation started for {self.symbol}")
            
        except Exception as e:
            print(f"❌ Failed to start animation: {e}")
    
    def _update_realtime_chart(self, frame):
        """Update real-time chart (animation callback)"""
        with self.chart_lock:
            if len(self.realtime_timestamps) < 2:
                return self.actual_line, self.predicted_line
            
            # Get recent data
            times = list(self.realtime_timestamps)
            actual = list(self.realtime_actual)
            predicted = list(self.realtime_predicted)
            
            # Update lines
            self.actual_line.set_data(times, actual)
            self.predicted_line.set_data(times, predicted)
            
            # Auto-scale axes
            if len(actual) > 0:
                # Calculate price range
                all_prices = actual + predicted
                min_price = min(all_prices)
                max_price = max(all_prices)
                price_range = max_price - min_price
                
                # Add margin
                margin = price_range * 0.1 if price_range > 0 else min_price * 0.1
                self.ax.set_ylim(min_price - margin, max_price + margin)
                
                # Set x-axis range
                if len(times) > 1:
                    time_range = (times[-1] - times[0]).total_seconds()
                    time_margin = max(time_range * 0.05, 60)  # 5% or 1 minute
                    self.ax.set_xlim(times[0], times[-1])
            
            # Format time labels
            if len(times) > 0:
                # Show limited number of ticks
                if len(times) <= 8:
                    tick_times = times
                else:
                    step = max(1, len(times) // 4)
                    tick_indices = list(range(0, len(times), step))
                    if tick_indices[-1] != len(times) - 1:
                        tick_indices.append(len(times) - 1)
                    tick_times = [times[i] for i in tick_indices]
                
                self.ax.set_xticks(tick_times)
                self.ax.set_xticklabels(
                    [t.strftime('%H:%M:%S') for t in tick_times],
                    rotation=45
                )
        
        # Redraw the canvas
        if self.fig:
            self.fig.canvas.draw_idle()
        
        return self.actual_line, self.predicted_line
    
    def show_realtime_chart(self):
        """Display real-time chart window"""
        if self.enable_realtime_chart and self.fig:
            try:
                # In Pydroid, we can't show interactive windows, so save to file instead
                self.save_chart_image()
            except Exception as e:
                print(f"⚠️ Could not display chart window: {e}")
    
    def save_chart_image(self):
        """Save current chart as image"""
        if self.fig and len(self.realtime_timestamps) >= 5:
            try:
                # Create directory if it doesn't exist
                chart_dir = os.path.join(
                    os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
                    "data", "charts"
                )
                os.makedirs(chart_dir, exist_ok=True)
                
                # Generate filename
                timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
                filename = os.path.join(chart_dir, f"{self.symbol}_chart_{timestamp}.png")
                
                # Save the figure
                self.fig.savefig(filename, dpi=100, bbox_inches='tight')
                print(f"💾 Chart saved: {filename}")
                
                return filename
            except Exception as e:
                print(f"⚠️ Failed to save chart: {e}")
        return None
    
    def generate_text_chart(self, recent_points: int = 40) -> str:
        """Generate text chart for command line display (English)"""
        if len(self.actual_prices) < 2:
            return "📈 Not enough data to generate chart"
        
        # Get recent data
        points_to_show = min(recent_points, len(self.actual_prices))
        
        actual = list(self.actual_prices)[-points_to_show:]
        predicted = list(self.predicted_prices)[-points_to_show:]
        timestamps = list(self.timestamps)[-points_to_show:]
        
        # Calculate statistics
        stats = self._calculate_chart_stats(actual, predicted)
        
        # Generate chart lines
        chart_lines = []
        
        # Chart title and stats
        chart_lines.append(f"📈 {self.symbol} Price Comparison (Last {points_to_show} points)")
        chart_lines.append(f"📊 Prediction Accuracy: {stats['accuracy']:.1%} | MAE: {stats['mae']:.4f} | RMSE: {stats['rmse']:.4f}")
        
        # Price range calculation
        all_prices = actual + predicted
        min_price = min(all_prices)
        max_price = max(all_prices)
        price_range = max_price - min_price
        
        if price_range == 0:
            price_range = 1.0
        
        # Create chart grid
        grid_height = self.chart_height
        grid_width = min(self.chart_width, points_to_show * 2)
        
        # Initialize grid
        grid = [[' ' for _ in range(grid_width)] for _ in range(grid_height)]
        
        # Draw price curves
        for i in range(min(len(actual), grid_width // 2)):
            x = i * 2
            
            # Actual price
            actual_y = int((actual[i] - min_price) / price_range * (grid_height - 1))
            actual_y = grid_height - 1 - actual_y  # Flip Y axis
            
            # Predicted price
            predicted_y = int((predicted[i] - min_price) / price_range * (grid_height - 1))
            predicted_y = grid_height - 1 - predicted_y
            
            # Ensure coordinates are in range
            actual_y = max(0, min(grid_height - 1, actual_y))
            predicted_y = max(0, min(grid_height - 1, predicted_y))
            
            # Draw points
            if 0 <= x < grid_width:
                # Actual price with *
                grid[actual_y][x] = '*' if grid[actual_y][x] == ' ' else '‡'
                
                # Predicted price with +
                grid[predicted_y][x] = '+' if grid[predicted_y][x] == ' ' else '‡'
        
        # Convert to text lines
        for row in grid:
            chart_lines.append('│' + ''.join(row) + '│')
        
        # Add X axis
        time_labels = self._format_time_labels(timestamps)
        chart_lines.append('└' + '─' * grid_width + '┘')
        if time_labels:
            chart_lines.append(f"Time: {time_labels[0]} → {time_labels[-1]}")
        
        # Add legend (English)
        chart_lines.append("Legend: *Actual Price  +Predicted Price  ‡Both Overlap")
        
        return '\n'.join(chart_lines)
    
    def _calculate_chart_stats(self, actual: List[float], predicted: List[float]) -> Dict:
        """Calculate chart statistics"""
        if len(actual) != len(predicted) or len(actual) < 2:
            return {'accuracy': 0, 'mae': 0, 'rmse': 0}
        
        # Calculate direction accuracy
        correct_directions = 0
        total_directions = 0
        
        for i in range(1, len(actual)):
            actual_dir = 1 if actual[i] > actual[i-1] else (-1 if actual[i] < actual[i-1] else 0)
            predicted_dir = 1 if predicted[i-1] > actual[i-1] else (-1 if predicted[i-1] < actual[i-1] else 0)
            
            if actual_dir != 0:
                total_directions += 1
                if actual_dir == predicted_dir:
                    correct_directions += 1
        
        accuracy = correct_directions / total_directions if total_directions > 0 else 0
        
        # Calculate error metrics
        errors = []
        for i in range(1, min(len(actual), len(predicted))):
            error = abs(actual[i] - predicted[i-1])  # Compare previous prediction with current actual
            errors.append(error)
        
        mae = np.mean(errors) if errors else 0
        rmse = np.sqrt(np.mean(np.square(errors))) if errors else 0
        
        return {
            'accuracy': accuracy,
            'mae': mae,
            'rmse': rmse
        }
    
    def _format_time_labels(self, timestamps: List[datetime]) -> List[str]:
        """Format time labels"""
        if not timestamps:
            return []
        
        time_diff = (timestamps[-1] - timestamps[0]).total_seconds()
        
        if time_diff < 3600:  # Less than 1 hour
            return [ts.strftime('%M:%S') for ts in timestamps]
        else:
            return [ts.strftime('%H:%M') for ts in timestamps]
    
    def get_prediction_accuracy(self) -> Dict:
        """Get prediction accuracy statistics (English keys)"""
        if self.total_predictions == 0:
            return {
                'total_predictions': 0,
                'accuracy': 0,
                'avg_error': 0,
                'mae': 0,
                'rmse': 0
            }
        
        errors = list(self.prediction_errors)
        mae = np.mean(errors) if errors else 0
        rmse = np.sqrt(np.mean(np.square(errors))) if errors else 0
        
        return {
            'total_predictions': self.total_predictions,
            'accurate_predictions': self.accurate_predictions,
            'accuracy': self.accurate_predictions / self.total_predictions,
            'avg_error': mae,
            'mae': mae,
            'rmse': rmse
        }
    
    def close_chart(self):
        """Close chart window"""
        if self.fig:
            plt.close(self.fig)
            self.is_chart_running = False
            print(f"📊 Chart closed for {self.symbol}")