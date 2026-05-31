# 🔧 Phase 5 工作文档 — 部署 + 联调 + 发布

## 📌 文档说明
本文档是 Phase 5 的详细工作指南，由 Hermes 总控维护。
任何 worker 或 Hermes 重新启动后，阅读本文档即可快速接续工作。

---

## 🏗️ 项目架构回顾

```
┌─────────────────────────────────────────┐
│           Android App (Kotlin)           │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ │
│  │ HomeScreen│ │PredictScreen│ │BacktestScreen│ │
│  └─────────┘ └──────────┘ └──────────┘ │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ │
│  │PortfolioScreen│ │SettingsScreen│ │StockSearchScreen│ │
│  └─────────┘ └──────────┘ └──────────┘ │
└──────────────────┬──────────────────────┘
                   │ HTTP/REST API
┌──────────────────┴──────────────────────┐
│         Python FastAPI 后端              │
│  ┌──────────┐ ┌────────┐ ┌───────────┐ │
│  │ 策略引擎 │ │ 数据层 │ │ 回测引擎  │ │
│  └──────────┘ └────────┘ └───────────┘ │
└─────────────────────────────────────────┘
```

---

## 📂 关键文件路径

| 文件 | 路径 | 说明 |
|------|------|------|
| 技术方案 | `~/Py_Learning/ANDROID_PLAN.md` | 总体架构和任务拆分 |
| Kanban | `~/Py_Learning/KANBAN.md` | 任务看板和进展日志 |
| 代码审查 | `~/Py_Learning/CODE_REVIEW.md` | Phase 4 审查报告 |
| 后端完成文档 | `~/Py_Learning/backend/COMPLETION.md` | API 端点和运行方式 |
| Android 文档 | `~/Py_Learning/android/README.md` | Android 项目结构 |
| CI/CD Android | `~/Py_Learning/.github/workflows/android.yml` | Android CI 配置 |
| CI/CD 后端 | `~/Py_Learning/.github/workflows/backend.yml` | 后端 CI 配置 |
| Tushare Token | `~/Py_Learning/token/tushare_token.txt` | Tushare API Token |
| 后端入口 | `~/Py_Learning/backend/main.py` | FastAPI 应用入口 |
| 后端依赖 | `~/Py_Learning/backend/requirements.txt` | Python 依赖 |
| 后端 Dockerfile | `~/Py_Learning/backend/Dockerfile` | Docker 构建文件 |
| Android 入口 | `~/Py_Learning/android/app/src/main/java/com/tangtang/stockadvisor/MainActivity.kt` | Android 主入口 |
| Gradle 配置 | `~/Py_Learning/android/app/build.gradle.kts` | Android 构建配置 |

---

## 🔄 Phase 5 执行顺序

### Step 1: 验证 CI/CD 编译（P5-T1）
```bash
# 检查最新 GitHub Actions 状态
cd ~/Py_Learning
gh run list --limit 5
gh run view <run-id>  # 查看具体 run 的详情
```

### Step 2: 后端部署（P5-T2）
方案 A: Render.com（推荐）
- 注册 render.com
- 连接 GitHub 仓库
- 创建 Web Service，指向 backend/
- 环境变量: TUSHARE_TOKEN
- 部署后获得公网 URL

方案 B: Railway.app
- 注册 railway.app
- 连接 GitHub 仓库
- 选择 backend/ 目录
- 添加环境变量

方案 C: 自部署（Termux）
```bash
cd ~/Py_Learning/backend
pip install -r requirements.txt
nohup uvicorn main:app --host 0.0.0.0 --port 8000 > /tmp/backend.log 2>&1 &
```

### Step 3: 联调测试（P5-T3）
- Android 模拟器: http://10.0.2.2:8000
- 真机局域网: http://<电脑IP>:8000
- 真机公网: http://<部署URL>

### Step 4: 签名 APK（P5-T4）
```bash
# 生成 keystore
keytool -genkey -v -keystore stockadvisor.keystore \
  -alias stockadvisor -keyalg RSA -keysize 2048 -validity 10000

# 配置签名（在 android/app/build.gradle.kts 中添加 signingConfigs）
# 构建签名 APK
cd android
./gradlew assembleRelease
```

---

## 📋 进展记录

### 2026-06-01 00:15 — Phase 5 启动
- Hermes 完成全部文档阅读
- 更新 KANBAN.md，增加 Phase 5 详细计划
- 创建本文档作为接续指南
- 下一步: 验证 GitHub Actions CI/CD 编译状态

---

## ⚠️ 注意事项
1. **不要修改原始 Python 项目代码**（NewProjectV2402/ 目录）
2. **所有代码变更必须提交 GitHub**
3. **部署前先确保 CI/CD 编译通过**
4. **keystore 文件必须安全备份**，丢失后无法更新 APK
5. **Tushare Token** 存储在 `token/tushare_token.txt`，不要提交到 git（已在 .gitignore 中）
