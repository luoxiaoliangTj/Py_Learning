# tools/position_manager_tool.py
#!/usr/bin/env python3
"""
持仓管理工具 - 独立工具模块
管理真实持仓信息，与回测系统解耦
支持从 Markdown 持仓文件自动导入最新持仓与资金信息
"""

import os
import json
import sys
import re
import logging
import glob
from datetime import datetime, timedelta

# 添加项目路径
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)

from config.config import cfg
from config.logging_config import get_logger

# 尝试导入 tushare
try:
    import tushare as ts
    TUSHARE_AVAILABLE = True
except ImportError:
    TUSHARE_AVAILABLE = False

# 尝试导入 CapitalManager
try:
    from Tools.capital_manager_tool import CapitalManager
    CAPITAL_MANAGER_AVAILABLE = True
except ImportError:
    CAPITAL_MANAGER_AVAILABLE = False

logger = get_logger("PositionManager")

# 持仓文件目录（固定路径）
HOLDINGS_MD_DIR = "/storage/emulated/0/Documents/mindmaps/炒股/"


class PositionManager:
    """持仓管理器 - 独立于交易模块"""

    def __init__(self):
        self.positions_file = os.path.join(cfg.DATA_DIR, "real_positions.json")
        self.cache_file = os.path.join(cfg.DATA_DIR, "name_code_cache.json")
        self.stock_list_file = os.path.join(cfg.DATA_DIR, "stock_list_cache.json")
        self.positions = {}
        self.name_code_cache = {}   # 股票名称 -> 代码映射缓存
        self.stock_list = {}        # 全量股票列表 {名称: 代码}
        self.load_positions()
        self.load_cache()
        self.load_stock_list()

    def load_positions(self):
        """加载持仓信息"""
        try:
            if os.path.exists(self.positions_file):
                with open(self.positions_file, 'r', encoding='utf-8') as f:
                    self.positions = json.load(f)
                logger.info(f"已加载 {len(self.positions)} 个持仓")
            else:
                self.positions = {}
                logger.info("无现有持仓记录")
        except Exception as e:
            logger.error(f"加载持仓失败: {e}")
            self.positions = {}

    def save_positions(self):
        """保存持仓信息"""
        try:
            os.makedirs(cfg.DATA_DIR, exist_ok=True)
            with open(self.positions_file, 'w', encoding='utf-8') as f:
                json.dump(self.positions, f, ensure_ascii=False, indent=2)
            logger.info("持仓信息已保存")
            return True
        except Exception as e:
            logger.error(f"保存持仓失败: {e}")
            return False

    def load_cache(self):
        """加载名称-代码缓存"""
        try:
            if os.path.exists(self.cache_file):
                with open(self.cache_file, 'r', encoding='utf-8') as f:
                    self.name_code_cache = json.load(f)
                logger.info(f"已加载 {len(self.name_code_cache)} 条名称-代码映射")
            else:
                self.name_code_cache = {}
        except Exception as e:
            logger.error(f"加载缓存失败: {e}")
            self.name_code_cache = {}

    def save_cache(self):
        """保存名称-代码缓存"""
        try:
            with open(self.cache_file, 'w', encoding='utf-8') as f:
                json.dump(self.name_code_cache, f, ensure_ascii=False, indent=2)
            logger.info("名称-代码缓存已保存")
            return True
        except Exception as e:
            logger.error(f"保存缓存失败: {e}")
            return False

    def load_stock_list(self):
        """
        加载全量股票列表（用于快速名称->代码映射）
        优先使用 Tushare（一次性获取全量），失败则使用本地缓存
        """
        # 1. 尝试从本地缓存加载（7天有效期）
        if os.path.exists(self.stock_list_file):
            try:
                with open(self.stock_list_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                last_update = data.get('last_update')
                if last_update:
                    last_time = datetime.strptime(last_update, '%Y-%m-%d %H:%M:%S')
                    if datetime.now() - last_time < timedelta(days=7):
                        self.stock_list = data.get('stock_list', {})
                        logger.info(f"已加载股票列表缓存 ({len(self.stock_list)} 条)")
                        return
                    else:
                        logger.info("股票列表缓存已过期，尝试更新...")
            except Exception as e:
                logger.warning(f"加载股票列表缓存失败: {e}")

        # 2. 尝试从 Tushare 获取全量股票列表（一次性，避免频繁调用）
        if TUSHARE_AVAILABLE:
            try:
                # 设置 token
                if hasattr(cfg, 'TUSHARE_TOKEN') and cfg.TUSHARE_TOKEN:
                    ts.set_token(cfg.TUSHARE_TOKEN)

                pro = ts.pro_api()
                # 获取所有上市股票（一次性获取全部）
                df = pro.stock_basic(
                    exchange='',
                    list_status='L',
                    fields='ts_code,name'
                )

                if df is not None and not df.empty:
                    stock_map = {}
                    for _, row in df.iterrows():
                        name = row['name'].strip()
                        code = row['ts_code'].split('.')[0]
                        stock_map[name] = code

                    self.stock_list = stock_map

                    # 保存到缓存文件
                    data = {
                        'stock_list': stock_map,
                        'last_update': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                    }
                    with open(self.stock_list_file, 'w', encoding='utf-8') as f:
                        json.dump(data, f, ensure_ascii=False, indent=2)

                    logger.info(f"已从 Tushare 获取股票列表 ({len(stock_map)} 条)")
                    return
                else:
                    logger.warning("Tushare 返回空数据")

            except Exception as e:
                logger.warning(f"从 Tushare 获取股票列表失败: {e}")

        # 3. 如果 Tushare 失败，尝试使用已有缓存（即使过期）
        if os.path.exists(self.stock_list_file):
            try:
                with open(self.stock_list_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                self.stock_list = data.get('stock_list', {})
                if self.stock_list:
                    logger.warning(f"使用已过期的股票列表缓存 ({len(self.stock_list)} 条)")
                    return
            except Exception:
                pass

        # 4. 最终 fallback：使用名称-代码缓存
        self.stock_list = self.name_code_cache.copy()
        if self.stock_list:
            logger.warning(f"使用名称-代码缓存作为股票列表 ({len(self.stock_list)} 条)")
        else:
            logger.warning("无任何股票列表可用，将仅依赖文件中的代码列或名称匹配")

    @staticmethod
    def _clean_number_str(number_text):
        """
        清洗数字字符串，移除千分位逗号、货币符号等非数字字符（保留负号和小数点），
        并转换为整数或浮点数。如果无法解析，返回0.0。

        参数:
            number_text (str): 可能包含逗号的原始数字文本，如 "1,234.56", "￥-789.00"

        返回:
            float 或 int: 清洗后的纯数字。整数会尽量返回int类型。
        """
        if not number_text or not isinstance(number_text, str):
            return 0.0

        # 移除常见的干扰字符：货币符号、千分位逗号、空格、中文括号等
        # 保留负号、数字和小数点
        cleaned = re.sub(r'[^\d.-]', '', number_text)

        # 处理可能出现的多个负号或小数点（非法情况）
        if cleaned.count('-') > 1 or cleaned.count('.') > 1:
            # 如果格式非常混乱，尝试更保守的提取：匹配第一个合法的数字模式
            match = re.search(r'-?\d+\.?\d*', number_text.replace(',', ''))
            if match:
                cleaned = match.group()
            else:
                return 0.0

        if not cleaned or cleaned in ['-', '.']:
            return 0.0

        try:
            # 转换为浮点数
            num_as_float = float(cleaned)
            # 如果是整数，则返回int（例如 3100.0 -> 3100）
            if num_as_float.is_integer():
                return int(num_as_float)
            return num_as_float
        except ValueError:
            return 0.0

    def _fuzzy_match_name(self, name):
        """
        在现有持仓中通过名称模糊匹配（增强版）
        处理括号、空格、大小写、部分包含等常见差异
        返回匹配的 symbol 或 None
        """
        if not name or not self.positions:
            return None

        # 预处理函数：移除括号内容、空格，转小写
        def preprocess(n):
            if not n:
                return ""
            n = re.sub(r'[\(（\[【].*?[\)）\]】]', '', n)  # 移除括号及其中内容
            n = re.sub(r'\s+', '', n)  # 移除所有空白字符
            n = n.lower()
            return n

        processed_target = preprocess(name)
        if not processed_target:
            return None

        best_match = None
        best_score = 0.7  # 相似度阈值，可根据需要调整

        for symbol, info in self.positions.items():
            db_name = info.get('stock_name', '')
            if not db_name:
                continue

            processed_db = preprocess(db_name)
            if not processed_db:
                continue

            # 情况1: 完全相等（预处理后）
            if processed_target == processed_db:
                return symbol

            # 情况2: 相互包含
            if processed_target in processed_db or processed_db in processed_target:
                # 计算包含比例作为相似度
                longer = max(len(processed_target), len(processed_db))
                shorter = min(len(processed_target), len(processed_db))
                contain_ratio = shorter / longer if longer > 0 else 0
                if contain_ratio > best_score:
                    best_score = contain_ratio
                    best_match = symbol
                    logger.debug(f"模糊匹配候选: '{name}' -> '{db_name}' ({symbol}), 分数: {contain_ratio:.2f}")

        return best_match

    def get_position(self, symbol):
        """获取指定股票持仓"""
        return self.positions.get(symbol, {
            'shares': 0,
            'cost_price': 0,
            'stock_name': '',
            'last_updated': None
        })

    def update_position(self, symbol, shares, cost_price, stock_name=None):
        """更新单个股票持仓"""
        # 如果 shares == 0 且股票已存在，保留原成本价
        if shares == 0 and symbol in self.positions:
            existing = self.positions[symbol]
            final_name = stock_name or existing.get('stock_name', f"股票{symbol}")
            # 清仓时，明确保留原成本价
            final_cost = existing.get('cost_price', 0.0)
        else:
            # 新增或修改持仓
            final_name = stock_name or f"股票{symbol}"
            # 直接使用传入的 cost_price，允许负数
            final_cost = cost_price

        self.positions[symbol] = {
            'shares': shares,
            'cost_price': final_cost,  # 现在 final_cost 可以是负数
            'stock_name': final_name,
            'last_updated': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        }
        return self.save_positions()

    def delete_position(self, symbol):
        """删除股票持仓"""
        if symbol in self.positions:
            del self.positions[symbol]
            result = self.save_positions()
            if result:
                logger.info(f"已删除 {symbol} 持仓")
            return result
        return False

    def get_all_positions(self):
        """获取所有持仓"""
        return self.positions

    def clear_all_positions(self):
        """清空所有持仓"""
        self.positions = {}
        return self.save_positions()

    def _get_stock_code_from_name_local(self, stock_name):
        """
        根据股票名称从本地获取股票代码
        顺序：名称-代码缓存 -> 全量股票列表
        返回 (代码, 是否成功)
        """
        # 1. 检查名称-代码缓存
        if stock_name in self.name_code_cache:
            code = self.name_code_cache[stock_name]
            logger.debug(f"从本地缓存获取 {stock_name} 的代码: {code}")
            return code, True

        # 2. 使用全量股票列表（从 Tushare 获取）
        if self.stock_list and stock_name in self.stock_list:
            code = self.stock_list[stock_name]
            self.name_code_cache[stock_name] = code
            self.save_cache()
            logger.info(f"从股票列表获取 {stock_name} 的代码: {code}")
            return code, True

        # 无法获取，返回失败
        logger.debug(f"本地无法获取 {stock_name} 的代码")
        return None, False

    def _parse_md_file(self, filepath):
        """
        解析持仓 Markdown 文件 (增强版：支持股票代码列)
        返回 (account_info, stocks_list)
        account_info = {'total_assets': float, 'available_cash': float}
        stocks_list = [{'symbol': str, 'name': str, 'shares': int, 'cost_price': float}, ...]
        """
        account_info = {'total_assets': 0.0, 'available_cash': 0.0}
        stocks = []

        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
        except Exception as e:
            logger.error(f"读取文件失败: {e}")
            return account_info, stocks

        # 解析账户总览部分
        total_assets_match = re.search(r'总资产\s*:\s*([￥$]?\s*[\d,]+\.?\d*)', content)
        if total_assets_match:
            account_info['total_assets'] = self._clean_number_str(total_assets_match.group(1))

        available_cash_match = re.search(r'可用资金\s*:\s*([￥$]?\s*[\d,]+\.?\d*)', content)
        if available_cash_match:
            account_info['available_cash'] = self._clean_number_str(available_cash_match.group(1))

        # 找到表头行
        lines = content.split('\n')
        header_line = None
        for line in lines:
            if '股票名称' in line and '市值' in line:
                header_line = line
                break
        if not header_line:
            logger.warning("未找到持仓表格表头")
            return account_info, stocks

        # 解析表头列名
        headers = [cell.strip() for cell in header_line.split('|')[1:-1]]
        # 确定所需列的索引
        col_index = {}
        target_cols = ['股票名称 (链接)', '持仓/可用', '成本价', '股票代码']
        for i, col in enumerate(headers):
            for target in target_cols:
                if target in col:  # 使用包含关系，更灵活
                    col_index[target] = i
                    break

        # 检查必要列是否存在
        if '股票名称 (链接)' not in col_index or '持仓/可用' not in col_index or '成本价' not in col_index:
            logger.warning(f"表格缺少必要列: {col_index}")
            return account_info, stocks
        # "股票代码"列是可选的，如果没有，后续逻辑会处理

        # 遍历数据行
        for line in lines:
            line = line.strip()
            if not line.startswith('|'):
                continue
            if '---' in line:  # 跳过分隔行
                continue

            cells = line.split('|')[1:-1]  # 移除首尾的空白单元格
            if len(cells) <= max(col_index.values()):
                continue  # 跳过列数不足的行

            # --- 1. 提取股票名称 ---
            name_cell = cells[col_index['股票名称 (链接)']].strip()
            match_name = re.search(r'\[\[(.+?)\]\]', name_cell)
            if not match_name:
                continue
            stock_name = match_name.group(1).strip()

            # --- 2. 提取股票代码 (核心：优先使用文件中的代码列) ---
            symbol = None
            if '股票代码' in col_index:
                code_cell = cells[col_index['股票代码']].strip()
                if code_cell and code_cell not in ['-', '--', 'N/A']:
                    # 清理代码，只保留数字
                    symbol = re.sub(r'[^\d]', '', code_cell)
                    if not symbol:  # 如果清理后为空，则视为无效
                        symbol = None

            # --- 3. 提取持仓股数 ---
            shares_cell = cells[col_index['持仓/可用']].strip()
            shares_part = shares_cell.split('/')[0].strip()
            shares = int(self._clean_number_str(shares_part))

            # --- 4. 提取成本价 ---
            cost_cell = cells[col_index['成本价']].strip()
            special_cost_keywords = ['特殊成本', '停牌', '－', '--', '——', 'N/A', 'NaN']
            if any(keyword in cost_cell for keyword in special_cost_keywords) or cost_cell in ['-', '']:
                cost_price = 0.0
            else:
                cost_price = self._clean_number_str(cost_cell)

            # 将解析结果存入列表
            stocks.append({
                'symbol': symbol,  # 可能为 None
                'name': stock_name,
                'shares': shares,
                'cost_price': cost_price
            })

        return account_info, stocks

    def import_from_latest_md(self):
        """
        从最新的持仓 Markdown 文件导入数据 (核心：同步持仓与资金)
        返回 (是否成功, 消息)
        """
        # 检查目录是否存在
        if not os.path.exists(HOLDINGS_MD_DIR):
            return False, f"持仓目录不存在: {HOLDINGS_MD_DIR}"

        # 获取所有 .md 文件
        md_files = glob.glob(os.path.join(HOLDINGS_MD_DIR, "*.md"))
        if not md_files:
            return False, "未找到任何 .md 文件"

        # 按文件名中的日期排序
        def extract_date(filename):
            basename = os.path.basename(filename)
            match = re.search(r'(\d{8})', basename)
            if match:
                return match.group(1)
            return str(os.path.getmtime(filename))

        md_files.sort(key=extract_date, reverse=True)
        latest_file = md_files[0]
        logger.info(f"找到最新持仓文件: {latest_file}")

        # 解析文件
        account_info, stocks = self._parse_md_file(latest_file)

        if not stocks:
            return False, "未在文件中解析到任何有效持仓数据"

        # 构建文件中的股票名称集合（用于清仓判断）
        file_names = {s['name'] for s in stocks}

        # === 核心功能1：同步持仓 ===
        existing_symbols = set(self.positions.keys())
        updated_symbols = set()
        skipped_names = []      # 记录因无代码且无法匹配而被跳过的股票
        no_code_names = []      # 记录文件中没有代码的股票（用于警告）

        # 处理每个股票
        for stock in stocks:
            name = stock['name']
            shares = stock['shares']
            cost_price = stock['cost_price']
            symbol_from_file = stock['symbol']  # 从文件解析出的代码，可能为 None

            final_symbol = None
            match_method = "未匹配"

            # 第一步：优先使用文件中解析出的代码
            if symbol_from_file:
                final_symbol = symbol_from_file
                match_method = "文件代码列"
            else:
                # 第二步：文件无代码，尝试在现有持仓中通过名称匹配（兼容旧数据）
                no_code_names.append(name)
                # 2.1 精确匹配名称
                for symbol, info in self.positions.items():
                    if info.get('stock_name') == name:
                        final_symbol = symbol
                        match_method = "名称精确匹配(无文件代码)"
                        break
                # 2.2 模糊匹配
                if not final_symbol:
                    final_symbol = self._fuzzy_match_name(name)
                    if final_symbol:
                        match_method = "名称模糊匹配(无文件代码)"

                # 2.3 如果通过名称匹配到了现有持仓，使用现有持仓的代码
                if final_symbol:
                    pass  # 已经赋值，不需要做其他操作
                else:
                    # 2.4 尝试从本地缓存获取代码
                    code, ok = self._get_stock_code_from_name_local(name)
                    if ok:
                        final_symbol = code
                        match_method = "本地缓存"
                    else:
                        # 彻底无法匹配，跳过
                        skipped_names.append(name)
                        logger.warning(f"跳过股票 '{name}'：文件中无代码，且无法匹配到现有持仓，本地缓存中也没有。")
                        continue  # 跳过本次循环，不执行后面的更新

            # 成功找到标识符，更新持仓
            self.update_position(final_symbol, shares, cost_price, stock_name=name)
            
            # === 新增：清理该股票可能存在的旧临时记录 ===
            if final_symbol and not final_symbol.startswith('NAME:'):  # 只有当本次使用的是正规代码时才清理
                old_keys_to_delete = []
                for old_key, old_info in self.positions.items():
                    # 查找条件：键以'NAME:'开头，且股票名称与当前处理的股票相同
                    if (old_key.startswith('NAME:') and 
                        old_info.get('stock_name') == name and 
                        old_key != final_symbol):  # 确保不是正在使用的key本身
                        old_keys_to_delete.append(old_key)
                
                for old_key in old_keys_to_delete:
                    del self.positions[old_key]
                    logger.info(f"已清理冗余的临时记录: {old_key} -> 被 {final_symbol} 替换")
            # === 清理逻辑结束 ===
            
            updated_symbols.add(final_symbol)
            update_type = "更新" if final_symbol in existing_symbols else "新增"
            logger.info(f"{update_type} {final_symbol} - {name}: {shares}股 @ {cost_price:.2f} (通过{match_method})")

        # 处理清仓的股票（数据库中有但本次文件中没有的）
        cleared_symbols = []
        for symbol, pos_info in list(self.positions.items()):  # 使用list避免迭代中修改的问题
            stock_name_in_db = pos_info.get('stock_name', '')
            # 关键：清仓判断基于股票名称是否在本次文件的名称集合中
            if stock_name_in_db and stock_name_in_db not in file_names:
                if pos_info.get('shares', 0) > 0:  # 只有当前有持仓的才需要清仓
                    self.positions[symbol]['shares'] = 0
                    self.positions[symbol]['last_updated'] = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                    cleared_symbols.append(symbol)
                    logger.info(f"标记清仓: {symbol} - {stock_name_in_db}")

        # 保存持仓更新
        self.save_positions()

        # === 核心功能2：同步资金（以.md文件为权威来源）===
        capital_sync_msg = ""
        if CAPITAL_MANAGER_AVAILABLE:
            try:
                capital_mgr = CapitalManager()
                # 从解析结果中获取文件声明的数值
                file_available_cash = account_info.get('available_cash')
                file_total_assets = account_info.get('total_assets')
                
                update_kwargs = {}
                
                # 1. 更新可用资金 (回测试交易现金)
                if file_available_cash is not None and file_available_cash >= 0:
                    update_kwargs['available_cash'] = file_available_cash
                
                # 2. 更新总资金 (回测试算总收益的基准)
                # 映射关系：.md文件中的`总资产` = CapitalManager中的`total_capital`
                if file_total_assets is not None and file_total_assets > 0:
                    update_kwargs['total_capital'] = file_total_assets
                elif file_available_cash is not None and file_total_assets is None:
                    # 如果.md文件只有可用资金，将总资金设为相同值（保守策略）
                    update_kwargs['total_capital'] = file_available_cash
                    logger.info("文件未提供总资产，将总资金设置为与可用资金相同。")
                
                if update_kwargs:
                    success = capital_mgr.update_capital(**update_kwargs)
                    if success:
                        capital_sync_msg = f"资金同步：可用资金={file_available_cash:,.2f}, 总资金={update_kwargs.get('total_capital', file_available_cash):,.2f}"
                        logger.info(f"✅ {capital_sync_msg}")
                    else:
                        capital_sync_msg = "⚠️  资金同步失败"
                        logger.warning(capital_sync_msg)
                else:
                    capital_sync_msg = "ℹ️  文件中无有效资金信息，跳过同步。"
                    logger.info(capital_sync_msg)
                    
            except Exception as e:
                capital_sync_msg = f"❌ 资金同步出错: {e}"
                logger.error(capital_sync_msg)
        else:
            capital_sync_msg = "ℹ️  CapitalManager不可用，跳过资金同步。"
            logger.warning(capital_sync_msg)

        # 构建详细返回消息
        success_count = len(updated_symbols)
        total_count = len(stocks)
        skip_count = len(skipped_names)
        clear_count = len(cleared_symbols)
        no_code_count = len(no_code_names)

        msg_lines = []
        msg_lines.append(f"📊 持仓导入报告：")
        msg_lines.append(f"  • 解析文件: {os.path.basename(latest_file)}")
        msg_lines.append(f"  • 发现股票: {total_count} 支")
        msg_lines.append(f"  • 成功处理: {success_count} 支 ({success_count/total_count*100:.1f}%)")
        if no_code_count > 0:
            msg_lines.append(f"  ⚠️  有 {no_code_count} 支股票在文件中无代码，依赖名称匹配: {', '.join(no_code_names[:3])}")
            if no_code_count > 3:
                msg_lines.append(f"    等... (共{no_code_count}支)")
        if skip_count > 0:
            msg_lines.append(f"  ❌ 跳过处理: {skip_count} 支 (原因: 无代码且无法匹配): {', '.join(skipped_names[:3])}")
            if skip_count > 3:
                msg_lines.append(f"    等... (共{skip_count}支)")
        msg_lines.append(f"  • 标记清仓: {clear_count} 支")
        msg_lines.append(f"💰 {capital_sync_msg}")

        return True, "\n".join(msg_lines)


# ========== 工具主函数 ==========
def run_tool():
    """工具主函数 - 持仓管理界面"""
    manager = PositionManager()

    # 启动时自动导入最新持仓
    print("\n🔄 正在自动导入最新持仓与资金...")
    success, msg = manager.import_from_latest_md()
    if success:
        print(f"✅ {msg}")
    else:
        print(f"⚠️ {msg}")

    while True:
        print("\n" + "=" * 40)
        print("           持仓管理系统")
        print("=" * 40)
        print("1 - 查看当前持仓")
        print("2 - 添加/修改持仓")
        print("3 - 删除持仓")
        print("4 - 清空所有持仓")
        print("5 - 生成回测持仓配置")
        print("0 - 返回主菜单")

        choice = input("请选择: ").strip()

        if choice == '1':
            show_all_positions(manager)
        elif choice == '2':
            add_or_update_position(manager)
        elif choice == '3':
            delete_position(manager)
        elif choice == '4':
            clear_all_positions(manager)
        elif choice == '5':
            generate_backtest_config(manager)
        elif choice == '0':
            print("返回主菜单...")
            break
        else:
            print("❌ 无效选择")


def show_all_positions(manager):
    """显示所有持仓"""
    positions = manager.get_all_positions()
    if not positions:
        print("📭 当前无持仓")
        return

    print("\n📊 当前持仓列表:")
    print("-" * 60)
    for symbol, position in positions.items():
        print(f"股票代码: {symbol}")
        print(f"  股票名称: {position['stock_name']}")
        print(f"  持仓数量: {position['shares']} 股")
        print(f"  成本价格: {position['cost_price']:.2f} 元")
        print(f"  总成本: {position['shares'] * position['cost_price']:.2f} 元")
        print(f"  最后更新: {position.get('last_updated', '未知')}")
        print("-" * 60)


def add_or_update_position(manager):
    """添加或修改持仓"""
    print("\n➕ 添加/修改持仓")

    symbol = input("请输入股票代码 (如 601939): ").strip()
    if not symbol:
        print("❌ 股票代码不能为空")
        return

    # 检查是否已有持仓
    existing = manager.get_position(symbol)
    if existing['shares'] > 0:
        print(f"📋 现有持仓: {existing['shares']}股 @ {existing['cost_price']:.2f}元")
        change = input("是否修改? (y/N): ").strip().lower()
        if change != 'y':
            return

    try:
        shares = int(input("请输入持仓数量 (股): ").strip())
        cost_price = float(input("请输入成本价格 (元): ").strip())
        stock_name = input("请输入股票名称 (可选): ").strip()

        if shares < 0 or cost_price <= 0:
            print("❌ 持仓数量和成本价格必须为正数")
            return

        if manager.update_position(symbol, shares, cost_price, stock_name):
            print(f"✅ 成功更新 {symbol} 持仓")
        else:
            print("❌ 更新失败")

    except ValueError:
        print("❌ 输入格式错误，请确保输入数字")


def delete_position(manager):
    """删除持仓"""
    symbol = input("请输入要删除的股票代码: ").strip()
    if not symbol:
        return

    existing = manager.get_position(symbol)
    if existing['shares'] == 0:
        print(f"❌ 未找到 {symbol} 的持仓记录")
        return

    confirm = input(f"确认删除 {symbol} 的持仓? (y/N): ").strip().lower()
    if confirm == 'y':
        manager.delete_position(symbol)


def clear_all_positions(manager):
    """清空所有持仓"""
    positions = manager.get_all_positions()
    if not positions:
        print("📭 当前无持仓可清空")
        return

    print(f"⚠️  即将清空 {len(positions)} 个持仓记录")
    confirm = input("确认清空所有持仓? (y/N): ").strip().lower()
    if confirm == 'y':
        if manager.clear_all_positions():
            print("✅ 已清空所有持仓")
        else:
            print("❌ 清空失败")


def generate_backtest_config(manager):
    """生成回测持仓配置"""
    positions = manager.get_all_positions()
    if not positions:
        print("📭 无持仓数据，无法生成配置")
        return

    print("\n🎯 生成回测持仓配置")
    print("此配置将在回测时使用，不影响真实持仓数据库")

    # 选择要回测的股票
    symbols = list(positions.keys())
    for i, symbol in enumerate(symbols, 1):
        pos = positions[symbol]
        print(f"{i} - {symbol} ({pos['stock_name']}): {pos['shares']}股 @ {pos['cost_price']:.2f}元")

    choice = input("选择要回测的股票编号 (多个用逗号分隔, 全部输入a): ").strip()

    selected_positions = {}
    if choice.lower() == 'a':
        selected_positions = positions
    else:
        try:
            indices = [int(x.strip()) for x in choice.split(',')]
            for idx in indices:
                if 1 <= idx <= len(symbols):
                    symbol = symbols[idx - 1]
                    selected_positions[symbol] = positions[symbol]
        except ValueError:
            print("❌ 输入格式错误")
            return

    if not selected_positions:
        print("❌ 未选择任何股票")
        return

    # 生成临时配置文件
    temp_config = {
        'backtest_positions': selected_positions,
        'generated_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        'note': '此文件为回测临时配置，不影响真实持仓'
    }

    temp_file = os.path.join(cfg.DATA_DIR, "backtest_temp_positions.json")
    try:
        with open(temp_file, 'w', encoding='utf-8') as f:
            json.dump(temp_config, f, ensure_ascii=False, indent=2)
        print(f"✅ 回测配置已生成: {temp_file}")
        print("💡 在回测时会自动使用此配置")
    except Exception as e:
        print(f"❌ 生成配置失败: {e}")


if __name__ == "__main__":
    run_tool()
