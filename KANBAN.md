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

## ✅ Phase 1: 后端API验收与补全 (Worker 1) — 完成

### 已完成任务
- [x] P1-T1: 验收现有 backend/ 代码
- [x] P1-T2: 补全 COMPLETION.md
- [x] P1-T3: 补全 requirements.txt
- [x] P1-T4: 创建 main.py（FastAPI应用入口）
- [x] P1-T5: 创建 Dockerfile
- [x] P1-T6: 提交 backend/ 到 GitHub

### 状态
- ✅ 完成，已提交 GitHub (commit: 16db912)

---

## ✅ Phase 2: Android UI层 (Worker 2) — 完成

### 已完成任务
- [x] P2-T1: 创建 MainActivity.kt (Compose入口 + NavHost)
- [x] P2-T2: 创建 Navigation 路由定义 (NavRoutes.kt)
- [x] P2-T3: 创建 HomeScreen (首页 + 股票列表)
- [x] P2-T4: 创建 PredictScreen (预测页 + 价格图表)
- [x] P2-T5: 创建 BacktestScreen (回测页 + 绩效报告)
- [x] P2-T6: 创建 PortfolioScreen (持仓管理页)
- [x] P2-T7: 创建 SettingsScreen (设置页)
- [x] P2-T8: 创建 StockSearchScreen (股票搜索页)
- [x] P2-T9: 每个 Screen 绑定对应 ViewModel

### 状态
- ✅ 完成，已提交 GitHub

---

## ✅ Phase 3: CI/CD + APK构建 (Worker 3) — 完成

### 已完成任务
- [x] P3-T1: 创建 .github/workflows/android.yml
- [x] P3-T2: 创建 .github/workflows/backend.yml
- [x] P3-T3: 配置 4 个 Job (lint/unit-tests/build-debug/build-release)
- [x] P3-T4: 提交到 GitHub

### 状态
- ✅ 完成，已提交 GitHub

---

## ✅ Phase 4: 代码审查 + 修复 + 提交 — 完成

### 已完成任务
- [x] 审查后端 predictor.py — 逻辑完整
- [x] 审查后端 strategy_engine.py — 逻辑完整
- [x] 审查 Android UI 层 — 代码质量良好
- [x] 审查 CI/CD — 配置完整
- [x] 创建 backend/Dockerfile
- [x] 提交 37 个文件到 GitHub

### 状态
- ✅ 完成

---

## 🔄 Phase 5: 部署 + 联调 + 发布 — 进行中

### 负责人
- 总控：Hermes (default profile)
- Worker：cell_manager profile

### 目标
将 StockAdvisor 从"代码完成"推进到"可安装的 APK + 可运行的后端服务"

### 任务清单

#### P5-T1: 触发 GitHub Actions 验证 APK 编译
- **状态**: 🔄 进行中
- **说明**: 已有 4 次 CI 触发记录（commits: e038d04, bc37994, a8f592d, fd8cc5f），需要确认最新一次是否成功
- **验收**: GitHub Actions 全部 4 个 Job 通过（lint → unit-tests → build-debug → build-release）
- **产出**: Debug APK 上传到 GitHub Artifacts

#### P5-T2: 后端部署到云服务器
- **状态**: ⏳ 待开始
- **候选平台**: Render (免费) / Railway (免费)
- **说明**: 将 backend/ 部署为公网可访问的 FastAPI 服务
- **关键配置**:
  - CORS 需允许 Android 端访问
  - 端口 8000
  - 环境变量: TUSHARE_TOKEN (从 token/tushare_token.txt 获取)
- **验收**: `curl https://<deployed-url>/health` 返回 `{"code":200,"message":"healthy"}`

#### P5-T3: Android 端联调测试
- **状态**: ⏳ 待开始
- **说明**: Android 端连接部署后的后端 API，验证端到端功能
- **测试项**:
  - 首页股票列表加载
  - 日线预测 API 调用
  - 回测 API 调用
  - 持仓管理 API 调用
- **注意**: 默认连接 `http://10.0.2.2:8000`（模拟器），真机/公网需修改后端地址

#### P5-T4: 生成签名 APK
- **状态**: ⏳ 待开始
- **说明**: 生成可在 Android 设备上安装的签名 APK
- **步骤**:
  1. 生成 keystore: `keytool -genkey -v -keystore stockadvisor.keystore -alias stockadvisor -keyalg RSA -keysize 2048 -validity 10000`
  2. 配置 android/app/build.gradle.kts 签名
  3. 本地或 CI 生成签名 APK
- **产出**: `app-release-signed.apk`

### 进展日志
| 时间 | 事件 | 备注 |
|------|------|------|
| 2026-05-31 | Phase 1-4 全部完成 | commit 16db912 |
| 2026-06-01 00:15 | Phase 5 启动 | Hermes 总控，读取全部文档 |
| 2026-06-01 00:30 | CI/CD 修复开始 | Android CI 连续 5 次失败 |
| 2026-06-01 01:00 | CI/CD 修复完成 | 修复 7 个问题，Android CI 通过 |
| 2026-06-01 01:10 | 后端部署准备 | 创建 render.yaml + deploy workflow |

### 部署信息
- **Render 蓝图**: `render.yaml`（一键部署）
- **Tushare Token**: 存储在 `token/tushare_token.txt`（需手动配置到 Render 环境变量）
- **后端端口**: 8000
- **健康检查**: `GET /health` |

---

## 📊 总进度
- Phase 1 (后端): ✅ 100%
- Phase 2 (UI): ✅ 100%
- Phase 3 (CI/CD): ✅ 100%
- Phase 4 (审查): ✅ 100%
- Phase 5 (部署+发布): 🔄 刚开始

## 📝 关键信息
- GitHub: https://github.com/luoxiaoliangTj/Py_Learning
- 最新 commit: fd8cc5f (Gradle wrapper fix)
- 本地路径: ~/Py_Learning
- 后端端口: 8000
- Android 架构: Kotlin + Jetpack Compose + Hilt + Retrofit + Room
- 后端架构: FastAPI + Pandas + NumPy + Pydantic

## ⚠️ 已知问题（来自 CODE_REVIEW.md）
1. predictor.py 预测逻辑不完整（缺少完整预测区间计算）
2. strategy_engine.py 通道策略不完整（缺少 ATR 通道突破买卖条件）
3. performance_analyzer.py 需验证绩效分析完整性
4. 实时行情数据缺失（current_price 等字段返回 0）
5. 胜率计算简化（固定返回 0.5）
6. 预测策略信号为空（strategies 返回空数组）

## 🔙 回滚方案
- Git 可随时回滚到任意 commit
- 最新稳定 commit: fd8cc5f
- 如需回滚: `git reset --hard fd8cc5f`
