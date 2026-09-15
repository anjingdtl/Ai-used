#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bridge HTTP 层集成测试：启动真实 ThreadingHTTPServer，走真实 HTTP 请求。

运行：python3 bridge_http_test.py
全部通过则退出码 0；任一失败退出码非 0（供 CI 使用）。

覆盖端点：
  GET  /health                     200
  GET  /providers                  200（含 mock 标记）
  POST /pair                       401 / 200
  GET  /quota unknown provider     404
  GET  /quota unavailable provider 200（source=unsupported，不抛 TypeError —— 回归 P0-4）
  GET  /quota available mock       200（source=bridge，真实 bucket）
"""
import json
import os
import socket
import sys
import threading

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bridge  # noqa: E402

from http.server import ThreadingHTTPServer  # noqa: E402


class _HTTPError(Exception):
    def __init__(self, code, body):
        self.code = code
        self.body = body
        super().__init__(f"HTTP {code}")


def _request(port, method, path, secret=None):
    import urllib.error
    import urllib.request

    req = urllib.request.Request(f"http://127.0.0.1:{port}{path}", method=method)
    if secret is not None:
        req.add_header("Authorization", "Bearer " + secret)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        body_raw = e.read().decode("utf-8")
        raise _HTTPError(e.code, body_raw)


def _free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


def main():
    os.environ["AIQUOTA_BRIDGE_ENABLE_MOCK"] = "1"
    os.environ.pop("AIQUOTA_GLM_TOKEN", None)  # 让 GLM unavailable
    os.environ.pop("AIQUOTA_MINIMAX_KEY", None)
    os.environ.pop("AIQUOTA_OPENCODE_KEY", None)

    secret = "test-secret-abc"
    port = _free_port()
    bridge.BridgeHandler.secret = secret
    bridge.BridgeHandler.adapters = bridge.make_adapters()
    server = ThreadingHTTPServer(("127.0.0.1", port), bridge.BridgeHandler)
    t = threading.Thread(target=server.serve_forever, daemon=True)
    t.start()

    try:
        # 1) /health
        code, body = _request(port, "GET", "/health")
        assert code == 200 and body.get("status") == "ok", body
        print("  OK /health")

        # 2) /providers
        code, body = _request(port, "GET", "/providers")
        assert code == 200 and isinstance(body.get("providers"), list), body
        names = [p["provider"] for p in body["providers"]]
        assert "mock" in names and "glm" in names, names
        print("  OK /providers ->", names)

        # 3) /pair 401
        try:
            _request(port, "POST", "/pair", secret="wrong-secret")
            raise AssertionError("应返回 401")
        except _HTTPError as e:
            assert e.code == 401, e
        print("  OK /pair 401 (错误 secret)")

        # 4) /pair 200
        code, body = _request(port, "POST", "/pair", secret=secret)
        assert code == 200 and body.get("paired") is True, body
        print("  OK /pair 200")

        # 5) /quota unknown provider -> 404
        try:
            _quota(port, secret, "does-not-exist", "acc")
            raise AssertionError("应 404")
        except _HTTPError as e:
            assert e.code == 404 and "unknown provider" in e.body, e.body
        print("  OK /quota unknown provider -> 404")

        # 6) /quota unavailable provider -> 200 source=unsupported（回归 P0-4）
        code, body = _quota(port, secret, "glm", "acc")
        assert code == 200, body
        assert body.get("source") == "unsupported", body
        assert body.get("accountId") == "acc", body
        assert body.get("note"), body
        print("  OK /quota unavailable provider -> unsupported:", body.get("note"))

        # 7) /quota available mock provider -> 200 source=bridge
        code, body = _quota(port, secret, "mock", "acc")
        assert code == 200, body
        assert body.get("source") == "bridge", body
        assert body.get("buckets"), body
        assert body["buckets"][0]["windowType"] == "WEEKLY", body
        print("  OK /quota available mock ->", body["buckets"][0]["windowType"])
    finally:
        server.shutdown()

    print("bridge_http_test: ALL PASSED")


def _quota(port, secret, provider, account_id):
    import urllib.error
    import urllib.request

    req = urllib.request.Request(f"http://127.0.0.1:{port}/quota", method="GET")
    req.add_header("Authorization", "Bearer " + secret)
    req.add_header("X-Provider", provider)
    req.add_header("X-Account", account_id)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        raise _HTTPError(e.code, e.read().decode("utf-8"))


if __name__ == "__main__":
    main()