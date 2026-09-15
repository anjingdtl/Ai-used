#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bridge 适配器的 fixture 自测：不联网，直接喂 fixtures/ 里的真实形状响应做解析验证。

运行：python3 bridge_self_test.py
全部通过则退出码 0；任一失败打印并可退出码非 0（供 CI 使用）。

覆盖：
  * GLM / MiniMax / OpenCode 解析
  * 统一协议 WindowType 契约（输出必须能被 Android WindowType.valueOf 成功解析）
  * OpenCode resetInSec -> ISO-8601 UTC（绝不允许把 1200 塞进 resetAt）
"""
import json
import os
import re
import sys
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bridge  # noqa: E402

FIXTURES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")

# 与 Android WindowType 枚举一一对应的协议常量
PROTOCOL_WINDOWS = {
    "ROLLING", "ROLLING_5_HOURS", "DAILY", "WEEKLY", "MONTHLY",
    "CREDIT", "BALANCE", "TOKEN", "REQUEST_COUNT", "CUSTOM", "UNKNOWN",
}

_ISO_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$")


class _FakeResp:
    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def read(self):
        return json.dumps(self._payload).encode("utf-8")


def _load(name):
    with open(os.path.join(FIXTURES, name), encoding="utf-8") as f:
        return json.load(f)


def _patch(payload):
    def _fake(url, headers, timeout=20):
        return payload
    bridge._http_get = _fake
    return payload


def _assert_protocol_windows(buckets):
    for b in buckets:
        window = b.get("windowType")
        assert window in PROTOCOL_WINDOWS, f"windowType {window!r} 不在协议集合: {b}"
        assert window not in ("5H", "ROLLING_5H", "WEEK", "MONTH"), f"非法别名 {window}"
        reset = b.get("resetAt")
        if reset is not None:
            assert _ISO_RE.match(reset) or reset.endswith("Z"), f"resetAt 非 ISO-UTC: {reset}"


def run_glm():
    payload = _load("glm_quota_2026_09.json")
    _patch(payload)
    out = bridge.GlmAdapter().fetch("acc1")
    assert out["source"] == "bridge", out
    assert len(out["buckets"]) == 2, out  # TOKENS 5H + TOKENS 周去重合并 TIME
    assert out["buckets"][0]["usedPercent"] == 37.0, out
    assert out["buckets"][0]["windowType"] == "ROLLING_5_HOURS", out
    assert out["buckets"][1]["windowType"] == "WEEKLY", out
    _assert_protocol_windows(out["buckets"])
    print("  GLM parse OK:", [(b["id"], b["windowType"], b["usedPercent"]) for b in out["buckets"]])


def run_minimax():
    # 场景 1（P0-6）：官方 schema 未公开、fixture 为模糊的单值 remaining_percent。
    # 不允许依赖猜测字段默默成功 —— 必须诚实返回 unsupported，绝不产出空 Bucket + LIVE。
    ambiguous = _load("minimax_token_plan_2026_09.json")
    _patch(ambiguous)
    out = bridge.MiniMaxAdapter().fetch("acc2")
    assert out["source"] == "unsupported", out
    assert "buckets" not in out, out
    print("  MiniMax ambiguous -> unsupported OK (拒绝猜测解析)")

    # 场景 2：识别到结构化窗口对象（rollingUsage / weeklyUsage）时才解析为合法 Bucket。
    structured = {
        "rollingUsage": {"usagePercent": 37.0, "resetInSec": 1200},
        "weeklyUsage": {"usagePercent": 63.0, "resetInSec": 3600},
    }
    _patch(structured)
    out = bridge.MiniMaxAdapter().fetch("acc2")
    assert out["source"] == "bridge", out
    windows = [b["windowType"] for b in out["buckets"]]
    assert "ROLLING_5_HOURS" in windows and "WEEKLY" in windows, out
    assert out["buckets"][0]["usedPercent"] == 37.0, out
    _assert_protocol_windows(out["buckets"])
    print("  MiniMax structured -> OK:", [(b["id"], b["windowType"], b["usedPercent"]) for b in out["buckets"]])


def run_opencode():
    payload = _load("opencode_usage_2026_09.json")
    _patch(payload)
    before = datetime.now(timezone.utc).timestamp()
    out = bridge.OpenCodeAdapter().fetch("acc3")
    after = datetime.now(timezone.utc).timestamp()
    assert out["source"] == "bridge", out
    assert len(out["buckets"]) == 3, out
    assert out["buckets"][0]["usedPercent"] == 32.0, out
    _assert_protocol_windows(out["buckets"])
    # resetInSec -> ISO-UTC：rolling(1200s) 必须落在 now+1200 附近
    rolling = out["buckets"][0]
    assert rolling["resetAt"], f"rolling resetAt 为空: {out}"
    reset_ts = datetime.fromisoformat(rolling["resetAt"].replace("Z", "+00:00")).timestamp()
    assert before + 1190 <= reset_ts <= after + 1210, f"reset 应为 now+1200s: {rolling['resetAt']}"
    assert out["buckets"][1]["windowType"] == "WEEKLY"
    assert out["buckets"][2]["windowType"] == "MONTHLY"
    print("  OpenCode parse OK:", [(b["id"], b["windowType"], b["usedPercent"]) for b in out["buckets"]])
    print("  OpenCode reset OK:", rolling["resetAt"], "(= now + 1200s)")


def main():
    os.environ.setdefault("AIQUOTA_GLM_TOKEN", "sk-test-glm")
    os.environ.setdefault("AIQUOTA_MINIMAX_KEY", "sk-test-minimax")
    os.environ.setdefault("AIQUOTA_OPENCODE_KEY", "sk-test-opencode")
    run_glm()
    run_minimax()
    run_opencode()
    print("bridge_self_test: ALL PASSED")


if __name__ == "__main__":
    main()