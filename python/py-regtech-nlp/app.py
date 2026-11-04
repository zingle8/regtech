#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""py-regtech-nlp —— RegTech 文本分析服务（Python 3.8+，仅标准库）。

命名约定：Python 服务统一 ``py-`` 前缀，Java 服务统一 ``jv-`` 前缀。

启动即接入 ai-gateway 网关平台（注册 + 心跳 + 调用日志采集），
接入方式与 ``ai-gateway/sdk/python`` 一致，SDK 已内置在 ``python/_shared/jv_gateway``。

对外接口（经网关：``/api/regtech/nlp/**``）：

    GET  /api/regtech/nlp/health       健康检查
    GET  /api/regtech/nlp/stats        运行时统计
    POST /api/regtech/nlp/keywords     关键词抽取
    POST /api/regtech/nlp/segment      中文切分
    POST /api/regtech/nlp/similarity   两段文本相似度

本地直连：http://127.0.0.1:28110/api/regtech/nlp/health
"""

from __future__ import annotations

import json
import os
import re
import sys
import threading
import time
from datetime import datetime
from socketserver import ThreadingMixIn
from wsgiref.simple_server import WSGIServer, make_server

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SHARED_DIR = os.path.abspath(os.path.join(BASE_DIR, "..", "_shared"))
if SHARED_DIR not in sys.path:
    sys.path.insert(0, SHARED_DIR)

from jv_gateway import GatewayClient                      # noqa: E402
from jv_gateway.wsgi import WSGITraceMiddleware           # noqa: E402

# ---------------------------------------------------------------- 配置

SERVICE_NAME = os.environ.get("SERVICE_NAME", "py-regtech-nlp")
PORT = int(os.environ.get("PORT", "28110"))
CONTEXT = "/api/regtech/nlp"
APP_ID = os.environ.get("APP_ID", "app-regtech")
APP_NAME = os.environ.get("APP_NAME", "NCS RegTech 监管科技")
GATEWAY_BASE_URL = os.environ.get("GATEWAY_BASE_URL", "http://127.0.0.1:28090/api")
GATEWAY_IP = os.environ.get("GATEWAY_IP", "127.0.0.1")
VERSION = "1.0.0"

# ---------------------------------------------------------------- 文本处理

STOPWORDS = {
    "的", "了", "和", "与", "及", "或", "在", "是", "为", "对", "等", "中", "者",
    "应当", "可以", "不得", "依照", "按照", "规定", "本", "条", "款", "项", "并",
    "有", "由", "向", "将", "予以", "进行", "其", "该", "其他", "以上", "以下",
    "the", "a", "an", "of", "to", "and", "or", "in", "on", "for", "is", "are",
}

_PUNCT = re.compile(r"[\s\u3000-\u303f\uff00-\uffef,.;:!?()\[\]{}<>\"'`~@#$%^&*_+=|\\/-]+")
_CJK_RUN = re.compile(r"[\u4e00-\u9fff]+")
_LATIN_WORD = re.compile(r"[A-Za-z][A-Za-z0-9_+-]{1,}")


def normalize(text: str) -> str:
    return _PUNCT.sub(" ", text or "").strip()


def cjk_runs(text: str):
    return _CJK_RUN.findall(text or "")


def latin_words(text: str):
    return [w.lower() for w in _LATIN_WORD.findall(text or "") if w.lower() not in STOPWORDS]


def segment(text: str, limit: int = 200):
    """无词典切分：长候选优先的贪心 n-gram 切分（够用且零依赖）。"""
    text = normalize(text)
    tokens = []
    for run in cjk_runs(text):
        i = 0
        while i < len(run):
            step = 1
            for n in (4, 3, 2):
                if i + n <= len(run) and run[i:i + n] not in STOPWORDS:
                    step = n
                    break
            tokens.append(run[i:i + step])
            i += step
    tokens.extend(latin_words(text))
    return tokens[:limit]


def extract_keywords(text: str, top_k: int = 10):
    """n-gram 词频打分：score = 频次 × (长度-1)，取长候选优先去重。"""
    text = normalize(text)
    counter = {}
    for run in cjk_runs(text):
        for n in range(2, min(6, len(run)) + 1):
            for i in range(len(run) - n + 1):
                gram = run[i:i + n]
                if gram in STOPWORDS:
                    continue
                counter[gram] = counter.get(gram, 0) + 1
    for word in latin_words(text):
        counter[word] = counter.get(word, 0) + 2

    scored = sorted(
        counter.items(),
        key=lambda kv: (kv[1] * (len(kv[0]) - 1), kv[1], kv[0]),
        reverse=True,
    )
    picked = []

    def conflicts(gram: str) -> bool:
        return any(gram in exist or exist in gram for exist, _ in picked)

    # 第一轮：重复出现的候选（可靠关键词）
    for gram, freq in scored:
        if freq < 2 or conflicts(gram):
            continue
        picked.append((gram, freq))
        if len(picked) >= top_k:
            break
    # 第二轮：低频候选只取 2~4 字，避免长窗口边界噪声
    for gram, freq in scored:
        if len(picked) >= top_k:
            break
        if freq >= 2 or not (2 <= len(gram) <= 4) or conflicts(gram):
            continue
        picked.append((gram, freq))
    total = float(sum(f for _, f in picked)) or 1.0
    return [{"word": w, "count": f, "weight": round(f / total, 4)} for w, f in picked]


def similarity(a: str, b: str) -> dict:
    """字符 2-gram Jaccard 与最长公共子序列比率的加权相似度。"""
    na, nb = normalize(a), normalize(b)
    if not na or not nb:
        return {"score": 0.0, "jaccard": 0.0, "lcs": 0.0, "lengthA": len(na), "lengthB": len(nb)}

    def grams(s):
        return {s[i:i + 2] for i in range(len(s) - 1)} or {s}

    ga, gb = grams(na), grams(nb)
    jaccard = len(ga & gb) / float(len(ga | gb))

    # LCS 动态规划（长度截断，避免超长文本拖慢）
    x, y = na[:2000], nb[:2000]
    prev = [0] * (len(y) + 1)
    for ch in x:
        cur = [0]
        for j, ch2 in enumerate(y, 1):
            cur.append(prev[j - 1] + 1 if ch == ch2 else max(prev[j], cur[j - 1]))
        prev = cur
    lcs = prev[-1] / float(max(len(x), len(y)) or 1)

    return {
        "score": round(0.4 * jaccard + 0.6 * lcs, 4),
        "jaccard": round(jaccard, 4),
        "lcs": round(lcs, 4),
        "lengthA": len(na),
        "lengthB": len(nb),
    }

# ---------------------------------------------------------------- 运行时统计

_STATS_LOCK = threading.Lock()
_STATS = {"startedAt": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"), "requests": 0, "byPath": {}}


def _hit(path: str) -> None:
    with _STATS_LOCK:
        _STATS["requests"] += 1
        _STATS["byPath"][path] = _STATS["byPath"].get(path, 0) + 1


def _uptime() -> int:
    started = datetime.strptime(_STATS["startedAt"], "%Y-%m-%dT%H:%M:%S")
    return int((datetime.now() - started).total_seconds())

# ---------------------------------------------------------------- WSGI 应用


def _json(start_response, data, status: int = 200):
    body = json.dumps(data, ensure_ascii=False).encode("utf-8")
    start_response(
        "%d %s" % (status, "OK" if status == 200 else "Bad Request"),
        [("Content-Type", "application/json; charset=utf-8"),
         ("Content-Length", str(len(body)))],
    )
    return [body]


def _read_json(environ) -> dict:
    try:
        length = int(environ.get("CONTENT_LENGTH") or 0)
        raw = environ["wsgi.input"].read(length) if length else b"{}"
        return json.loads(raw.decode("utf-8") or "{}")
    except Exception:
        return {}


def _ok(data):
    return {"code": 200, "message": "操作成功", "data": data}


def handle_health(environ, start_response):
    return _json(start_response, _ok({
        "service": SERVICE_NAME,
        "status": "UP",
        "language": "Python",
        "port": PORT,
        "uptime": _uptime(),
    }))


def handle_stats(environ, start_response):
    with _STATS_LOCK:
        snapshot = dict(_STATS)
        snapshot["byPath"] = dict(_STATS["byPath"])
    snapshot["uptime"] = _uptime()
    snapshot["serviceName"] = SERVICE_NAME
    return _json(start_response, _ok(snapshot))


def handle_keywords(environ, start_response):
    payload = _read_json(environ)
    text = str(payload.get("text") or "")
    if not text.strip():
        return _json(start_response, {"code": 400, "message": "text 不能为空", "data": None}, 400)
    top_k = int(payload.get("topK") or 10)
    return _json(start_response, _ok({"keywords": extract_keywords(text, top_k), "length": len(text)}))


def handle_segment(environ, start_response):
    payload = _read_json(environ)
    text = str(payload.get("text") or "")
    if not text.strip():
        return _json(start_response, {"code": 400, "message": "text 不能为空", "data": None}, 400)
    return _json(start_response, _ok({"tokens": segment(text, int(payload.get("limit") or 200))}))


def handle_similarity(environ, start_response):
    payload = _read_json(environ)
    a, b = str(payload.get("a") or ""), str(payload.get("b") or "")
    if not a.strip() or not b.strip():
        return _json(start_response, {"code": 400, "message": "a / b 均不能为空", "data": None}, 400)
    return _json(start_response, _ok(similarity(a, b)))


ROUTES = {
    "/health": ("GET", handle_health),
    "/stats": ("GET", handle_stats),
    "/keywords": ("POST", handle_keywords),
    "/segment": ("POST", handle_segment),
    "/similarity": ("POST", handle_similarity),
}


def app(environ, start_response):
    path = environ.get("PATH_INFO") or "/"
    if path.startswith(CONTEXT):
        path = path[len(CONTEXT):] or "/"
    path = path.rstrip("/") or "/"

    if path == "/":
        return _json(start_response, _ok({
            "service": SERVICE_NAME,
            "endpoints": sorted(ROUTES.keys()),
            "gatewayPrefix": CONTEXT,
        }))

    route = ROUTES.get(path)
    if route is None:
        return _json(start_response, {"code": 404, "message": "接口不存在", "data": None}, 404)

    method, handler = route
    if environ.get("REQUEST_METHOD", "GET").upper() != method:
        return _json(start_response, {"code": 405, "message": "方法不允许，请使用 " + method, "data": None}, 405)

    _hit(path)
    return handler(environ, start_response)


class ThreadingWSGIServer(ThreadingMixIn, WSGIServer):
    daemon_threads = True
    allow_reuse_address = True


def main() -> None:
    client = GatewayClient(
        server_addr=GATEWAY_BASE_URL,
        app_id=APP_ID,
        app_name=APP_NAME,
        service_name=SERVICE_NAME,
        port=PORT,
        ip=GATEWAY_IP,
        version=VERSION,
        exclude_paths=("/health", "/api/regtech/nlp/health", "/favicon.ico"),
    )
    client.start()

    httpd = make_server("0.0.0.0", PORT, WSGITraceMiddleware(app, client),
                        server_class=ThreadingWSGIServer)
    print("[%s] %s 已启动 → http://127.0.0.1:%d%s" %
          (SERVICE_NAME, time.strftime("%H:%M:%S"), PORT, CONTEXT), flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        client.stop()
        httpd.server_close()


if __name__ == "__main__":
    main()
