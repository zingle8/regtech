#!/usr/bin/env bash
# 停止全部微服务（Java jv-* / Python py-*）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SERVICES=(
  jv-regtech-user
  jv-regtech-overview
  jv-regtech-kb
  jv-regtech-graph
  jv-regtech-ontology
  jv-regtech-ai
  py-regtech-nlp
)

for name in "${SERVICES[@]}"; do
  pid_file=".run/${name}.pid"
  if [ -f "$pid_file" ]; then
    pid="$(cat "$pid_file")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid"
      echo "已停止 ${name}（pid ${pid}）"
    fi
    rm -f "$pid_file"
  fi
done
