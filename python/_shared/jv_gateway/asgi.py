"""ASGI 中间件：FastAPI / Starlette 自动采集 + 入口鉴权（零第三方依赖）。

FastAPI::

    app.add_middleware(ASGITraceMiddleware, client=client)
    app.add_middleware(ASGIAuthMiddleware, auth=GatewayAuth(client))
"""


class ASGITraceMiddleware:
    def __init__(self, app, client):
        self.app = app
        self.client = client

    async def __call__(self, scope, receive, send):
        import time
        if scope["type"] != "http" or self._excluded(scope.get("path", "/")):
            await self.app(scope, receive, send)
            return

        start = time.time()
        status_holder = {"code": 200}

        async def send_wrapper(message):
            if message["type"] == "http.response.start":
                status_holder["code"] = message["status"]
            await send(message)

        try:
            await self.app(scope, receive, send_wrapper)
        except Exception:
            self.client.report(method=scope.get("method", "GET"), path=scope.get("path", "/"),
                               status_code=500, cost_ms=int((time.time() - start) * 1000),
                               err_type="UPSTREAM_ERROR")
            raise

        code = status_holder["code"]
        self.client.report(method=scope.get("method", "GET"), path=scope.get("path", "/"),
                           status_code=code, cost_ms=int((time.time() - start) * 1000),
                           err_type="NONE" if code < 400 else "UPSTREAM_ERROR")

    def _excluded(self, path):
        # 与 Java SDK excludePaths、网关 TraceGlobalFilter 排除清单同一组路径
        return path.startswith("/api/trace/") or path.startswith("/api/discovery/services/")


class ASGIAuthMiddleware:
    """ASGI 入口鉴权：验签不通过返回 401/403 JSON；上报为出站调用，不在其列。"""

    def __init__(self, app, auth):
        self.app = app
        self.auth = auth

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        headers = {k.decode("latin-1").lower(): v.decode("latin-1")
                   for k, v in scope.get("headers", [])}
        decision = self.auth.check(scope.get("path", "/"),
                                   headers.get("authorization", ""),
                                   headers.get("x-api-key", ""))
        if decision is not None:
            status, body = decision
            await send({"type": "http.response.start", "status": status,
                        "headers": [(b"content-type", b"application/json")]})
            await send({"type": "http.response.body", "body": body.encode("utf-8")})
            return
        await self.app(scope, receive, send)
