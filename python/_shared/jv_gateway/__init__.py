# -*- coding: utf-8 -*-
"""
jv-gateway —— 网关平台 Python 接入 SDK（自动接入）。

零第三方依赖，提供：
- ``GatewayClient``：服务注册 / 心跳 / 下线 / 调用日志采集与批量上报
- ``jv_gateway.wsgi.WSGITraceMiddleware``：Flask / Django 自动采集
- ``jv_gateway.asgi.ASGITraceMiddleware``：FastAPI / Starlette 自动采集
"""

from .client import (
    DEFAULT_SERVER_ADDR,
    ERR_AUTH_FAIL,
    ERR_LIMIT_REJECT,
    ERR_NONE,
    ERR_PARAM_INVALID,
    ERR_ROUTE_FAIL,
    ERR_UPSTREAM_ERROR,
    ERR_UPSTREAM_TIMEOUT,
    HEADER_TRACE_ID,
    GatewayClient,
    resolve_err_type,
    resolve_format,
)

__version__ = "1.0.0"

__all__ = [
    "GatewayClient",
    "DEFAULT_SERVER_ADDR",
    "HEADER_TRACE_ID",
    "ERR_NONE",
    "ERR_AUTH_FAIL",
    "ERR_ROUTE_FAIL",
    "ERR_LIMIT_REJECT",
    "ERR_PARAM_INVALID",
    "ERR_UPSTREAM_TIMEOUT",
    "ERR_UPSTREAM_ERROR",
    "resolve_err_type",
    "resolve_format",
    "__version__",
]
