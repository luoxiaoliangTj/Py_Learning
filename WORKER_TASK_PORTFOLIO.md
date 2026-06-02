# Worker 任务书：持仓本地存储修复

## 任务概述
修复持仓导入和加载逻辑，从"走后端API"改为"存手机本地JSON"，对齐原始代码 NewProjectV2402 的 position_manager_tool.py 和 capital_manager_tool.py。

## 原始代码参考
- `/data/data/com.termux/files/home/code_scan/NewProjectV2402/Tools/position_manager_tool.py` (835行)
- `/data/data/com.termux/files/home/code_scan/NewProjectV2402/Tools/capital_manager_tool.py` (254行)
- `/data/data/com.termux/files/home/code_scan/NewProjectV2402/config/config.py` (129行)

## 当前文件
- `~/Py_Learning/android/app/src/main/java/com/tangtang/stockadvisor/viewmodel/PortfolioViewModel.kt` (325行)
- `~/Py_Learning/android/app/src/main/java/com/tangtang/stockadvisor/ui/screen/PortfolioScreen.kt` (435行)
- `~/Py_Learning/android/app/src/main/java/com/tangtang/stockadvisor/data/repository/StockRepository.kt` (266行)
- `~/Py_Learning/android/app/src/main/java/com/tangtang/stockadvisor/data/model/Models.kt` (233行)

## 存储路径
使用 Android 内部存储：`Context.getFilesDir()/data/`
```
/data/data/com.tangtang.stockadvisor/files/data/
├── real_positions.json      ← 持仓明细（对齐原始代码格式）
└── global_capital.json      ← 资金信息（对齐原始代码格式）
```

## real_positions.json 格式（对齐原始代码）
```json
{
  "601939": {
    "shares": 100,
    "cost_price": 114.544,
    "stock_name": "建设银行",
    "last_updated": "2026-06-02 12:00:00"
  }
}
```

## global_capital.json 格式（对齐原始代码）
```json
{
  "available_cash": 10203.08,
  "total_capital": 346919.21,
  "last_updated": "2026-06-02 12:00:00",
  "note": "从持仓文件同步"
}
```

## 具体修改要求

### 1. PortfolioViewModel.kt 修改

#### 1.1 新增本地存储方法
```kotlin
// 保存持仓到本地JSON
private fun savePositionsToLocal(positions: Map<String, PositionData>)

// 从本地JSON读取持仓
private fun loadPositionsFromLocal(): Map<String, PositionData>

// 保存资金到本地JSON
private fun saveCapitalToLocal(capital: CapitalData)

// 从本地JSON读取资金
private fun loadCapitalFromLocal(): CapitalData
```

#### 1.2 修改 importPortfolioFromUri()
当前逻辑：解析.md → 转JSON字符串 → 发后端API
修复后：解析.md → 5级代码匹配 → 写本地JSON → 重新加载

```kotlin
fun importPortfolioFromUri(uri: Uri, context: Context) {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        try {
            // 1. 读取文件内容（IO操作）
            val content = withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                reader.readText().also { reader.close() }
            }

            // 2. 解析.md文件（CPU操作）
            val (holdings, capital) = withContext(Dispatchers.Default) {
                parseMdPortfolio(content)
            }

            // 3. 5级代码匹配（对齐原始代码 position_manager_tool.py）
            val matchedHoldings = performCodeMatching(holdings)

            // 4. 写本地JSON
            savePositionsToLocal(matchedHoldings)
            if (capital != null) {
                saveCapitalToLocal(capital)
            }

            // 5. 重新加载显示
            loadPortfolio()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = e.message ?: "导入失败"
            )
        }
    }
}
```

#### 1.3 新增5级代码匹配（对齐原始代码）
```kotlin
private fun performCodeMatching(rawHoldings: List<RawHolding>): Map<String, PositionData> {
    val positions = loadPositionsFromLocal()
    val result = mutableMapOf<String, PositionData>()
    val fileNames = rawHoldings.map { it.name }.toSet()

    for (raw in rawHoldings) {
        var finalSymbol: String? = null
        var matchMethod = "未匹配"

        // 第1级：文件中的股票代码列
        if (!raw.symbol.isNullOrEmpty()) {
            finalSymbol = raw.symbol
            matchMethod = "文件代码列"
        }

        // 第2级：现有持仓精确匹配名称
        if (finalSymbol == null) {
            for ((sym, info) in positions) {
                if (info.stockName == raw.name) {
                    finalSymbol = sym
                    matchMethod = "名称精确匹配"
                    break
                }
            }
        }

        // 第3级：现有持仓模糊匹配
        if (finalSymbol == null) {
            finalSymbol = fuzzyMatchName(raw.name, positions)
            if (finalSymbol != null) {
                matchMethod = "名称模糊匹配"
            }
        }

        // 第4级：跳过（App端无本地缓存，暂不实现）
        // 第5级：跳过
        if (finalSymbol == null) {
            Log.w("Portfolio", "跳过股票 '${raw.name}'：无代码且无法匹配")
            continue
        }

        result[finalSymbol] = PositionData(
            shares = raw.shares,
            costPrice = raw.costPrice,
            stockName = raw.name,
            lastUpdated = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )
    }

    // 清仓处理：数据库有但文件没有的股票 → shares=0
    for ((sym, info) in positions) {
        if (info.stockName !in fileNames && info.shares > 0) {
            result[sym] = info.copy(shares = 0)
        }
    }

    return result
}
```

#### 1.4 修改 loadPortfolio()
当前逻辑：调 repository.getHoldings() → 后端API
修复后：从本地JSON读取 → 计算盈亏 → 更新UI

```kotlin
fun loadPortfolio() {
    viewModelScope.launch {
        _uiState.value = PortfolioUiState(isLoading = true)
        try {
            val positions = withContext(Dispatchers.IO) {
                loadPositionsFromLocal()
            }
            val capital = withContext(Dispatchers.IO) {
                loadCapitalFromLocal()
            }

            val items = positions.values.filter { it.shares > 0 }.map { pos ->
                // 从后端获取实时行情（这个需要网络）
                // 暂时用成本价作为当前价
                val currentPrice = pos.costPrice // TODO: 从实时数据获取
                val marketValue = pos.shares * currentPrice
                val cost = pos.shares * pos.costPrice
                val profitLoss = marketValue - cost
                val profitLossPercent = if (cost > 0) (profitLoss / cost) * 100 else 0.0

                PortfolioItemUi(
                    code = pos.stockName, // TODO: 需要存code
                    name = pos.stockName,
                    shares = pos.shares,
                    avgCost = pos.costPrice,
                    currentPrice = currentPrice,
                    marketValue = marketValue,
                    profitLoss = profitLoss,
                    profitLossPercent = profitLossPercent
                )
            }

            val totalMV = items.sumOf { it.marketValue }
            val totalCost = items.sumOf { it.avgCost * it.shares }
            val totalPL = totalMV - totalCost
            val totalPLPct = if (totalCost > 0) (totalPL / totalCost) * 100 else 0.0

            _uiState.value = PortfolioUiState(
                isLoading = false,
                items = items,
                totalMarketValue = totalMV,
                totalCost = totalCost,
                totalProfitLoss = totalPL,
                totalProfitLossPercent = totalPLPct
            )
        } catch (e: Exception) {
            _uiState.value = PortfolioUiState(
                isLoading = false,
                error = e.message ?: "加载失败"
            )
        }
    }
}
```

### 2. PortfolioScreen.kt 修改

#### 2.1 保留 SAF 选择器
用户仍然通过 SAF 选择 .md 文件，这个不变。

#### 2.2 显示导入结果
选择文件后，显示解析结果：
- 成功导入 X 支
- 跳过 Y 支（无代码无法匹配）
- 标记清仓 Z 支

#### 2.3 无持仓时的显示
当本地JSON为空时，显示导入引导卡片（保留现有逻辑）。

### 3. StockRepository.kt 修改
持仓相关方法不再调用后端API，改为操作本地文件。

## 质量检查
1. 选择 .md 文件后，本地JSON正确写入
2. App重启后，从本地JSON正确读取
3. 5级代码匹配正确执行
4. 清仓处理正确执行
5. 编译通过
