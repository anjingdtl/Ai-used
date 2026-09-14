# Grok / xAI 额度

## 额度规则

Grok 个人套餐（Grok Basic / Super）为订阅制。**截至当前调研，xAI 官方未开放对个人用户的额度查询接口**，无法可靠获取剩余用量。

App 将其标记为 `UNAVAILABLE` 平台：

| 项 | 值 |
| --- | --- |
| connectorType | `UNAVAILABLE` |
| providerId | `grok` |
| 额度状态 | 一直显示为「暂未开放额度查询」，不发起网络请求 |

实现上由 `UnavailableQuotaProvider` 占位，`QuotaRepositoryImpl.refresh` 对 `UNAVAILABLE` 账号直接跳过查询并记录 `UNAVAILABLE` 状态，不产生无谓的网络流量。

## 未来接入

若 xAI 日后开放额度接口，只需：

1. 实现 `QuotaProvider` 返回该平台额度；
2. 在 `ProviderId` + DI 中注册；
3. 把账号 connectorType 从 `UNAVAILABLE` 改为 `BRIDGE` / `API` 即可，UI 与仓库无需改动。