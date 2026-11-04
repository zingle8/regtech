# 接口清单

所有接口经网关：`http://127.0.0.1:28090`，下表路径为网关侧完整路径。
除标注「公开」外均需 `Authorization: Bearer <token>`。响应统一 `{ code, message, data }`。

## 1. 用户 jv-regtech-user

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/regtech/user/auth/login` | 公开。登录，返回 token |
| POST | `/api/regtech/user/auth/logout` | 退出，旧令牌立即失效 |
| GET | `/api/regtech/user/auth/me` | 当前用户 |
| GET | `/api/regtech/user/users` | 分页：`keyword/role/status/current/size` |
| POST | `/api/regtech/user/users` | 新增 |
| PUT | `/api/regtech/user/users/{id}` | 编辑（带 password 则重置并失效旧令牌） |
| PUT | `/api/regtech/user/users/{id}/status` | 启停 |
| PUT | `/api/regtech/user/users/{id}/password` | 重置密码 |
| DELETE | `/api/regtech/user/users/{id}` | 逻辑删除 |

## 2. 总览 jv-regtech-overview

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/regtech/overview/summary` | 聚合指标（用户/会话/知识库/图谱/本体/Ollama） |
| GET | `/api/regtech/overview/trend?days=14` | 问答趋势（空日期补 0） |

## 3. 知识库 jv-regtech-kb

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/regtech/kb/datasets` | 数据集列表 |
| POST | `/api/regtech/kb/datasets` | 新建数据集 |
| PUT/DELETE | `/api/regtech/kb/datasets/{id}` | 修改 / 删除（级联文档分块） |
| GET | `/api/regtech/kb/documents` | 分页：`datasetId/keyword/current/size` |
| GET | `/api/regtech/kb/documents/{id}` | 文档详情（含正文） |
| POST | `/api/regtech/kb/documents` | 入库：切分 + 批量向量化 |
| DELETE | `/api/regtech/kb/documents/{id}` | 删除文档与分块 |
| POST | `/api/regtech/kb/search` | 检索：`{query,datasetId,topK,mode}`，向量失败自动降级关键词 |
| POST | `/api/regtech/kb/rag/ask` | RAG：`{question,datasetId,topK}` → `{answer,sources,mode,cost}` |
| GET | `/api/regtech/kb/articles` | 条款分页 |
| POST | `/api/regtech/kb/articles` | 新增条款（自动向量化） |
| GET/PUT/DELETE | `/api/regtech/kb/articles/{id}` | 详情 / 修改 / 删除 |
| GET | `/api/regtech/kb/articles/changes?limit=12` | 条文变更动态：`[{action,lawName,articleNo,title,summary,operatorName,operateTime}]`（新增/更新/删除留痕） |
| POST | `/api/regtech/kb/articles/search` | 混合检索：`{query,jurisdiction,topK}` |
| POST | `/api/regtech/kb/diff/compare` | 对比：`{leftId,rightId}` → diff 块 + 相似度 + 双方出处 |
| GET | `/api/regtech/kb/diff/jurisdictions` | 可选法域及条文数：`[{jurisdiction,total}]` |
| POST | `/api/regtech/kb/diff/matrix` | 多地域/多场景交叉对比：`{jurisdictions,scenarios,topK,explain}` → 场景 × 法域矩阵、跨域相似度、知识库出处、可选 AI 解读 |
| GET | `/api/regtech/kb/diff/records` | 对比历史 |
| GET/DELETE | `/api/regtech/kb/diff/records/{id}` | 详情 / 删除 |
| GET | `/api/regtech/kb/stats` | 公开（内部聚合）：数据集/文档/分块/条款/差异/法域分布 |

## 4. 图谱 jv-regtech-graph

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/regtech/graph/graph` | ECharts 图数据：`keyword/category/limit` |
| GET | `/api/regtech/graph/nodes` | 节点列表 |
| GET/PUT/DELETE | `/api/regtech/graph/nodes/{id}` | 节点详情 / 修改 / 删除（含边） |
| POST | `/api/regtech/graph/nodes` | 新增节点 |
| POST | `/api/regtech/graph/edges` | 新增关系 |
| DELETE | `/api/regtech/graph/edges/{id}` | 删除关系 |
| POST | `/api/regtech/graph/build?limit=200` | 从法条库重建图谱（清空后重建） |
| GET | `/api/regtech/graph/stats` | 公开（内部聚合）：节点/关系/分类分布 |

## 5. 本体 jv-regtech-ontology

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/regtech/ontology/classes` | 本体类树 |
| POST | `/api/regtech/ontology/classes` | 新增类 |
| PUT/DELETE | `/api/regtech/ontology/classes/{id}` | 修改 / 删除（有子类时拒绝） |
| GET/POST | `/api/regtech/ontology/properties` | 属性列表 / 新增 |
| DELETE | `/api/regtech/ontology/properties/{id}` | 删除属性 |
| GET/POST | `/api/regtech/ontology/individuals` | 个体列表 / 新增 |
| PUT/DELETE | `/api/regtech/ontology/individuals/{id}` | 修改 / 删除 |
| POST | `/api/regtech/ontology/rag/ask` | 本体 RAG：`{question,topK}` → `{answer,ontologyHits,expansions,sources}` |
| GET | `/api/regtech/ontology/stats` | 公开（内部聚合） |

## 6. AI jv-regtech-ai

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/regtech/ai/models` | 本地 Ollama 模型（OpenAI 格式）+ 默认模型 + 在线状态 |
| POST | `/api/regtech/ai/v1/chat/completions` | 非流式对话（OpenAI 响应结构） |
| POST | `/api/regtech/ai/v1/chat/completions/stream` | SSE 流式；首帧 `{type:meta,conversationId}`，增量 `{type:delta}`，结束 `[DONE]` |
| POST | `/api/regtech/ai/v1/embeddings` | 文本向量化（OpenAI 结构） |
| GET | `/api/regtech/ai/conversations` | 会话列表 |
| POST | `/api/regtech/ai/conversations` | 新建会话 |
| GET | `/api/regtech/ai/conversations/{id}/messages` | 消息历史 |
| PUT/DELETE | `/api/regtech/ai/conversations/{id}` | 重命名 / 删除 |
| GET | `/api/regtech/ai/stats` | 公开（内部聚合）：会话/消息/模型分布/Ollama |
| POST | `/api/regtech/ai/internal/chat` | 公开（内部调用）：知识库与本体 RAG 使用 |
| POST | `/api/regtech/ai/embeddings` | 公开（内部调用）：批量向量化 |

## 7. 文本分析 py-regtech-nlp（Python）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/regtech/nlp/health` | 健康检查：`{service,status,language,port,uptime}` |
| GET | `/api/regtech/nlp/stats` | 运行时统计：`{requests,byPath,uptime,startedAt}` |
| POST | `/api/regtech/nlp/keywords` | `{text,topK}` → `{keywords:[{word,count,weight}],length}` |
| POST | `/api/regtech/nlp/segment` | `{text,limit}` → `{tokens:[...]}` |
| POST | `/api/regtech/nlp/similarity` | `{a,b}` → `{score,jaccard,lcs,lengthA,lengthB}` |

Python 服务与 Java 服务共用网关路由、鉴权（JWT 透传）与调用日志采集，
语言字段在服务发现中显示为 `Python 3.x`。

## 8. 健康检查

`GET {context-path}/health`（公开），返回服务名、端口、状态、时间（Java 与 Python 一致）。
