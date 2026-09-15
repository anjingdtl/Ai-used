# ChatGPT / Codex 额度（`providerId: codex`）

> 最后核实日期：2026-09-15（以官方现状为准，勿以本文替代线上核实）
> 状态口径：**IMPLEMENTED / UNSUPPORTED_AUTOMATION**（官方无订阅额度 API；`codex status` 仅登录态，额度需交互式 `/status`，无法非交互取值 -> `source="unsupported"`，不编造）

## 套餐结构

ChatGPT Plus / Pro 与 Codex 采用**订阅制**。官方不提供面向用户的精确 Token 配额仪表盘，
也没有公开的 Usage REST API（有 `platform.openai.com/usage` 组织级用量，但那是 API 计费，
**不是**订阅套餐剩余额度，App 禁止用它冒充订阅额度）。

## 额度窗口

Codex 的用量/限流以**会话内**触发为准；无稳定、非交互可取的"剩余额度"数值接口。

## 是否有官方 API

- **额度查询**：无公开的订阅剩余额度 API。
- **API 计费用量**：`platform.openai.com/usage` 存在，但属组织 API 计费，与订阅套餐额度是两码事，不采用。

## 是否有官方 CLI 查询能力

- `codex` CLI：`codex status` 仅返回**登录态**，不返回额度数字。
- 额度需在**交互式会话**内 `/status` 查看，无法可靠地非交互解析。

## 认证方式

- 桌面端：`codex login` / `codex auth` 建立的本地登录态。
- Android 端：无第三方密钥，凭据为 Bridge Bearer。

## 是否支持 Android 直连

否。无 Android 可直连的订阅额度接口，**必须 Bridge**。

## 实际代码实现方式

- Android：`BridgeQuotaProvider`（connector=BRIDGE），经 `BridgeClient` 调桌面桥 `/quota?X-Provider=codex`。
- 桌面桥：`CodexAdapter` 调 `codex status` 判定登录态；因额度需会话内 `/status` 交互，
  当前对额度返回 `source="unsupported"`（诚实标注），不会编造数字。

## 可靠性风险

- 无自动化额度取值来源 → 无法在 Bridge 端得到稳定数值，需等待官方 CLI/API 提供。
- 若坚持解析 CLI 文本：必须独立解析器 + fixture 测试 + 版本识别 + 失败明确返回 unsupported，
  禁止脆弱地截取终端 ANSI。