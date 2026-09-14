# 智谱 GLM Coding Plan 额度

## 额度规则

GLM Coding Plan 采用「**5 小时 + 每周积分**」双窗口机制：

- 每 5 小时有一次用量窗口（快速冷却、短期可用量）
- 每自然周刷新积分池（长期总配额）

两者都以积分计数。因此该平台在 App 中表现为 **两个 QuotaBucket**：

| Bucket | windowType | type | 说明 |
| --- | --- | --- | --- |
| 5 小时窗口 | `ROLLING`（周期 5h） | `COUNT` | 短期用量，接近 0 即被限流 |
| 每周积分 | `WEEKLY` | `COUNT` | `isKeyBucket`，用于告警 |

## 连接方式

智谱官方**不提供公开的额度查询 API**，故通过「桌面本地桥接」（`bridge` connector）获取。

桌面端 CLI（chatglm CLI / GLM Coding CLI）需在本地或局域网开启 **Quota Bridge 服务**，App 通过 `BridgeClient` 请求 `/quota`，Bridge 返回通用 `BridgeBucket` 列表，随后由 `SnapshotBuilder` 归一化为 `QuotaSnapshot`。

## 接入参数

- `providerId`: `glm`
- connectorType: `BRIDGE`
- 凭据：桥接 Bearer Token（存于 Keystore 加密）

## 参考

- 额度窗口在 Provider 层以 `windowType` 表达，UI 仅按桶渲染，无需平台专用逻辑。