# 环境配置说明

> 本文档汇总平台所有服务、数据库、中间件的连接配置。
> 以下账号密码均为**本地开发环境默认值**（写在 `application.yml` 的 `${ENV:默认值}` 回退项里），生产部署时必须通过环境变量覆盖，不要使用默认口令。

## 1. 中间件总览

| 中间件 | 地址 | 账号 / 密码 | 数据库 | 用途 |
|---|---|---|---|---|
| MySQL | `127.0.0.1:3306` | `root` / `Root@123456` | `regtech_platform` | 用户、会话、AI 对话、总览指标 |
| MySQL（网关） | `127.0.0.1:3306` | `root` / `Root@123456` | `api_gateway` | ai-gateway 网关平台（服务注册 / 接口登记 / 调用日志） |
| PostgreSQL | `127.0.0.1:5432` | `postgres` / `Pg@123456` | `regtech_kb` | 知识库、条文、图谱、本体（已启用 `pgvector` 扩展） |
| Ollama | `http://127.0.0.1:11434` | 无鉴权 | — | 本地大模型：对话 `gpt-oss:20b`、向量 `qwen3-embedding:0.6b` |
| ai-gateway | 代理 `http://127.0.0.1:28090`，控制台 `http://127.0.0.1:28080` | 网关控制台账号见网关平台 | 依赖上面 `api_gateway` 库 | 统一入口、鉴权、服务注册与调用采集（外部平台，不在本仓库） |

数据库初始化脚本：

| 库 | 脚本 |
|---|---|
| MySQL `regtech_platform` | `sql/mysql/regtech_platform.sql` |
| PostgreSQL `regtech_kb` | `sql/postgres/postgresql-regtech_knowledge.sql` |
| MySQL `api_gateway`（网关接口登记） | `sql/gateway/regtech_register_gateway.sql` |

## 2. 服务端口清单

| 服务 | 端口 | 上下文 | 语言/框架 | 数据库 |
|---|---|---|---|---|
| jv-regtech-user | 28100 | `/api/regtech/user` | Java / Spring Boot | MySQL `regtech_platform` |
| jv-regtech-overview | 28101 | `/api/regtech/overview` | Java / Spring Boot | MySQL `regtech_platform` |
| jv-regtech-kb | 28102 | `/api/regtech/kb` | Java / Spring Boot | PostgreSQL `regtech_kb` |
| jv-regtech-graph | 28103 | `/api/regtech/graph` | Java / Spring Boot | PostgreSQL `regtech_kb` |
| jv-regtech-ontology | 28104 | `/api/regtech/ontology` | Java / Spring Boot | PostgreSQL `regtech_kb` |
| jv-regtech-ai | 28105 | `/api/regtech/ai` | Java / Spring Boot | MySQL `regtech_platform` + Ollama |
| py-regtech-nlp | 28110 | `/api/regtech/nlp` | Python 3 / WSGI（纯标准库） | 无（无状态） |
| frontend portal | 28200 | `/`（dev，`/api` 代理到 28090） | Vue3 + Vite | 无 |
| **统一入口（经网关）** | **28090** | `/api/regtech/**` | — | — |

> 所有服务启动后自动注册到 ai-gateway（`app-regtech` 应用），外部统一走 `http://127.0.0.1:28090/api/regtech/**`，也可绕过网关直连各服务端口。

## 3. 各服务数据源配置（`application.yml`）

所有配置均支持环境变量覆盖，格式 `${变量名:默认值}`：

### MySQL 连接（user / overview / ai）

```yaml
spring.datasource:
  driver-class-name: com.mysql.cj.jdbc.Driver
  url:      ${MYSQL_URL:jdbc:mysql://127.0.0.1:3306/regtech_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true}
  username: ${MYSQL_USERNAME:root}
  password: ${MYSQL_PASSWORD:Root@123456}
```

### PostgreSQL 连接（kb / graph / ontology）

```yaml
spring.datasource:
  driver-class-name: org.postgresql.Driver
  url:      ${PG_URL:jdbc:postgresql://127.0.0.1:5432/regtech_kb}
  username: ${PG_USERNAME:postgres}
  password: ${PG_PASSWORD:Pg@123456}
  hikari:   # 连接池（各服务相同）
    maximum-pool-size: 4
    minimum-idle: 1
    connection-timeout: 5000
```

### 模型与网关（按服务）

| 环境变量 | 默认值 | 使用方 |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | jv-regtech-ai |
| `OLLAMA_EMBED_MODEL` | `qwen3-embedding:0.6b` | jv-regtech-kb（条文/文档向量化） |
| `GATEWAY_BASE_URL` | `http://127.0.0.1:28090/api` | 全部服务（网关注册与转发） |
| `REGTECH_JWT_SECRET` | `ai-ncs-regtech-jwt-secret-key-change-me-please` | 全部 Java 服务（JWT 签名，**生产必须替换**） |
| `AI_BASE` | `http://127.0.0.1:28105/api/regtech/ai` | jv-regtech-kb（调用 AI 生成解读） |
| `SERVICE_NAME` / `PORT` | `py-regtech-nlp` / `28110` | py-regtech-nlp |

## 4. 应用账号

| 系统 | 账号 | 密码 | 说明 |
|---|---|---|---|
| 平台管理台（前端登录） | `admin` | `Admin@123` | 初始化数据内置，见 `sql/mysql/regtech_platform.sql` |
| ai-gateway 控制台 | 以网关平台部署为准 | — | 不属于本仓库 |

## 5. 环境变量覆盖示例

```bash
# 例：切换 PostgreSQL 地址与密码
export PG_URL=jdbc:postgresql://10.0.0.8:5432/regtech_kb
export PG_USERNAME=regtech
export PG_PASSWORD='生产环境强口令'

# 例：切换 MySQL 与 JWT 密钥
export MYSQL_URL='jdbc:mysql://10.0.0.9:3306/regtech_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
export MYSQL_USERNAME=regtech
export MYSQL_PASSWORD='生产环境强口令'
export REGTECH_JWT_SECRET='每环境独立的随机长字符串'

# 之后正常启动（脚本会继承环境变量）
bash scripts/start-all.sh
```

## 6. 安全提示

1. 本文件与 `application.yml` 中的口令均为**本地开发默认值**；仓库若公开或配置用于共享/生产环境，请务必：
   - 修改数据库口令，并通过环境变量注入，不要写回代码；
   - 更换 `REGTECH_JWT_SECRET`；
   - 修改 `admin` 初始密码。
2. 数据库端口（3306 / 5432）不要直接暴露公网；生产环境建议加白名单或走内网。
3. Ollama 无鉴权，仅限本机/内网使用。
