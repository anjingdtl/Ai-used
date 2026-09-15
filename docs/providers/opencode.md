# OpenCode Go 额度（`providerId: opencode_go`）

> 最后核实日期：2026-09-15
> 状态口径：**IMPLEMENTED / NEEDS_CREDENTIAL**（代码已按当前真实结构实现，真实 E2E 待真实账号 Key）

## 官方用量接口

```
GET https://opencode.ai/zen/go/v1/usage
Authorization: Bearer <Key>
```

### 真实结构（见 `desktop-bridge/fixtures/opencode_usage_2026_09.json`）

顶层分 `rollingUsage` / `weeklyUsage` / `monthlyUsage` 三档，每档结构：

```json
{ "rollingUsage": { "usagePercent": 32.0, "resetInSec": 1200 } }
```

- `usagePercent`：0–100 已耗百分比
- `resetInSec`：**相对秒偏移**（`1200` = 从现在起 1200 秒）

## 窗口模型

| Bucket | windowType（协议常量） | 来源 |
| --- | --- | --- |
| 滚动用量 | `ROLLING_5_HOURS` | `rollingUsage` |
| 本周额度 | `WEEKLY` | `weeklyUsage` |
| 本月额度 | `MONTHLY` | `monthlyUsage` |

## reset 语义（P0-7，重要）

- 顶层/各档返回的是 `resetInSec`（**相对秒**），不是绝对时间。
- 必须计算为：`now + resetInSec` 输出 ISO-8601 UTC（如 `2026-09-15T10:20:30Z`）。
- **禁止**把 `1200` 这类秒数直接塞进 `resetAt` 字符串。
- 所有 reset 统一经 `_resolve_reset()` 归一为 ISO-8601 UTC。

## 通道

- 桌面桥：`AIQUOTA_OPENCODE_KEY`，或自动读取本地 `~/.local/share/opencode/auth.json`（`opencode-go` 条目，兼容 `XDG_DATA_HOME` / `OPENCODE_AUTH_CONTENT`）。
- Android：`BridgeQuotaProvider(ProviderId.OPENCODE_GO)`，经 Bridge 查询。

## 可靠性风险

- 官方端点字段随产品演进；结构不匹配返回 `source="unsupported"`，绝不伪造。
- 自测见 `bridge_self_test.py::run_opencode`（32% / 53% / 11%，并验证 reset = now+1200s）。
- 需要真实账号 Key 做最终 E2E。