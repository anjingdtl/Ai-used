# MiniMax Token / Coding Plan 额度（`providerId: minimax`）

> 最后核实日期：2026-09-15（以官方现状为准，勿以本文替代线上核实）
> 状态：**代码已实现，真实 E2E 未验收（待真实账号 Key）**

## 套餐结构

MiniMax 提供 **Token Plan（订阅 Token 包）** 与 Coding Plan。两者均以额度/用量展示。

## 额度窗口

以**滚动窗口**为主（`ROLLING_5H`），部分响应含 `window_type`/`cycle`。App 建模为 QuotaBucket。

## 是否有官方 API

**Yes**，官方公开端点：

```
GET https://www.minimaxi.com/v1/token_plan/remains
```

响应含用量百分比字段（`used_percent` / `remaining_percent` / `rolling*`）。

## 是否有官方 CLI 查询能力

无独立 CLI；官方 API 即权威来源。

## 认证方式

- 桌面桥：环境变量 `AIQUOTA_MINIMAX_KEY`，请求头 `Authorization: Bearer <key>`。
- Android 端凭据：Bridge Bearer（存于 Keystore 加密）。

## 是否支持 Android 直连

**当前版本未开启直连**。Android 走 `BridgeQuotaProvider`（connector=BRIDGE），
Bridge 侧再直连官方 `minimaxi.com` 端点。

## 实际代码实现方式

- 桌面桥：`MiniMaxAdapter` → 官方 `/v1/token_plan/remains`，解析 used/remaining/rolling 百分比。
  结构不匹配时返回 `source="unsupported"`，不伪造。
- Android：`ProviderModule.bindMiniMax()` = `BridgeQuotaProvider(ProviderId.MINIMAX)`。

## 可靠性风险

- 响应字段名较杂（`used_percent`/`remaining_percent`/`rollingUsage` 并存），按多种 key 兜底解析。
- 自测见 `bridge_self_test.py::run_minimax`（37% 样例）。
- 需要真实账号 Key 做最终 E2E。