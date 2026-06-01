# StockAdvisor — 代码审查报告 + 实施方案

> 日期：2026-06-01
> 作者：Hermes (总控 PM)
> 状态：Phase 5 — Runtime Bug Fix

---

## 一、问题诊断

### 当前运行时错误
```
java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType
```

### 根本原因分析

这个错误发生在 **Retrofit + Gson 反序列化泛型类型时**。具体原因链：

1. **原始代码**使用 `ApiResponse<T>` 泛型类作为 API 响应包装
2. **Retrofit 接口**定义返回 `ApiResponse<List<StockInfo>>` 等泛型类型
3. **Gson 反序列化**时，由于 Java/Kotlin 泛型擦除，无法在运行时获取 `List<StockInfo>` 的具体类型信息
4. Gson 尝试将 `Class` 强制转换为 `ParameterizedType` 来获取泛型参数 → **抛出异常**

### 我之前的错误尝试
- ❌ 把 `ApiResponse<T>` 换成具体类型（`StockListResponse` 等），但 `data` 字段仍然是 `List<StockInfo>` 泛型 → 同样的错误
- ❌ 给 `build.gradle.kts` 加 import、改 keystore 路径 → 无关
- ❌ 改 `render.yaml` → 无关

### 正确方案
**彻底抛弃 Retrofit 的自动反序列化**，改用：
- **OkHttp** 手动发 HTTP 请求
- **Gson** 手动反序列化 JSON 字符串
- 用 `TypeToken` 正确传递泛型类型信息

---

## 二、代码现状盘点

### 文件清单（33个 Kotlin 文件）

| 层 | 文件 | 状态 | 问题 |
|----|------|------|------|
| **App** | `StockAdvisorApp.kt` | ✅ OK | Hilt Application |
| **Main** | `MainActivity.kt` | ⚠️ 需修复 | 引用 `ui.screens.*` 但 `ui.screen.*` 也有文件 |
| **Nav** | `AppNavGraph.kt` | ⚠️ 需修复 | 引用 `ui.screen.*` |
| **Nav** | `NavRoutes.kt` | ✅ OK | 路由定义 |
| **API** | `ApiClient.kt` | ⚠️ 已改 | 从 Retrofit → OkHttp+Gson |
| **API** | `StockApiService.kt` | ⚠️ 废弃 | Retrofit 接口，不再使用 |
| **API** | `NetworkModule.kt` | ⚠️ 废弃 | Hilt 注入 Retrofit，不再使用 |
| **Model** | `Models.kt` | ⚠️ 需清理 | 有重复的 `ApiResponse<T>` 和 `MapResponse` |
| **Repo** | `StockRepository.kt` | ⚠️ 已改 | 调用 ApiClient |
| **Local** | `StockDao.kt` | ✅ OK | Room DAO |
| **Local** | `Entities.kt` | ✅ OK | Room Entities |
| **Local** | `DatabaseModule.kt` | ✅ OK | Hilt DB 模块 |
| **Screen** | `ui/screens/HomeScreen.kt` | ⚠️ 重复 | 与 `ui/screen/HomeScreen.kt` 重复 |
| **Screen** | `ui/screens/PredictScreen.kt` | ⚠️ 重复 | 与 `ui/screen/PredictScreen.kt` 重复 |
| **Screen** | `ui/screens/BacktestScreen.kt` | ⚠️ 重复 | 与 `ui/screen/BacktestScreen.kt` 重复 |
| **Screen** | `ui/screens/PortfolioScreen.kt` | ⚠️ 重复 | 与 `ui/screen/PortfolioScreen.kt` 重复 |
| **Screen** | `ui/screens/SettingsScreen.kt` | ⚠️ 重复 | 与 `ui/screen/SettingsScreen.kt` 重复 |
| **Screen** | `ui/screens/StockSearchScreen.kt` | ⚠️ 重复 | 与 `ui/screen/StockSearchScreen.kt` 重复 |
| **Screen** | `ui/screen/HomeScreen.kt` | ✅ 主版本 | 有 Scaffold+TopAppBar |
| **Screen** | `ui/screen/PredictScreen.kt` | ✅ 主版本 | |
| **Screen** | `ui/screen/BacktestScreen.kt` | ✅ 主版本 | |
| **Screen** | `ui/screen/PortfolioScreen.kt` | ✅ 主版本 | 定义 PortfolioItemUi |
| **Screen** | `ui/screen/SettingsScreen.kt` | ✅ 主版本 | |
| **Screen** | `ui/screen/StockSearchScreen.kt` | ✅ 主版本 | |
| **Screen** | `ui/screen/PullToRefreshWrapper.kt` | ✅ OK | |
| **ViewModel** | `HomeViewModel.kt` | ✅ OK | |
| **ViewModel** | `PredictViewModel.kt` | ✅ OK | |
| **ViewModel** | `BacktestViewModel.kt` | ✅ OK | |
| **ViewModel** | `PortfolioViewModel.kt` | ✅ OK | |
| **ViewModel** | `SettingsViewModel.kt` | ✅ OK | |
| **Theme** | `Color.kt/Theme.kt/Type.kt` | ✅ OK | |

### 核心问题清单

| # | 问题 | 严重度 | 影响 |
|---|------|--------|------|
| 1 | Retrofit Gson 泛型反序列化崩溃 | 🔴 P0 | 所有 API 调用失败 |
| 2 | `ui/screens/` 和 `ui/screen/` 双版本冲突 | 🟡 P1 | 编译可能通过但运行时行为不确定 |
| 3 | `StockApiService.kt` 和 `NetworkModule.kt` 废弃但未删除 | 🟢 P2 | 代码冗余 |
| 4 | `Models.kt` 有重复的 `ApiResponse<T>` 泛型类 | 🟢 P2 | 代码冗余 |
| 5 | `MainActivity.kt` 和 `AppNavGraph.kt` 引用不同包路径 | 🟡 P1 | 可能加载错误的 Screen |

---

## 三、实施方案

### Phase 5-R1: Runtime Bug Fix（当前）

#### 任务 5-R1-T1: 修复 API 层（ApiClient）
- **Worker**: Hermes
- **内容**: 已改为 OkHttp + Gson 手动解析
- **验收**: 编译通过，APK 安装后 API 调用不崩溃

#### 任务 5-R1-T2: 清理废弃文件
- **Worker**: Hermes
- **内容**: 删除 `StockApiService.kt`、`NetworkModule.kt`
- **验收**: 编译通过

#### 任务 5-R1-T3: 清理重复 Screen 文件
- **Worker**: Hermes
- **内容**: 删除 `ui/screens/` 目录下的重复文件（因为 `ui/screen/` 是主版本）
- **验收**: 编译通过

#### 任务 5-R1-T4: 修复 Models.kt
- **Worker**: Hermes
- **内容**: 删除泛型 `ApiResponse<T>`，保留具体响应类型
- **验收**: 编译通过

#### 任务 5-R1-T5: 修复 MainActivity 和 AppNavGraph 包路径
- **Worker**: Hermes
- **内容**: 统一使用 `ui.screen.*`（主版本）
- **验收**: 编译通过

#### 任务 5-R1-T6: 端到端验证
- **Worker**: Hermes
- **内容**: 编译 APK → 安装 → 点持仓 → 不崩溃
- **验收**: 持仓页面正常显示（即使数据为空）

---

### Phase 6: 功能完善（待 Phase 5-R1 完成后）

#### 任务 6-T1: 后端部署验证
- **Worker**: Hermes + 用户操作
- **内容**: Render 部署后端，配置 TUSHARE_TOKEN
- **验收**: `GET /health` 返回 200

#### 任务 6-T2: 联调测试
- **Worker**: Hermes
- **内容**: Android 端连接后端，验证股票列表加载
- **验收**: 首页显示股票列表

#### 任务 6-T3: 签名 APK
- **Worker**: Hermes + 用户操作
- **内容**: 配置 GitHub Secrets，触发签名构建
- **验收**: 下载签名 APK，安装运行正常

---

## 四、Worker 分配

| Worker | 角色 | 当前任务 | 状态 |
|--------|------|----------|------|
| Hermes | PM + 全栈 | 5-R1-T1~T6 全部 | 🔄 进行中 |
| 用户 | 操作 | 6-T1 Render 部署 | ⏳ 等待 |

---

## 五、验收标准

### Phase 5-R1 完成标准
- [ ] APK 编译通过
- [ ] 安装后启动不崩溃
- [ ] 点持仓页面不崩溃（显示空列表或数据）
- [ ] 点设置页面正常
- [ ] 所有废弃文件已清理

### Phase 6 完成标准
- [ ] 后端部署成功
- [ ] 首页加载股票列表
- [ ] 签名 APK 可安装

---

## 六、风险评估

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| OkHttp 手动解析仍有泛型问题 | 低 | 高 | 已用 TypeToken 解决 |
| 后端未部署导致 API 超时 | 中 | 中 | catch 异常，显示友好错误 |
| 重复文件清理导致编译错误 | 低 | 中 | 逐个清理，每次编译验证 |
