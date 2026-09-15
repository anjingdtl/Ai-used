# Bridge 协议契约（Quota Bridge Protocol）

> 本文件是 Desktop Bridge 与 Android 端之间额度协议的**唯一权威契约**。
> Desktop（Python）Adapter 与 Android（Kotlin）解析器必须严格执行同一套常量，禁止各自创造别名。

- 日期：2026-09-15
- 状态：V1 候选阶段锁定的协议 v1。

## 1. 通道与端点

默认监听 `0.0.0.0:8787`。

| 端点 | 方法 | 鉴权 | 作用 |
| --- | --- | --- | --- |
| `/health` | GET | 无 | 桥在线与版本 |
| `/providers` | GET | 无 | 已注册 Provider 列表及 `supportsQuota` |
| `/pair`   | POST | `Authorization: Bearer <secret>` | 校验 Bridge Secret，返回账号 ID |
| `/quota`  | GET  | `Authorization: Bearer <secret>`，`X-Provider`，`X-Account` | 返回目标 Provider 的额度响应 |

`/quota` 的响应体即下文「额度响应」；`X-Provider` / `X-Account` 为必填请求头。

## 2. 传输安全

- 生产优先 HTTPS；局域网 HTTP 仅限可信 Wi-Fi（见 `docs/security.md`）。
- Release 构建默认禁止明文；Debug 构建允许受控局域网明文以支持真机联调。

## 3. 额度响应（`/quota`）

```json
{
  "provider": "glm",
  "accountId": "acc-1",
  "accountName": "GLM Coding Plan",
  "plan": "GLM Coding Plan",
  "source": "bridge",
  "buckets": [
    {
      "id": "glm-rolling_5_hours",
      "name": "积分（5小时滚动）",
      "type": "PERCENT",
      "used": 10360.0,
      "limit": 28000.0,
      "remaining": 17640.0,
      "usedPercent": 63.0,
      "remainingPercent": 37.0,
      "unit": null,
      "windowType": "ROLLING_5_HOURS",
      "windowStartAt": null,
      "resetAt": "2026-09-15T10:20:30Z"
    }
  ],
  "balance": null,
  "queriedAt": "2026-09-15T10:20:30Z"
}
```

### 字段语义

| 字段 | 说明 | 规则 |
| --- | --- | --- |
| `provider` | 厂商 key（`glm` / `minimax` / `opencode`） | 必填，非空 |
| `accountId` | 账号标识 | 必填，非空 |
| `source` | `"bridge"` = 真实额度；`"unsupported"` = 厂商未开放/未配置 | **`unsupported` 不得当作成功额度** |
| `buckets[].windowType` | 见下节枚举 | 必须命中协议枚举 |
| `buckets[].remainingPercent` / `usedPercent` | 0–100 浮点 | 越界（>100 或 <⁻1）视为服务端非法 |
| `buckets[].resetAt` | 下次重置时间 | **必须为 ISO-8601 UTC**（如 `2026-09-15T10:20:30Z`），禁止塞数字秒 | 
| `balance` | Balance-only 厂商（可选） | 与 buckets 二选一，至少一个非空 |

**强校验**：`provider`/`accountId` 非空 + `source != "unsupported"` + 至少一个非空 bucket 或合法 balance。
任何不满足项 => Android 端抛出 `QueryError.InvalidResponse` 或 `QueryError.ProviderUnavailable`，
**绝不生成空 Snapshot 后标记 LIVE**。

## 4. windowType 统一枚举（Name 契约）

Desktop 与 Android 必须使用同一套字符串。此集合已写死在

- Desktop：`desktop-bridge/bridge.py` 的 `_WINDOW_*` 常量与 `_norm_window()`
- Android：`com.aiquota.app.domain.model.WindowType`

合法值（大小写敏感的 UPPER_SNAKE）：

```
ROLLING
ROLLING_5_HOURS
DAILY
WEEKLY
MONTHLY
CREDIT
BALANCE
TOKEN
REQUEST_COUNT
CUSTOM
UNKNOWN
```

**禁止**出现在任何 Adapter 输出的值：`5H`、`ROLLING_5H`、`WEEK`、`MONTH`、`DAILY_WINDOW` 等。
Desktop 端 `_norm_window()` 负责把历史/内部别名归一到上述集合；Android 端 `WindowType.valueOf()`
必须能解析全部输出值，解析不了即违反协议。

## 5. 厂商语义映射（2026-09 核对）

| 厂商 | 窗口 | windowType | 来源 |
| --- | --- | --- | --- |
| GLM | 5 小时滚动 | `ROLLING_5_HOURS` | 官方 `nextResetTime` 毫秒级，unit=3 |
| GLM | 每周 | `WEEKLY` | unit=6 |
| MiniMax | 5 小时滚动 | `ROLLING_5_HOURS` | `window_type=rolling` |
| MiniMax | 每周 | `WEEKLY` | 双窗口聚合 |
| OpenCode | 滚动 | `ROLLING` | `rollingUsage` |
| OpenCode | 每周 | `WEEKLY` | `weeklyUsage` |
| OpenCode | 每月 | `MONTHLY` | `monthlyUsage` |

## 6. resetAt 归一规则

所有 `resetAt` 必须以 ISO-8601 UTC 输出。Desktop 端 `_resolve_reset()` 按量级区分：

- 数值 `> 1e11` => 毫秒时间戳（如 GLM `nextResetTime`）
- 数值 `> 1e8`  => 绝对秒时间戳
- 数值 `0..1e8` => 相对秒偏移（如 OpenCode `resetInSec=1200` => `now + 1200s`）
- ISO 字符串 => 原样保留

禁止把 `resetInSec` 数字直接塞进 `resetAt`。

## 7. 一致性测试

- Python：`desktop-bridge/bridge_self_test.py`（输出值必须通过 Android `valueOf`，协议常量集合比对）。
- Python HTTP：`desktop-bridge/bridge_http_test.py`（覆盖 `/health`、`/providers`、`/pair`、`/quota` 各分支）。
- Android：`WindowTypeProtocolTest.kt`（协议集合与 `WindowType.entries` 一一对应）。