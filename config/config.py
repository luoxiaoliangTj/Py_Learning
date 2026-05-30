import os
import json
from dataclasses import dataclass

@dataclass
class Config:
    # 基础路径
    BASE_DIR        = os.path.dirname(os.path.dirname(__file__))
    DATA_DIR        = os.path.join(BASE_DIR, "data")
    LOG_DIR         = os.path.join(BASE_DIR, "logs")
    RESULT_DIR      = os.path.join(BASE_DIR, "realtime_results")
    BACKTEST_DIR    = os.path.join(BASE_DIR, "backtest_results")
    ALGO_DIR        = os.path.join(BASE_DIR, "algorithms")
    
    # 新增：Token目录（集中管理所有API密钥）
    TOKEN_DIR       = os.path.join(BASE_DIR, "token")

    # 股票 & 指数默认 - 这些将在 __post_init__ 中被覆盖
    STOCK_SYMBOL    = "000001"  # 默认值，会被覆盖
    STOCK_NAME      = "平安银行"  # 默认值，会被覆盖
    INDEX_SYMBOL    = "000001"  # 默认值，会被覆盖
    INDEX_NAME      = "上证指数"  # 默认值，会被覆盖

    # 日志
    LOG_LEVEL       = os.getenv("LOG_LEVEL", "INFO").upper()
    
    # Token配置（将从文件加载）
    TUSHARE_TOKEN   = ""  # 初始化时从token文件加载

    def __post_init__(self):
        """初始化后处理 - 从持久化文件加载股票配置和Token"""
        self._load_current_stock()
        self._load_tokens()  # 新增：加载所有Token
    
    def _load_current_stock(self):
        """从持久化文件加载当前股票配置"""
        current_stock_file = os.path.join(self.DATA_DIR, "current_stock.json")
        
        if os.path.exists(current_stock_file):
            try:
                with open(current_stock_file, 'r', encoding='utf-8') as f:
                    current_stock = json.load(f)
                
                # 使用持久化配置覆盖默认值
                self.STOCK_SYMBOL = current_stock.get("symbol", self.STOCK_SYMBOL)
                self.STOCK_NAME = current_stock.get("name", self.STOCK_NAME)
                self.INDEX_SYMBOL = current_stock.get("index_symbol", self.INDEX_SYMBOL)
                self.INDEX_NAME = current_stock.get("index_name", self.INDEX_NAME)
                
                print(f"✅ 已从持久化配置加载: {self.STOCK_SYMBOL} - {self.STOCK_NAME}")
            except Exception as e:
                print(f"❌ 加载持久化股票配置失败: {e}")
        
        # 环境变量优先级最高（可以覆盖持久化配置）
        self.STOCK_SYMBOL = os.getenv("STOCK_SYMBOL", self.STOCK_SYMBOL)
        self.STOCK_NAME = os.getenv("STOCK_NAME", self.STOCK_NAME)
        self.INDEX_SYMBOL = os.getenv("INDEX_SYMBOL", self.INDEX_SYMBOL)
        self.INDEX_NAME = os.getenv("INDEX_NAME", self.INDEX_NAME)

    def _load_tokens(self):
        """从token目录加载所有Token"""
        try:
            # 确保token目录存在
            os.makedirs(self.TOKEN_DIR, exist_ok=True)
            
            # 加载Tushare Token
            tushare_token_file = os.path.join(self.TOKEN_DIR, "tushare_token.txt")
            if os.path.exists(tushare_token_file):
                with open(tushare_token_file, 'r', encoding='utf-8') as f:
                    token = f.read().strip()
                    if token:
                        self.TUSHARE_TOKEN = token
                        print(f"✅ 已从文件加载 Tushare Token")
                    else:
                        print(f"⚠️  Tushare Token 文件为空: {tushare_token_file}")
            else:
                print(f"ℹ️  Tushare Token 文件不存在: {tushare_token_file}")
                print(f"   如需使用Tushare，请在 {self.TOKEN_DIR} 目录下创建 tushare_token.txt 文件")
                
            # 可以在这里添加加载其他Token的代码
            # 例如：other_token_file = os.path.join(self.TOKEN_DIR, "other_token.txt")
            # 如果未来需要加载更多Token，只需扩展此方法即可
            
        except Exception as e:
            print(f"❌ 加载Token失败: {e}")

    def save_tushare_token(self, token):
        """保存Tushare Token到文件"""
        try:
            os.makedirs(self.TOKEN_DIR, exist_ok=True)
            token_file = os.path.join(self.TOKEN_DIR, "tushare_token.txt")
            
            with open(token_file, 'w', encoding='utf-8') as f:
                f.write(token.strip())
            
            self.TUSHARE_TOKEN = token.strip()
            print(f"✅ Tushare Token 已保存到: {token_file}")
            return True
            
        except Exception as e:
            print(f"❌ 保存Token失败: {e}")
            return False

    def list_token_files(self):
        """列出token目录中的所有文件"""
        try:
            if not os.path.exists(self.TOKEN_DIR):
                print(f"Token目录不存在: {self.TOKEN_DIR}")
                return []
            
            token_files = []
            for filename in os.listdir(self.TOKEN_DIR):
                filepath = os.path.join(self.TOKEN_DIR, filename)
                if os.path.isfile(filepath):
                    # 安全地读取文件大小（不显示具体内容）
                    size = os.path.getsize(filepath)
                    token_files.append({
                        'name': filename,
                        'path': filepath,
                        'size': size
                    })
            
            return token_files
            
        except Exception as e:
            print(f"❌ 列出Token文件失败: {e}")
            return []

# 单例
cfg = Config()