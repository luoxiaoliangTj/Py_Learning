# [file name]: tools/daily_downloader.py
#!/usr/bin/env python3
"""
daily_downloader.py - 日线数据下载工具
固定版：新浪（主）+ 搜狐（备）+ Tushare（备）三数据源
确保数据格式兼容，文件命名正确
支持从token目录自动加载Tushare Token
"""
import os
import sys
import pandas as pd
from datetime import datetime, timedelta
import requests
import json
import time
import random

# 尝试导入Tushare，如果失败则禁用该数据源
try:
    import tushare as ts
    TUSHARE_AVAILABLE = True
except ImportError:
    TUSHARE_AVAILABLE = False
    print("警告: 未安装tushare，如需使用请运行: pip install tushare")

# 修复导入问题 - 直接使用相对路径
try:
    # 尝试直接导入 config
    sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    from config.config import cfg
    from config.logging_config import get_logger
except ImportError:
    # 如果导入失败，使用默认配置
    print("警告: 无法导入 config 模块，使用默认配置")
    
    # 创建默认配置类
    class DefaultConfig:
        def __init__(self):
            self.DATA_DIR = "./data"
            self.TUSHARE_TOKEN = ""
    
    cfg = DefaultConfig()
    
    # 创建默认日志函数
    def get_logger(name):
        class DefaultLogger:
            def info(self, msg):
                print(f"[INFO] {msg}")
            def warning(self, msg):
                print(f"[WARNING] {msg}")
            def error(self, msg):
                print(f"[ERROR] {msg}")
            def debug(self, msg):
                print(f"[DEBUG] {msg}")
        return DefaultLogger()

logger = get_logger("DailyDownloader")

class DailyDataDownloader:
    """日线数据下载器 - 新浪（主）+ 搜狐（备）+ Tushare（备）"""
    
    def __init__(self):
        # 数据源配置 - 固定三个数据源
        self.supported_sources = ['sina', 'sohu']
        
        # 如果Tushare可用，添加到数据源列表
        if TUSHARE_AVAILABLE:
            self.supported_sources.append('tushare')
        
        self.data_source_config = {
            'sina': {
                'name': '新浪财经', 
                'description': '主数据源，历史数据完整稳定',
                'enabled': True
            },
            'sohu': {
                'name': '搜狐财经', 
                'description': '备用数据源，历史数据完整',
                'enabled': True
            }
        }
        
        # 添加Tushare配置（如果可用）
        if TUSHARE_AVAILABLE:
            self.data_source_config['tushare'] = {
                'name': 'Tushare',
                'description': '专业金融数据源，需要token',
                'enabled': True
            }
        
        # 标准数据格式定义
        self.standard_columns = ['日期', '开盘', '最高', '最低', '收盘', '成交量', '成交额']
        
        # 用户代理列表
        self.user_agents = [
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
        ]
        
        # 初始化Tushare token - 从配置中获取
        self.tushare_token = cfg.TUSHARE_TOKEN
        
        # 如果从配置中获取到了token，自动设置
        if self.tushare_token and TUSHARE_AVAILABLE:
            self.set_tushare_token(self.tushare_token)
            logger.info(f"已从配置文件加载 Tushare Token: {self.tushare_token[:8]}...{self.tushare_token[-4:] if len(self.tushare_token) > 12 else ''}")
        elif TUSHARE_AVAILABLE and self.data_source_config.get('tushare', {}).get('enabled', False):
            logger.warning("Tushare数据源已启用但未检测到Token，将自动禁用")
            self.data_source_config['tushare']['enabled'] = False
    
    def get_available_sources(self):
        """获取可用的数据源列表"""
        return [source for source, config in self.data_source_config.items() 
                if config['enabled']]
    
    def get_random_user_agent(self):
        """获取随机用户代理"""
        return random.choice(self.user_agents)
    
    def set_tushare_token(self, token):
        """设置Tushare token"""
        self.tushare_token = token
        if TUSHARE_AVAILABLE and token:
            ts.set_token(token)
            # 同时更新配置对象的token
            try:
                if hasattr(cfg, 'save_tushare_token'):
                    cfg.save_tushare_token(token)
                logger.info("Tushare token已设置并保存")
            except Exception as e:
                logger.warning(f"保存Tushare token到配置文件失败: {e}")
                logger.info("Tushare token已临时设置（未保存）")
    
    def download_from_sina(self, symbol, years=8, max_retries=3):
        """从新浪财经下载日线数据 - 主数据源"""
        for attempt in range(max_retries):
            try:
                logger.info(f"从新浪财经下载 {symbol} 的日线数据...")
                
                # 使用稳定可靠的新浪历史数据API
                if symbol.startswith('6'):
                    sina_symbol = f"sh{symbol}"  # 沪市
                else:
                    sina_symbol = f"sz{symbol}"  # 深市
                    
                url = "http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData"
                
                # 计算数据长度（大约的交易天数）
                datalen = years * 250  # 每年大约250个交易日
                
                params = {
                    'symbol': sina_symbol,
                    'scale': 240,  # 240分钟，即日线
                    'ma': 'no',
                    'datalen': datalen
                }
                
                headers = {
                    'User-Agent': self.get_random_user_agent(),
                    'Referer': 'http://finance.sina.com.cn',
                    'Accept': '*/*',
                    'Accept-Encoding': 'gzip, deflate',
                    'Connection': 'keep-alive'
                }
                
                # 添加请求延迟，防止请求过快
                time.sleep(0.5)
                
                response = requests.get(url, params=params, headers=headers, timeout=30)
                response.raise_for_status()
                
                # 解析JSON数据
                data = response.json()
                if not data:
                    if attempt < max_retries - 1:
                        time.sleep(2)
                        continue
                    raise ValueError("新浪财经返回空数据")
                
                # 转换新浪数据为标准格式
                standard_df = self._convert_sina_to_standard(data, symbol)
                
                if standard_df.empty:
                    if attempt < max_retries - 1:
                        time.sleep(2)
                        continue
                    raise ValueError("新浪财经数据转换后为空")
                
                logger.info(f"新浪财经下载成功: {len(standard_df)} 条记录")
                return True, standard_df, "新浪财经下载成功"
                
            except Exception as e:
                logger.error(f"新浪财经下载失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(2)
                    continue
                return False, None, f"新浪财经下载失败: {str(e)}"
    
    def download_from_sohu(self, symbol, years=8, max_retries=3):
        """从搜狐财经下载日线数据 - 使用001download.py中的实现"""
        for attempt in range(max_retries):
            try:
                logger.info(f"从搜狐财经下载 {symbol} 的日线数据...")
                
                # 搜狐财经API
                if symbol.startswith('6'):
                    sohu_symbol = f"cn_{symbol}"
                else:
                    sohu_symbol = f"cn_{symbol}"
                
                url = f"http://q.stock.sohu.com/hisHq"
                params = {
                    'code': f"zs_{symbol}" if symbol in ['000001', '399001'] else sohu_symbol,
                    'start': (datetime.now() - timedelta(days=years * 365)).strftime('%Y%m%d'),
                    'end': datetime.now().strftime('%Y%m%d'),
                    'stat': '1',
                    'order': 'D',
                    'period': 'd'
                }
                
                headers = {
                    'User-Agent': self.get_random_user_agent(),
                    'Referer': 'http://q.stock.sohu.com/'
                }
                
                response = requests.get(url, params=params, headers=headers, timeout=15)
                response.raise_for_status()
                
                data = response.json()
                
                if not data or 'hq' not in data[0]:
                    raise ValueError("搜狐财经返回空数据")
                
                # 转换数据格式
                standard_df = self._convert_sohu_to_standard(data[0]['hq'], symbol)
                
                if standard_df.empty:
                    raise ValueError("搜狐财经数据转换后为空")
                
                logger.info(f"搜狐财经下载成功: {len(standard_df)} 条记录")
                return True, standard_df, "搜狐财经下载成功"
                
            except Exception as e:
                logger.error(f"搜狐财经下载失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                    continue
                return False, None, f"搜狐财经下载失败: {str(e)}"
    
    def download_from_tushare(self, symbol, years=8, max_retries=3):
        """从Tushare下载日线数据 - 备用数据源"""
        if not TUSHARE_AVAILABLE:
            return False, None, "Tushare未安装，请运行: pip install tushare"
        
        if not self.tushare_token:
            return False, None, "未设置Tushare token，请先设置token"
        
        for attempt in range(max_retries):
            try:
                logger.info(f"从Tushare下载 {symbol} 的日线数据...")
                
                # 计算时间范围
                end_date = datetime.now().strftime('%Y%m%d')
                start_date = (datetime.now() - timedelta(days=years * 365)).strftime('%Y%m%d')
                
                # 确定股票代码格式
                if symbol.startswith('6'):
                    ts_code = f"{symbol}.SH"  # 沪市
                else:
                    ts_code = f"{symbol}.SZ"  # 深市
                
                # 获取Tushare数据
                pro = ts.pro_api()
                df = pro.daily(ts_code=ts_code, start_date=start_date, end_date=end_date)
                
                if df is None or df.empty:
                    raise ValueError("Tushare返回空数据")
                
                # 转换数据格式
                standard_df = self._convert_tushare_to_standard(df, symbol)
                
                if standard_df.empty:
                    raise ValueError("Tushare数据转换后为空")
                
                logger.info(f"Tushare下载成功: {len(standard_df)} 条记录")
                return True, standard_df, "Tushare下载成功"
                
            except Exception as e:
                logger.error(f"Tushare下载失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(2)
                    continue
                return False, None, f"Tushare下载失败: {str(e)}"
    
    def _convert_sina_to_standard(self, sina_data, symbol):
        """将新浪财经数据转换为标准格式"""
        try:
            records = []
            
            for item in sina_data:
                # 新浪返回的字段：day, open, high, low, close, volume
                record = {
                    '日期': pd.to_datetime(item['day']),
                    '开盘': float(item['open']),
                    '最高': float(item['high']),
                    '最低': float(item['low']),
                    '收盘': float(item['close']),
                    '成交量': int(float(item['volume'])) * 100,  # 新浪返回的是手数，转换为股数
                    '成交额': float(item.get('amount', 0))  # 如果有成交额字段，直接使用
                }
                
                # 如果没有成交额字段，估算成交额（收盘价 * 成交量）
                if record['成交额'] == 0:
                    record['成交额'] = record['收盘'] * record['成交量']
                
                records.append(record)
            
            df = pd.DataFrame(records)
            
            # 按日期排序
            if not df.empty:
                df = df.sort_values('日期').reset_index(drop=True)
                df = self._clean_data(df)
            
            return df
            
        except Exception as e:
            logger.error(f"转换新浪数据失败: {e}")
            return pd.DataFrame()
    
    def _convert_sohu_to_standard(self, sohu_data, symbol):
        """将搜狐财经数据转换为标准格式 - 使用001download.py中的实现"""
        try:
            records = []
            
            for item in sohu_data:
                record = {
                    '日期': pd.to_datetime(item[0]),
                    '开盘': float(item[1]),
                    '收盘': float(item[2]),
                    '涨跌额': float(item[3]),
                    '涨跌幅': float(item[4].strip('%')),
                    '最低': float(item[5]),
                    '最高': float(item[6]),
                    '成交量': int(float(item[7])),
                    '成交额': float(item[8])
                }
                # 只保留需要的列
                record = {k: v for k, v in record.items() if k in self.standard_columns}
                records.append(record)
            
            df = pd.DataFrame(records)
            
            if not df.empty:
                df = df.sort_values('日期').reset_index(drop=True)
                df = self._clean_data(df)
            
            return df
            
        except Exception as e:
            logger.error(f"转换搜狐财经数据失败: {e}")
            return pd.DataFrame()
    
    def _convert_tushare_to_standard(self, tushare_data, symbol):
        """将Tushare数据转换为标准格式"""
        try:
            # Tushare数据列名映射
            column_mapping = {
                'trade_date': '日期',
                'open': '开盘',
                'high': '最高',
                'low': '最低',
                'close': '收盘',
                'vol': '成交量',
                'amount': '成交额'
            }
            
            # 重命名列
            df = tushare_data.rename(columns=column_mapping)
            
            # 转换日期格式
            df['日期'] = pd.to_datetime(df['日期'], format='%Y%m%d')
            
            # 选择标准列
            available_columns = [col for col in self.standard_columns if col in df.columns]
            standard_df = df[available_columns].copy()
            
            # 按日期排序
            if not standard_df.empty:
                standard_df = standard_df.sort_values('日期').reset_index(drop=True)
                standard_df = self._clean_data(standard_df)
            
            return standard_df
            
        except Exception as e:
            logger.error(f"转换Tushare数据失败: {e}")
            return pd.DataFrame()
    
    def _clean_data(self, df):
        """数据清洗：处理缺失值和异常值"""
        if df.empty:
            return df
        
        # 修复FutureWarning：使用新的ffill和bfill方法
        df = df.ffill()  # 前向填充
        df = df.bfill()  # 后向填充处理开头缺失值
        
        # 处理极端异常值：使用3σ原则
        for col in ['开盘', '最高', '最低', '收盘']:
            if col in df.columns:
                mean = df[col].mean()
                std = df[col].std()
                # 将超出3σ的值替换为均值
                if std > 0:  # 避免除零
                    df[col] = df[col].apply(lambda x: mean if (x < mean - 3*std or x > mean + 3*std) else x)
        
        # 处理成交量异常值（使用中位数和IQR）
        if '成交量' in df.columns:
            q1 = df['成交量'].quantile(0.25)
            q3 = df['成交量'].quantile(0.75)
            iqr = q3 - q1
            
            if iqr > 0:  # 避免无效的IQR
                lower_bound = q1 - 1.5 * iqr
                upper_bound = q3 + 1.5 * iqr
                
                median_volume = df['成交量'].median()
                df['成交量'] = df['成交量'].apply(
                    lambda x: median_volume if (x < lower_bound or x > upper_bound) else x
                )
        
        return df
    
    def validate_data_quality(self, df, symbol):
        """验证数据质量"""
        checks = []
        
        checks.append(('数据量', len(df) > 0, f"没有数据"))
        checks.append(('列完整性', set(self.standard_columns).issubset(set(df.columns)), "缺少必要列"))
        
        if '日期' in df.columns and len(df) > 1:
            df_sorted = df.sort_values('日期')
            date_gaps = (df_sorted['日期'].diff().dt.days > 5).sum()
            checks.append(('日期连续性', date_gaps < len(df) * 0.5, f"发现 {date_gaps} 个较大日期间隔"))
        
        if len(df) > 0:
            price_checks = []
            for price_col in ['开盘', '最高', '最低', '收盘']:
                if price_col in df.columns:
                    valid_prices = (df[price_col] > 0).all()
                    price_checks.append(valid_prices)
            
            if price_checks:
                checks.append(('价格合理性', all(price_checks), "存在无效价格数据"))
        
        passed = len(df) > 0 and all([check[1] for check in checks if check[0] != '数据量'])
        details = [f"{name}: {'通过' if result else '失败 - ' + message}" 
                  for name, result, message in checks]
        
        return passed, details
    
    def save_data(self, df, symbol, backup_existing=True):
        """保存数据到标准文件 - 修复文件命名"""
        # 修复文件命名：使用小写
        file_path = os.path.join(cfg.DATA_DIR, f"ccb_{symbol}_daily.csv")
        
        if backup_existing and os.path.exists(file_path):
            backup_path = os.path.join(cfg.DATA_DIR, f"ccb_{symbol}_daily_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv")
            try:
                import shutil
                shutil.copy2(file_path, backup_path)
                logger.info(f"已备份旧数据到: {backup_path}")
            except Exception as e:
                logger.warning(f"备份旧数据失败: {e}")
        
        os.makedirs(cfg.DATA_DIR, exist_ok=True)
        
        try:
            # 确保日期格式正确
            if '日期' in df.columns:
                df = df.copy()
                df['日期'] = pd.to_datetime(df['日期']).dt.strftime('%Y-%m-%d')
            
            df.to_csv(file_path, index=False, encoding='utf-8-sig')
            logger.info(f"数据已保存: {file_path}")
            
            # 验证文件是否成功创建
            if os.path.exists(file_path) and os.path.getsize(file_path) > 100:
                return file_path
            else:
                logger.error("文件保存后验证失败")
                return None
                
        except Exception as e:
            logger.error(f"保存数据失败: {e}")
            return None
    
    def download_daily_data(self, symbol, years=8, preferred_source=None):
        """
        下载日线数据的主函数
        """
        logger.info(f"开始下载 {symbol} 的日线数据...")
        
        available_sources = self.get_available_sources()
        if not available_sources:
            return False, "没有可用的数据源", None, None
        
        # 确定数据源使用顺序 - 默认新浪优先
        if preferred_source and preferred_source in available_sources:
            source_order = [preferred_source] + [s for s in available_sources if s != preferred_source]
        else:
            source_order = available_sources
        
        for source in source_order:
            logger.info(f"尝试数据源: {source}")
            
            if source == 'sina':
                success, df, message = self.download_from_sina(symbol, years)
            elif source == 'sohu':
                success, df, message = self.download_from_sohu(symbol, years)
            elif source == 'tushare':
                success, df, message = self.download_from_tushare(symbol, years)
            else:
                continue
            
            if success:
                quality_ok, quality_details = self.validate_data_quality(df, symbol)
                
                if quality_ok:
                    file_path = self.save_data(df, symbol)
                    if file_path:
                        return True, f"{message} | 数据质量验证通过", file_path, source
                    else:
                        logger.warning(f"数据源 {source} 的数据保存失败")
                else:
                    logger.warning(f"数据源 {source} 的数据质量验证失败: {quality_details}")
            else:
                logger.warning(f"数据源 {source} 下载失败: {message}")
        
        return False, "所有数据源均失败", None, None
    
    def check_existing_data(self, symbol):
        """检查现有数据状态"""
        # 修复文件命名：使用小写
        file_path = os.path.join(cfg.DATA_DIR, f"ccb_{symbol}_daily.csv")
        
        if not os.path.exists(file_path):
            return False, "数据文件不存在", None
        
        try:
            df = pd.read_csv(file_path)
            if df.empty:
                return False, "数据文件为空", None
            
            if '日期' in df.columns:
                df['日期'] = pd.to_datetime(df['日期'])
                data_days = len(df)
                date_range = f"{df['日期'].min().strftime('%Y-%m-%d')} 至 {df['日期'].max().strftime('%Y-%m-%d')}"
                days_span = (df['日期'].max() - df['日期'].min()).days
                
                # 计算涨跌幅
                if len(df) >= 2:
                    start_price = df['收盘'].iloc[0]
                    end_price = df['收盘'].iloc[-1]
                    change_pct = (end_price / start_price - 1) * 100
                    price_info = f", 涨跌幅: {change_pct:+.1f}%"
                else:
                    price_info = ""
                
                info = f"数据量: {data_days}条, 时间范围: {date_range} ({days_span}天{price_info})"
                return True, info, df
            else:
                return False, "数据文件格式错误: 缺少日期列", None
                
        except Exception as e:
            return False, f"数据文件读取失败: {e}", None

# 工具接口函数
def run_tool():
    """工具入口函数 - 供RunUI调用"""
    try:
# 从环境变量获取当前股票（如果RunUI已选择）
        current_symbol = os.environ.get("STOCK_SYMBOL", None)
        
        if current_symbol:
            default_symbol = current_symbol
            print("\\n" + "="*50)
            print("📊 日线数据下载工具（固定三数据源版）")
            print("="*50)
            print(f"🔍 当前选中股票: {default_symbol}")
            symbol_input_prompt = f"请输入股票代码 (回车默认{default_symbol}): "
        else:
            default_symbol = None
            symbol_input_prompt = "请输入股票代码 (如: 000001): "

        downloader = DailyDataDownloader()
        
        available_sources = downloader.get_available_sources()
        print("可用数据源:")
        for i, source in enumerate(available_sources, 1):
            config = downloader.data_source_config[source]
            print(f"  {i}. {config['name']} - {config['description']}")
        print()

        # 检查Tushare状态并给出提示
        if TUSHARE_AVAILABLE and 'tushare' in downloader.supported_sources:
            if downloader.tushare_token:
                masked_token = f"{downloader.tushare_token[:8]}...{downloader.tushare_token[-4:]}" if len(downloader.tushare_token) > 12 else downloader.tushare_token
                print(f"✅ Tushare Token 状态: 已加载 ({masked_token})")
            else:
                print("⚠️  Tushare Token 状态: 未配置")
                print("   如需使用Tushare，请在token目录下创建tushare_token.txt文件")
                print("   或运行: python token/init_tokens.py")
                
                choice = input("\\n是否手动输入Tushare Token临时使用? (y/N): ").strip().lower()
                if choice == 'y':
                    token_input = input("请输入Tushare Token: ").strip()
                    if token_input:
                        # 尝试保存到配置文件
                        try:
                            if hasattr(cfg, 'save_tushare_token'):
                                cfg.save_tushare_token(token_input)
                                print("✅ Token已保存到配置文件")
                            else:
                                downloader.set_tushare_token(token_input)
                                print("✅ Token已临时设置（未保存）")
                        except Exception as e:
                            downloader.set_tushare_token(token_input)
                            print("✅ Token已临时设置（保存失败）")
                    else:
                        print("未提供Token，将禁用Tushare数据源")
                        downloader.data_source_config['tushare']['enabled'] = False
                        available_sources = downloader.get_available_sources()
                else:
                    print("禁用Tushare数据源")
                    downloader.data_source_config['tushare']['enabled'] = False
                    available_sources = downloader.get_available_sources()

        symbol = input(symbol_input_prompt).strip()
        if not symbol and default_symbol:
            symbol = default_symbol
        if not symbol:
            print("❌ 股票代码不能为空")
            return
        
        if not symbol.isdigit() or len(symbol) != 6:
            print("❌ 股票代码必须是6位数字")
            return
        
        exists, info, existing_df = downloader.check_existing_data(symbol)
        if exists:
            print(f"📁 现有数据: {info}")
            
            choice = input("\n是否重新下载? (y/N): ").strip().lower()
            if choice != 'y':
                print("取消下载")
                return
        else:
            print(f"📁 现有数据: {info}")
        
        years_input = input(f"数据年限 (回车默认8年): ").strip()
        try:
            years = 8 if not years_input else int(years_input)
            if years <= 0 or years > 20:
                print("❌ 年限必须在1-20年之间")
                return
        except ValueError:
            print("❌ 请输入有效的数字")
            return
        
        print("\n选择数据源 (回车自动选择):")
        for i, source in enumerate(available_sources, 1):
            config = downloader.data_source_config[source]
            print(f"  {i}. {config['name']}")
        
        source_choice = input("请选择: ").strip()
        preferred_source = None
        if source_choice and source_choice.isdigit():
            idx = int(source_choice) - 1
            if 0 <= idx < len(available_sources):
                preferred_source = available_sources[idx]
        
        print(f"\n开始下载 {symbol} 的 {years} 年日线数据...")
        success, message, file_path, source_used = downloader.download_daily_data(symbol, years, preferred_source)
        
        print("\n" + "="*50)
        if success:
            print(f"✅ 下载成功!")
            print(f"   数据源: {downloader.data_source_config[source_used]['name']}")
            print(f"   文件位置: {file_path}")
            print(f"   结果: {message}")
            
            exists, info, _ = downloader.check_existing_data(symbol)
            if exists:
                print(f"   最终状态: {info}")
        else:
            print(f"❌ 下载失败!")
            print(f"   错误信息: {message}")
            print(f"   建议: 请检查网络连接，或稍后重试")
        
        print("="*50)
        
    except Exception as e:
        logger.error(f"工具运行异常: {e}")
        print(f"❌ 工具运行出错: {e}")

if __name__ == "__main__":
    run_tool()