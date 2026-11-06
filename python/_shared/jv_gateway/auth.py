"""SDK 入口鉴权：回调网关开放验签接口，防止绕过网关直连业务系统。

用法（FastAPI）::

    from jv_gateway import GatewayClient
    from jv_gateway.auth import GatewayAuth
    from jv_gateway.asgi import ASGIAuthMiddleware

    client = GatewayClient(app_id="app-order", service_name="order-service", port=8000)
    auth = GatewayAuth(client)                      # 复用 server_addr 与 client_token
    app.add_middleware(ASGIAuthMiddleware, auth=auth)

配置（构造参数）：
- exempt_paths：豁免路径前缀（默认健康检查 / 文档页）
- cache_seconds：验签结果缓存秒数（同一 token 不重复回调网关）
- fail_open：网关不可达时 True 放行（可用性优先）/ False 拒绝（安全优先，默认）

与 Java 端 GatewayAuthFilter 同语义；SDK 数据上报是出站调用，不受入口鉴权影响——
即使调用方验签失败，日志与 span 照常上报，网关平台始终能看到真实流量。
"""

import time

from ._http import post_json

DEFAULT_EXEMPT = ("/health", "/actuator", "/docs", "/openapi.json", "/favicon.ico")


class GatewayAuth:
    def __init__(self, client, exempt_paths=DEFAULT_EXEMPT, cache_seconds=60,
                 timeout=3.0, fail_open=False):
        self._server = client.server_addr
        self._client_token = client.client_token
        self._exempt = tuple(exempt_paths)
        self._cache_seconds = cache_seconds
        self._timeout = timeout
        self._fail_open = fail_open
        self._cache = {}

    def check(self, path, authorization="", api_key=""):
        """返回 None = 放行；返回 (status, json_str) = 拒绝。"""
        for p in self._exempt:
            if path.startswith(p):
                return None

        token = ""
        if authorization.startswith("Bearer "):
            token = authorization[7:].strip()
        elif api_key:
            token = api_key.strip()
        if not token:
            return 401, '{"code":401,"message":"未提供访问凭证（Authorization: Bearer 或 X-Api-Key）"}'

        expires = self._cache.get(token)
        if expires and expires > time.time():
            return None  # 缓存有效期内通过

        try:
            status, body = post_json(self._server + "/open/auth/token/verify",
                                     {"token": token}, self._client_token, self._timeout)
            valid = status == 200 and (body.get("data") or {}).get("valid") is True
        except Exception:
            if self._fail_open:
                return None
            return 403, '{"code":403,"message":"验签服务不可达"}'

        if valid:
            if len(self._cache) >= 2048:
                self._cache.clear()
            self._cache[token] = time.time() + self._cache_seconds
            return None
        return 401, '{"code":401,"message":"凭证无效或已过期"}'
