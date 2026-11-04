# Python 服务（`py-` 前缀）

本目录存放平台内所有 **Python 语言微服务**，命名统一 `py-` 前缀；
Java 服务统一 `jv-` 前缀，放在 `backend/`（Maven 多模块）。

所有 Python 服务与 Java 服务一样，**启动即接入 ai-gateway 网关平台**，
被网关监控、转发并采集调用日志，接入协议与 `ai-gateway/sdk/python` 完全一致。

```
python/
├── _shared/jv_gateway/       # 网关 Python SDK（从 ai-gateway/sdk/python 同步，零第三方依赖）
│   ├── client.py             # 注册 / 心跳 / 下线 / 采集 / 批量上报
│   ├── wsgi.py               # WSGI 中间件（Flask / Django / 本项目的 wsgiref）
│   └── asgi.py               # ASGI 中间件（FastAPI / Starlette）
└── py-regtech-nlp/           # 28110 文本分析服务
    └── app.py
```

## 1. 命名与端口

| 服务 | 端口 | 网关路由 | 说明 |
|---|---|---|---|
| `py-regtech-nlp` | 28110 | `/api/regtech/nlp/**` → `http://127.0.0.1:28110` | 关键词抽取、中文切分、文本相似度 |

新增 Python 服务时：

1. 目录名 = 服务名 = `py-xxx`；
2. `GatewayClient(service_name="py-xxx", port=<端口>)`；
3. `sql/gateway/regtech_register_gateway.sql` 增补 `tb_gw_service / tb_gw_api / tb_gw_route` 三处；
4. `scripts/start-all.sh`、`stop-all.sh`、`status.sh` 的列表加上该服务。

## 2. 网关接入（与 Java 同构）

```python
client = GatewayClient(
    server_addr=os.environ.get("GATEWAY_BASE_URL", "http://127.0.0.1:28090/api"),
    app_id="app-regtech",
    app_name="NCS RegTech 监管科技",
    service_name="py-regtech-nlp",
    port=28110,
)
client.start()                                          # 注册 + 心跳线程 + 上报线程
app = WSGITraceMiddleware(app, client)                  # WSGI 采集
# FastAPI：app.add_middleware(ASGITraceMiddleware, client=client)
```

- **注册**：`POST {server_addr}/discovery/services/register`，每 30s 心跳，`stop()` 时 `DELETE .../deregister`
- **采集**：中间件记录 method/path/status/cost/报文（超 8KB 截断），`errType` 按状态码推断
- **上报**：内存队列 + 后台线程每 3s 或满 50 条 `POST {server_addr}/trace/logs/batch`，失败即丢弃，绝不影响业务

对照 Java 版：`jv-regtech-gateway-bridge` 的 `GatewayRegistrar` / `GatewayTraceFilter` / `TraceReporter`。

## 3. py-regtech-nlp 接口

统一响应 `{code, message, data}`，直连或经网关访问均可：

```bash
# 直连
curl http://127.0.0.1:28110/api/regtech/nlp/health

# 经网关（需登录令牌）
curl http://127.0.0.1:28090/api/regtech/nlp/health -H "Authorization: Bearer <token>"
```

| 方法 | 路径 | 入参 | 出参 |
|---|---|---|---|
| GET | `/api/regtech/nlp/health` | — | `{service,status,language,port,uptime}` |
| GET | `/api/regtech/nlp/stats` | — | `{requests,byPath,uptime,startedAt}` |
| POST | `/api/regtech/nlp/keywords` | `{"text":"...","topK":10}` | `{keywords:[{word,count,weight}],length}` |
| POST | `/api/regtech/nlp/segment` | `{"text":"...","limit":200}` | `{tokens:[...]}` |
| POST | `/api/regtech/nlp/similarity` | `{"a":"...","b":"..."}` | `{score,jaccard,lcs,lengthA,lengthB}` |

```bash
curl -X POST http://127.0.0.1:28090/api/regtech/nlp/keywords \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  -d '{"text":"经营机构应当履行投资者适当性义务，做好适当性管理","topK":5}'
```

## 4. 单独启动

```bash
PORT=28110 python3 python/py-regtech-nlp/app.py
# 或随全平台启动
./scripts/start-all.sh
```

环境变量：`GATEWAY_BASE_URL`、`GATEWAY_IP`、`PORT`、`SERVICE_NAME`、`APP_ID`、`APP_NAME`。
