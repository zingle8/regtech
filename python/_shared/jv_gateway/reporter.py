"""调用日志 / span 异步批量上报：队列满直接丢弃，绝不阻塞业务线程（与 Java SDK 同语义）。"""

import logging
import queue
import threading

from ._http import post_json

log = logging.getLogger("jv_gateway")

PATH_LOG_BATCH = "/trace/logs/batch"
PATH_SPAN_BATCH = "/trace/spans/batch"


class TraceReporter:
    """内存队列 + 后台线程批量上报。start() 启动，stop() 刷出剩余并退出。"""

    def __init__(self, server_addr, client_token="", report_interval=3.0, queue_size=1024,
                 batch_size=50, timeout=5.0):
        self._url_log = server_addr.rstrip("/") + PATH_LOG_BATCH
        self._url_span = server_addr.rstrip("/") + PATH_SPAN_BATCH
        self._client_token = client_token
        self._interval = max(1.0, report_interval)
        self._batch_size = batch_size
        self._timeout = timeout
        self._q = queue.Queue(maxsize=queue_size)
        self._stop = threading.Event()
        self._thread = None

    def start(self):
        if self._thread is None:
            self._thread = threading.Thread(target=self._loop, name="jv-gateway-reporter", daemon=True)
            self._thread.start()

    def stop(self, flush_timeout=5.0):
        if self._thread is None:
            return
        self._drain()
        self._stop.set()
        self._thread.join(timeout=flush_timeout)
        self._thread = None

    def offer_log(self, record):
        self._offer(record)

    def offer_span(self, span):
        self._offer(span)

    # ------------------------------------------------------------------
    def _offer(self, item):
        try:
            self._q.put_nowait(item)
        except queue.Full:
            log.debug("上报队列已满，丢弃一条记录")  # 与 Java SDK 一致：满即丢

    def _loop(self):
        while not self._stop.is_set():
            self._stop.wait(self._interval)
            if not self._stop.is_set():
                self._drain()

    def _drain(self):
        logs, spans = [], []
        while True:
            try:
                item = self._q.get_nowait()
            except queue.Empty:
                break
            if "spanId" in item or "kind" in item:
                spans.append(item)
            else:
                logs.append(item)
            if len(logs) >= self._batch_size:
                self._flush(self._url_log, logs)
                logs = []
            if len(spans) >= self._batch_size:
                self._flush(self._url_span, spans)
                spans = []
        if logs:
            self._flush(self._url_log, logs)
        if spans:
            self._flush(self._url_span, spans)

    def _flush(self, url, items):
        try:
            status, body = post_json(url, items, self._client_token, self._timeout)
            if status >= 300:
                log.warning("上报失败 %s: HTTP %s", url, status)
        except Exception as e:  # 网络/平台不可用：仅告警，下个周期重试
            log.warning("上报异常 %s: %s", url, e)
