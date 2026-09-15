# 验收与测试

## 单元测试（64 个，全绿，13 类）

运行：

```bash
./gradlew :app:testDebugUnitTest
```

| 测试类 | 覆盖点 | cases |
| --- | --- | --- |
| `SecureCipherStoreTest` | Keystore AES-GCM 加解密往返、AAD 绑定、篡改检测 | 5 |
| `RoomCredentialStoreTest` | 凭据加密落盘 / 读取 | 5 |
| `RefreshIntervalTest` | 刷新间隔合法映射（≥15min 约束） | 3 |
| `MapperTest` | 实体 ⇄ 领域模型映射 | 3 |
| `FormatTest` | 百分位/单位/相对时间格式化 | 3 |
| `QuotaRepositoryFlowTest` | Last-Known-Good、断网流、SYNCING/UNAVAILABLE 状态 | 4 |
| `QuotaRepositoryImplTest` | LIVE/CACHED/FAILED_NO_CACHE/UNAVAILABLE 四态；ProviderUnavailable 有缓存=CACHED、无缓存=UNAVAILABLE | 6 |
| `QuotaRepositoryUnsupportedTest` | unsupported/空响应绝不标记 LIVE（去重到仓库层） | 4 |
| `HistoryPruneTest` | P1-3：按保留窗口真正清理过期历史、窗口边界保留 | 2 |
| `NotificationRecoveryTest` | P1-2：阈值触发去重、恢复清除、null 视为未恢复 | 4 |
| `BridgeClientTest` | BridgeClient 协程 + `response.use{}` 关闭、错误结构化映射 | 7 |
| `BridgeQuotaValidatorTest` | P0-9：provider/accountId/source/buckets/百分比越界/resetAt/windowType 强校验 | 13 |
| `WindowTypeProtocolTest` | Bridge 协议 windowType 枚举与 Android `WindowType.entries` 一一对应 | 5 |

## 桌面桥测试（Python，全绿）

```bash
python3 desktop-bridge/bridge_self_test.py   # 适配器解析 + 协议前缀 + reset 语义
python3 desktop-bridge/bridge_http_test.py    # /health /providers /pair /quota 各分支 HTTP
```

- `bridge_self_test`：GLM 5h 滚动/周、MiniMax 模糊单值 → unsupported（拒绝猜测解析）、MiniMax 结构化双窗口、OpenCode 三档 + `now+1200s` reset 校验。
- `bridge_http_test`：health 200、providers 列表、pair 401/200、quota unknown→404、unavailable→`source="unsupported"`、可用 mock→WEEKLY。

## 构建

```bash
./gradlew :app:assembleDebug    # debug APK
./gradlew :app:assembleRelease  # release APK
```

## 当前环境静态验收（无模拟器/真机）

> 本开发沙箱**无 /dev/kvm、无 emulator 二进制、无 system image**，无法启动 Android 模拟器做「安装/断网/重启」交互验收。为不伪造验收结果，如实记录：**无 emulator / KVM / device**。已用「全量编译 + 单测全绿 + Python 桥集成测试 + Dex 静态检查」双保险保证可交付质量。

| 校验项 | 命令/来源 | 结果 |
| --- | --- | --- |
| Debug 单元测试 | `./gradlew :app:testDebugUnitTest` | 64/64 通过（13 类） |
| 桌面桥自测 | `python3 desktop-bridge/bridge_self_test.py` | 全绿 |
| 桌面桥 HTTP 集成 | `python3 desktop-bridge/bridge_http_test.py` | 全绿 |
| Release 不含 Debug Mock | Dex 静态检查 `MockScenario/DebugQuotaProvider` | CI 阻断 |
| Android↔Bridge HTTP | Debug 放行局域网明文 / Release 默认禁止（`docs/security.md`） | 策略已落地 |
| 真机/模拟器安装 | 本环境无设备 | **未执行，不伪造** |

## 在有 KVM / 真机 / 模拟器环境的验收清单

若后续在具备 Android 模拟器（或真机）的机器上验收，建议按以下流程回归：

1. 启动桌面桥：`AIQUOTA_GLM_TOKEN=... python3 desktop-bridge/bridge.py`（或任一真实 Provider 环境变量）。
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk` 后冷启动，完成 Onboarding 直达主页。
3. 添加 GLM / MiniMax / OpenCode：调 Debug/局域网 HTTP Bridge，Test Connection 应显示具体 Provider 状态（Bridge 在线 · Provider 可用 · 未配置密钥 · 未注册 等细因，而非笼统「Provider 可用」）。
4. 下拉刷新：卡片出现额度桶（5h 滚动/周/月）并更新历史，状态 LIVE。
5. 断网刷新：保留上一次数据（Last Known Good），状态 CACHED 而非清空。
6. 重启验证：额度从 Room 恢复，界面立即有值。
7. 告警触发后恢复：把阈值调高 → 命中通知一次 → 额度恢复到阈值+余量 → 清除标记 → 再次跌破可重新提醒。
8. 让 Bridge 返回 `source="unsupported"`（未配置 Token）：刷新后旧额度仍在显示为 CACHED/UNAVAILABLE，**绝不变 LIVE**。

## V1 候选条件核对（截至 2026-09-15）

- [x] Android 可真实访问 Desktop Bridge（Debug 局域网 HTTP 放行 + Release 禁止明文策略）。
- [x] Test Connection 验证目标 Provider（/health → /pair → /providers → supportsQuota → quota probe）。
- [x] unsupported / 空 buckets 不会标记 LIVE（BridgeQuotaValidator + 仓库 CACHED/UNAVAILABLE 分支）。
- [x] 结构化 Bridge 错误映射（401→Unauthorized、429→RateLimited、5xx→ServerError、超时/连接→BridgeOffline）。
- [x] Bridge 协议 windowType 统一契约 + reset ISO-8601。
- [x] P1：通知恢复、历史清理、Widget 生命周期、Onboarding 时序、备份策略。
- [ ] 至少一个真实 Provider 端到端（需真实账号 Token，见 README 四级状态 NEVER-credential 平台）。当前 **NEEDS_CREDENTIAL**，未伪造 E2E。