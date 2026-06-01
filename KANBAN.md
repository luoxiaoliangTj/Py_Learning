# StockAdvisor 项目看板

> 更新时间：2026-06-01
> 文档：[[StockAdvisor项目档案]]

---

## 🔴 Blocked / 待解决

### B1: APK 安装后闪退
- **现象**：编译通过，安装后点击图标闪退
- **可能原因**：Compose BOM 版本兼容性 / Hilt 注入问题 / minSdk 不匹配
- **下一步**：需要 adb logcat 获取崩溃日志

### B2: 持仓加载架构错误
- **问题**：当前设计让 App 直接从 API 读持仓，但持仓数据源是用户本地 `.md` 文件
- **正确方案**：用户通过文件选择器导入 `.md` → App 解析 → 同步到后端
- **影响**：PortfolioScreen、PortfolioViewModel、后端 API 均需修改

---

## 🔵 In Progress / 进行中

### I1: 修复编译错误 ✅ 已完成
- [x] HomeScreen 缺 onPortfolioClick/onSettingsClick
- [x] SettingsScreen 缺 onBack
- [x] StockSearchScreen 参数名错误
- [x] ApiClient.kt response.body!! 语法
- **Run ID**: 26751636044（待验证）

### I2: 持仓导入功能设计
- **负责人**：Hermes（PM/架构）
- **子任务**：
  - [ ] 后端添加 `/api/portfolio/import` 接口
  - [ ] App 端 SAF 文件选择器
  - [ ] App 端 `.md` 表格解析
  - [ ] PortfolioScreen 添加导入按钮
  - [ ] PortfolioViewModel 添加 import 方法

---

## ✅ Done / 已完成

### Phase 1: 后端 FastAPI ✅
- 10 个 API 端点
- Token 管理 API
- 部署配置 render.yaml

### Phase 2: Android UI 基础架构 ✅
- 6 个 Screen 页面
- 5 个 ViewModel
- Navigation + Hilt DI
- OkHttp + Gson API 层

### Phase 3: CI/CD ✅
- GitHub Actions 编译 workflow
- 签名 APK 构建 workflow
- Keystore 配置

### Phase 4: 代码审查 ✅
- 多轮代码审查
- 泛型擦除问题排查
- Retrofit → OkHttp 迁移

### Phase 5-R1: 编译错误修复 ✅
- 7 个编译错误全部修复
- 已推送 commit 15cc9d2

---

## 📊 进度总览

| Phase | 状态 | 完成度 |
|-------|------|--------|
| Phase 1: 后端 | ✅ 完成 | 100% |
| Phase 2: Android UI | ✅ 完成 | 100% |
| Phase 3: CI/CD | ✅ 完成 | 100% |
| Phase 4: 代码审查 | ✅ 完成 | 100% |
| Phase 5: 编译修复 | ⏳ 进行中 | 80% |
| Phase 6: 持仓导入重构 | 🔴 未开始 | 0% |
| Phase 7: 联调测试 | 🔴 未开始 | 0% |
| Phase 8: 签名 APK 发布 | 🔴 未开始 | 0% |

---

## 📝 关键决策记录

### D1: 持仓交互方式（2026-06-01）
**原始方案（错误）**：App 硬编码路径读取 `.md` 文件
**正确方案**：用户通过 SAF 文件选择器选取 `.md` 文件 → App 解析 → 同步后端
**原因**：Android 无法直接访问桌面文件系统，需要用户交互选择

### D2: API 响应处理（2026-06-01）
**方案**：OkHttp 手动请求 + Gson 手动解析
**原因**：Retrofit 泛型擦除导致 `ParameterizedType` 运行时错误

### D3: 后端部署方式（2026-06-01）
**方案**：Render Web Service（非 Blueprint）
**原因**：Blueprint 需要银行卡认证
