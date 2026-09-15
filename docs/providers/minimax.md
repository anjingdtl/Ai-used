# MiniMax Token Plan 额度（`providerId: minimax`）

> 最后核实日期：2026-09-15
> 状态口径：**IMPLEMENTED / NEEDS_CREDENTIAL · 结构未公开**（解析只在识别到结构化窗口对象时成功，否则诚实返回 unsupported）

## 官方接口

```
GET https://www.minimaxi.com/v1/token_plan/remains
Authorization: Bearer <Token Plan 订阅 Key>
```

## ⚠️ 结构未公开（P0-6 诚实口径）

- MiniMax 官方文档**只提供 curl 示例，未公开返回 JSON schema**。
- 因此**不依赖** `remaining_percent` 之类的单个猜测字段来算出额度并默默成功。
- 解析器仅在响应中出现**结构化窗口对象**（如 `rollingUsage` / `weeklyUsage`，内含 `usagePercent` + `resetInSec` / `resetAt`）时才解析为对应窗口的 QuotaBucket。
- 否则返回 `source="unsupported"`（`Provider 未配置/未开放` 语义），**绝不产出空 Bucket + LIVE**。

fixture `desktop-bridge/fixtures/minimax_token_plan_2026_09.json` 即代表这种「模糊单值」形态，用于回归验证必须被拒绝。

## 窗口模型（假设结构，待真实抓包固化）

| Bucket | windowType | 来源字段（假设） |
| --- | --- | --- |
| 滚动用量（5 小时） | `ROLLING_5_HOURS` | `rollingUsage` |
| 周用量 | `WEEKLY` | `weeklyUsage` |

> 拿到一次真实脱敏抓包后，请按真实结构更新解析器与 fixture，并在此记录字段说明。

## 通道

- 桌面桥：`AIQUOTA_MINIMAX_KEY` 环境变量。
- Android：`BridgeQuotaProvider(ProviderId.MINIMAX)`，经 Bridge 查询。

## 可靠性风险

- **未做真实 E2E**：官方 schema 未确认，当前解析是「结构未知时拒绝、有结构时解析」的安全策略。
- 需提供一次真实账号的脱敏响应（或直接给 `AIQUOTA_MINIMAX_KEY` 供 Bridge 直查）才能固化为 E2E_VERIFIED。