#!/usr/bin/env bash
# 查看各服务状态与网关注册情况
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

printf "%-22s %-8s %-10s %s\n" "SERVICE" "PORT" "HEALTH" "PID"
for name in jv-regtech-user jv-regtech-overview jv-regtech-kb jv-regtech-graph \
            jv-regtech-ontology jv-regtech-ai py-regtech-nlp; do
  case "$name" in
    jv-regtech-user)     port=28100; ctx=/api/regtech/user ;;
    jv-regtech-overview) port=28101; ctx=/api/regtech/overview ;;
    jv-regtech-kb)       port=28102; ctx=/api/regtech/kb ;;
    jv-regtech-graph)    port=28103; ctx=/api/regtech/graph ;;
    jv-regtech-ontology) port=28104; ctx=/api/regtech/ontology ;;
    jv-regtech-ai)       port=28105; ctx=/api/regtech/ai ;;
    py-regtech-nlp)      port=28110; ctx=/api/regtech/nlp ;;
    *) continue ;;
  esac
  health="DOWN"
  curl -fsS "http://127.0.0.1:${port}${ctx}/health" >/dev/null 2>&1 && health="UP"
  pid="-"
  [ -f ".run/${name}.pid" ] && pid="$(cat .run/${name}.pid)"
  printf "%-22s %-8s %-10s %s\n" "$name" "$port" "$health" "$pid"
done

echo
echo "==> 网关平台服务注册（api_gateway.tb_gw_service）"
mysql -h"${MYSQL_HOST:-127.0.0.1}" -P"${MYSQL_PORT:-3306}" -u"${MYSQL_USER:-root}" -p"${MYSQL_PASS:-Root@123456}" \
  -e "SELECT service_name, language, ip, port, status, last_heartbeat_time FROM api_gateway.tb_gw_service WHERE app_id='app-regtech' ORDER BY port;" 2>/dev/null \
  || echo "（MySQL 不可达或未初始化）"
