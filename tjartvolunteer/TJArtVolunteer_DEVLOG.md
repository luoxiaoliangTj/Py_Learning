# TJArtVolunteer 开发全记录

## 项目概述
- **产品名**：天津美术志愿（TJArtVolunteer）
- **包名**：com.tangtang.tjartvolunteer
- **定位**：天津美术高考志愿辅助App，离线运行，数据内置
- **技术栈**：Kotlin + Jetpack Compose + Material3 + Room DB
- **构建环境**：Termux Android → proot-distro Ubuntu 24.04（解决ARM aapt2问题）
- **API级别**：minSdk=26, targetSdk=34

## 开发时间线

### 2026-06-04 第一天
从零到可用，12小时内完成核心功能。

## 架构设计

### 三页架构
- **第1页（首页）**：成绩输入 + 全局悬浮调试面板
- **第2页（推荐结果）**：保/稳/冲三档推荐，点击展开详细计算过程
- **第3页（院校库）**：65所院校列表，搜索/筛选，点击进入院校详情

### 核心算法：排名换算
- 核心思想：**用排名做换算，不用绝对分数直接比**
- 不同年份公式不同（2024=40%/60%, 2025=50%/50%），分数不能直接比
- 用户输入今年分数→算综合分→与历年录取最低分做差→得出分差→分类保/稳/冲
- 用户可以输入"今年总人数"，用于位次比例换算（目前大部分数据只有分数没有位次）

### 校考排除策略
- **宁多勿少**：只排除已确认的校考专业，其余全部纳入推荐
- 按专业逐个排查，不按学校一刀切
- 天津美术学院：校考4个（绘画/雕塑/中国画/实验艺术），其余统考

## 数据状态
- **65所院校**，295个专业
- **60所**有分数数据（2024年或2025年）
- **2所**有位次数据（天津美术学院、天津传媒学院）
- **5所**无任何分数数据
- **59所**只有minScore没有minRank
- 数据来源：原Python项目整理 + 网络收集

## 关键技术问题与解决方案

### 1. ARM构建问题
- **问题**：Termux原生编译aapt2只支持x86_64，ARM64设备无法构建
- **解决**：用proot-distro安装Ubuntu 24.04，在proot内编译
- **命令**：`proot-distro login ubuntu --bind /path/to/project:/project -- bash -c '...'`

### 2. 数据库预加载失败（最关键的bug）
- **问题**：App启动时预加载院校数据，但只插入了1所
- **根因链**：
  1. `UniversityInfo.toEntity()`里`examExclude.joinToString()`对null列表调用NPE
  2. Gson反序列化时，JSON没有的字段会用null而不是Kotlin默认值
  3. `UniversityEntity`的`note: String`字段不允许null
- **修复**：`toEntity()`里所有list字段加`?.joinToString() ?: ""`，`note`加`?: ""`
- **最终方案**：用`Thread { loadPresetData() }.start()` + `runBlocking`逐条插入

### 3. Room Callback死锁
- **问题**：用`RoomDatabase.Callback.onCreate()`填充数据会死锁
- **原因**：onCreate在数据库事务内调用，里面又做数据库操作
- **解决**：改用Application里手动触发

### 4. 全局日志系统
- **问题**：调试信息分散在各处，跨页面看不到
- **解决**：
  - 创建`DebugLog`单例对象，全局统一调用
  - 所有日志带标签：`[SYS]` `[DATA]` `[USER]` `[CALC]` `[RESULT]` `[FAIL]` `[PAGE]`
  - MainActivity右上角悬浮面板，所有页面可见
  - 颜色区分：红=错误 橙=警告 绿=结果 蓝=用户操作 紫=计算

### 5. 跨页面数据共享
- **问题**：ResultScreen用`viewModel()`创建独立实例，拿不到HomeScreen的计算结果
- **解决**：创建`RecommendationHolder`单例对象存储推荐结果

### 6. 位次数据缺失
- **问题**：60所院校里只有2所有minRank，导致大部分被跳过
- **修复**：允许minRank为null，只用minScore做匹配

## 项目文件结构
```
tjartvolunteer/
├── app/src/main/
│   ├── assets/data/universities.json  (65所院校+295专业)
│   ├── java/com/tangtang/tjartvolunteer/
│   │   ├── MainActivity.kt           (底部导航+全局悬浮调试面板)
│   │   ├── TJArtVolunteerApp.kt        (Application+预加载)
│   │   ├── DebugLog.kt               (全局日志)
│   │   ├── RecommendationHolder.kt   (跨页面推荐结果共享)
│   │   ├── data/
│   │   │   ├── db/AppDatabase.kt     (Room数据库 v5)
│   │   │   ├── db/UniversityDao.kt   (DAO)
│   │   │   └── model/University.kt    (Entity+JSON模型+toEntity)
│   │   ├── domain/algorithm/
│   │   │   └── ScoreCalculator.kt    (综合分计算+推荐引擎)
│   │   ├── viewmodel/HomeViewModel.kt
│   │   ├── navigation/NavGraph.kt
│   │   └── ui/screen/
│   │       ├── HomeScreen.kt         (第1页:输入)
│   │       ├── ResultScreen.kt       (第2页:推荐结果)
│   │       ├── UniversityListScreen.kt(第3页:院校库)
│   │       ├── UniversityDetailScreen.kt(院校详情)
│   │       ├── SyncLogScreen.kt      (同步日志)
│   │       └── ProfileScreen.kt       (我的)
│   └── res/xml/network_security_config.xml
└── release/tjart/                    (原始数据文件)
```

## 版本历史
- **v0.1.0**：首次编译成功，基础UI
- **v0.2.0**：重构架构，分离静态/动态数据
- **v0.2.1~v0.2.3**：修复Flow collect死锁，加重试
- **v0.2.5**：修复NPE（examExclude/note null），逐所插入+日志
- **v0.2.6**：三页架构（首页/推荐结果/院校库），排名算法，详细计算过程
- **v0.2.7**：全局日志系统，修复匹配bug（2所→60所），位次显示修复

## 待完成
- [ ] 收集2025年更多院校的录取数据（目前只有2所2025年数据）
- [ ] 收集位次数据（目前只有2所有minRank）
- [ ] 掌上高考API网络同步
- [ ] 数据变更确认UI完善
- [ ] 专业细分的历年录取分数
- [ ] GitHub Release发布

## 编译命令
```bash
proot-distro login ubuntu --bind /data/data/com.termux/files/home/Py_Learning/tjartvolunteer:/project --bash -c '
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
cd /project
echo "sdk.dir=/opt/android-sdk" > local.properties
./gradlew clean assembleDebug
'
```

## APK发送方式
```bash
source ~/.hermes/.env
curl --max-time 300 -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendDocument" \
  -F "chat_id=8215266997" \
  -F "document=@/data/data/com.termux/files/home/TJArtVolunteer-v0.2.7.apk" \
  -F "caption=版本说明"
```
