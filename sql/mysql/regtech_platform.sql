-- =============================================================
--  NCS RegTech · MySQL 业务库（用户 / AI 会话）
--  用法：mysql -uroot -p < sql/mysql/regtech_platform.sql
--  说明：内置管理员 admin / Admin@123（BCrypt），与 API 网关平台一致
-- =============================================================

CREATE DATABASE IF NOT EXISTS regtech_platform
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE regtech_platform;

SET NAMES utf8mb4;

-- ----------------------------
-- 1. 平台用户
-- ----------------------------
DROP TABLE IF EXISTS tb_rt_user;
CREATE TABLE tb_rt_user (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username        VARCHAR(64)  NOT NULL                COMMENT '登录账号',
  password        VARCHAR(128) NOT NULL                COMMENT 'BCrypt 密码',
  nickname        VARCHAR(64)           DEFAULT NULL   COMMENT '姓名',
  email           VARCHAR(128)          DEFAULT NULL   COMMENT '邮箱',
  phone           VARCHAR(32)           DEFAULT NULL   COMMENT '手机号',
  role            VARCHAR(32)  NOT NULL DEFAULT 'ANALYST' COMMENT '角色：ADMIN/ANALYST/VIEWER',
  status          TINYINT      NOT NULL DEFAULT 1      COMMENT '1 启用，0 禁用',
  token_version   INT          NOT NULL DEFAULT 0      COMMENT '退出登录自增，使旧令牌失效',
  last_login_time DATETIME              DEFAULT NULL   COMMENT '最后登录时间',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_role (role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'RegTech 平台用户';

-- ----------------------------
-- 2. AI 会话
-- ----------------------------
DROP TABLE IF EXISTS tb_rt_ai_conversation;
CREATE TABLE tb_rt_ai_conversation (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  user_id       BIGINT               DEFAULT NULL COMMENT '归属用户',
  title         VARCHAR(128) NOT NULL DEFAULT '新会话',
  model         VARCHAR(64)          DEFAULT NULL COMMENT '使用的模型',
  scene         VARCHAR(32)  NOT NULL DEFAULT 'chat' COMMENT 'chat/kb-rag/ontology-rag/diff',
  message_count INT          NOT NULL DEFAULT 0,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_update (update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 会话';

DROP TABLE IF EXISTS tb_rt_ai_message;
CREATE TABLE tb_rt_ai_message (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT      NOT NULL,
  role            VARCHAR(16) NOT NULL COMMENT 'system/user/assistant',
  content         MEDIUMTEXT,
  tokens          INT                  DEFAULT NULL,
  cost_ms         INT                  DEFAULT NULL,
  sources         TEXT COMMENT '引用来源 JSON',
  create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted         TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_conversation (conversation_id),
  KEY idx_create (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 消息';

-- ----------------------------
-- 3. 模型配置（可选：用于控制台展示/扩展多模型）
-- ----------------------------
DROP TABLE IF EXISTS tb_rt_ai_model;
CREATE TABLE tb_rt_ai_model (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  name            VARCHAR(64) NOT NULL COMMENT '显示名',
  provider        VARCHAR(32) NOT NULL DEFAULT 'ollama',
  base_url        VARCHAR(255)         DEFAULT NULL,
  model           VARCHAR(64) NOT NULL COMMENT '模型标识，如 gpt-oss:20b',
  embedding_model VARCHAR(64)          DEFAULT NULL,
  temperature     DECIMAL(3, 2)        DEFAULT 0.70,
  max_tokens      INT                  DEFAULT 4096,
  is_default      TINYINT     NOT NULL DEFAULT 0,
  status          TINYINT     NOT NULL DEFAULT 1,
  create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '模型配置';

INSERT INTO tb_rt_ai_model (name, provider, base_url, model, embedding_model, temperature, is_default, status) VALUES
('GPT-OSS 20B', 'ollama', 'http://127.0.0.1:11434', 'gpt-oss:20b', 'qwen3-embedding:0.6b', 0.70, 1, 1),
('Qwen3 8B', 'ollama', 'http://127.0.0.1:11434', 'qwen3.8:latest', 'qwen3-embedding:0.6b', 0.70, 0, 1);

-- ----------------------------
-- 4. 内置管理员（admin / Admin@123）
-- ----------------------------
INSERT INTO tb_rt_user (username, password, nickname, email, role, status, token_version) VALUES
('admin', '$2a$10$Uv8R5rQN79vut4LRnQjdcOtSyo8yoWN3aSvadEpktLVIpjzEAfPHO', '平台管理员', 'admin@ncs-regtech.com', 'ADMIN', 1, 0);
