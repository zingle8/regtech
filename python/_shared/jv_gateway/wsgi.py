# -*- coding: utf-8 -*-
"""
WSGI 中间件：为 Flask / Django / Bottle 等同步框架自动采集调用日志。

用法::

    from flask import Flask
    from jv_gateway import GatewayClient
    from jv_gateway.wsgi import WSGITraceMiddleware

    app = Flask(__name__)
    client = GatewayClient(app_id="app-order", service_name="order-service", port=8000)
    client.start()

    app.wsgi_app = WSGITraceMiddleware(app.wsgi_app, client)
"""

from __future__ import annotations

import io
import time
from typing import Any, Dict, Iterable, List

from .client import GatewayClient, HEADER_TRACE_ID, resolve_err_type, resolve_format

__all__ = ["WSGITraceMiddleware"]


class WSGITraceMiddleware:
    """按 WSGI 协议包装应用，不依赖任何 Web 框架。"""

    def __init__(self, app, client: GatewayClient, capture_body: bool = True) -> None:
        self.app = app
        self.client = client
        self.capture_body = capture_body

    def __call__(self, environ: Dict[str, Any], start_response):
        path = environ.get("PATH_INFO", "/")
        if not self.client.should_trace(path):
            return self.app(environ, start_response)

        client = self.client
        method = environ.get("REQUEST_METHOD", "GET")
        started = time.time()
        req_time = _now()

        # 请求头（WSGI 把 HTTP_ 前缀的键规范化为 header）
        req_headers = {
            k[5:].replace("_", "-"): v
            for k, v in environ.items()
            if k.startswith("HTTP_")
        }

        # 缓存请求体：读取后放回 BytesIO，不能影响下游读取
        req_body_text = None
        req_bytes = 0
        if self.capture_body:
            try:
                length = int(environ.get("CONTENT_LENGTH") or 0)
                if 0 < length <= client.max_body_size:
                    raw = environ["wsgi.input"].read(length)
                    environ["wsgi.input"] = io.BytesIO(raw)
                    req_bytes = len(raw)
                    req_body_text = raw.decode(_charset(environ.get("CONTENT_TYPE")), "replace")
            except Exception:
                req_body_text = None

        status_holder: Dict[str, Any] = {"status": 200, "headers": []}
        chunks: List[bytes] = []

        def _start_response(status, response_headers, exc_info=None):
            try:
                status_holder["status"] = int(str(status).split()[0])
            except Exception:
                status_holder["status"] = 500
            status_holder["headers"] = response_headers
            write = start_response(status, response_headers, exc_info)

            def _write(data):
                chunks.append(data)
                return write(data)

            return _write

        def _stream() -> Iterable[bytes]:
            try:
                for chunk in self.app(environ, _start_response):
                    chunks.append(chunk)
                    yield chunk
            finally:
                self._emit(
                    environ, method, path, status_holder, chunks,
                    req_headers, req_body_text, req_bytes, req_time, started,
                )

        return _stream()

    def _emit(self, environ, method, path, status_holder, chunks,
              req_headers, req_body_text, req_bytes, req_time, started) -> None:
        try:
            client = self.client
            cost = int((time.time() - started) * 1000)
            status = int(status_holder.get("status") or 500)
            res_headers = status_holder.get("headers") or []
            content_type = _header_value(res_headers, "Content-Type")
            raw = b"".join(c for c in chunks if c)
            res_body_text = None
            if client.max_body_size and raw:
                res_body_text = raw[: client.max_body_size].decode(
                    _charset(content_type), "replace"
                )

            client.report(
                client.build_trace_log(
                    method=method,
                    path=path,
                    status_code=status,
                    cost_ms=cost,
                    trace_id=environ.get("HTTP_X_GATEWAY_TRACE_ID") or client.new_trace_id(),
                    protocol=environ.get("SERVER_PROTOCOL", "HTTP/1.1"),
                    req_ip=environ.get("REMOTE_ADDR"),
                    req_port=_to_int(environ.get("REMOTE_PORT")),
                    # 注意：wsgiref 对无 body 的 GET 也会给出 CONTENT_TYPE='text/plain'，
                    # 只有真正读到请求体时才采信它，否则会误判成 TEXT
                    req_content_type=environ.get("CONTENT_TYPE") if req_bytes else None,
                    res_content_type=content_type,
                    req_headers=req_headers,
                    res_headers=res_headers,
                    req_body=req_body_text,
                    res_body=res_body_text,
                    query_string=environ.get("QUERY_STRING") or None,
                    req_time=req_time,
                )
            )
        except Exception:
            pass


def _now() -> str:
    from datetime import datetime
    return datetime.now().strftime("%Y-%m-%dT%H:%M:%S")


def _charset(content_type: Any) -> str:
    try:
        ct = str(content_type or "")
        for part in ct.split(";"):
            part = part.strip()
            if part.lower().startswith("charset="):
                return part.split("=", 1)[1].strip() or "utf-8"
    except Exception:
        pass
    return "utf-8"


def _header_value(headers, name: str) -> Any:
    for k, v in headers or []:
        if str(k).lower() == name.lower():
            return v
    return None


def _to_int(value: Any):
    try:
        return int(value)
    except (TypeError, ValueError):
        return None
