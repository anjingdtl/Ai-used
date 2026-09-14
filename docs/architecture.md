# 架构设计

AI 额度管家采用 **Clean Architecture + MVVM + Repository** 分层，配合 Hilt 依赖注入。目标是让「平台适配」与「业务/UI」解耦，新增一个 AI 服务时无需改动核心逻辑。

## 分层

```
┌─────────────────────────────────────────────────────────────┐
│  UI 层 (ui/)         Compose 页面 + ViewModel + 格式化工具      │
│    └── 只面向 domain 层的模型与仓库接口，不感知平台私有数据结构      │
├─────────────────────────────────────────────────────────────┤
│  Domain 层 (domain/)  纯 Kotlin，无 Android 依赖               │
│    ├── model/          QuotaBucket / QuotaSnapshot / 枚举      │
│    └── repository/     6 个仓库接口                             │
├─────────────────────────────────────────────────────────────┤
│  Data 层 (data/)      Room / DataStore / Keystore 实现         │
│    ├── database/      实体 + DAO                               │
│    ├── repository/    仓库实现（Last-Known-Good 核心）           │
│    ├── mapper/        实体 ⇄ 领域模型                           │
│    └── security/      Keystore AES-256-GCM 加密凭据             │
├─────────────────────────────────────────────────────────────┤
│  Provider 层 (provider/)  各平台适配，实现统一 QuotaProvider 接口 │
│    ├── bridge/         桌面端局域网桥接                          │
│    ├── debug/          Debug 模拟                               │
│    ├── unavailable/    未开放额度平台占位                         │
│    └── ProviderRegistry  注册中心（路由）                       │
└─────────────────────────────────────────────────────────────┘
```

依赖方向：`UI → Domain ← Data`、`Domain ← Provider`。数据层与 Provider 层都**面向 Domain 接口编程**，互不直接耦合。

## 关键机制

### 1. 动态 Quota Bucket（额度桶）

不硬编码任何平台字段（如 `fiveHourQuota`），而是用统一的桶模型描述任意平台的额度：

- `windowType: ROLLING / DAILY / WEEKLY / MONTHLY / CUSTOM`
- `type: PERCENT / COUNT / MONEY`
- 字段：`remaining / used / limit / remainingPercent / resetAt (next reset)`
- `isKeyBucket`：标记「关键额度桶」，用于告警聚合

这样 GLM 的「5 小时 + 每周积分」、MiniMax 的 Token、Codex 的订阅配额都能用同一套模型表达，UI 无需为每个平台写分支。

### 2. Last Known Good Snapshot

`QuotaRepositoryImpl.refresh(accountId)` 的流程：

```
读取账号 → 取 Provider → 若是 UNAVAILABLE 跳过
→ 是否有本地缓存 → 调 provider.fetchQuota()
   ├─ 成功 → persistSnapshot()（覆盖缓存）+ record(history) + 标记 LIVE
   ├─ ProviderUnavailable → 标记 UNAVAILABLE
   ├─ QueryError → 有缓存标 CACHED，无缓存标 FAILED_NO_CACHE
   └─ 其它异常 → toQueryError() 同上
```

关键点：**只有网络查询成功才覆盖持久化快照**，失败时旧快照原样保留。而所有 `observe*` 的 UI Flow 完全由本地 Room 驱动，因此**断网/失败时 UI 立即有值**（直观的离线可用）。

### 3. Provider 注册中心

`ProviderRegistry` 持有 `Map<ProviderId, QuotaProvider>`，Repository 只按 `providerId` 取实例。新增平台：

1. `ProviderId` 枚举追加一项；
2. 实现 `QuotaProvider` 接口；
3. 在 DI Module 构建实例并注册。

### 4. 安全存储（Keystore AES-256-GCM）

`SecureCipherStore`：用 Android Keystore 生成不可导出的 AES 密钥，凭据以 AES-256-GCM 加密后存入 Room，`usesCleartextTraffic=false`。API Key 不明文落地。

### 5. 后台同步 + 告警

`QuotaSyncScheduler` 用 WorkManager 注册周期任务（最小 15 分钟），`QuotaSyncWorker` 执行 `refreshAll()` 后评估所有「关键额度桶」是否跌破用户阈值，命中则通过 `NotificationRepository` 去重后发通知，避免重复骚扰。

### 6. 桌面端桥接协议

对无公开 API 的平台，`BridgeClient` 通过局域网 HTTP 与桌面端 CLI 通信：`GET /health`、`GET /providers`、`GET /quota`（带 Bearer 凭据）、`POST /pair`。配额结构用通用 `BridgeBucket` 传输，再由 `SnapshotBuilder` 归一化为 `QuotaSnapshot`。

## 仓库接口（Domain）

| 接口 | 职责 |
| --- | --- |
| `QuotaRepository` | 快照查询/刷新、`observeEnabledStates`、`observeState`、`refreshAll` |
| `HistoryRepository` | 额度历史变化记录 |
| `AccountRepository` | 账号元数据 + 加密凭据管理 |
| `SettingsRepository` | 刷新间隔/实时监控/通知阈值（DataStore） |
| `NotificationRepository` | 通知去重状态 |