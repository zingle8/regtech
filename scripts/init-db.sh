#!/usr/bin/env bash
# 初始化三个数据库：MySQL 业务库、PostgreSQL 知识库（pgvector）、API 网关注册数据
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-Root@123456}"

PG_HOST="${PG_HOST:-127.0.0.1}"
PG_PORT="${PG_PORT:-5432}"
PG_USER="${PG_USER:-postgres}"
PG_PASS="${PG_PASS:-Pg@123456}"

echo "==> 初始化 MySQL 业务库 regtech_platform"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" < sql/mysql/regtech_platform.sql

echo "==> 初始化 PostgreSQL 知识库 regtech_kb（需已安装 pgvector）"
PGPASSWORD="$PG_PASS" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "SELECT 1 FROM pg_database WHERE datname = 'regtech_kb'" | grep -q 1 \
  || PGPASSWORD="$PG_PASS" createdb -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" regtech_kb

PGPASSWORD="$PG_PASS" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d regtech_kb -v ON_ERROR_STOP=1 \
  -f sql/postgres/postgresql-regtech_knowledge.sql

echo "==> 注册到 API 网关平台（api_gateway 库）"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" < sql/gateway/regtech_register_gateway.sql

echo "完成。默认管理员：admin / Admin@123"
