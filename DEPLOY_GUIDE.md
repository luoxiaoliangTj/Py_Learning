# 🚀 StockAdvisor 部署指南

## 后端部署（Render）

### 一键部署
1. 访问 https://render.com 并用 GitHub 账号登录
2. Dashboard → **New +** → **Web Service** → **Deploy from Blueprint**
3. 连接 GitHub 仓库 `luoxiaoliangTj/Py_Learning`
4. Render 自动检测 `render.yaml`，点击 **Create Web Service**
5. 在 Environment 页面添加环境变量：
   - `TUSHARE_TOKEN`: `b57f26d791c2a8e4fef5874895638b67655164e2fc0d634d2cf0b53e`
6. 等待部署完成（约 2-3 分钟）
7. 获得 URL：`https://stockadvisor-backend-xxxx.onrender.com`

### 验证
```bash
curl https://<your-url>/health
# 应返回: {"code":200,"message":"healthy"}
```

---

## 签名 APK 生成

### 1. 设置 GitHub Secrets
在 GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret：
- `KEYSTORE_PASSWORD`: 自定义密码（如 `stockadvisor123`）
- `KEY_PASSWORD`: 自定义密码（如 `stockadvisor123`）

### 2. 触发构建
GitHub 仓库 → Actions → **Build Signed Release APK** → **Run workflow**

### 3. 下载 APK
构建完成后，在 Artifacts 中下载 `stockadvisor-release-signed`

---

## Android 端配置

### 连接后端
1. 安装 APK 到 Android 设备
2. 打开 App → 设置页
3. 修改后端 URL 为 Render 部署的 URL
4. 保存后返回首页即可使用

### 模拟器测试
如果使用 Android 模拟器，默认 URL `http://10.0.2.2:8000` 即可（需本地运行后端）

---

## 项目结构
```
Py_Learning/
├── backend/          # FastAPI 后端
│   ├── main.py       # 应用入口
│   ├── core/         # 核心逻辑
│   ├── api/          # API 路由
│   └── models/       # 数据模型
├── android/          # Android 客户端
│   ├── app/          # 主应用模块
│   └── gradle/       # Gradle 配置
├── .github/          # GitHub Actions
│   ├── workflows/
│   │   ├── android.yml           # Android CI
│   │   ├── backend.yml           # 后端 CI
│   │   ├── deploy-backend.yml    # 后端部署
│   │   └── build-signed-apk.yml  # 签名 APK
├── token/            # API Token（不提交到 git）
├── render.yaml       # Render 蓝图
└── KANBAN.md         # 项目看板
```
