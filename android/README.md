# StockAdvisor Android

## 项目结构
```
android/
├── app/
│   ├── build.gradle.kts          # 模块级 Gradle 配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/tangtang/stockadvisor/
│           ├── MainActivity.kt           # 主入口（Hilt + Compose）
│           ├── StockAdvisorApp.kt        # Application 类
│           ├── data/
│           │   ├── api/                  # Retrofit API 接口
│           │   ├── local/                # Room 数据库
│           │   ├── model/               # 数据模型
│           │   └── repository/           # 仓库层
│           ├── ui/
│           │   ├── navigation/           # NavHost 导航图
│           │   ├── screen/               # 各页面 Composable
│           │   └── theme/                # Material3 主题
│           └── viewmodel/               # ViewModel 层
└── build.gradle.kts              # 项目级 Gradle 配置
```

## 架构
- **UI**: Jetpack Compose + Material3
- **DI**: Hilt
- **网络**: Retrofit2 + OkHttp
- **本地存储**: Room + DataStore
- **图表**: MPAndroidChart
- **导航**: Navigation Compose

## 构建
```bash
cd android
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK
```

## CI/CD
GitHub Actions 自动编译，APK 上传到 Artifacts。
```

## API 对接
Android 端默认连接 `http://10.0.2.2:8000`（Android 模拟器访问本机）。
真机测试需修改 `SettingsScreen` 中的后端地址。
