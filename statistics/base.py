import pandas as pd
import abc
import numpy as np

class StatPluginBase(abc.ABC):
    name: str = ''
    
    @abc.abstractmethod
    def fit(self, csv_path: str, meta: dict) -> dict:
        pass
    
    def is_better(self, df_daily: pd.DataFrame, old_params: dict) -> bool:
        if old_params is None:
            return True
        return len(df_daily) > 1.05 * old_params.get('fit_length', 0)
    
    def _atr(self, df: pd.DataFrame, n: int = 20) -> pd.Series:
        df = df.copy()
        df['tr'] = np.maximum(
            df['high'] - df['low'],
            np.maximum(
                np.abs(df['high'] - df['close'].shift(1)),
                np.abs(df['low'] - df['close'].shift(1))
            )
        )
        return df['tr'].rolling(n).mean()