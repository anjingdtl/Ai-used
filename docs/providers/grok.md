# Grok / xAI 额度（`providerId: grok`）

> 最后核实日期：2026-09-15（以官方现状为准，勿以本文替代线上核实）
> 状态口径：**UNSUPPORTED**（官方无订阅额度 API / CLI 非交互取值；Android 端 `UnavailableQuotaProvider` 恒显示 UNAVAILABLE）

## 套餐结构

Grok / xAI（Grok Build）采用订阅制。官方**不提供**面向用户的额度查询 API。

## 额度窗口

Grok Build CLI 的 TUI 内 `/usage` 会显示周池百分比，但仅交互式可见。

## 是否有官方 API

**否**。官方未开放订阅剩余额度 REST API。

## 是否有官方 CLI 查询能力

`grok` CLI 存在，但 `/usage` 是交互式 TUI，非交互取不到稳定数字。

## 认证方式

- 桌面端：grok CLI 本地登录态。
- Android 端：无第三方密钥。

## 是否支持 Android 直连

否。官方无接口，**不支持直连也不支持可靠 Bridge 取值**。

## 实际代码实现方式

- Android：`ProviderModule.bindGrok()` = `UnavailableQuotaProvider`（永远返回 UNAVAILABLE）。
- 桌面桥：`GrokAdapter` 探测到 grok CLI 存在即标记"可用"，但 `/usage` 交互无法自动取值，
  额度统一返回 `source="unsupported"`，不会编造数字。

## 可靠性风险

- 官方未提供接口 → App 对该平台显示 `UNAVAILABLE`，不做伪额度。
- 一旦官方提供 Usage/Quota API 或 CLI 非交互能力，再升级为真实 Provider。