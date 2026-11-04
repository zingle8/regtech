#!/usr/bin/env bash
# 编译并后台启动全部微服务
#   Java 服务：jv- 前缀（Maven 多模块）
#   Python 服务：py- 前缀（标准库 WSGI，自带网关注入）
# 前置：MySQL / PostgreSQL(pgvector) / Ollama / ai-gateway 已启动
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mkdir -p .run

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  echo "==> 编译后端（首次会下载依赖）"
  mvn -f backend/pom.xml -q -DskipTests package
fi

start_java() {
  local name="$1" port="$2" ctx="$3"
  local jar="backend/${name}/target/${name}.jar"
  if [ ! -f "$jar" ]; then
    echo "!! 缺少 $jar"
    return 1
  fi
  if [ -f ".run/${name}.pid" ] && kill -0 "$(cat .run/${name}.pid)" 2>/dev/null; then
    echo "-- ${name} 已在运行（pid $(cat .run/${name}.pid)）"
    return 0
  fi
  nohup java -jar "$jar" > ".run/${name}.log" 2>&1 &
  echo $! > ".run/${name}.pid"
  echo "-> ${name} 启动中，端口 ${port}，pid $!"
}

start_py() {
  local name="$1" port="$2" ctx="$3"
  local entry="python/${name}/app.py"
  if [ ! -f "$entry" ]; then
    echo "!! 缺少 $entry"
    return 1
  fi
  if [ -f ".run/${name}.pid" ] && kill -0 "$(cat .run/${name}.pid)" 2>/dev/null; then
    echo "-- ${name} 已在运行（pid $(cat .run/${name}.pid)）"
    return 0
  fi
  PORT="$port" nohup python3 "$entry" > ".run/${name}.log" 2>&1 &
  echo $! > ".run/${name}.pid"
  echo "-> ${name} 启动中，端口 ${port}，pid $!"
}

start_java jv-regtech-user     28100 /api/regtech/user
start_java jv-regtech-overview 28101 /api/regtech/overview
start_java jv-regtech-kb       28102 /api/regtech/kb
start_java jv-regtech-graph    28103 /api/regtech/graph
start_java jv-regtech-ontology 28104 /api/regtech/ontology
start_java jv-regtech-ai       28105 /api/regtech/ai
start_py   py-regtech-nlp      28110 /api/regtech/nlp

SPECS=(
  "28100:/api/regtech/user"
  "28101:/api/regtech/overview"
  "28102:/api/regtech/kb"
  "28103:/api/regtech/graph"
  "28104:/api/regtech/ontology"
  "28105:/api/regtech/ai"
  "28110:/api/regtech/nlp"
)

echo "==> 等待健康检查"
for _ in $(seq 1 40); do
  ok=0
  for spec in "${SPECS[@]}"; do
    port="${spec%%:*}"
    ctx="${spec#*:}"
    if curl -fsS "http://127.0.0.1:${port}${ctx}/health" >/dev/null 2>&1; then
      ok=$((ok + 1))
    fi
  done
  if [ "$ok" -eq "${#SPECS[@]}" ]; then
    echo "全部服务已就绪"
    break
  fi
  sleep 3
done

echo "==> 启动前端开发服务器（http://127.0.0.1:28200）"
cd frontend
[ -d node_modules ] || npm install
npm run dev
