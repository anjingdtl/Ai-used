# 验收与测试

## 单元测试（11 个，全绿）

运行：

```bash
gradle :app:testDebugUnitTest
```

覆盖范围：

| 测试类 | 覆盖点 |
| --- | --- |
| `QuotaRepositoryImplTest` | 首次成功刷新 → 持久化 + 历史 + 标记 LIVE；已有缓存且失败 → 保留历史标记 CACHED；无缓存失败 → 标记 FAILED_NO_CACHE；UNAVAILABLE 平台不查询；ProviderUnavailable 映射 UNAVAILABLE |
| `MapperTest` | 实体 ⇄ 领域模型映射正确性 |
| `FormatTest` | 百分位四舍五入、带单位数值、相对时间格式化 |

## 构建

```bash
# debug APK
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 静态验收（当前沙箱环境）

> 说明：本开发沙箱**无 /dev/kvm、无 emulator 二进制、无 system image**，因此无法启动 Android 模拟器做「安装/断网/重启」交互验收。为保证可交付的产物质量，采用静态验收 + 单元测试双保险，并记录如下验证结果：

| 校验项 | 命令 | 结果 |
| --- | --- | --- |
| APK 打包 | `apkanalyzer apk summary` | `com.aiquota.app.debug` / v1.0.0-debug |
| 启动入口 | `apkanalyzer manifest print` | 存在 `com.aiquota.app.MainActivity` |
| 网络明文 | manifest `usesCleartextTraffic` | `false`（禁止明文传输） |
| 权限清单 | manifest `uses-permission` | INTERNET / POST_NOTIFICATIONS / FOREGROUND_SERVICE 等齐全 |
| 打包完整性 | `apkanalyzer files list` | 199 个文件正常打包 |

## 在有 KVM 的真机/模拟器环境的验收清单

若后续在具备 Android 模拟器（或真机）的机器上验收，建议按以下流程回归：

1. 安装：`adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. 冷启动：确认进入 Dashboard，空态引导添加账号
3. 添加「Debug 模拟平台」：应出现额度卡片并展示剩余/百分比
4. 下拉刷新：卡片刷新并更新历史
5. 断网验证：关闭网络后下拉刷新，卡片**保留上一次数据**（Last Known Good），状态标记 CACHED/FAILED_NO_CACHE 而非清空
6. 重启验证：杀进程重开，额度数据从 Room 恢复，界面立即有值
7. 触发告警：把阈值调高至当前额度已低于的水平，观察去重后仅通知一次