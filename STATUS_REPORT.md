# 📊 StockAdvisor — Phase 5-R1 进展报告

> 日期：2026-06-01
> 状态：🔄 构建中

---

## 一、问题根因确认

**错误**: `java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType`

**根因**: Retrofit + Gson 反序列化泛型类型时，Java/Kotlin 泛型擦除导致 Gson 无法获取运行时类型信息，尝试将 `Class` 强制转换为 `ParameterizedType` 时崩溃。

**关键发现**: 原始代码使用 `ApiResponse<T>` 泛型类包装所有 API 响应，这是根本原因。

---

## 二、已完成的修复（5-R1-T1 ~ T5）

### ✅ 5-R1-T1: API 层重写
- **从**: Retrofit 自动反序列化 `ApiResponse<T>`
- **到**: OkHttp 手动发请求 + Gson 手动用 `TypeToken` 反序列化
- **文件**: `ApiClient.kt`（重写）、`StockRepository.kt`（重写）

### ✅ 5-R1-T2: 删除废弃文件
- 删除 `StockApiService.kt`（Retrofit 接口）
- 删除 `NetworkModule.kt`（Hilt Retrofit 注入）

### ✅ 5-R1-T3: 删除重复 Screen 文件
- 删除 `ui/screens/` 目录下 6 个重复文件
- 原因：`ui/screens/` 和 `ui/screen/` 有同名类，导致混乱

### ✅ 5-R1-T4: 清理 Models.kt
- 删除泛型 `ApiResponse<T>` 类
- 保留具体响应类型（`StockListResponse` 等），`data` 字段用 `JsonElement`

### ✅ 5-R1-T5: 统一包路径
- `MainActivity.kt`: `import ui.screens.*` → `import ui.screen.*`
- `PortfolioScreen` 调用补上 `onBack` 参数

---

## 三、代码现状

### 文件清单（清理后）
| 层 | 文件数 | 关键文件 |
|----|--------|----------|
| App | 2 | StockAdvisorApp.kt, MainActivity.kt |
| Nav | 2 | NavRoutes.kt, AppNavGraph.kt |
| API | 1 | ApiClient.kt (OkHttp+Gson) |
| Model | 1 | Models.kt (无泛型) |
| Repo | 1 | StockRepository.kt |
| Local | 3 | StockDao.kt, Entities.kt, DatabaseModule.kt |
| Screen | 7 | ui/screen/*.kt (Home/Predict/Backtest/Portfolio/Settings/StockSearch/PullToRefresh) |
| ViewModel | 5 | Home/Predict/Backtest/Portfolio/Settings |
| Theme | 3 | Color/Theme/Type |
| **总计** | **25** | 从 33 个减少到 25 个 |

### 架构图
```
┌─────────────────────────────────┐
│         MainActivity.kt          │
│  (ui.screen.* 统一引用)          │
├─────────────────────────────────┤
│  HomeScreen │ PortfolioScreen    │
│  PredictScreen │ BacktestScreen  │
│  SettingsScreen │ StockSearch    │
├─────────────────────────────────┤
│  ViewModels (5个)               │
├─────────────────────────────────┤
│  StockRepository                │
│  └─> ApiClient (OkHttp+Gson)    │
│       └─> 后端 FastAPI          │
├─────────────────────────────────┤
│  Room Database (本地缓存)        │
└─────────────────────────────────┘
```

---

## 四、待完成

### ⏳ 5-R1-T6: 端到端验证
- 编译通过 → 下载 APK → 安装 → 点持仓 → 不崩溃
- 当前构建 run: https://github.com/luoxiaoliangTj/Py_Learning/actions/runs/26747164366

### ⏳ 6-T1: 后端部署（用户操作）
- Render 部署后端
- 配置 TUSHARE_TOKEN

### ⏳ 6-T2: 联调测试
- Android 端连接后端
- 验证股票列表加载

### ⏳ 6-T3: 签名 APK
- 配置 GitHub Secrets
- 触发签名构建

---

## 五、风险评估

| 风险 | 状态 | 缓解 |
|------|------|------|
| OkHttp 手动解析仍有问题 | 🟢 低 | 已用 TypeToken，完全绕过 Gson 泛型 |
| 后端未部署 API 超时 | 🟡 中 | catch 异常，显示"加载失败"不崩溃 |
| 清理导致编译错误 | 🟢 低 | 每次清理后编译验证 |
