"""WSGI 中间件：Flask / Django 自动采集 + 入口鉴权（零第三方依赖）。"""

import time


def _now():
    return time.time()


class _ReplayBody:
    """可重读的 body 包装：SDK 读完缓存体后放回，业务代码仍能正常读取。"""

    def __init__(self, data):
        self._data = data

    def read(self, n=-1):
        return self._data if n != 0 else b""


class WSGITraceMiddleware:
    """app.wsgi_app = WSGITraceMiddleware(app.wsgi_app, client)"""

    MAX_BODY = 8192

    def __init__(self, wsgi_app, client):
        self.wsgi_app = wsgi_app
        self.client = client

    def __call__(self, environ, start_response):
        path = environ.get("PATH_INFO", "/")
        if self._excluded(path):
            return self.wsgi_app(environ, start_response)

        req_time = _now()
        length = int(environ.get("CONTENT_LENGTH") or 0)
        req_body = environ["wsgi.input"].read(length)[: self.MAX_BODY] if length else b""
        environ["wsgi.input"] = _ReplayBody(req_body)

        status_holder = {}

        def _start_response(status, headers, exc_info=None):
            status_holder["code"] = int(status.split(" ", 1)[0])
            return start_response(status, headers, exc_info)

        try:
            captured = []
            app_iter = self.wsgi_app(environ, _start_response)
            for chunk in app_iter:
                captured.append(chunk)
            if hasattr(app_iter, "close"):
                app_iter.close()
            res_body = b"".join(captured)
        except Exception:
            self.client.report(method=environ.get("REQUEST_METHOD", "GET"), path=path,
                               status_code=500, cost_ms=int((_now() - req_time) * 1000),
                               req_body=req_body.decode("utf-8", "replace")[: self.MAX_BODY],
                               err_type="UPSTREAM_ERROR")
            raise

        code = status_holder.get("code", 200)
        self.client.report(method=environ.get("REQUEST_METHOD", "GET"), path=path,
                           status_code=code, cost_ms=int((_now() - req_time) * 1000),
                           req_body=req_body.decode("utf-8", "replace")[: self.MAX_BODY],
                           res_body=res_body.decode("utf-8", "replace")[: self.MAX_BODY],
                           req_bytes=length, res_bytes=len(res_body),
                           err_type="NONE" if code < 400 else "UPSTREAM_ERROR")
        return captured

    def _excluded(self, path):
        # 与 Java SDK excludePaths、网关 TraceGlobalFilter 排除清单同一组路径
        return path.startswith("/api/trace/") or path.startswith("/api/discovery/services/")


class WSGIAuthMiddleware:
    """WSGI 入口鉴权：app.wsgi_app = WSGIAuthMiddleware(app.wsgi_app, auth)

    验签不通过返回 401/403 JSON；SDK 数据上报为出站调用，不在其列。
    """

    def __init__(self, wsgi_app, auth):
        self.wsgi_app = wsgi_app
        self.auth = auth

    def __call__(self, environ, start_response):
        decision = self.auth.check(environ.get("PATH_INFO", "/"),
                                   environ.get("HTTP_AUTHORIZATION", ""),
                                   environ.get("HTTP_X_API_KEY", ""))
        if decision is not None:
            status, body = decision
            start_response(f"{status} Denied", [("Content-Type", "application/json"),
                                                ("Content-Length", str(len(body.encode("utf-8"))))])
            return [body.encode("utf-8")]
        return self.wsgi_app(environ, start_response)
