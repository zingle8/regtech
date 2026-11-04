# -*- coding: utf-8 -*-
"""
ASGI 中间件：为 FastAPI / Starlette / Django(ASGI) 等异步框架自动采集调用日志。

用法::

    from fastapi import FastAPI
    from jv_gateway import GatewayClient
    from jv_gateway.asgi import ASGITraceMiddleware

    client = GatewayClient(app_id="app-order", service_name="order-service", port=8000)
    client.start()

    app = FastAPI()
    app.add_middleware(ASGITraceMiddleware, client=client)
"""

from __future__ import annotations

import time
from typing import Any, Dict, List

from .client import GatewayClient

__all__ = ["ASGITraceMiddleware"]


class ASGITraceMiddleware:
    """纯 ASGI 中间件，既是类也是合法的 ``add_middleware`` 工厂。"""

    def __init__(self, app, client: GatewayClient, capture_body: bool = True) -> None:
        self.app = app
        self.client = client
        self.capture_body = capture_body

    async def __call__(self, scope: Dict[str, Any], receive, send) -> None:
        if scope.get("type") != "http":
            await self.app(scope, receive, send)
            return

        path = scope.get("path", "/")
        if not self.client.should_trace(path):
            await self.app(scope, receive, send)
            return

        client = self.client
        started = time.time()
        req_time = _now()
        method = scope.get("method", "GET")
        req_headers = _headers_to_dict(scope.get("headers"))
        req_body: List[bytes] = []
        cached: List[Dict[str, Any]] = []

        # 主动预读请求体，再把事件重放给下游。
        # 这样不依赖框架是否读取 body（原生 ASGI 应用可能根本不读），采集才算可靠。
        # 超过 max_body_size 就停止预读，剩余部分由下游继续从原 receive 读。
        if self.capture_body:
            total = 0
            limit = client.max_body_size
            while True:
                msg = await receive()
                cached.append(msg)
                if msg.get("type") != "http.request":
                    break
                chunk = msg.get("body") or b""
                if chunk and (not limit or total < limit):
                    req_body.append(chunk if not limit else chunk[: max(0, limit - total)])
                total += len(chunk)
                if not msg.get("more_body") or (limit and total >= limit):
                    break

        async def _receive():
            if cached:
                return cached.pop(0)
            return await receive()

        status: Dict[str, int] = {"code": 500}
        res_headers: List[List[Any]] = []
        res_body: List[bytes] = []

        async def _send(msg):
            mtype = msg.get("type")
            if mtype == "http.response.start":
                status["code"] = int(msg.get("status") or 500)
                res_headers.extend(msg.get("headers") or [])
            elif mtype == "http.response.body":
                body = msg.get("body") or b""
                if body and sum(len(b) for b in res_body) < client.max_body_size:
                    res_body.append(body)
            await send(msg)

        try:
            await self.app(scope, _receive, _send)
        finally:
            try:
                cost = int((time.time() - started) * 1000)
                raw_req = b"".join(req_body)
                raw_res = b"".join(res_body)
                server = scope.get("server") or (None, None)
                client_addr = scope.get("client") or (None, None)

                client.report(
                    client.build_trace_log(
                        method=method,
                        path=path,
                        status_code=status["code"],
                        cost_ms=cost,
                        trace_id=req_headers.get("x-gateway-trace-id") or client.new_trace_id(),
                        protocol="HTTP/" + scope.get("http_version", "1.1"),
                        req_ip=client_addr[0],
                        req_port=client_addr[1],
                        res_ip=self.client.ip,
                        res_port=server[1] or self.client.port,
                        req_content_type=req_headers.get("content-type"),
                        res_content_type=_header(res_headers, b"content-type"),
                        req_headers=req_headers,
                        res_headers=_headers_to_dict(res_headers),
                        req_body=raw_req.decode(_charset(req_headers.get("content-type")), "replace") or None,
                        res_body=raw_res.decode(_charset(_header(res_headers, b"content-type")), "replace") or None,
                        query_string=(scope.get("query_string") or b"").decode("latin-1") or None,
                        req_time=req_time,
                    )
                )
            except Exception:
                pass


def _now() -> str:
    from datetime import datetime
    return datetime.now().strftime("%Y-%m-%dT%H:%M:%S")


def _headers_to_dict(headers) -> Dict[str, str]:
    out: Dict[str, str] = {}
    for k, v in headers or []:
        try:
            out[k.decode("latin-1")] = v.decode("latin-1")
        except Exception:
            continue
    return out


def _header(headers, name: bytes):
    for k, v in headers or []:
        if k.lower() == name:
            try:
                return v.decode("latin-1")
            except Exception:
                return None
    return None


def _charset(content_type: Any) -> str:
    try:
        for part in str(content_type or "").split(";"):
            part = part.strip()
            if part.lower().startswith("charset="):
                return part.split("=", 1)[1].strip() or "utf-8"
    except Exception:
        pass
    return "utf-8"
