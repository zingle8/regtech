# 接入 API 网关平台（ai-gateway）说明

RegTech 平台与 ai-gateway 的关系：**网关是唯一入口与观测面，RegTech 是受管应用**。
不引入 Nacos / 注册中心，服务直连网关，靠 `tb_gw_route` 做转发，靠 bridge 做注册与采集。

## 1. 三个接入动作

### 1.1 实例自动注册（jv-regtech-gateway-bridge）

`jv-regtech-gateway-bridge` 按官方 `jv-gateway-client-starter` 的协议实现，零外部依赖：

| 动作 | 方法 / 路径 | 频率 |
|---|---|---|
| 注册 | `POST {base-url}/discovery/services/register` | 启动一次 |
| 心跳 | `POST {base-url}/discovery/services/heartbeat` | 30s |
| 下线 | `DELETE {base-url}/discovery/services/deregister` | 进程退出 |

注册体（`Payloads.ServiceInstance`）：

```json
{
  "appId": "app-regtech",
  "appName": "NCS RegTech 监管科技",
  "serviceName": "jv-regtech-kb",
  "ip": "127.0.0.1",
  "port": 28102,
  "language": "Java 17",
  "version": "1.0.0",
  "registerTime": "2026-11-04T10:00:00"
}
```

网关侧落地到 `tb_gw_service`，控制台「接入中心 → 服务发现」按健康状态展示。
**网关不可达只打 WARN，不阻塞业务启动。**

### 1.2 调用日志采集（GatewayTraceFilter）

- 位置：`jv-regtech-gateway-bridge/.../GatewayTraceFilter.java`，注册顺序 `HIGHEST_PRECEDENCE + 10`，先于鉴权。
- 采集字段与网关 `TraceLogDTO` 一致：`appId / serviceName / api / traceId / statusCode / cost / reqBody / resBody / reqIp ...`
- 写入方式：`ContentCachingRequestWrapper` + `ContentCachingResponseWrapper` 旁路复制，**不重写响应**。
- 上报方式：内存队列（容量 1024）+ 批量（50 条）+ 定时（3s）POST `{base-url}/trace/logs/batch`；队列满直接丢弃，采集不反压业务。
- 敏感头（`Authorization` / `Cookie`）脱敏为 `******`；SSE 流式接口只记元数据。
- 开关：`regtech.gateway.trace.enabled`；采样率 `sample-rate`（0~1）。

控制台「日志追踪」按 `appId = app-regtech` 或路径 `/api/regtech` 可检索全链路。

### 1.3 路由与接口登记（SQL）

首次执行 `sql/gateway/regtech_register_gateway.sql`：

| path_prefix | upstream_url | prefix_path | strip |
|---|---|---|---|
| `/api/regtech/user/**` | `http://127.0.0.1:28100` | - | 0 |
| `/api/regtech/overview/**` | `http://127.0.0.1:28101` | - | 0 |
| `/api/regtech/kb/**` | `http://127.0.0.1:28102` | - | 0 |
| `/api/regtech/graph/**` | `http://127.0.0.1:28103` | - | 0 |
| `/api/regtech/ontology/**` | `http://127.0.0.1:28104` | - | 0 |
| `/api/regtech/ai/**` | `http://127.0.0.1:28105` | - | 0 |
| `/api/regtech/nlp/**` | `http://127.0.0.1:28110` | - | 0 |
| `/v1/**` | `http://127.0.0.1:28105` | `/api/regtech/ai` | 0 |

由于各服务 `context-path` 与外部路径一致，`strip_prefix=0` 即可，无需重写。
`/v1/**` 用 `prefix_path` 补齐上下文，让外部 OpenAI 客户端直接访问网关。

> 注意：网关的 `StripPrefix` 按**路径段数**裁剪（`StripPrefix=1` 只去掉第一段），
> 不支持按前缀字符串裁剪。因此 Python 服务同样使用 `strip_prefix=0`，
> 由应用自身挂载 `/api/regtech/nlp` 前缀，保证与 Java 服务的 `context-path` 语义一致。

### 1.4 Python 服务接入（py- 前缀）

Python 服务使用 `python/_shared/jv_gateway`（与 ai-gateway 官方 SDK 一致，零第三方依赖）：

```python
from jv_gateway import GatewayClient
from jv_gateway.wsgi import WSGITraceMiddleware

client = GatewayClient(server_addr="http://127.0.0.1:28090/api",
                       app_id="app-regtech", app_name="NCS RegTech 监管科技",
                       service_name="py-regtech-nlp", port=28110)
client.start()                                  # 注册 + 心跳 + 上报
app = WSGITraceMiddleware(app, client)          # WSGI 采集；FastAPI 用 jv_gateway.asgi
```

| 能力 | Java (`jv-regtech-gateway-bridge`) | Python (`jv_gateway`) |
|---|---|---|
| 注册 / 心跳 / 下线 | `GatewayRegistrar` | `GatewayClient.start()/stop()` |
| 请求采集 | `GatewayTraceFilter`（Servlet Filter） | `WSGITraceMiddleware` / `ASGITraceMiddleware` |
| 异步批量上报 | `TraceReporter` | 后台线程 + `queue.Queue` |
| 失败策略 | 丢弃，不阻塞业务 | 丢弃，不阻塞业务 |

注册/采集协议、字段、`errType` 中文标签与 Java 端完全一致，
控制台「服务发现」「日志追踪」可按 `appId = app-regtech` 统一筛选 Java 与 Python 服务。

## 2. 鉴权链路

```
浏览器 --Authorization: Bearer <JWT>--> 网关（透传） --> 服务 AuthInterceptor
```

- JWT 由 `jv-regtech-user` 签发，密钥 `regtech.jwt.secret` 在所有服务一致；
- 各服务只校验签名与有效期，不查库，避免横向依赖；
- 退出登录/改密使 `token_version+1`，旧令牌无法通过用户服务的登录态接口；
- 白名单按服务配置：`regtech.auth.exclude-paths`（登录、健康检查、内部检索/向量接口）。

## 3. 请求路径约定

前端 `baseURL=/api`，模块路径直接以 `/regtech/<service>` 开头：

```
/api/regtech/user/auth/login
/api/regtech/kb/documents
/api/regtech/graph/graph
/api/regtech/ai/v1/chat/completions/stream
```

好处：**后端端口或实例变化只需改网关路由表，前端零改动**。

## 4. 自检清单

```bash
# 1) 服务健康
curl http://127.0.0.1:28102/api/regtech/kb/health

# 2) 经网关访问（观察是否 200 且响应头带 traceId）
curl -i http://127.0.0.1:28090/api/regtech/kb/health

# 3) 网关服务发现
curl "http://127.0.0.1:28090/api/discovery/services?keyword=regtech&current=1&size=20"

# 4) 路由表
mysql -uroot -p -e "SELECT path_prefix,upstream_url,status FROM api_gateway.tb_gw_route WHERE app_id='app-regtech';"

# 5) 调用日志（登录后）
curl "http://127.0.0.1:28090/api/trace/logs?appId=app-regtech&current=1&size=10"
```

常见问题：

| 现象 | 排查 |
|---|---|
| 服务注册不上 | `GATEWAY_BASE_URL` 是否含 `/api`；网关 28090 是否可达；查看服务日志 WARN |
| 经网关 404 | `tb_gw_route` 是否有对应前缀；网关路由缓存 30s，稍等或重启 proxy |
| 经网关 401 | 请求是否带 `Authorization`；服务白名单是否覆盖该路径 |
| 日志追踪无数据 | `regtech.gateway.trace.enabled`；网关 `/api/trace/logs/batch` 是否可用 |
| 流式对话经网关中断 | 确认网关未开启响应缓冲；AI 服务 `spring.mvc.async.request-timeout` |
