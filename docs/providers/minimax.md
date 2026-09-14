# MiniMax Token / Coding Plan 额度

## 额度规则

MiniMax 的 Coding Plan 通常以 **Token / 用量配额** 计费（月度方案），剩余可用 Token 为关键指标。

App 建模：

| Bucket | windowType | type | 说明 |
| --- | --- | --- | --- |
| Token 配额 | `MONTHLY` | `COUNT` | 剩余 Token / 总 Token，`isKeyBucket` |
| 余额金额 | `CUSTOM` | `MONEY` | 可选，若账号为预付金模式 |

## 连接方式

- 无稳定的公开额度查询 API，通过「桌面本地桥接」（`bridge` connector）获取。
- `providerId`: `minimax`