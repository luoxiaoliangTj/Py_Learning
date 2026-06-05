# algorithms/strategy/state_manager.py
"""
策略状态管理器 - 增强版（支持分层状态管理）
"""
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
import json
import os
import shutil


class StrategyStateManager:
    """策略状态管理器 - 支持日线策略和盘中调整分层管理"""
    
    def __init__(self, symbol: str, base_dir: str = None):
        self.symbol = symbol
        
        # 设置基础目录
        if base_dir:
            self.base_dir = base_dir
        else:
            # 默认路径：项目根目录/data/strategy_states
            self.base_dir = os.path.join(
                os.path.dirname(os.path.dirname(os.path.dirname(__file__))), 
                "data", "strategy_states"
            )
        
        # 创建分层目录
        self.daily_dir = os.path.join(self.base_dir, "daily")          # 日线策略
        self.intraday_dir = os.path.join(self.base_dir, "intraday")    # 盘中调整
        self.archive_dir = os.path.join(self.base_dir, "archive")      # 归档
        
        # 确保目录存在
        for dir_path in [self.base_dir, self.daily_dir, self.intraday_dir, self.archive_dir]:
            os.makedirs(dir_path, exist_ok=True)
        
        self.current_state = None
        self.state_type = None  # 'daily' 或 'intraday'
        self.current_state_record = None  # 完整的状态记录
    
    def save_daily_strategy(self, strategy: Dict, reason: str = "日线分析策略") -> bool:
        """
        保存日线策略（由TradePlanAlgo调用）
        日线策略是基准策略，每天至少保存一次
        """
        try:
            # 确保信心度字段存在
            if 'confidence' not in strategy:
                strategy['confidence'] = strategy.get('feasibility_score', 0.5)
            
            # 准备状态记录
            state_record = {
                'symbol': self.symbol,
                'type': 'daily',
                'timestamp': datetime.now().isoformat(),
                'trading_date': self._get_trading_date(),
                'reason': reason,
                'state': strategy,
                'source': 'TradePlanAlgo'
            }
            
            # 保存到日线策略文件（覆盖）
            daily_file = os.path.join(self.daily_dir, f"{self.symbol}.json")
            
            # 备份旧文件（如果有）
            if os.path.exists(daily_file):
                backup_file = os.path.join(self.archive_dir, 
                                         f"{self.symbol}_daily_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json")
                shutil.copy2(daily_file, backup_file)
            
            # 保存新文件
            with open(daily_file, 'w', encoding='utf-8') as f:
                json.dump(state_record, f, ensure_ascii=False, indent=2)
            
            print(f"📅 保存日线策略: {reason} (交易日: {state_record['trading_date']})")
            
            # 同时更新当前状态
            self.current_state = strategy
            self.state_type = 'daily'
            self.current_state_record = state_record
            
            return True
            
        except Exception as e:
            print(f"❌ 日线策略保存失败: {e}")
            return False
    
    def save_intraday_adjustment(self, strategy: Dict, reason: str = "实时调整") -> bool:
        """
        保存盘中调整（由在线预测器调用）
        盘中调整只在交易时间内有效
        """
        try:
            # 检查当前是否为交易时间（如果不是交易时间也保存，但标记为非交易时间）
            is_trading_hours = self._is_trading_hours()
            
            # 确保信心度字段存在
            if 'confidence' not in strategy:
                strategy['confidence'] = strategy.get('feasibility_score', 0.5)
            
            # 准备状态记录
            state_record = {
                'symbol': self.symbol,
                'type': 'intraday',
                'timestamp': datetime.now().isoformat(),
                'trading_date': self._get_trading_date(),
                'reason': reason,
                'state': strategy,
                'source': 'OnlinePredictor',
                'is_trading_hours': is_trading_hours
            }
            
            # 保存到盘中调整文件（覆盖）
            intraday_file = os.path.join(self.intraday_dir, f"{self.symbol}.json")
            
            # 同时保存到历史记录（追加）
            history_file = os.path.join(self.intraday_dir, f"{self.symbol}_history.json")
            
            # 保存最新盘中调整
            with open(intraday_file, 'w', encoding='utf-8') as f:
                json.dump(state_record, f, ensure_ascii=False, indent=2)
            
            # 追加到历史记录
            self._append_to_history(history_file, state_record)
            
            print(f"📊 保存盘中调整: {reason}")
            
            # 同时更新当前状态
            self.current_state = strategy
            self.state_type = 'intraday'
            self.current_state_record = state_record
            
            return True
            
        except Exception as e:
            print(f"❌ 盘中调整保存失败: {e}")
            return False
    
    def load_latest_strategy(self) -> Optional[Dict]:
        """
        智能加载最新策略
        返回整个状态记录（包含类型、原因、时间戳等）
        """
        try:
            # 加载日线策略和盘中调整
            daily_state = self._load_daily_strategy()
            intraday_state = self._load_intraday_strategy()
            
            current_date = datetime.now().date()
            is_trading_hours = self._is_trading_hours()
            
            selected_record = None
            
            # 判断逻辑
            # 1. 如果是交易时间，优先检查盘中调整
            if is_trading_hours and intraday_state:
                intraday_date = self._parse_date(intraday_state.get('trading_date', ''))
                if intraday_date == current_date:
                    selected_record = intraday_state
                    print(f"📊 使用今日盘中调整 ({intraday_state.get('reason', '')})")
            
            # 2. 如果没有盘中调整，检查今日日线策略
            if not selected_record and daily_state:
                daily_date = self._parse_date(daily_state.get('trading_date', ''))
                if daily_date == current_date:
                    selected_record = daily_state
                    print(f"📅 使用今日日线策略 ({daily_state.get('reason', '')})")
            
            # 3. 如果都没有，使用最新的日线策略（可能是昨天的）
            if not selected_record and daily_state:
                selected_record = daily_state
                print(f"📅 使用最新日线策略 ({daily_state.get('reason', '')})")
            
            # 4. 最后，如果有盘中调整（可能是昨天的），使用它
            if not selected_record and intraday_state:
                selected_record = intraday_state
                print(f"📊 使用最新盘中调整 ({intraday_state.get('reason', '')})")
            
            if selected_record:
                self.current_state = selected_record['state']
                self.state_type = selected_record['type']
                self.current_state_record = selected_record
                return selected_record
            else:
                self.current_state = None
                self.state_type = None
                self.current_state_record = None
                print(f"⚠️  未找到 {self.symbol} 的策略状态")
                return None
                
        except Exception as e:
            print(f"❌ 策略加载失败: {e}")
            return None
    
    def load_state_data(self) -> Optional[Dict]:
        """加载状态数据（仅状态部分，兼容旧接口）"""
        record = self.load_latest_strategy()
        if record:
            return record['state']
        return None
    
    def get_state_info(self) -> Dict[str, Any]:
        """获取当前状态信息"""
        if not self.current_state_record:
            return {}
        
        return {
            'symbol': self.symbol,
            'type': self.state_type,
            'has_state': self.current_state is not None,
            'reason': self.current_state_record.get('reason', ''),
            'timestamp': self.current_state_record.get('timestamp', ''),
            'trading_date': self.current_state_record.get('trading_date', ''),
            'daily_exists': os.path.exists(os.path.join(self.daily_dir, f"{self.symbol}.json")),
            'intraday_exists': os.path.exists(os.path.join(self.intraday_dir, f"{self.symbol}.json")),
            'last_daily_update': self._get_file_mtime(os.path.join(self.daily_dir, f"{self.symbol}.json")),
            'last_intraday_update': self._get_file_mtime(os.path.join(self.intraday_dir, f"{self.symbol}.json"))
        }
    
    def clear_intraday_state(self, symbol: str = None):
        """清理盘中状态（每天收盘后调用）"""
        target_symbol = symbol or self.symbol
        
        intraday_file = os.path.join(self.intraday_dir, f"{target_symbol}.json")
        if os.path.exists(intraday_file):
            # 归档而不是删除
            archive_file = os.path.join(self.archive_dir, 
                                      f"{target_symbol}_intraday_{datetime.now().strftime('%Y%m%d')}.json")
            shutil.move(intraday_file, archive_file)
            print(f"🗑️  已归档盘中状态: {target_symbol}")
    
    def clear_all_states(self, symbol: str = None):
        """清理所有状态（用于重置）"""
        target_symbol = symbol or self.symbol
        
        # 清理日线文件
        daily_file = os.path.join(self.daily_dir, f"{target_symbol}.json")
        if os.path.exists(daily_file):
            os.remove(daily_file)
        
        # 清理盘中文件
        intraday_file = os.path.join(self.intraday_dir, f"{target_symbol}.json")
        if os.path.exists(intraday_file):
            os.remove(intraday_file)
        
        # 清理历史文件
        history_file = os.path.join(self.intraday_dir, f"{target_symbol}_history.json")
        if os.path.exists(history_file):
            os.remove(history_file)
        
        # 重置内部状态
        self.current_state = None
        self.state_type = None
        self.current_state_record = None
        
        print(f"🗑️  已清理所有状态: {target_symbol}")
    
    def has_today_intraday_state(self) -> bool:
        """检查是否有今日的盘中状态"""
        intraday_file = os.path.join(self.intraday_dir, f"{self.symbol}.json")
        if not os.path.exists(intraday_file):
            return False
        
        try:
            with open(intraday_file, 'r', encoding='utf-8') as f:
                state_record = json.load(f)
            
            state_date = state_record.get('trading_date', '')
            today = datetime.now().strftime('%Y-%m-%d')
            
            return state_date == today
        except:
            return False
    
    # ========== 私有方法 ==========
    
    def _load_daily_strategy(self) -> Optional[Dict]:
        """加载日线策略"""
        daily_file = os.path.join(self.daily_dir, f"{self.symbol}.json")
        if os.path.exists(daily_file):
            try:
                with open(daily_file, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except Exception as e:
                print(f"❌ 日线策略加载失败: {e}")
        return None
    
    def _load_intraday_strategy(self) -> Optional[Dict]:
        """加载盘中策略"""
        intraday_file = os.path.join(self.intraday_dir, f"{self.symbol}.json")
        if os.path.exists(intraday_file):
            try:
                with open(intraday_file, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except Exception as e:
                print(f"❌ 盘中策略加载失败: {e}")
        return None
    
    def _append_to_history(self, history_file: str, state_record: Dict):
        """追加到历史记录"""
        try:
            history_data = []
            if os.path.exists(history_file):
                with open(history_file, 'r', encoding='utf-8') as f:
                    history_data = json.load(f)
            
            # 只保留最近100条记录
            history_data.append(state_record)
            if len(history_data) > 100:
                history_data = history_data[-100:]
            
            with open(history_file, 'w', encoding='utf-8') as f:
                json.dump(history_data, f, ensure_ascii=False, indent=2)
                
        except Exception as e:
            print(f"⚠️ 历史记录保存失败: {e}")
    
    def _get_trading_date(self) -> str:
        """获取当前交易日"""
        now = datetime.now()
        
        # 如果是交易时间前（9:30前），算前一个交易日
        if now.hour < 9 or (now.hour == 9 and now.minute < 30):
            # 如果是周一，则退回上周五
            if now.weekday() == 0:  # 周一
                delta = 3  # 退回3天到上周五
            else:
                delta = 1  # 退回1天
            trading_date = now - timedelta(days=delta)
        else:
            trading_date = now
        
        return trading_date.strftime('%Y-%m-%d')
    
    def _parse_date(self, date_str: str) -> Optional[datetime.date]:
        """解析日期字符串"""
        try:
            if date_str:
                return datetime.strptime(date_str, '%Y-%m-%d').date()
        except:
            pass
        return None
    
    def _is_trading_hours(self) -> bool:
        """
        判断是否为A股交易时间
        周一至周五 9:30-11:30, 13:00-15:00
        """
        now = datetime.now()
        
        # 检查是否为周末
        if now.weekday() >= 5:  # 5=周六, 6=周日
            return False
        
        hour = now.hour
        minute = now.minute
        
        # 上午交易时间：9:30-11:30
        if (hour == 9 and minute >= 30) or (hour == 10) or (hour == 11 and minute <= 30):
            return True
        
        # 下午交易时间：13:00-15:00
        if (hour == 13) or (hour == 14) or (hour == 15 and minute == 0):
            return True
        
        return False
    
    def _get_file_mtime(self, filepath: str) -> Optional[str]:
        """获取文件修改时间"""
        if os.path.exists(filepath):
            mtime = os.path.getmtime(filepath)
            return datetime.fromtimestamp(mtime).strftime('%Y-%m-%d %H:%M:%S')
        return None
    
    def get_recent_states(self, limit: int = 10) -> List[Dict]:
        """获取最近的状态记录"""
        states = []
        
        # 从日线历史获取
        daily_file = os.path.join(self.daily_dir, f"{self.symbol}.json")
        if os.path.exists(daily_file):
            try:
                with open(daily_file, 'r', encoding='utf-8') as f:
                    daily_record = json.load(f)
                states.append(daily_record)
            except:
                pass
        
        # 从盘中历史获取
        history_file = os.path.join(self.intraday_dir, f"{self.symbol}_history.json")
        if os.path.exists(history_file):
            try:
                with open(history_file, 'r', encoding='utf-8') as f:
                    history_data = json.load(f)
                # 添加最近的记录
                states.extend(history_data[-limit:])
            except:
                pass
        
        # 按时间戳排序
        states.sort(key=lambda x: x.get('timestamp', ''), reverse=True)
        
        return states[:limit]