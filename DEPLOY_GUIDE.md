# 🚀 后端部署指南（Render）

## 一键部署步骤

### 1. 注册 Render
- 访问 https://render.com
- 用 GitHub 账号登录

### 2. 创建 Web Service
1. 点击 **"New +"** → **"Web Service"**
2. 选择 **"Deploy from Blueprint"**
3. 连接 GitHub 仓库 `luoxiaoliangTj/Py_Learning`
4. Render 会自动检测 `render.yaml`

### 3. 配置环境变量
在 Render Dashboard → Environment 添加：
- `TUSHARE_TOKEN`: `b57f26d791c2a8e4fef5874895638b67655164e2fc0d634d2cf0b53e`

### 4. 部署
- 点击 **"Create Web Service"**
- Render 会自动构建 Docker 镜像并部署
- 获得类似 `https://stockadvisor-backend-xxxx.onrender.com` 的 URL

### 5. 验证
```bash
curl https://<your-url>/health
# 应返回: {"code":200,"message":"healthy"}
```

### 6. 更新 Android 端
修改 `android/app/src/main/java/com/tangtang/stockadvisor/data/api/RetrofitClient.kt`（或类似文件）：
```kotlin
// 从
private const val BASE_URL = "http://10.0.2.2:8000"
// 改为
private const val BASE_URL = "https://<your-render-url>"
```

## 备选方案：Railway
1. 访问 https://railway.app
2. 用 GitHub 登录
3. 创建新项目 → 选择 GitHub 仓库
4. 设置根目录为 `backend/`
5. 添加环境变量 `TUSHARE_TOKEN`
6. 部署

## 注意事项
- Render 免费层有 15 分钟无请求后休眠
- 首次冷启动约 30-60 秒
- 如需 24/7 运行，考虑升级到付费层
