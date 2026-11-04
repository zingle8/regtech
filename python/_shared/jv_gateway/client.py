# -*- coding: utf-8 -*-
"""
JV Gateway Python 客户端（自动接入）。

对标 Java 版 ``jv-gateway-client-starter``，做三件事：

1. 启动时把自身实例注册到平台（jv-gateway-discovery），定期心跳，退出时下线；
2. 采集请求 / 响应，组装成平台约定的日志模型；
3. 内存队列 + 后台线程异步批量上报（jv-gateway-trace），队列满直接丢弃。

设计原则与 Java 版一致：**任何采集、上报失败都不能影响业务**。
全部使用标准库，无第三方依赖。

快速开始::

    from jv_gateway import GatewayClient

    client = GatewayClient(app_id="app-order", app_name="订单中心",
                           service_name="order-service", port=8000)
    client.start()          # 注册 + 启动心跳/上报线程

    client.report(method="GET", path="/api/orders", status_code=200, cost_ms=12)
    client.stop()           # 刷写剩余日志 + 下线

Web 框架可用内置中间件自动采集，见 ``jv_gateway.wsgi`` / ``jv_gateway.asgi``。
"""

from __future__ import annotations

import atexit
import json
import logging
import platform
import queue
import socket
import threading
import time
import urllib.request
import uuid
from datetime import datetime
from typing import Any, Dict, List, Optional, Sequence

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
]

# ---------------------------------------------------------------- 常量（与 Java 端一致）

DEFAULT_SERVER_ADDR = "http://127.0.0.1:28080/api"

PATH_SERVICE_REGISTER = "/discovery/services/register"
PATH_SERVICE_HEARTBEAT = "/discovery/services/heartbeat"
PATH_SERVICE_DEREGISTER = "/discovery/services/deregister"
PATH_TRACE_BATCH = "/trace/logs/batch"

HEADER_TRACE_ID = "X-Gateway-Trace-Id"

# 错误类型是中文标签，控制台「错误类型」筛选按此匹配
ERR_NONE = "无"
ERR_AUTH_FAIL = "鉴权失败"
ERR_ROUTE_FAIL = "路由失败"
ERR_LIMIT_REJECT = "限流拒绝"
ERR_PARAM_INVALID = "参数校验失败"
ERR_UPSTREAM_TIMEOUT = "上游响应超时"
ERR_UPSTREAM_ERROR = "上游响应异常"

DEFAULT_EXCLUDE_PATHS = ("/actuator", "/error", "/favicon.ico", "/webjars", "/health", "/metrics")


def resolve_err_type(status_code: Optional[int]) -> str:
    """按状态码推断错误类型，规则与 Java ``ErrType.resolve`` 保持一致。"""
    if status_code is None:
        return ERR_NONE
    if status_code in (401, 403):
        return ERR_AUTH_FAIL
    if status_code == 404:
        return ERR_ROUTE_FAIL
    if status_code in (400, 422):
        return ERR_PARAM_INVALID
    if status_code == 429:
        return ERR_LIMIT_REJECT
    if status_code == 504:
        return ERR_UPSTREAM_TIMEOUT
    if status_code >= 500:
        return ERR_UPSTREAM_ERROR
    return ERR_NONE


def resolve_format(content_type: Optional[str]) -> str:
    """把 Content-Type 归一成控制台展示用的格式名，规则与 Java 端一致。"""
    if not content_type:
        return "-"
    ct = content_type.lower()
    if "json" in ct:
        return "JSON"
    if "protobuf" in ct:
        return "Protobuf"
    if "xml" in ct:
        return "XML"
    if "x-www-form-urlencoded" in ct:
        return "form"
    if "form-data" in ct:
        return "form-data"
    if "text/" in ct:
        return "TEXT"
    return ct.split(";")[0].strip()


def _local_ip() -> str:
    """取本机对外 IP（UDP 不发包，仅用于让内核选路由）。"""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"


def _now_iso() -> str:
    """Java LocalDateTime 可反序列化的 ISO-8601 本地时间（无时区）。"""
    return datetime.now().strftime("%Y-%m-%dT%H:%M:%S")


class GatewayClient:
    """网关接入客户端：注册、心跳、下线、日志采集上报。

    所有参数都有默认值，最小用法只需提供 ``app_id`` / ``service_name`` / ``port``。
    """

    def __init__(
        self,
        server_addr: str = DEFAULT_SERVER_ADDR,
        app_id: str = "app-demo",
        app_name: str = "示例应用",
        service_name: str = "demo-service",
        port: Optional[int] = None,
        ip: Optional[str] = None,
        language: Optional[str] = None,
        version: str = "v1.0.0",
        registry_enabled: bool = True,
        trace_enabled: bool = True,
        heartbeat_interval: float = 30.0,
        flush_interval: float = 3.0,
        batch_size: int = 50,
        queue_size: int = 1024,
        timeout: float = 5.0,
        max_body_size: int = 8192,
        sample_rate: float = 1.0,
        exclude_paths: Sequence[str] = DEFAULT_EXCLUDE_PATHS,
        logger: Optional[logging.Logger] = None,
    ) -> None:
        self.server_addr = (server_addr or DEFAULT_SERVER_ADDR).rstrip("/")
        self.app_id = app_id
        self.app_name = app_name
        self.service_name = service_name
        self.port = port if port is not None else int(_env("PORT", "8000"))
        self.ip = ip or _local_ip()
        self.language = language or "Python {}".format(platform.python_version())
        self.version = version

        self.registry_enabled = registry_enabled
        self.trace_enabled = trace_enabled

        self.heartbeat_interval = max(5.0, float(heartbeat_interval))
        self.flush_interval = max(0.5, float(flush_interval))
        self.batch_size = max(1, int(batch_size))
        self.timeout = max(0.5, float(timeout))
        self.max_body_size = max(0, int(max_body_size))
        self.sample_rate = min(1.0, max(0.0, float(sample_rate)))
        self.exclude_paths = tuple(exclude_paths or ())

        self.log = logger or logging.getLogger("jv_gateway")

        self._queue: "queue.Queue[Dict[str, Any]]" = queue.Queue(maxsize=max(1, int(queue_size)))
        self._stop_event = threading.Event()
        self._threads: List[threading.Thread] = []
        self._started = False
        self._atexit_registered = False
        self._sample_counter = 0
        self._sample_lock = threading.Lock()

    # ------------------------------------------------------------ 生命周期

    def start(self) -> "GatewayClient":
        """注册到平台并启动后台线程（心跳 + 定时上报）。重复调用安全。"""
        if self._started:
            return self
        self._started = True
        self._stop_event.clear()

        if self.registry_enabled:
            self.register()
            self._spawn("jv-gateway-heartbeat", self._heartbeat_loop)

        if self.trace_enabled:
            self._spawn("jv-gateway-reporter", self._flush_loop)

        if not self._atexit_registered:
            atexit.register(self._atexit_stop)
            self._atexit_registered = True

        self.log.info(
            "网关接入已启动：%s(%s) %s:%s → %s",
            self.app_id, self.service_name, self.ip, self.port, self.server_addr,
        )
        return self

    def stop(self, deregister: bool = True) -> None:
        """停止采集：刷写剩余日志、下线。重复调用安全。"""
        if not self._started:
            return
        self._started = False
        self._stop_event.set()

        for t in self._threads:
            t.join(timeout=2.0)
        self._threads.clear()

        if self.trace_enabled:
            try:
                self.flush()
            except Exception:
                pass

        if deregister and self.registry_enabled:
            self.deregister()

    def _atexit_stop(self) -> None:
        try:
            self.stop()
        except Exception:
            pass

    def _spawn(self, name: str, target) -> None:
        t = threading.Thread(target=target, name=name, daemon=True)
        t.start()
        self._threads.append(t)

    def _heartbeat_loop(self) -> None:
        while not self._stop_event.wait(self.heartbeat_interval):
            try:
                self.heartbeat()
            except Exception:
                pass

    def _flush_loop(self) -> None:
        while not self._stop_event.wait(self.flush_interval):
            try:
                self.flush()
            except Exception:
                pass

    # ------------------------------------------------------------ 注册 / 心跳 / 下线

    def instance(self) -> Dict[str, Any]:
        return {
            "appId": self.app_id,
            "appName": self.app_name,
            "serviceName": self.service_name,
            "ip": self.ip,
            "port": self.port,
            "language": self.language,
            "version": self.version,
            "registerTime": _now_iso(),
        }

    def register(self) -> bool:
        ok = self._request(PATH_SERVICE_REGISTER, self.instance())
        if ok:
            self.log.info("已注册到网关平台：%s (%s) %s:%s",
                          self.app_id, self.service_name, self.ip, self.port)
        else:
            self.log.warning("注册到网关平台失败（不影响业务启动）")
        return ok

    def heartbeat(self) -> bool:
        # 心跳失败只记 debug，避免刷屏
        return self._request(PATH_SERVICE_HEARTBEAT, self.instance(), quiet=True)

    def deregister(self) -> bool:
        return self._request(PATH_SERVICE_DEREGISTER, self.instance(), method="DELETE", quiet=True)

    # ------------------------------------------------------------ 日志采集

    def should_trace(self, path: str) -> bool:
        """是否采集该路径：排除名单 + 采样率。"""
        if not self.trace_enabled:
            return False
        for prefix in self.exclude_paths:
            if path.startswith(prefix.replace("**", "")):
                return False
        if self.sample_rate >= 1.0:
            return True
        if self.sample_rate <= 0.0:
            return False
        with self._sample_lock:
            self._sample_counter += 1
            return (self._sample_counter % 100) < (self.sample_rate * 100)

    def build_trace_log(
        self,
        method: str,
        path: str,
        status_code: int,
        cost_ms: int,
        trace_id: Optional[str] = None,
        protocol: str = "HTTP/1.1",
        req_ip: Optional[str] = None,
        req_port: Optional[int] = None,
        res_ip: Optional[str] = None,
        res_port: Optional[int] = None,
        req_content_type: Optional[str] = None,
        res_content_type: Optional[str] = None,
        req_headers: Optional[Any] = None,
        res_headers: Optional[Any] = None,
        req_body: Optional[str] = None,
        res_body: Optional[str] = None,
        err_type: Optional[str] = None,
        req_time: Optional[str] = None,
        res_time: Optional[str] = None,
        query_string: Optional[str] = None,
    ) -> Dict[str, Any]:
        """组装一条完整的日志模型（字段与平台 TraceLogDTO 对齐）。"""
        rb = self._truncate(req_body)
        sb = self._truncate(res_body)
        return {
            "appId": self.app_id,
            "appName": self.app_name,
            "serviceName": self.service_name,
            "api": "{} {}".format(method.upper(), path),
            "reqParams": query_string,
            "traceId": trace_id or self.new_trace_id(),
            "protocol": protocol,
            "reqFormat": resolve_format(req_content_type),
            "resFormat": resolve_format(res_content_type),
            "reqIp": req_ip,
            "reqPort": req_port,
            "resIp": res_ip or self.ip,
            "resPort": res_port or self.port,
            "statusCode": status_code,
            "errType": err_type or resolve_err_type(status_code),
            "cost": int(cost_ms),
            "reqTime": req_time or _now_iso(),
            "resTime": res_time or _now_iso(),
            "reqHeader": _headers_to_text(req_headers),
            "resHeader": _headers_to_text(res_headers),
            "reqBody": rb,
            "resBody": sb,
            "reqBytes": len(req_body.encode("utf-8")) if req_body else 0,
            "resBytes": len(res_body.encode("utf-8")) if res_body else 0,
        }

    def report(self, record: Optional[Dict[str, Any]] = None, **fields) -> None:
        """把一条日志放入队列（立即返回，绝不阻塞业务线程）。

        既支持传入完整 record，也支持直接传字段::

            client.report(method="GET", path="/api/x", status_code=200, cost_ms=8)
        """
        if not self.trace_enabled:
            return
        try:
            if record is None:
                record = self.build_trace_log(
                    method=fields.pop("method", "GET"),
                    path=fields.pop("path", "/"),
                    status_code=fields.pop("status_code", 200),
                    cost_ms=fields.pop("cost_ms", 0),
                    **fields,
                )
            self._queue.put_nowait(record)
        except queue.Full:
            self.log.debug("上报队列已满，丢弃一条日志")
        except Exception as e:  # 采集异常绝不影响业务
            self.log.debug("日志采集失败：%s", e)

    def flush(self) -> int:
        """把队列里的日志批量发出去，返回成功上报条数。"""
        total = 0
        while True:
            batch: List[Dict[str, Any]] = []
            try:
                while len(batch) < self.batch_size:
                    batch.append(self._queue.get_nowait())
            except queue.Empty:
                pass
            if not batch:
                break
            if not self._request(PATH_TRACE_BATCH, batch, quiet=True):
                # 与 Java 版一致：失败即丢弃当前批次，不重试、不阻塞
                self.log.debug("上报 %d 条日志失败，已丢弃", len(batch))
                break
            total += len(batch)
        return total

    def new_trace_id(self) -> str:
        return uuid.uuid4().hex

    def _truncate(self, text: Optional[str]) -> Optional[str]:
        if text is None:
            return None
        if self.max_body_size and len(text) > self.max_body_size:
            return text[: self.max_body_size] + "...(truncated)"
        return text

    # ------------------------------------------------------------ HTTP

    def _request(self, path: str, payload: Any, method: str = "POST",
                 quiet: bool = False) -> bool:
        """发一个 JSON 请求；任何异常都被吞掉并返回 False。"""
        url = self.server_addr + path
        try:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            req = urllib.request.Request(
                url, data=data, method=method,
                headers={"Content-Type": "application/json; charset=utf-8"},
            )
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                body = resp.read().decode("utf-8", "replace")
            if not quiet:
                self.log.debug("%s %s → %s", method, path, body[:200])
            return True
        except Exception as e:
            if not quiet:
                self.log.debug("%s %s 失败：%s", method, path, e)
            return False


def _headers_to_text(headers: Any) -> Optional[str]:
    """把 dict / list[tuple] 形式的 header 拼成 "k: v" 多行文本。"""
    if not headers:
        return None
    try:
        if isinstance(headers, dict):
            items = headers.items()
        elif isinstance(headers, (list, tuple)):
            items = headers
        else:
            return str(headers)
        return "\n".join("{}: {}".format(k, v) for k, v in items)
    except Exception:
        return None


def _env(key: str, default: str) -> str:
    import os
    return os.environ.get(key) or default
