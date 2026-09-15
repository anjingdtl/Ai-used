# OpenCode Go 额度（`providerId: opencode_go`）

> 最后核实日期：2026-09-15（以官方现状为准，勿以本文替代线上核实）
> 状态：**代码已实现，真实 E2E 未验收（待真实账号 Key）**

## 套餐结构

OpenCode Go / opencode 提供按周期（滚动 5h / 周 / 月）的使用额度。

## 额度窗口

| Bucket | windowType |
| --- | --- |
| 滚动 5 小时 | `ROLLING_5H` |
| 本周额度 | `WEEKLY` |
| 本月额度 | `MONTHLY` |

## 是否有官方 API

**Yes**，官方用量端点：

```
GET https://opencode.ai/zen/go/v1/usage
```

响应含 `rollingUsage` / `weeklyUsage` / `monthlyUsage` 百分比（或对象，内含
`usagePercent` / `resetInSec` 等）。

## 是否有官方 CLI 查询能力

opencode CLI 以其本地登录态（`~/.local/share/opencode/auth.json`，`opencode-go` 条目）保存 API Key；
Bridge 可读取该 Key 直查官方用量 API。

## 认证方式

- 桌面桥：环境变量 `AIQUOTA_OPENCODE_KEY`，或自动读取本地 `auth.json`（兼容 `XDG_DATA_HOME` /
  `OPENCODE_AUTH_CONTENT`）。请求头 `Authorization: Bearer <key>`。
- Android 端凭据：Bridge Bearer（存于 Keystore 加密）。

## 是否支持 Android 直连

**当前版本未开启直连**。Android 走 `BridgeQuotaProvider`（connector=BRIDGE），
Bridge 侧再直连官方 `opencode.ai` 端点。

## 实际代码实现方式

- 桌面桥：`OpenCodeAdapter` → 官方 `/zen/go/v1/usage`，用 `_pct_value` 兼容数字/对象字段，
  产出 5h/周/月 桶。结构不匹配返回 `source="unsupported"`。
- Android：`ProviderModule.bindOpenCode()` = `BridgeQuotaProvider(ProviderId.OPENCODE_GO)`。

## 可靠性风险

- 官方端点字段随产品演进，`_pct_value` 多 key 兜底已加。
- 自测见 `bridge_self_test.py::run_opencode`（32% / 53% / 11% 样例）。
- 需要真实账号 Key 做最终 E2E。