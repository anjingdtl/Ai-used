# AI 额度管家（AI Quota）

一个原生 Android 应用，用于**统一查询和监控**你订阅的多个 AI Coding 服务的剩余额度，避免在关键开发时被平台限流/断供。

- 平台：Android（minSdk 28 / targetSdk 36）
- 语言：Kotlin
- UI：Jetpack Compose + Material 3
- 架构：Clean Architecture + MVVM + Repository + Hilt

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 统一额度查询 | 一次刷新，汇总 ChatGPT/Codex、智谱 GLM、MiniMax、OpenCode Go 的剩余额度 |
| 动态 Quota Bucket | 用统一的「额度桶」抽象适配不同平台的额度规则（5 小时/日/周/月、百分比/计数/金额） |
| Last Known Good Snapshot | 每次查询成功即持久化快照，断网/失败时保留上一次有效数据，保证离线可用 |
| 多窗口聚合 | 每个平台可包含多个额度桶，展示剩余/已用/百分比/下次重置时间 |
| 后台周期刷新 | WorkManager 最小 15 分钟一次自动刷新额度 |
| 额度告警 | 关键额度低于设定阈值（30/20/10/5%）时发通知，带去重防骚扰 |
| 安全存储 | API Key 用 Android Keystore + AES-256-GCM 加密落盘，不存明文 |
| 桌面端桥接 | 对无公开 API 的平台，通过局域网 HTTP 协议从桌面 CLI 拉取额度 |
| 离线可用 | 所有 UI 由本地缓存驱动，网络抖动不影响查看 |
| 桌面小组件 | 主屏小组件直接显示当前最低关键额度百分比 |
| 首次引导 | 冷启动三屏 Onboarding，一次点击直达主页 |

## 支持的平台

> 状态口径：**代码状态** = 仓库内实现到什么程度；**真实 E2E** = 是否已用真实账号 + 真实查询跑通端到端。
> 直到用真实账号完成一次成功查询，任何平台都**不会**标记为「可查询」。

| 平台 | 实现方式 | 代码状态 | 真实 E2E 状态 |
| --- | --- | --- | --- |
| ChatGPT / Codex | Android Bridge Provider + 桌面桥适配器 | 已实现（Bridge 协议就绪） | 未验收（需桌面 CLI 登录态/Tokey 且 Android 连接 Bridge） |
| 智谱 GLM Coding Plan | Android Bridge Provider + 官方用量接口适配器 | 已实现 | 未验收（待真实账号 Token 验证） |
| MiniMax Token / Coding Plan | Android Bridge Provider + 桌面桥适配器 | 已实现 | 未验收（待真实账号 Token 验证） |
| OpenCode Go | Android Bridge Provider + 桌面桥适配器 | 已实现 | 未验收（待真实账号 Token 验证） |
| Grok / xAI | Unavailable 占位 Provider | 已实现（后端明确不提供额度接口） | 官方无接口，不支持 |
| Debug（内置模拟数据） | 本地 Mock（仅 `src/debug`） | 已实现 | 仅 debug 构建，**打不进 Release** |

各平台额度数据与桥接协议详见 [docs/providers](docs/providers/)。
桌面桥使用方式见 [desktop-bridge/README.md](desktop-bridge/README.md)。

## 快速开始

环境要求：JDK 17、Android SDK（compileSdk 36）。

```bash
# 构建 debug APK
./gradlew :app:assembleDebug

# 单元测试
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# 桌面桥解析自测（无真实账号也能验证解析逻辑）
python3 desktop-bridge/bridge_self_test.py
```

> 仓库已内置 Gradle Wrapper（`./gradlew` / `gradlew.bat`），无需自备 Gradle。
> Windows 用 `gradlew.bat assembleDebug`；Linux/macOS 用 `./gradlew assembleDebug`。
> 依赖仓库可按环境自动切换官方仓库与阿里云镜像（见 `settings.gradle.kts`）。

## 目录结构

```
app/src/main/java/com/aiquota/app/
├── AiQuotaApplication.kt        # Application：通知渠道 + 定时同步
├── MainActivity.kt              # 单一 Activity + Compose 导航
├── domain/                      # 领域层（纯 Kotlin，无 Android 依赖）
│   ├── model/                   #   领域模型（QuotaBucket / QuotaSnapshot / ProviderAccount …）
│   └── repository/              #   仓库接口（Quota/History/Account/Notification/Settings）
├── data/                        # 数据层
│   ├── database/                #   Room 数据库（实体 + DAO）
│   ├── repository/              #   仓库实现（含 Last-Known-Good 逻辑）
│   ├── mapper/                  #   实体 ⇄ 领域模型映射
│   └── security/                #   Keystore AES-GCM 加密存储
├── core/
│   ├── network/                 #   OkHttp/Retrofit 工厂 + 错误映射
│   └── notify/                  #   通知（QuotaNotifier）
├── provider/                    # 平台适配层
│   ├── base/                    #   SnapshotBuilder 等公共工具
│   ├── bridge/                  #   桌面端局域网桥接客户端
│   ├── debug/                   #   Debug 模拟 Provider
│   ├── unavailable/             #   未开放额度平台的占位 Provider
│   └── ProviderRegistry.kt      #   Provider 注册中心
├── ui/                          # 表现层（Compose）
│   ├── dashboard/               #   主列表
│   ├── detail/                  #   单平台详情 + 历史
│   ├── addaccount/              #   添加账号
│   ├── settings/                #   设置
│   ├── onboarding/              #   首次引导
│   ├── components/              #   复用组件
│   ├── theme/                   #   主题
│   └── util/                    #   格式化工具
├── widget/                      # 桌面 AppWidget
└── work/                        # WorkManager：周期同步 + 额度评估
```

## 架构要点

Clean Architecture 分层，见 [docs/architecture.md](docs/architecture.md)。

## 测试与验收

单元测试覆盖 Credential 链、Keystore AES-GCM、Last-Known-Good 断网流、同步事件、Bridge 客户端与 Provider、通知恢复、历史清理、刷新间隔等。
运行与静态验收说明见 [docs/acceptance-testing.md](docs/acceptance-testing.md)。