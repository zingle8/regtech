"""GatewayClient：注册 + 心跳 + 采集上报一站式客户端；verify_token 调开放验签接口。"""

import json
import logging
import socket
import threading
import urllib.request
import uuid
from datetime import datetime

from ._http import post_json
from .reporter import TraceReporter

log = logging.getLogger("jv_gateway")

PATH_REGISTER = "/discovery/services/register"
PATH_HEARTBEAT = "/discovery/services/heartbeat"
PATH_DEREGISTER = "/discovery/services/deregister"
PATH_VERIFY = "/open/auth/token/verify"


def _iso_now():
    return datetime.now().strftime("%Y-%m-%dT%H:%M:%S")


def _new_ids():
    trace_id = uuid.uuid4().hex + uuid.uuid4().hex[:8]
    return trace_id[:32], uuid.uuid4().hex[:16]


def _local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def verify_token(server_addr, token, timeout=3.0):
    """调网关开放验签接口：返回 data 字典（valid=True/False，tokenType=USER/API_KEY）。

    凭证语义与网关、Java SDK 一致：用户 JWT 或 API Key（gwak- 前缀）均走此接口。
    """
    url = server_addr.rstrip("/") + PATH_VERIFY
    status, body = post_json(url, {"token": token}, "", timeout)
    return body.get("data") or {"valid": False}


class GatewayClient:
    """注册 + 心跳 + 上报线程。start() 后可用；stop() 刷出剩余日志并主动下线。

    Args:
        server_addr: 平台统一入口，如 http://127.0.0.1:28090/api
        app_id / app_name: 注册身份
        service_id: 服务ID（英文唯一标识，如 order-service）；
                   未配置时回退用 service_name，兼容旧写法
        service_name: 服务名（中文显示名，如 订单服务），可空，空则与服务ID一致
        port: 服务端口；0 表示无 HTTP 服务（纯脚本 / 定时任务）
        client_token: 上报共享密钥（X-Gateway-Client-Token），与网关 proxy 校验端同值
        heartbeat_interval: 心跳秒数（默认 30，下限 10）
    """

    def __init__(self, app_id, service_id=None, app_name=None, server_addr="http://127.0.0.1:28090/api",
                 service_name=None,
                 port=0, ip=None, language="Python", version="1.0.0", client_token="",
                 heartbeat_interval=30.0):
        self.app_id = app_id
        self.app_name = app_name or app_id
        # 服务ID（英文唯一标识）：未配置时回退 service_name，兼容旧写法
        self.service_id = service_id or service_name
        # 服务名（中文显示名）：未配置时与服务ID一致
        self.service_name = service_name or self.service_id
        self.port = int(port)
        self.ip = ip or _local_ip()
        self.language = language
        self.version = version
        self.client_token = client_token
        self.server_addr = server_addr.rstrip("/")
        self._heartbeat_interval = max(10.0, heartbeat_interval)
        self._registered = False
        self._stop = threading.Event()
        self._thread = None
        self.reporter = TraceReporter(server_addr, client_token)

    # ------------------------------------------------------------ 生命周期
    def start(self):
        self.register()
        self.reporter.start()
        if self._thread is None:
            self._thread = threading.Thread(target=self._loop, name="jv-gateway-heartbeat", daemon=True)
            self._thread.start()
        return self

    def stop(self):
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=5)
            self._thread = None
        self.reporter.stop()
        self.deregister()

    def register(self):
        ok = self._post(PATH_REGISTER, self._instance())
        if ok:
            self._registered = True
            log.info("已注册到网关平台：%s (%s) %s:%s",
                     self.app_id, self.service_id, self.ip, self.port)
        else:
            self._registered = False
            log.warning("注册失败（不影响业务启动，下个心跳周期自动重试）")
        return ok

    def heartbeat(self):
        ok = self._post(PATH_HEARTBEAT, self._instance())
        self._registered = ok
        return ok

    def deregister(self):
        """主动下线：DELETE 语义（与 Java SDK、服务端 @DeleteMapping 一致）。"""
        try:
            data = json.dumps(self._instance()).encode("utf-8")
            req = urllib.request.Request(self.server_addr + PATH_DEREGISTER, data=data, method="DELETE")
            req.add_header("Content-Type", "application/json")
            if self.client_token:
                req.add_header("X-Gateway-Client-Token", self.client_token)
            with urllib.request.urlopen(req, timeout=5.0) as resp:
                return resp.status == 200
        except Exception as e:
            log.warning("平台下线 %s 调用失败：%s", PATH_DEREGISTER, e)
            return False

    # ------------------------------------------------------------ 采集上报
    def report(self, method="GET", path="/", status_code=200, cost_ms=0, req_body="",
               res_body="", err_type="NONE", req_bytes=0, res_bytes=0,
               trace_id=None, span_id=None):
        """无 Web 框架时手动上报一条调用日志（含 server span）。字段与 TraceLogDTO 对齐。"""
        trace_id = trace_id or _new_ids()[0]
        span_id = span_id or _new_ids()[1]
        now = _iso_now()
        self.reporter.offer_log({
            "appId": self.app_id, "appName": self.app_name,
            "serviceId": self.service_id, "serviceName": self.service_name,
            "api": path, "protocol": "HTTP/1.1", "reqIp": self.ip, "reqPort": self.port,
            "statusCode": status_code, "errType": err_type, "cost": int(cost_ms),
            "reqTime": now, "resTime": now, "reqBody": req_body or "", "resBody": res_body or "",
            "reqBytes": int(req_bytes or 0), "resBytes": int(res_bytes or 0),
            "traceId": trace_id, "reqFormat": "application/json", "resFormat": "application/json",
        })
        self.reporter.offer_span({
            "traceId": trace_id, "spanId": span_id, "parentSpanId": "", "kind": "SERVER",
            "appId": self.app_id, "appName": self.app_name,
            "serviceId": self.service_id, "serviceName": self.service_name,
            "api": path, "peerService": "", "statusCode": status_code, "errType": err_type,
            "cost": int(cost_ms), "startTime": now,
        })

    def _instance(self):
        return {
            "appId": self.app_id, "appName": self.app_name,
            "serviceId": self.service_id, "serviceName": self.service_name,
            "ip": self.ip, "port": self.port, "language": self.language,
            "version": self.version, "registerTime": _iso_now(),
        }

    def _post(self, path, payload):
        try:
            status, body = post_json(self.server_addr + path, payload, self.client_token, 5.0)
            if status == 200 and body.get("code") == 200:
                return True
            log.warning("平台接口 %s 返回 %s：%s", path, status, body.get("message"))
            return False
        except Exception as e:
            log.warning("平台接口 %s 调用失败：%s", path, e)
            return False

    def _loop(self):
        while not self._stop.wait(self._heartbeat_interval):
            try:
                # 未注册成功时先补注册（幂等，服务端按 serviceName upsert），与 Java SDK 同语义
                if not self._registered:
                    self.register()
                if self._registered:
                    self.heartbeat()
            except Exception as e:  # 后台线程绝不拖垮业务
                log.debug("心跳异常：%s", e)
