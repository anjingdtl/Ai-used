# 安全与隐私策略

> 本文件说明 AI 额度管家对凭据、加密、网络与备份的策略与取舍。

## 1. 凭据安全

- 平台 Token / Bridge Secret 属于敏感数据，**明文不会持久化**。
- 凭据经 AES-GCM 加密后写入 Room（`CredentialEntity.encryptedBlob`），AAD（additional authenticated data）绑定 `accountId`，防止密文被跨账号重放。
- 解密密钥来自 Android Keystore（`SecureCipherStore`），进程外不可导出。
- Debug 与 Release 通过不同的 Key 别名隔离。

## 2. 网络传输

### 2.1 明文（cleartext）策略

- **Release（生产）**：`usesCleartextTraffic` 全局禁止，`network_security_config` 仅放行本机回环与模拟器宿主：
  - `127.0.0.1`、`localhost`、`10.0.2.2`
- **Debug（开发/真机联调）**：允许局域网明文 HTTP，使 `http://<LAN-IP>:8787` 桥可访问。

> 这与「不全局放开 cleartext」的安全最佳实践一致：正式包默认不可访问任意公网明文 HTTP。

### 2.2 Bridge 接入提示

- Bridge 建议使用 HTTPS。
- 若在 Debug 用 `http://` 接入：
  - 仅建议在**可信 Wi-Fi** 中使用；UI 会提示「当前使用未加密局域网连接，仅建议在可信 Wi-Fi 中使用」。
- `https://` 输入即正常安全连接。

### 2.3 错误映射不透传

所有查询失败在 UI 上只映射为用户可读中文分类（未连接/超时/凭据无效/限流/服务不可用/解析失败），
不展示底层异常名与敏感地址；Bridge 请求地址中的 Secret 通过 `SecretMasker` 脱敏。

## 3. 数据备份策略

- `android:allowBackup=true`，但通过 `dataExtractionRules`（Android 12+）与 `fullBackupContent`（Android 11 及以下）**精确排除**敏感数据库：
  - `ai_quota.db`（含加密凭据 blob、最近快照、通知事件）
  - 及其 wal / shm / journal 附属文件
- 目的：Android AutoBackup / 设备迁移不会把加密凭据与缓存快照上传到云端或迁移到新设备。
- 取舍：本地数据（设置、加密 Key 由 Keystore 保护）不参与云备份，迁移新设备需重新配置账号。

## 4. 审计与建议

- 若产品定位需要「可在新设备恢复凭据」，应改为显式的端到端加密导出（用户主动输密码）而非默认系统备份。
- 未来新增任何存储敏感数据的表，需同步加入 `data_extraction_rules.xml` / `backup_rules.xml` 的排除列表，否则自动备份会带上它。