# NCS RegTech · 监管科技平台

面向金融合规团队的一体化监管科技平台：**法规知识库（pgvector） + 法律知识图谱 + 本体论 RAG + 本地大模型问答 + 条文差异对比**。

- 后端：Java 17 / Spring Boot 3.2 / 原生 MyBatis / MySQL + PostgreSQL(pgvector)
- Python：标准库 WSGI 服务（零第三方依赖），同一套网关接入协议
- 前端：Vue 3 / Vite / Element Plus / ECharts（npm workspaces 模块化）
- 接入：全部服务与接口注册进 **ai-gateway API 网关平台**，统一入口、统一鉴权、统一调用采集

**命名约定（强制）**

| 语言 | 前缀 | 范围 | 示例 |
|---|---|---|---|
| Java | `jv-` | 服务名、Maven 子模块名、`spring.application.name`、网关注册的 `service_name` | `jv-regtech-kb` |
| Python | `py-` | 服务名、服务目录名、网关注册的 `service_name` | `py-regtech-nlp` |
| 前端 | 不强制 | 目录与文件名保持业务语义 | `modules/kb` |
| PostgreSQL 脚本 | `postgresql-` | `sql/postgres/` 下的 DDL/初始化脚本 | `postgresql-regtech_knowledge.sql` |
| MySQL / 网关脚本 | 不强制 | 保持业务语义 | `regtech_platform.sql` |

> 路由前缀 `/api/regtech/**` 属于 URL 路径（对外契约），不随服务名变化。

---

## 1. 架构

```
                        ┌─────────────────────────────────────────────┐
  浏览器  ── /api/** ──▶ │  ai-gateway  jv-gateway-proxy :28090        │
                        │  · 路由转发（tb_gw_route）                  │
                        │  · 鉴权 / 限流 / 熔断 / 调用日志采集         │
                        └───────────────┬─────────────────────────────┘
                                        │ 按 /api/regtech/** 分发
        ┌───────────────┬───────────────┼───────────────┬───────────────┬──────────────┐
        ▼               ▼               ▼               ▼               ▼              ▼
  jv-regtech-user   jv-regtech-overview   jv-regtech-kb     jv-regtech-graph  jv-regtech-ontology  jv-regtech-ai
    :28100           :28101          :28102          :28103          :28104         :28105
  登录/用户        统计聚合       知识库/条文/差异    知识图谱          本体 RAG      OpenAI 兼容
      │                │               │               │               │              │
   MySQL           MySQL + HTTP     PostgreSQL      PostgreSQL      PostgreSQL      MySQL + Ollama
                                  (pgvector 1024)   (kg_node/edge)  (ontology_*)    :11434

  另有一个 Python 服务（同类网关注册 + 采集）：
  py-regtech-nlp :28110  →  文本分析：关键词抽取 / 中文切分 / 相似度
```

Java 服务引入 `jv-regtech-gateway-bridge`、Python 服务引入 `python/_shared/jv_gateway`
（两者与网关官方 SDK 协议一致）：
启动自动注册实例 → 周期心跳 → 退出下线 → 请求级调用日志异步上报。

## 2. 服务与端口

| 服务 | 端口 | Context Path | 数据源 | 职责 |
|---|---|---|---|---|
| jv-regtech-user | 28100 | `/api/regtech/user` | MySQL | 登录、JWT、用户与角色 |
| jv-regtech-overview | 28101 | `/api/regtech/overview` | MySQL + HTTP | 总览指标聚合、条文变更动态 |
| jv-regtech-kb | 28102 | `/api/regtech/kb` | PostgreSQL | 知识库、文档切分向量化、条文检索、RAG、差异对比 |
| jv-regtech-graph | 28103 | `/api/regtech/graph` | PostgreSQL | 图谱节点/关系、自动构图 |
| jv-regtech-ontology | 28104 | `/api/regtech/ontology` | PostgreSQL | 本体建模、概念扩展 RAG |
| jv-regtech-ai | 28105 | `/api/regtech/ai` | MySQL + Ollama | OpenAI 兼容对话/向量、会话留存 |
| **py-regtech-nlp** | **28110** | `/api/regtech/nlp` | 无 | 关键词抽取、中文切分、文本相似度（Python 3.11） |
| 前端 portal | 28200 | — | — | 管理台（`/api` 代理到 28090） |

## 3. 目录结构

```
project_ai-ncs-regtech/
├── backend/
│   ├── pom.xml                     # 聚合父 POM
│   ├── jv-regtech-common/             # 统一响应/异常/分页/JWT/鉴权拦截器/RestClient
│   ├── jv-regtech-gateway-bridge/     # 网关接入桥：注册、心跳、调用日志采集上报
│   ├── jv-regtech-user/               # 28100
│   ├── jv-regtech-overview/           # 28101
│   ├── jv-regtech-kb/                 # 28102
│   ├── jv-regtech-graph/              # 28103
│   ├── jv-regtech-ontology/           # 28104
│   └── jv-regtech-ai/                 # 28105
├── python/
│   ├── _shared/jv_gateway/          # 网关 Python SDK（注册/心跳/采集，零依赖）
│   └── py-regtech-nlp/              # 28110 文本分析服务（app.py）
├── frontend/
│   ├── packages/shared/            # 请求封装、用户态、格式化、样式令牌
│   ├── packages/ui/                # Shell / Sidebar / Header / StatCard
│   ├── modules/                    # overview / kb / graph / ai / diff / ontology / system
│   └── apps/portal/                # 应用装配、路由、登录页
├── sql/
│   ├── mysql/regtech_platform.sql  # 业务库 + 内置管理员
│   ├── postgres/postgresql-regtech_knowledge.sql # pgvector 表结构与初始化法规数据
│   └── gateway/regtech_register_gateway.sql # 网关：服务/接口/路由注册
├── scripts/                        # init-db / start-all / stop-all / status
└── docs/                           # 网关接入、接口清单
```

## 4. 快速开始

### 4.1 前置依赖

| 依赖 | 说明 |
|---|---|
| JDK 17、Maven 3.9+ | 后端（Java 服务） |
| Python 3.8+ | Python 服务，仅标准库，无需 pip 安装任何依赖 |
| Node 18+ | 前端 |
| MySQL 5.7+ | `regtech_platform`（业务）+ `api_gateway`（网关平台） |
| PostgreSQL 16 + pgvector | `regtech_kb`，推荐镜像 `pgvector/pgvector:pg16` |
| Ollama | 本地模型，默认 `gpt-oss:20b` + `qwen3-embedding:0.6b` |
| ai-gateway | 已启动 28080 控制台 / 28090 代理 |

```bash
# PostgreSQL（pgvector）
docker run -d --name regtech-pg \
  -e POSTGRES_PASSWORD=Pg@123456 -e POSTGRES_DB=regtech_kb \
  -p 5432:5432 pgvector/pgvector:pg16

# Ollama 模型
ollama serve
ollama pull gpt-oss:20b
ollama pull qwen3-embedding:0.6b
```

### 4.2 初始化数据库

```bash
chmod +x scripts/*.sh
./scripts/init-db.sh          # 可用 MYSQL_PASS / PG_PASS 等环境变量覆盖
```

### 4.3 启动

```bash
./scripts/start-all.sh        # 编译后端 → 起 6 个 Java + 1 个 Python 服务 → 起前端 dev
./scripts/status.sh           # 查看健康状态与网关注册结果
./scripts/stop-all.sh         # 停止后端
```

访问入口、登录账号、端口与中间件凭据见 **第 5 节 启动信息与登录账号**。

## 5. 启动信息与登录账号

### 5.1 访问入口

| 用途 | 地址 | 说明 |
|---|---|---|
| 前端管理台 | <http://127.0.0.1:28200> | 登录页，开发模式（Vite，`/api` 代理到 28090） |
| 网关控制台 | <http://127.0.0.1:28080> | 接入中心 / 服务发现 / 日志追踪 |
| 统一 API 入口 | <http://127.0.0.1:28090> | 网关代理，前端与外部调用统一走这里 |
| OpenAI 兼容入口 | `http://127.0.0.1:28090/v1` | `/v1/chat/completions`、`/v1/embeddings` |

### 5.2 登录账号

| 账号 | 密码 | 角色 | 说明 |
|---|---|---|---|
| `admin` | `Admin@123` | ADMIN | 内置平台管理员，登录页已预填 |

- 登录接口：`POST http://127.0.0.1:28090/api/regtech/user/auth/login`，请求体 `{"username":"admin","password":"Admin@123"}`
- 令牌：JWT，置于 `Authorization: Bearer <token>`，有效期 8 小时（`regtech.jwt.expire-hours`）
- 退出登录 / 改密会递增 `token_version`，旧令牌立即失效；可在「平台管理 → 用户管理」新增账号、启停、重置密码
- 账号来源：`sql/mysql/regtech_platform.sql`（BCrypt 密文）；若用户表为空，`jv-regtech-user` 的 `AdminInitializer` 会在启动时自动创建该管理员

### 5.3 服务与端口

| 服务 | 端口 | 健康检查 | 语言 |
|---|---|---|---|
| jv-regtech-user | 28100 | `GET /api/regtech/user/health` | Java 17 |
| jv-regtech-overview | 28101 | `GET /api/regtech/overview/health` | Java 17 |
| jv-regtech-kb | 28102 | `GET /api/regtech/kb/health` | Java 17 |
| jv-regtech-graph | 28103 | `GET /api/regtech/graph/health` | Java 17 |
| jv-regtech-ontology | 28104 | `GET /api/regtech/ontology/health` | Java 17 |
| jv-regtech-ai | 28105 | `GET /api/regtech/ai/health` | Java 17 |
| py-regtech-nlp | 28110 | `GET /api/regtech/nlp/health` | Python 3.11 |
| 前端 portal | 28200 | — | Vue 3 |

健康检查为公开接口，直连端口或经网关 `28090` 均可。

### 5.4 中间件与依赖凭据

| 组件 | 地址 | 账号 / 凭据 | 库 / 模型 |
|---|---|---|---|
| MySQL 5.7+ | `127.0.0.1:3306` | `root` / `Root@123456` | `regtech_platform`（业务）、`api_gateway`（网关） |
| PostgreSQL 16 + pgvector | `127.0.0.1:5432` | `postgres` / `Pg@123456` | `regtech_kb` |
| Ollama | `http://127.0.0.1:11434` | — | 对话 `gpt-oss:20b`、向量 `qwen3-embedding:0.6b`（1024 维） |
| ai-gateway | 控制台 28080 / 代理 28090 | — | 应用编码 `app-regtech` |

默认值均可用环境变量覆盖：

```bash
# 数据库
MYSQL_URL / MYSQL_USERNAME / MYSQL_PASSWORD
PG_URL / PG_USERNAME / PG_PASSWORD          # 各 PG 服务 application.yml 内

# 模型
OLLAMA_BASE_URL / OLLAMA_CHAT_MODEL / OLLAMA_EMBED_MODEL

# 网关（Java 与 Python 通用）
GATEWAY_ENABLED=false                       # 关闭注册与采集
GATEWAY_BASE_URL=http://127.0.0.1:28090/api
GATEWAY_IP=127.0.0.1

# JWT（所有服务必须一致）
REGTECH_JWT_SECRET=ai-ncs-regtech-jwt-secret-key-change-me-please
```

### 5.5 常用命令与冒烟测试

```bash
# 启停与状态
./scripts/start-all.sh        # 编译 + 起 7 个后端服务 + 前端 dev（阻塞）
SKIP_BUILD=1 ./scripts/start-all.sh   # 跳过编译
./scripts/status.sh           # 健康状态 + 网关服务注册表
./scripts/stop-all.sh

# 健康检查（公开，无需令牌）
curl http://127.0.0.1:28100/api/regtech/user/health
curl http://127.0.0.1:28090/api/regtech/nlp/health

# 登录拿令牌
TOKEN=$(curl -s -X POST http://127.0.0.1:28090/api/regtech/user/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

# 带令牌调用业务接口
curl -s http://127.0.0.1:28090/api/regtech/overview/summary -H "Authorization: Bearer $TOKEN"
curl -s http://127.0.0.1:28090/api/regtech/kb/datasets -H "Authorization: Bearer $TOKEN"
```

未携带令牌访问受保护接口返回 `{"code":401,"message":"未登录或登录已过期"}`。

## 6. 接入 API 网关平台（重点）

三层接入，互不依赖，缺一不可：

1. **应用/服务注册**：`jv-regtech-gateway-bridge`（Java）/ `python/_shared/jv_gateway`（Python）启动时 POST `/discovery/services/register`，
   每 30s 心跳，退出 DELETE 下线。
   网关「接入中心 → 服务发现」可看到 `jv-regtech-*` 六个 + `py-regtech-nlp` 一个，共七个实例与健康状态。
2. **接口登记**：`sql/gateway/regtech_register_gateway.sql` 写入 `tb_gw_service / tb_gw_api / tb_gw_route`，
   服务详情页展示接口清单，转发规则 30s 内热加载。
3. **流量入口**：前端所有请求走 `/api`（28090），网关按路由分发并记录调用日志；
   OpenAI 兼容入口额外暴露 `/v1/**`，通过 `prefix_path=/api/regtech/ai` 重写。

Python 服务的接入方式（与 Java 完全同构，不依赖任何第三方库）：

```python
import sys
sys.path.insert(0, "python/_shared")
from jv_gateway import GatewayClient
from jv_gateway.wsgi import WSGITraceMiddleware

client = GatewayClient(server_addr="http://127.0.0.1:28090/api",
                       app_id="app-regtech", app_name="NCS RegTech 监管科技",
                       service_name="py-regtech-nlp", port=28110)
client.start()                                   # 注册 + 心跳 + 上报线程
# WSGI：app = WSGITraceMiddleware(app, client)；ASGI：app.add_middleware(ASGITraceMiddleware, client=client)
```

路由行保持 `strip_prefix=0`、`prefix_path=NULL`，让上游收到的仍是完整
`/api/regtech/nlp/**`，与 Java 服务的 `context-path` 语义保持一致
（网关的 `StripPrefix` 只按「段数」裁剪，不按前缀字符串裁剪）。

关闭采集或改网关地址（每个服务均支持环境变量）：

```bash
GATEWAY_ENABLED=false
GATEWAY_BASE_URL=http://127.0.0.1:28090/api
```

细节见 [docs/gateway-integration.md](docs/gateway-integration.md)。

## 7. 功能页面

| 页面 | 路径 | 说明 |
|---|---|---|
| 数据总览 | `/overview` | 法规/知识库/图谱/问答指标，问答趋势、法域分布、条文变更动态 |
| 法律知识库 | `/kb` | 数据集与文档管理、上传入库（自动切分 + 向量化）、语义检索、RAG 溯源问答 |
| 条文检索 | `/articles` | 法规条款混合检索（向量 + 关键词），条款维护 |
| 条文对比 | `/diff` | 多地域对比 / 多场景对比：按场景在各法域召回条文，给出跨域语义相似度、逐条 LCS 精读与知识库数据集 / 文档出处 |
| 法律知识图谱 | `/graph` | 法规→条款→主题关系网络，节点详情、邻接关系、一键从法条库构图 |
| AI 问答 | `/ai` | OpenAI 格式流式对话，模型选择、会话历史；可挂载平台知识库（指定数据集或全库）做检索增强，回答展示引用资料与相似度 |
| 差异对比 | `/diff` | 两条法规逐行 LCS 比对，修订类型与相似度，历史留痕 |
| 本体论 RAG | `/ontology` | 本体类/属性/个体建模，概念扩展检索 + 结构化溯源 |
| 用户管理 | `/system/users` | 账号、角色、启停、重置密码 |

## 8. OpenAI 兼容接口

```bash
# 模型列表（需登录令牌）
curl http://127.0.0.1:28090/api/regtech/ai/models -H "Authorization: Bearer <token>"

# 流式对话
curl -N http://127.0.0.1:28090/api/regtech/ai/v1/chat/completions/stream \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  -d '{"model":"gpt-oss:20b","messages":[{"role":"user","content":"适当性管理的要求？"}],"stream":true}'

# 通过网关的 OpenAI 别名
curl http://127.0.0.1:28090/v1/chat/completions \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  -d '{"model":"gpt-oss:20b","messages":[{"role":"user","content":"你好"}]}'
```

## 9. 常见问题

| 现象 | 处理 |
|---|---|
| 页面提示 Ollama 未连接 | `ollama serve`；核对 `OLLAMA_BASE_URL`；`ollama pull` 模型 |
| 检索只有关键词结果 | 向量模型未安装或 AI 服务未启动，`/embeddings` 失败会自动降级 |
| 图谱为空 | 打开图谱页点「从法条库构图」，或 `POST /api/regtech/graph/build` |
| 网关里看不到服务 | 检查 `GATEWAY_BASE_URL` 是否指向 28090；查看服务日志 `对接 API 网关平台失败` |
| 上传文档 500 | 确认 pgvector 扩展已安装、`embedding` 列为 `vector(1024)` |
| 401 | 令牌 8 小时过期；退出登录会使旧令牌立即失效 |
