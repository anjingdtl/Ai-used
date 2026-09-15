# 智谱 GLM Coding Plan 额度（`providerId: glm`）

> 最后核实日期：2026-09-15（以官方现状为准，勿以本文替代线上核实）
> 状态：**代码已实现，真实 E2E 未验收（待真实账号 Token）**

## 套餐结构

GLM Coding Plan 采用「**5 小时 + 每周积分**」双窗口机制：

- 每 5 小时一个用量窗口（短期可用量，接近 0 会被限流）
- 每自然周刷新积分池（长期总配额）

两者都以积分计数，App 建模为**多个 QuotaBucket**。

## 额度窗口

| Bucket | windowType | 说明 |
| --- | --- | --- |
| 5 小时窗口 | `ROLLING_5H` | 短期用量，`isKeyBucket` 用于告警 |
| 每周积分 | `WEEKLY` | 长期总配额 |

## 是否有官方 API

**Yes**，官方已提供稳定用量接口（`api.z.ai` 用量监控）：

```
GET https://api.z.ai/api/monitor/usage/quota/limit
```

响应 `data.limits[]`，按 `unit` 区分窗口（`3`=5H、`4`=WEEKLY），字段含 `percentage`、`resetAt`。

## 是否有官方 CLI 查询能力

官方 Coding Plan 插件/工具内部使用上述稳定接口；CLI 非必需。

## 认证方式

- 桌面桥：环境变量 `AIQUOTA_GLM_TOKEN` 携带账号 Token，请求头 `Authorization: <token>`。
- Android 端凭据：Bridge Bearer（存于 Keystore 加密）。

## 是否支持 Android 直连

**当前版本未开启直连**。Android 统一走 `BridgeQuotaProvider`（connector=BRIDGE），
Bridge 侧再直连官方 `api.z.ai` 用量接口。若未来希望去掉桌面依赖，在 ProviderRegistry
增加一个直连官方端点的 `DirectQuotaProvider` 即可，无需改页面模型。

## 实际代码实现方式

- 桌面桥：`GlmAdapter`（`KeyProviderAdapter`）→ 官方 `api.z.ai/api/monitor/usage/quota/limit`，
  解析 limits 去重合并出 5H/周 两个桶。未配置 Token 时返回 `source="unsupported"`，绝不伪造。
- Android：`ProviderModule.bindGlm()` = `BridgeQuotaProvider(ProviderId.GLM)`。

## 可靠性风险

- 依赖官方非公开约定端点，`unit` 编码/字段名变更需随官方演进而更新。
- 自测见 `desktop-bridge/bridge_self_test.py::run_glm`（37% / 15% 样例）。
- 需要真实账号 Token 做最终 E2E。