# 智谱 GLM Coding Plan 额度（`providerId: glm`）

> 最后核实日期：2026-09-15
> 状态口径：**IMPLEMENTED / NEEDS_CREDENTIAL**（代码已按当前真实结构实现，真实 E2E 待真实账号 Token）

## 官方用量接口

```
GET https://api.z.ai/api/monitor/usage/quota/limit
Authorization: <订阅密钥>（裸 Token，无 Bearer 前缀）
```

### 真实响应结构（2026-09 核对，见 `desktop-bridge/fixtures/glm_quota_2026_09.json`）

```json
{
  "code": 200, "msg": "...", "success": true,
  "data": {
    "level": "max",
    "limits": [{
      "type": "CREDIT_LIMIT", "unit": 3, "number": 5,
      "usage": 28000.0, "currentValue": 10360.0, "remaining": 17640.0,
      "percentage": 37, "nextResetTime": 1787563232239
    }]
  }
}
```

### 字段语义（勿沿用旧假设）

| 字段 | 语义 |
| --- | --- |
| `percentage` | 已耗百分比（0–100，整数，绝对值投影，可能被舍入） |
| `usage` / `currentValue` / `remaining` | 窗口总额度 / 已耗 / 剩余（绝对值） |
| `nextResetTime` | 下次重置时间，**毫秒级 Unix 时间戳**（不是 `resetAt` / `reset_at`） |
| `unit` | `3` => 5 小时滚动窗口；`6` => 每周（7 天）窗口 |

> 重要：**`unit=4` 不是 weekly**。旧文档/旧 fixture 的 `unit=4`、`resetAt` 字段已被 2026-09 核对的真实结构取代。
> 解析器优先用绝对值 `remaining/usage` 精确反算已耗百分比，避免只依赖被舍入的 `percentage`。

## 窗口模型

| Bucket | windowType（协议常量） | 说明 |
| --- | --- | --- |
| 积分（5 小时滚动） | `ROLLING_5_HOURS` | unit=3，短期用量，`isKeyBucket` 用于告警 |
| 周积分 | `WEEKLY` | unit=6，长期总配额 |

> 输出 windowType 必须是 `ROLLING_5_HOURS` / `WEEKLY`，**禁止** `5H` / `ROLLING_5H` / `WEEK`。

## 通道

- 桌面桥：`AIQUOTA_GLM_TOKEN` 环境变量携带订阅 Token。
- Android：`BridgeQuotaProvider(ProviderId.GLM)`，经 Bridge 查询，不直连官方。

## 可靠性风险

- `api.z.ai` 用量接口为官方稳定端点，但 `unit` 编码无公开枚举文档，字段名/编码演化需随官方更新。
- 未配置 Token 或结构未识别时返回 `source="unsupported"`，绝不伪造。
- 需要真实账号 Token 做最终 E2E（见 README 的「仍需提供的凭据」）。