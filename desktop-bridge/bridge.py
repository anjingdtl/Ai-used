#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI 额度管家 - Quota Desktop Bridge (v0.1.0)

标准库实现，Windows / macOS / Linux 均可用（`python bridge.py`）。

协议（与 Android 端 BridgeClient.kt 对齐）：
  GET  /health     -> {"status":"ok","version":"0.1.0"}
  GET  /providers  -> {"providers":[{provider,label,supportsQuota},...]}
  POST /pair       -> Bearer 校验 -> {"paired":true,"accountId":"local-bridge"}
  GET  /quota      -> Bearer + X-Provider + X-Account -> BridgeQuota JSON

安全原则：
  * Secret 首启随机生成并持久化；不接受 admin/123456/password。
  * 除 /health、/providers 外一律校验 Bearer。
  * provider adapter 只调用官方 CLI/本地登录态，拿不到就 source=unsupported，
    绝不伪造额度数字。
  * 内置 mock adapter 仅在本机显式开启（AIQUOTA_BRIDGE_ENABLE_MOCK=1）时可用，
    用于端到端管线自测，浏览器打开即可看到标记，生产不默认开启。
"""
from __future__ import annotations

import argparse
import json
import os
import secrets
import socket
import subprocess
import sys
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, List, Optional

VERSION = "0.1.0"
DEFAULT_PORT = 8787
SECRET_FILE = "bridge_secret.txt"


# --------------------------------------------------------------------------- #
# Secret 管理
# --------------------------------------------------------------------------- #
def load_or_create_secret(explicit: Optional[str]) -> str:
    if explicit:
        low = explicit.lower()
        if low in ("admin", "123456", "password", "secret", ""):
            raise SystemExit("拒绝使用弱 Secret：请用 --secret 传入随机值。")
        return explicit
    if os.path.isfile(SECRET_FILE):
        return open(SECRET_FILE, encoding="utf-8").read().strip()
    value = secrets.token_hex(16)
    with open(SECRET_FILE, "w", encoding="utf-8") as f:
        f.write(value + "\n")
    os.chmod(SECRET_FILE, 0o600)
    return value


# --------------------------------------------------------------------------- #
# Provider adapters —— 只返回真实数据；拿不到就 unsupported，不造假
# --------------------------------------------------------------------------- #
class ProviderAdapter:
    name: str = ""
    label: str = ""

    def available(self) -> bool:
        return False

    def fetch(self, account_id: str) -> Dict[str, Any]:
        return self._unsupported(account_id, "adapter 未实现")

    def _unsupported(self, account_id: str, note: str) -> Dict[str, Any]:
        return {
            "provider": self.name,
            "accountId": account_id,
            "source": "unsupported",
            "note": note,
        }


def _command_exists(name: str) -> bool:
    from shutil import which
    return which(name) is not None


class CodexAdapter(ProviderAdapter):
    """OpenAI codex CLI：`codex status` 只回登录态；额度需交互 `/status`。"""

    name = "codex"
    label = "ChatGPT / Codex（本地 codex CLI）"

    def available(self) -> bool:
        return _command_exists("codex")

    def fetch(self, account_id: str) -> Dict[str, Any]:
        if not self.available():
            return self._unsupported(account_id, "未检测到 codex CLI")
        try:
            r = subprocess.run(
                ["codex", "status"], capture_output=True, timeout=15, text=True,
            )
            if r.returncode != 0:
                return self._unsupported(account_id, "codex 未登录（codex login）")
            # 额度数值只能通过交互式会话 /status 取得，无法可靠地非交互解析，
            # 故不编造数字，交给上层以"已登录但用量需会话内查看"处理。
            return self._unsupported(
                account_id,
                "已登录，但 codex 用量需在交互会话内 /status 查看，暂无法自动取值"
            )
        except FileNotFoundError:
            return self._unsupported(account_id, "未检测到 codex CLI")
        except (subprocess.TimeoutExpired, OSError):
            return self._unsupported(account_id, "codex status 执行失败")


class GrokAdapter(ProviderAdapter):
    """Grok Build CLI：TUI 内 /usage 显示周池百分比；非交互取不到稳定数字。"""

    name = "grok"
    label = "Grok / xAI（本地 grok CLI）"

    def available(self) -> bool:
        return _command_exists("grok")

    def fetch(self, account_id: str) -> Dict[str, Any]:
        if not self.available():
            return self._unsupported(account_id, "未检测到 grok CLI")
        return self._unsupported(account_id, "grok /usage 需交互式 TUI，暂无法自动取值")


def _http_get(url: str, headers: Dict[str, str], timeout: int = 20) -> Any:
    """对官方 HTTPS 端点发 GET 并解析 JSON。失败抛异常，由调用方转 unsupported。"""
    import urllib.request

    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


class KeyProviderAdapter(ProviderAdapter):
    """需要 API Key / Token 的官方 Direct Provider。未配置密钥时诚实返回 unsupported。"""

    env_key = ""

    def available(self) -> bool:
        return bool(os.getenv(self.env_key))

    def fetch(self, account_id: str) -> Dict[str, Any]:
        key = os.getenv(self.env_key)
        if not key:
            return self._unsupported(account_id, f"未配置 {self.env_key}（真实账号密钥）")
        try:
            return self._query_official(key, account_id)
        except Exception as e:  # noqa: BLE001
            return self._unsupported(account_id, f"官方端点请求失败: {e}")

    def _query_official(self, key: str, account_id: str) -> Dict[str, Any]:
        raise NotImplementedError


def _bucket(
    bid: str, name: str, used_percent: Any, reset_at: Any = None, window: str = "CUSTOM"
) -> Dict[str, Any]:
    while isinstance(used_percent, (list, dict)):
        used_percent = None
    pct = None if used_percent is None else float(used_percent)
    return {
        "id": bid,
        "name": name,
        "type": "PERCENT",
        "used": pct,
        "limit": 100.0,
        "remaining": (100.0 - pct) if pct is not None else None,
        "usedPercent": pct,
        "remainingPercent": (100.0 - pct) if pct is not None else None,
        "unit": "%",
        "windowType": _norm_window(window),
        "resetAt": _resolve_reset(reset_at),
    }


# --------------------------------------------------------------------------- #
# Bridge 协议 WindowType 统一契约（与 Android WindowType 枚举一一对应）
# --------------------------------------------------------------------------- #
# 禁止 Adapter 自行创造 5H / ROLLING_5H / WEEK / MONTH 等别名。
# Android 侧：WindowType.valueOf(windowType) 必须能解析成功。
_WINDOW_ROLLING = "ROLLING"
_WINDOW_5H = "ROLLING_5_HOURS"
_WINDOW_DAILY = "DAILY"
_WINDOW_WEEKLY = "WEEKLY"
_WINDOW_MONTHLY = "MONTHLY"
_WINDOW_CREDIT = "CREDIT"
_WINDOW_BALANCE = "BALANCE"
_WINDOW_TOKEN = "TOKEN"
_WINDOW_REQUEST_COUNT = "REQUEST_COUNT"
_WINDOW_CUSTOM = "CUSTOM"
_WINDOW_UNKNOWN = "UNKNOWN"

# Android 之外的旧别名 -> 协议常量；未识别一律 CUSTOM
_WINDOW_ALIASES = {
    "5H": _WINDOW_5H,
    "ROLLING_5H": _WINDOW_5H,
    "ROLLING5H": _WINDOW_5H,
    "WEEK": _WINDOW_WEEKLY,
    "MONTH": _WINDOW_MONTHLY,
    "5HOURS": _WINDOW_5H,
    "5_HOURS": _WINDOW_5H,
}


def _norm_window(window: Any) -> str:
    if window is None:
        return _WINDOW_CUSTOM
    raw = str(window).strip().upper()
    if raw in _WINDOW_ALIASES:
        return _WINDOW_ALIASES[raw]
    return raw if raw else _WINDOW_CUSTOM


def _resolve_reset(reset_at: Any) -> Optional[str]:
    """把 reset 统一归一为 ISO-8601 UTC（如 2026-09-15T10:20:30Z）。

    输入可能为：
    - 相对秒偏移（int/float/纯数字字符串）：OpenCode `resetInSec`（如 1200）——表示"现在起 N 秒"；
    - 绝对秒时间戳（~1.7e9）：epoch 秒；
    - 绝对毫秒时间戳（~1.7e12）：GLM `nextResetTime`——毫秒；
    - ISO-8601 字符串：原样返回。
    通过数值量级区分，避免把毫秒时间戳或绝対 epoch 秒误当成秒偏移。
    无法解析返回 None，绝不返回原始数字字符串。
    """
    if reset_at is None:
        return None
    if isinstance(reset_at, bool):
        return None
    if isinstance(reset_at, (int, float)):
        return _resolve_numeric_reset(float(reset_at))
    s = str(reset_at).strip()
    if not s:
        return None
    try:
        return _resolve_numeric_reset(float(s))
    except ValueError:
        pass
    return s if _looks_iso(s) else None


def _resolve_numeric_reset(v: float) -> Optional[str]:
    """按数值量级把 reset 转 ISO-UTC：>1e11 毫秒时间戳；>1e8 秒时间戳；否则相对秒偏移。"""
    if v > 1e11:      # 毫秒时间戳，如 GLM nextResetTime = 1787563232239
        return _iso_from_epoch_ms(v)
    if v > 1e8:       # 秒时间戳（未来绝对 epoch）
        return _iso_from_epoch_sec(v)
    if v < 0:
        return None
    return _iso_from_now_seconds(v)  # 相对秒偏移，如 OpenCode resetInSec


def _iso_from_epoch_sec(sec: float) -> str:
    return datetime.fromtimestamp(sec, tz=timezone.utc).isoformat().replace("+00:00", "Z")


def _looks_iso(s: str) -> bool:
    try:
        datetime.fromisoformat(s)
        return True
    except ValueError:
        return False


def _iso_from_now_seconds(sec: float) -> str:
    return (datetime.now(timezone.utc) + __import__("datetime").timedelta(seconds=sec)).isoformat().replace("+00:00", "Z")


def _iso_from_epoch_ms(ms: float) -> str:
    return datetime.fromtimestamp(ms / 1000.0, tz=timezone.utc).isoformat().replace("+00:00", "Z")


def _plan_from(key: str) -> str:
    low = (key or "").lower()
    if "token" in low:
        return "Token Plan"
    return "Coding Plan"


class GlmAdapter(KeyProviderAdapter):
    """智谱 GLM Coding Plan —— 官方稳定用量接口。

    真实结构（2026-09 核对，见 fixtures/glm_quota_2026_09.json 来源说明）：
      GET https://api.z.ai/api/monitor/usage/quota/limit
      Authorization: <订阅密钥>（裸 token，无 Bearer）
      返回:
        { "code":200, "msg":..., "success":true,
          "data": { "level":"max",
                    "limits":[ { "type":"CREDIT_LIMIT", "unit":3, "number":5,
                                 "usage":28000, "currentValue":10360, "remaining":17640,
                                 "percentage":37, "nextResetTime":1787563232239 }, ... ] } }
    字段语义：
      * percentage：已耗百分比（0–100 整数，绝对值的近似投影）；
      * usage / currentValue / remaining：窗口总额度 / 已耗 / 剩余（绝对值）；
      * nextResetTime：下次重置时间，毫秒级 Unix 时间戳（不是 resetAt / reset_at）；
      * unit：3 -> 5 小时滚动窗口，6 -> 每周（7 天）窗口（多源一致归纳，官方无公开枚举）。
    本解析器优先用绝对值 remaining/usage 反算已耗百分比，避免只依赖被舍入的 percentage。
    """

    name = "glm"
    label = "智谱 GLM Coding Plan（官方用量接口）"
    env_key = "AIQUOTA_GLM_TOKEN"

    def _query_official(self, key: str, account_id: str) -> Dict[str, Any]:
        payload = _http_get(
            "https://api.z.ai/api/monitor/usage/quota/limit",
            {"Authorization": key, "Content-Type": "application/json"},
        )
        data = payload.get("data") if isinstance(payload, dict) else None
        limits = ((data.get("limits") if isinstance(data, dict) else None)
                  or payload.get("limits")) or []
        if not limits:
            return self._unsupported(account_id, "响应缺少 data.limits 字段")
        buckets = []
        seen = set()
        for item in limits:
            if not isinstance(item, dict):
                continue
            unit = item.get("unit")
            # unit=3 -> 5小时滚动；unit=6 -> 每周(7 天)；其它不臆测，统一 CUSTOM
            if unit == 3:
                window = _WINDOW_5H
            elif unit == 6:
                window = _WINDOW_WEEKLY
            else:
                window = _WINDOW_CUSTOM
            if window in seen:
                continue
            seen.add(window)
            pct = item.get("percentage")
            remaining = item.get("remaining")
            total = item.get("usage")
            # 有绝对值时用 remaining/usage 精确反算已耗百分比
            if (isinstance(remaining, (int, float))
                    and isinstance(total, (int, float)) and total):
                pct = 100.0 * (float(total) - float(remaining)) / float(total)
            buck = _bucket(
                "glm-" + window.lower(),
                f"积分（{window}）",
                pct,
                item.get("nextResetTime"),
                window,
            )
            buckets.append(buck)
        if not buckets:
            return self._unsupported(account_id, "未找到可解析的额度项")
        return {
            "provider": self.name,
            "accountId": account_id,
            "accountName": "GLM Coding Plan",
            "plan": "GLM Coding Plan",
            "buckets": buckets,
            "queriedAt": _now_iso(),
            "source": "bridge",
        }


class MiniMaxAdapter(KeyProviderAdapter):
    """MiniMax Token Plan —— 官方公开端点 /v1/token_plan/remains。

    重要（P0-6）：官方文档仅提供 curl 示例，**未公开 JSON schema**（截至 2026-09，
    见 fixtures/minimax_token_plan_2026_09.json 的 provenance 说明）。因此：
      - **不**依赖单个 `remaining_percent` 之类猜测字段计算额度并默默成功；
      - 仅在响应中出现**结构化窗口对象**（如 `rollingUsage` / `weeklyUsage`，内含
        usagePercent + resetInSec/resetAt）时才解析为对应窗口的 QuotaBucket；
      - 否则返回 `source="unsupported"`，绝不生成空 Bucket + LIVE。
    待拿到一次真实脱敏抓包后再按真实结构固化解析。
    """

    name = "minimax"
    label = "MiniMax Token Plan（官方 API）"
    env_key = "AIQUOTA_MINIMAX_KEY"

    def _query_official(self, key: str, account_id: str) -> Dict[str, Any]:
        payload = _http_get(
            "https://www.minimaxi.com/v1/token_plan/remains",
            {"Authorization": "Bearer " + key, "Content-Type": "application/json"},
        )
        buckets = []

        rolling = _window_object(payload, "rollingUsage", "rolling", "rolling_usage")
        if rolling is not None:
            buckets.append(
                _bucket(
                    "minimax-rolling_5_hours",
                    "滚动用量（5小时）",
                    _pct_value(rolling),
                    _reset_of(rolling),
                    _WINDOW_5H,
                )
            )

        weekly = _window_object(payload, "weeklyUsage", "weekly", "week_usage")
        if weekly is not None:
            buckets.append(
                _bucket(
                    "minimax-weekly",
                    "周用量",
                    _pct_value(weekly),
                    _reset_of(weekly),
                    _WINDOW_WEEKLY,
                )
            )

        if not buckets:
            # 官方结构未确认，拒绝猜测解析（P0-6）
            return self._unsupported(
                account_id,
                "官方 /v1/token_plan/remains 结构未公开；未识别到结构化窗口字段，拒绝猜测解析（需真实抓包确认）",
            )

        return {
            "provider": self.name,
            "accountId": account_id,
            "accountName": "MiniMax Token Plan",
            "plan": "Token Plan",
            "buckets": buckets,
            "queriedAt": _now_iso(),
            "source": "bridge",
        }


def _window_object(payload: Dict[str, Any], *names: str) -> Any:
    """从响应中提取『结构化窗口对象』（dict 形态，含 usagePercent + reset）。
    仅返回 dict；裸数字/缺失返回 None，避免把单一猜测字段当成窗口。
    """
    if not isinstance(payload, dict):
        return None
    for n in names:
        v = payload.get(n)
        if isinstance(v, dict) and (_pct_value(v) is not None):
            return v
    return None


def _reset_of(v: Any) -> Any:
    """从窗口对象（或任意值）中取 reset 字段：resetInSec / resetAt / resetAtMs。"""
    if isinstance(v, dict):
        return (
            v.get("resetInSec")
            or v.get("resetAt")
            or v.get("resetAtMs")
            or v.get("nextResetTime")
            or v.get("resetTime")
        )
    return v


def _coerce_pct(payload: Dict[str, Any], *names: str) -> Any:
    for n in names:
        v = _first(payload, n)
        if v is not None:
            return v
    return None


def _pct_value(v: Any) -> Any:
    """取值：数字直接用；对象则取 usagePercent/usedPercent/percent 等字段。"""
    if isinstance(v, dict):
        for k in ("usagePercent", "usedPercent", "percentUsed", "percent", "value"):
            if v.get(k) is not None:
                return v[k]
        return None
    return v


class OpenCodeAdapter(KeyProviderAdapter):
    """OpenCode Go —— 官方 API /zen/go/v1/usage。

    Key 优先取环境变量；否则从本地 ~/.local/share/opencode/auth.json 的
    opencode-go 条目读取（兼容 XDG_DATA_HOME / OPENCODE_AUTH_CONTENT）。
    """

    name = "opencode"
    label = "OpenCode Go（官方用量 API）"
    env_key = "AIQUOTA_OPENCODE_KEY"

    def available(self) -> bool:
        if os.getenv(self.env_key):
            return True
        return self._read_local_key() is not None

    def fetch(self, account_id: str) -> Dict[str, Any]:
        key = os.getenv(self.env_key) or self._read_local_key()
        if not key:
            return self._unsupported(account_id, "未检测到 OpenCode Go API Key（~/.local/share/opencode/auth.json）")
        try:
            return self._query_official(key, account_id)
        except Exception as e:  # noqa: BLE001
            return self._unsupported(account_id, f"官方端点请求失败: {e}")

    @staticmethod
    def _read_local_key() -> Optional[str]:
        import json as _json
        from pathlib import Path

        try:
            path = Path(os.getenv("OPENCODE_AUTH_CONTENT") or "")
            if not path.is_absolute():
                data_home = os.getenv("XDG_DATA_HOME")
                base = Path(data_home or (Path.home() / ".local" / "share")) / "opencode" / "auth.json"
                path = base
            if path.exists():
                data = _json.loads(path.read_text(encoding="utf-8"))
                entry = data.get("opencode-go") or {}
                v = entry.get("apiKey") or entry.get("key") or entry.get("value")
                if isinstance(v, dict):
                    v = v.get("apiKey") or v.get("key")
                return str(v) if v else None
        except Exception:  # noqa: BLE001
            return None
        return None

    def _query_official(self, key: str, account_id: str) -> Dict[str, Any]:
        payload = _http_get(
            "https://opencode.ai/zen/go/v1/usage",
            {"Authorization": "Bearer " + key},
        )

        def _bucket_for(name: str, id_suffix: str, usages: List[str], window: str) -> Optional[Dict[str, Any]]:
            v = _coerce_pct(payload, *usages)
            pct = _pct_value(v)
            if pct is None:
                return None
            reset_raw = None
            if isinstance(v, dict):
                reset_raw = v.get("resetInSec") or v.get("resetAt") or v.get("resetAtMs")
            return _bucket(
                "opencode-" + id_suffix, name, pct, reset_raw, window
            )

        buckets = []
        for name, s, usages, window in (
            ("滚动 5 小时", "5h", ("rollingUsage", "rolling"), _WINDOW_5H),
            ("本周额度", "weekly", ("weeklyUsage", "weekly"), _WINDOW_WEEKLY),
            ("本月额度", "monthly", ("monthlyUsage", "monthly"), _WINDOW_MONTHLY),
        ):
            buck = _bucket_for(name, s, usages, window)
            if buck is not None:
                buckets.append(buck)
        if not buckets:
            return self._unsupported(account_id, "响应缺少用量百分比字段")
        return {
            "provider": self.name,
            "accountId": account_id,
            "accountName": "OpenCode Go",
            "plan": "OpenCode Go",
            "buckets": buckets,
            "queriedAt": _now_iso(),
            "source": "bridge",
        }


class MockAdapter(ProviderAdapter):
    """仅用于端到端管线自测（AIQUOTA_BRIDGE_ENABLE_MOCK=1 才可用）。

    输出带明确 mark，Android 端会以 source="bridge" 显示，但本桥的 providers
    清单对该项标记 supportsQuota=true 且 label 注明 DEBUG。生产不启用。
    """

    name = "mock"
    label = "Mock（本地管线自测，仅 DEBUG）"

    def available(self) -> bool:
        return os.getenv("AIQUOTA_BRIDGE_ENABLE_MOCK") == "1"

    def fetch(self, account_id: str) -> Dict[str, Any]:
        now = datetime.now(timezone.utc)
        used = 37.0
        return {
            "provider": self.name,
            "accountId": account_id,
            "accountName": "Mock 账号",
            "plan": "Mock Plan (DEBUG)",
            "buckets": [
                {
                    "id": "mock-week",
                    "name": "每周额度 (DEBUG)",
                    "type": "PERCENT",
                    "used": used,
                    "limit": 100.0,
                    "remaining": 100.0 - used,
                    "usedPercent": used,
                    "remainingPercent": 100.0 - used,
                    "unit": "%",
                    "windowType": "WEEKLY",
                    "resetAt": _iso_days_from_now(now, 3),
                }
            ],
            "queriedAt": _now_iso(),
            "source": "bridge",
        }


# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #
def _first(payload: Dict[str, Any], *keys) -> Any:
    for k in keys:
        if k in payload and payload[k] is not None:
            return payload[k]
    return None


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _iso_days_from_now(now: datetime, days: int) -> str:
    return (now.replace(tzinfo=timezone.utc) + __import__("datetime").timedelta(days=days)).isoformat().replace("+00:00", "Z")


def make_adapters() -> List[ProviderAdapter]:
    return [
        GlmAdapter(),
        MiniMaxAdapter(),
        OpenCodeAdapter(),
        CodexAdapter(),
        GrokAdapter(),
        MockAdapter(),
    ]


# --------------------------------------------------------------------------- #
# HTTP Server
# --------------------------------------------------------------------------- #
class BridgeHandler(BaseHTTPRequestHandler):
    secret: str = ""
    adapters: List[ProviderAdapter] = []

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("[bridge] %s - %s\n" % (self.address_string(), fmt % args))

    # ---- plumbing ----
    def _send(self, code: int, obj: Any) -> None:
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self) -> Dict[str, Any]:
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        return json.loads(self.rfile.read(length).decode("utf-8") or "{}")

    def _auth_ok(self) -> bool:
        auth = self.headers.get("Authorization", "")
        return auth == "Bearer " + self.secret

    def _require_auth(self) -> bool:
        if self._auth_ok():
            return True
        self._send(401, {"error": "unauthorized"})
        return False

    # ---- routes ----
    def do_GET(self) -> None:  # noqa: N802
        if self.path.split("?")[0] == "/health":
            self._send(200, {"status": "ok", "version": VERSION})
            return
        if self.path.split("?")[0] == "/providers":
            self._send(200, {
                "providers": [
                    {"provider": a.name, "label": a.label + (" (可用)" if a.available() else " (未就绪)"),
                     "supportsQuota": a.available()}
                    for a in BridgeHandler.adapters
                ]
            })
            return
        if self.path.split("?")[0] == "/quota":
            if not self._require_auth():
                return
            provider = self.headers.get("X-Provider", "")
            account_id = self.headers.get("X-Account", "unknown")
            adapter = next((a for a in BridgeHandler.adapters if a.name == provider), None)
            if adapter is None:
                self._send(404, {"error": "unknown provider"})
                return
            if not adapter.available():
                self._send(200, adapter._unsupported(account_id, "provider 未就绪"))
                return
            self._send(200, adapter.fetch(account_id))
            return
        self._send(404, {"error": "not found"})

    def do_POST(self) -> None:  # noqa: N802
        if self.path.split("?")[0] == "/pair":
            if not self._require_auth():
                return
            self._send(200, {"paired": True, "accountId": "local-bridge"})
            return
        self._send(404, {"error": "not found"})


def main() -> None:
    parser = argparse.ArgumentParser(description="AI 额度管家 Quota Desktop Bridge")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--secret", default=None)
    args = parser.parse_args()

    secret = load_or_create_secret(args.secret)
    BridgeHandler.secret = secret
    BridgeHandler.adapters = make_adapters()

    server = ThreadingHTTPServer((args.host, args.port), BridgeHandler)
    ip = socket.gethostbyname(socket.gethostname())
    print(f"Quota Desktop Bridge  v{VERSION}")
    print(f"Secret: {secret[:8]}...（已持久化在 {SECRET_FILE}，/quota 与 /pair 需 Bearer）")
    print(f"监听地址: {args.host}:{args.port}")
    print(f"本机内网 IP 示例: http://{ip}:{args.port}")
    print("请在 Android App 里：连接方式=桌面 Bridge，地址填上面的 http://IP:8787，Secret 填上面串。")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止。")


if __name__ == "__main__":
    main()