# AI 额度管家 - Quota Desktop Bridge

把桌面端（Windows / macOS / Linux）能拿到的本地额度来源，统一转成标准
Quota JSON，供 Android App（`Ai-quota`）经 `BridgeClient` 查询。

> 本桥 **不抓网页登录、不保存用户密码、不伪造额度**。
> 每个 provider 的 adapter 只调用**官方 CLI / 官方本地登录态**获取真实数据；
> 本机缺少对应工具或未登录时，`/quota` 返回 `source: "unsupported"`，绝不生成假数字。

---

## 一、Windows 启动（推荐）

```bat
:: 一次性安装 Python（若未装）：https://www.python.org/downloads/ 勾选 Add to PATH
python --version

:: 进入目录启动
cd desktop-bridge
python bridge.py
```

Linux / macOS：

```bash
cd desktop-bridge
python3 bridge.py
```

首次运行会自动生成随机 **Bridge Secret**（`secrets` 模块，非 `admin`/`123456`），
打印如下：

```
Quota Desktop Bridge  v0.1.0
已生成/读取 Secret: sykord3va5qf1j4n0tocm...
监听地址: 0.0.0.0:8787
本机内网 IP 示例: http://192.168.1.100:8787
```

## 二、测试

```bash
# 健康检查
curl http://127.0.0.1:8787/health

# 支持的 provider（无需 token 也能看能力清单）
curl http://127.0.0.1:8787/providers

# 配对（需要 Bearer）
curl -H "Authorization: Bearer <SECRET>" http://127.0.0.1:8787/pair

# 查询额度
curl -H "Authorization: Bearer <SECRET>" -H "X-Provider: codex" http://127.0.0.1:8787/quota
```

## 三、Android 端配置

- **Bridge 地址**：`http://<电脑局域网IP>:8787`（同一局域网）。
- **Bridge Secret**：首次启动打印的那串随机串。
- 连接方式选「桌面 Bridge」，点「测试连接」，绿灯后保存账号。

> ⚠️ **HTTP 安全提示**：局域网裸 HTTP 明文传输 Secret。生产请用 HTTPS（建议反代
> 加 TLS，或在安卓侧用 Network Security Config 只放行该内网主机，且配合系统级
> filter/防火墙）。Release 包不允许全局 `usesCleartextTraffic=true`。

## 四、防火墙说明（Windows）

1. 首次启动时若 Windows 弹窗，勾选 **专用网络** 允许 Python；
2. 或手动放行：
   ```bat
   netsh advfirewall firewall add rule name="ai-quota-bridge" dir=in action=allow protocol=TCP localport=8787
   ```
3. 确保 Android 与电脑在**同一网段**。

## 五、命令行参数

```text
用法: python bridge.py [--host 0.0.0.0] [--port 8787] [--secret <值>] [--insecure-http]
  --host            绑定地址，默认 0.0.0.0
  --port            端口，默认 8787
  --secret          显式指定 Secret（否则随机生成并持久化到 bridge_secret.txt）
  --insecure-http   开发用：允许断言 provider 但永远不写假额度
```

## 六、与 Android 协议

- `GET  /health`    → `{"status":"ok","version":"0.1.0"}`
- `GET  /providers` → `{"providers":[{ "provider","label","supportsQuota" }, ...]}`
- `POST /pair`      → `{"paired":true,"accountId":"local-bridge"}`
- `GET  /quota`     → BridgeQuota JSON（见 [BridgeClient.kt](../../android 端)）

`/quota` 请求头：`Authorization: Bearer <SECRET>`、`X-Provider: <providerId>`、
`X-Account: <accountId>`。

未带/错 token 返回 `401`；命中不支持能力返回 `200` 但 `source="unsupported"`。