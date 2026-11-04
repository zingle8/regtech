-- =============================================================
--  RegTech → AI 网关平台（ai-gateway）注册脚本
--  目标库：api_gateway（MySQL）
--  作用：
--    1. 应用/服务注册：接入中心能看到 7 个 RegTech 微服务（6 Java + 1 Python）
--    2. 接口登记：服务详情能看到接口清单
--    3. 转发路由：网关按路由把 /api/regtech/** 转发到对应微服务
--  说明：各服务启动后还会自动注册并上报心跳（Java 用 jv-regtech-gateway-bridge，
--        Python 用 python/_shared/jv_gateway），本脚本用于首次初始化 / 手工登记，
--        重复执行安全。
--  命名约定：Java 服务 jv- 前缀，Python 服务 py- 前缀。
-- =============================================================

USE api_gateway;

-- 清理历史命名（改名前登记的 regtech-* 服务名）
DELETE FROM tb_gw_service
 WHERE app_id = 'app-regtech'
   AND service_name IN ('regtech-user', 'regtech-overview', 'regtech-kb',
                        'regtech-graph', 'regtech-ontology', 'regtech-ai');

-- ----------------------------
-- 1. 注册服务（服务发现）
-- ----------------------------
INSERT INTO tb_gw_service
  (app_id, app_name, service_name, ip, port, language, version, qps, avg_rt, status, instances, register_time, last_heartbeat_time)
VALUES
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-user',     '127.0.0.1', 28100, 'Java 17', '1.0.0', 0, 0, '健康', 1, NOW(), NOW()),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-overview', '127.0.0.1', 28101, 'Java 17', '1.0.0', 0, 0, '健康', 1, NOW(), NOW()),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-kb',       '127.0.0.1', 28102, 'Java 17', '1.0.0', 0, 0, '健康', 1, NOW(), NOW()),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-graph',    '127.0.0.1', 28103, 'Java 17', '1.0.0', 0, 0, '健康', 1, NOW(), NOW()),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-ontology', '127.0.0.1', 28104, 'Java 17', '1.0.0', 0, 0, '健康', 1, NOW(), NOW()),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-ai',       '127.0.0.1', 28105, 'Java 17', '1.0.0', 0, 0, '健康', 1, NOW(), NOW()),
  ('app-regtech', 'NCS RegTech 监管科技', 'py-regtech-nlp',      '127.0.0.1', 28110, 'Python 3.11', '1.0.0', 0, 0, '健康', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  app_id = VALUES(app_id),
  app_name = VALUES(app_name),
  ip = VALUES(ip),
  port = VALUES(port),
  language = VALUES(language),
  version = VALUES(version),
  status = '健康',
  last_heartbeat_time = NOW();

-- ----------------------------
-- 2. 接口清单（可重复执行）
-- ----------------------------
DELETE FROM tb_gw_api WHERE app_id = 'app-regtech';

INSERT INTO tb_gw_api (app_id, service_name, method, path, status) VALUES
  ('app-regtech', 'jv-regtech-user', 'POST',   '/api/regtech/user/auth/login',        '在线'),
  ('app-regtech', 'jv-regtech-user', 'POST',   '/api/regtech/user/auth/logout',       '在线'),
  ('app-regtech', 'jv-regtech-user', 'GET',    '/api/regtech/user/auth/me',           '在线'),
  ('app-regtech', 'jv-regtech-user', 'GET',    '/api/regtech/user/users',             '在线'),
  ('app-regtech', 'jv-regtech-user', 'POST',   '/api/regtech/user/users',             '在线'),
  ('app-regtech', 'jv-regtech-user', 'PUT',    '/api/regtech/user/users/{id}',        '在线'),
  ('app-regtech', 'jv-regtech-user', 'DELETE', '/api/regtech/user/users/{id}',        '在线'),

  ('app-regtech', 'jv-regtech-overview', 'GET', '/api/regtech/overview/summary',      '在线'),
  ('app-regtech', 'jv-regtech-overview', 'GET', '/api/regtech/overview/trend',        '在线'),

  ('app-regtech', 'jv-regtech-kb', 'GET',    '/api/regtech/kb/datasets',              '在线'),
  ('app-regtech', 'jv-regtech-kb', 'GET',    '/api/regtech/kb/documents',             '在线'),
  ('app-regtech', 'jv-regtech-kb', 'POST',   '/api/regtech/kb/documents',             '在线'),
  ('app-regtech', 'jv-regtech-kb', 'POST',   '/api/regtech/kb/search',                '在线'),
  ('app-regtech', 'jv-regtech-kb', 'GET',    '/api/regtech/kb/articles',              '在线'),
  ('app-regtech', 'jv-regtech-kb', 'GET',    '/api/regtech/kb/articles/changes',      '在线'),
  ('app-regtech', 'jv-regtech-kb', 'POST',   '/api/regtech/kb/articles/search',       '在线'),
  ('app-regtech', 'jv-regtech-kb', 'POST',   '/api/regtech/kb/rag/ask',               '在线'),
  ('app-regtech', 'jv-regtech-kb', 'POST',   '/api/regtech/kb/diff/compare',          '在线'),
  ('app-regtech', 'jv-regtech-kb', 'POST',   '/api/regtech/kb/diff/matrix',           '在线'),
  ('app-regtech', 'jv-regtech-kb', 'GET',    '/api/regtech/kb/diff/jurisdictions',    '在线'),
  ('app-regtech', 'jv-regtech-kb', 'GET',    '/api/regtech/kb/diff/records',          '在线'),

  ('app-regtech', 'jv-regtech-graph', 'GET',  '/api/regtech/graph/graph',              '在线'),
  ('app-regtech', 'jv-regtech-graph', 'GET',  '/api/regtech/graph/nodes',              '在线'),
  ('app-regtech', 'jv-regtech-graph', 'POST', '/api/regtech/graph/build',              '在线'),
  ('app-regtech', 'jv-regtech-graph', 'GET',  '/api/regtech/graph/stats',              '在线'),

  ('app-regtech', 'jv-regtech-ontology', 'GET',  '/api/regtech/ontology/classes',      '在线'),
  ('app-regtech', 'jv-regtech-ontology', 'GET',  '/api/regtech/ontology/individuals',  '在线'),
  ('app-regtech', 'jv-regtech-ontology', 'POST', '/api/regtech/ontology/rag/ask',      '在线'),

  ('app-regtech', 'jv-regtech-ai', 'GET',  '/api/regtech/ai/models',                   '在线'),
  ('app-regtech', 'jv-regtech-ai', 'POST', '/api/regtech/ai/v1/chat/completions',      '在线'),
  ('app-regtech', 'jv-regtech-ai', 'POST', '/api/regtech/ai/v1/chat/completions/stream','在线'),
  ('app-regtech', 'jv-regtech-ai', 'POST', '/api/regtech/ai/v1/embeddings',            '在线'),
  ('app-regtech', 'jv-regtech-ai', 'GET',  '/api/regtech/ai/conversations',            '在线'),

  ('app-regtech', 'py-regtech-nlp', 'GET',  '/api/regtech/nlp/health',                   '在线'),
  ('app-regtech', 'py-regtech-nlp', 'GET',  '/api/regtech/nlp/stats',                    '在线'),
  ('app-regtech', 'py-regtech-nlp', 'POST', '/api/regtech/nlp/keywords',                 '在线'),
  ('app-regtech', 'py-regtech-nlp', 'POST', '/api/regtech/nlp/segment',                  '在线'),
  ('app-regtech', 'py-regtech-nlp', 'POST', '/api/regtech/nlp/similarity',               '在线');

-- ----------------------------
-- 3. 转发路由（proxy 30 秒内热加载）
-- ----------------------------
DELETE FROM tb_gw_route WHERE app_id = 'app-regtech';

INSERT INTO tb_gw_route
  (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
VALUES
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-user',     '/api/regtech/user/**',     'http://127.0.0.1:28100', NULL, 0, 1, 'RegTech：登录与用户'),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-overview', '/api/regtech/overview/**', 'http://127.0.0.1:28101', NULL, 0, 1, 'RegTech：数据总览'),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-kb',       '/api/regtech/kb/**',       'http://127.0.0.1:28102', NULL, 0, 1, 'RegTech：知识库/条文/差异'),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-graph',    '/api/regtech/graph/**',    'http://127.0.0.1:28103', NULL, 0, 1, 'RegTech：知识图谱'),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-ontology', '/api/regtech/ontology/**', 'http://127.0.0.1:28104', NULL, 0, 1, 'RegTech：本体 RAG'),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-ai',       '/api/regtech/ai/**',       'http://127.0.0.1:28105', NULL, 0, 1, 'RegTech：AI 服务'),
  ('app-regtech', 'NCS RegTech 监管科技', 'jv-regtech-ai',       '/v1/**',                   'http://127.0.0.1:28105', '/api/regtech/ai', 0, 1, 'RegTech：OpenAI 兼容入口'),
  ('app-regtech', 'NCS RegTech 监管科技', 'py-regtech-nlp',      '/api/regtech/nlp/**',      'http://127.0.0.1:28110', NULL, 0, 1, 'RegTech：Python 文本分析');

-- 校验
-- SELECT service_name, ip, port, status, last_heartbeat_time FROM tb_gw_service WHERE app_id = 'app-regtech';
-- SELECT path_prefix, upstream_url, prefix_path, strip_prefix, status FROM tb_gw_route WHERE app_id = 'app-regtech';
