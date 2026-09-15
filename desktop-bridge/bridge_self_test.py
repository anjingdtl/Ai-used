#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bridge 适配器的 fixture 自测：不联网，直接喂真实形状的响应做解析验证。

运行：python3 bridge_self_test.py
全部通过则退出码 0；任一失败打印并可退出码非 0（供 CI 使用）。
"""
import ast
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bridge  # noqa: E402


class _FakeResp:
    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def read(self):
        return json.dumps(self._payload).encode("utf-8")


# monkeypatch urllib so适配器走本地 fixture，不发真实网络
def _patch_http_get(payload):
    bridge._http_get = lambda url, headers, timeout=20: _FakeResp() if False else payload
    return payload


def _make(url, headers, timeout=20):
    return _FakeResp()


def run_glm():
    payload = {"data": {"limits": [
        {"type": "TOKENS_LIMIT", "unit": 3, "percentage": 37.0, "resetAt": "2026-09-15T03:00:00Z"},
        {"type": "TOKENS_LIMIT", "unit": 4, "percentage": 15.0, "resetAt": None},
        {"type": "TIME_LIMIT", "unit": 4, "percentage": 8.0, "currentValue": 12, "usage": 150},
    ]}}
    bridge._http_get = _make
    _patch_http_get(payload)
    a = bridge.GlmAdapter()
    out = a.fetch("acc1")
    assert out["source"] == "bridge", out
    assert len(out["buckets"]) == 2, out  # TOKENS 5H + TOKENS 周去重合并 TIME
    assert out["buckets"][0]["usedPercent"] == 37.0, out
    print("  GLM parse OK:", [(b["id"], b["usedPercent"]) for b in out["buckets"]])


def run_minimax():
    payload = {"remaining_percent": 63.0, "plan": "Token Plan", "window": "rolling"}
    bridge._http_get = _make
    _patch_http_get(payload)
    out = bridge.MiniMaxAdapter().fetch("acc2")
    assert out["source"] == "bridge", out
    assert out["buckets"][0]["usedPercent"] == 37.0, out  # 100 - 63
    assert out["buckets"][0]["remainingPercent"] == 63.0, out
    print("  MiniMax parse OK:", out["buckets"][0]["usedPercent"])


def run_opencode():
    payload = {
        "rollingUsage": {"usagePercent": 32.0, "resetInSec": 1200},
        "weeklyUsage": {"usagePercent": 53.0, "resetInSec": 3600},
        "monthlyUsage": {"usagePercent": 11.0, "resetInSec": 86400},
    }
    bridge._http_get = _make
    _patch_http_get(payload)
    out = bridge.OpenCodeAdapter().fetch("acc3")
    assert out["source"] == "bridge", out
    assert len(out["buckets"]) == 3, out
    assert out["buckets"][0]["usedPercent"] == 32.0, out
    print("  OpenCode parse OK:", [(b["id"], b["usedPercent"]) for b in out["buckets"]])


def main():
    os.environ.setdefault("AIQUOTA_GLM_TOKEN", "sk-test-glm")
    os.environ.setdefault("AIQUOTA_MINIMAX_KEY", "sk-test-minimax")
    os.environ.setdefault("AIQUOTA_OPENCODE_KEY", "sk-test-opencode")
    bridge._http_get = _make
    run_glm()
    run_minimax()
    run_opencode()
    print("bridge_self_test: ALL PASSED")


if __name__ == "__main__":
    main()