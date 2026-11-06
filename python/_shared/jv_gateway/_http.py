"""最小 HTTP 封装：urllib 实现，零第三方依赖。所有请求自动携带共享密钥头。"""

import json
import urllib.error
import urllib.request

HEADER_CLIENT_TOKEN = "X-Gateway-Client-Token"


def post_json(url, payload, client_token="", timeout=5.0):
    """POST JSON，返回 (status_code, response_dict)；HTTP 错误返回 (code, body)；网络异常抛出。"""
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if client_token:
        req.add_header(HEADER_CLIENT_TOKEN, client_token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8") or "{}")
        except Exception:
            return e.code, {}
