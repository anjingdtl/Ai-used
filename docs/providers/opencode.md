# OpenCode Go 额度

## 额度规则

OpenCode Go 提供开发者额度包，具体窗口（日/月/总包）由账号属性决定，通过桥接返回的 `BridgeBucket` 自适应。

| Bucket | windowType | type | 说明 |
| --- | --- | --- | --- |
| 开发者额度 | 由桥接返回 | `COUNT` | `isKeyBucket` |

## 连接方式

- 通过「桌面本地桥接」（`bridge` connector）获取。
- `providerId`: `opencode`