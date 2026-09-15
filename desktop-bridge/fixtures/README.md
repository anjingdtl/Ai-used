# Bridge fixtures —— 来源与字段说明

本目录的 fixture 用于**解析逻辑自测**（`bridge_self_test.py` 直接读取，不发网络）。

> ⚠️ 每个 fixture 顶部 `_provenance` 记录来源/日期/字段说明。来源分三类：
> **官方文档**  官方公开文档确认的行/表/字段
> **逆向/工具** 社区工具或逆向得到的私有端点结构（准确性以真实响应为准）
> **真实响应**  已用真实账号抓取并脱敏
>
> 标注 `UNVERIFIED` 的字段，必须等拿到真实账号凭据抓包后在 `bridge_http_test.py / E2E`
> 中确认为权威。**禁止**把本目录想当然地当成官方契约。

## glm_quota_2026_09.json
- 来源：逆向/工具 + 社区真实响应示例。
  端点为 `GET https://api.z.ai/api/monitor/usage/quota/limit`（官方 Dashboard 底层，未公开文档化）。
- 日期：2026-09-15
- 结构（CONFIRMED，见 OmniRoute 2026-08 真实响应 + glm_usage.py）：
  `{ code, msg, success, data: { level, limits: [ { type, unit, number, usage, currentValue,
  remaining, percentage, nextResetTime } ] } }`
- 字段语义：
  - `usage/currentValue/remaining`：窗口总额 / 已耗 / 剩余（绝对值）；已耗% 用 `(usage-remaining)/usage` 精确反算，
    不只依赖被舍入的 `percentage`。
  - `percentage`：已耗百分比（0–100 整数）。
  - `nextResetTime`：**毫秒级 Unix 时间戳**（不是 `resetAt`/`reset_at`）。
  - `unit`：`3` → 5 小时滚动窗口；`6` → 每周(7 天)窗口。多源一致归纳，官方无公开枚举表；`unit=4` 未证实 → UNVERIFIED，不得硬编码。
- 官方确认：Coding Plan 为 **5 小时 + 每周** 双窗口（2026-07-30 改版为积分制）。

## minimax_token_plan_2026_09.json
- 来源：官方 API `GET https://www.minimaxi.com/v1/token_plan/remains`（国内）/ `.io`（海外）；
  `Authorization: Bearer <Token Plan 订阅 Key sk-cp-...>`。
- 日期：2026-09-15
- 结构：⚠️ 官方文档仅提供 curl，**未公开 JSON schema** → 字段级 **UNVERIFIED**。
  仅确认宏观语义为「5 小时 + 每周」双窗口、Bearer 认证、用量按百分比表达。
  解析器找不到必需字段时返回 `unsupported`，**绝不**靠猜字段私下成功。
- 字段（当前 fixture 代表上述宏观语义，均待真实抓包确认）：`used_percent` / `remaining_percent` / `plan` / `window_type` 等。

## opencode_usage_2026_09.json
- 来源：官方控制台源码（`subscription.get` 返回 `rollingUsage` 等）+ CodexBar 解析文档一致。
- 日期：2026-09-15
- 结构（强倾向 CONFIRMED）：顶层 `rollingUsage` / `weeklyUsage` / `monthlyUsage` 三档，
  每档对象 `{ usagePercent, resetInSec }`；`usagePercent` 为 0–100；`weekly`/`monthly` 可能缺失，需容错。
- `resetInSec` 语义：**从现在起 N 秒后的重置时刻**（相对秒偏移），Bridge 必须换算为 `now + N` 的 ISO-8601 UTC；
  **严禁**把 `1200` 直接写入 `resetAt`。

## 协议常量
所有 `windowType` 输出必须落在协议常量集合内（与 Android `WindowType` 枚举一一对应）：
`ROLLING` / `ROLLING_5_HOURS` / `DAILY` / `WEEKLY` / `MONTHLY` / `CREDIT` / `BALANCE` /
`TOKEN` / `REQUEST_COUNT` / `CUSTOM` / `UNKNOWN`。禁止 `5H`/`ROLLING_5H`/`WEEK`/`MONTH`。