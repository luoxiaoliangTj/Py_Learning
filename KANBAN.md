# 📋 StockAdvisor APK — Kanban 任务看板

## 项目目标
将 NewProjectV2402 Python 策略引擎项目转换为 Android APK
架构：原生 Kotlin + Jetpack Compose + FastAPI 后端

## 质量标准
- 代码必须可编译、可运行
- 每个 Phase 完成后必须验证
- 所有代码提交到 GitHub 仓库
- 不修改原始 Python 项目代码

---

## 🔴 Phase 1: 后端API验收与补全 (Worker 1)

### 任务
- [ ] P1-T1: 验收现有 backend/ 代码，检查API逻辑正确性
- [ ] P1-T2: 补全 COMPLETION.md
- [ ] P1-T3: 补全 requirements.txt
- [ ] P1-T4: 本地启动 FastAPI 服务，验证所有10个API端点
- [ ] P1-T5: 提交 backend/ 到 GitHub

### 验收标准
- [ ] `uvicorn main:app` 能正常启动
- [ ] 所有10个API端点返回正确JSON
- [ ] GitHub 仓库有 backend/ 目录

### 状态
- 分配：待分配
- 进度：0%

---

## 🟡 Phase 2: Android UI层 (Worker 2)

### 任务
- [ ] P2-T1: 创建 MainActivity.kt (Compose入口 + NavHost)
- [ ] P2-T2: 创建 Navigation 路由定义
- [ ] P2-T3: 创建 HomeScreen (首页 + 股票列表)
- [ ] P2-T4: 创建 PredictScreen (预测页 + 价格图表)
- [ ] P2-T5: 创建 BacktestScreen (回测页 + 绩效报告)
- [ ] P2-T6: 创建 PortfolioScreen (持仓管理页)
- [ ] P2-T7: 创建 SettingsScreen (设置页)
- [ ] P2-T8: 创建 StockSearchScreen (股票搜索页)
- [ ] P2-T9: 每个 Screen 绑定对应 ViewModel

### 验收标准
- [ ] 所有 Screen 文件创建完成
- [ ] Navigation 路由正确
- [ ] ViewModel 绑定正确
- [ ] 代码符合 Kotlin 规范

### 状态
- 分配：待分配
- 进度：0%

---

## 🟢 Phase 3: CI/CD + APK构建 (Worker 3)

### 任务
- [ ] P3-T1: 创建 .github/workflows/android.yml
- [ ] P3-T2: 配置 GitHub Secrets (signing key)
- [ ] P3-T3: 验证 Gradle 编译 (assembleDebug)
- [ ] P3-T4: 验证 CI/CD 流水线
- [ ] P3-T5: 生成签名 APK
- [ ] P3-T6: 提交所有代码到 GitHub

### 验收标准
- [ ] GitHub Actions 能自动触发
- [ ] assembleDebug 编译成功
- [ ] 生成 APK 文件
- [ ] 所有代码已提交 GitHub

### 状态
- 分配：待分配
- 进度：0%

---

## 📊 总进度
- Phase 1 (后端): ⬜⬜⬜⬜⬜ 0%
- Phase 2 (UI): ⬜⬜⬜⬜⬜ 0%
- Phase 3 (CI/CD): ⬜⬜⬜⬜⬜ 0%

## 📝 备注
- Phase 1 和 Phase 2 可以并行
- Phase 3 依赖 Phase 1 和 Phase 2 完成
- 每个 Worker 完成后必须提交代码到 GitHub
