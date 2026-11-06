"""jv_gateway —— AI 网关平台 Python 接入 SDK（零第三方依赖，Python 3.8+）。

快速上手::

    from jv_gateway import GatewayClient

    client = GatewayClient(
        server_addr="http://127.0.0.1:28090/api",
        app_id="app-order", app_name="订单中心",
        service_name="order-service", port=8000,
    )
    client.start()      # 注册 + 心跳 + 上报线程
    ...
    client.stop()       # 刷出剩余日志并下线

Web 框架采集：jv_gateway.wsgi.WSGITraceMiddleware（Flask/Django）、
jv_gateway.asgi.ASGITraceMiddleware（FastAPI/Starlette）。
入口鉴权（防止绕过网关直连）：jv_gateway.auth.GatewayAuth。
"""

from .client import GatewayClient, verify_token
from .reporter import TraceReporter
from .wsgi import WSGITraceMiddleware, WSGIAuthMiddleware
from .asgi import ASGITraceMiddleware, ASGIAuthMiddleware
from .auth import GatewayAuth as GatewayAuthMiddleware

__version__ = "1.0.0"
__all__ = [
    "GatewayClient", "TraceReporter", "verify_token",
    "WSGITraceMiddleware", "WSGIAuthMiddleware",
    "ASGITraceMiddleware", "ASGIAuthMiddleware", "GatewayAuthMiddleware",
]
