# ChatGPT / Codex 额度

## 额度规则

ChatGPT Plus / Pro 与 Codex 属于**订阅制**，官方没有面向用户的精确 Token 配额仪表盘，主要通过账号内的用量/触发限流判断。

App 将该平台建模为：

| Bucket | windowType | type | 说明 |
| --- | --- | --- | --- |
| 订阅额度 | `CUSTOM` | `PERCENT` | `isKeyBucket`，用于告警 |

## 连接方式

- 通过「桌面本地桥接」（`bridge` connector）获取运行桌面端 Codex CLI 的额度信号。
- 桌面端 Codex 亦可将登录会话桥接给 App。

`providerId`: `codex`。

> 说明：Codex 的精确额度字段随官方产品演进而变化，App 以通用 `BridgeBucket` 承载，Bridge 提供多少字段即展示多少，避免未来接口变更时改客户端。