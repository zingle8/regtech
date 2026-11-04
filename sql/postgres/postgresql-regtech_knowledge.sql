-- =============================================================
--  NCS RegTech · PostgreSQL 知识库（pgvector）
--  前置：需安装 pgvector 扩展（Docker 建议 pgvector/pgvector:pg16）
--  用法：psql -U postgres -f sql/postgres/postgresql-regtech_knowledge.sql
--  说明：
--    1. embedding 统一 1024 维（对应 Ollama qwen3-embedding:0.6b）
--    2. 初始化数据不含向量，先用「法律知识库」重新入库或调 AI /embeddings 生成
--    3. kg_node / kg_edge / ontology_* 与 kb_* 同库，便于图谱与本体服务联查
-- =============================================================

CREATE EXTENSION IF NOT EXISTS vector;

DROP TABLE IF EXISTS kb_chunk;
DROP TABLE IF EXISTS kb_document;
DROP TABLE IF EXISTS kb_dataset;
DROP TABLE IF EXISTS legal_article;
DROP TABLE IF EXISTS kb_diff;
DROP TABLE IF EXISTS kb_article_log;
DROP TABLE IF EXISTS kg_edge;
DROP TABLE IF EXISTS kg_node;
DROP TABLE IF EXISTS ontology_property;
DROP TABLE IF EXISTS ontology_individual;
DROP TABLE IF EXISTS ontology_class;

-- ----------------------------
-- 1. 知识库数据集
-- ----------------------------
CREATE TABLE kb_dataset (
  id              BIGSERIAL PRIMARY KEY,
  code            VARCHAR(64)  NOT NULL UNIQUE,
  name            VARCHAR(128) NOT NULL,
  description     VARCHAR(512),
  jurisdiction    VARCHAR(16)  DEFAULT 'CN',
  embedding_model VARCHAR(64)  DEFAULT 'qwen3-embedding:0.6b',
  doc_count       INT          NOT NULL DEFAULT 0,
  chunk_count     INT          NOT NULL DEFAULT 0,
  create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
  update_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE kb_dataset IS '知识库数据集';

-- ----------------------------
-- 2. 文档
-- ----------------------------
CREATE TABLE kb_document (
  id             BIGSERIAL PRIMARY KEY,
  dataset_id     BIGINT REFERENCES kb_dataset (id) ON DELETE SET NULL,
  title          VARCHAR(255) NOT NULL,
  source_type    VARCHAR(32)  DEFAULT 'TEXT',
  jurisdiction   VARCHAR(16)  DEFAULT 'CN',
  effective_date DATE,
  status         VARCHAR(32)  DEFAULT '现行有效',
  content        TEXT,
  char_count     INT          DEFAULT 0,
  chunk_count    INT          DEFAULT 0,
  create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
  update_time    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kb_doc_dataset ON kb_document (dataset_id);

-- ----------------------------
-- 3. 分块 + 向量
-- ----------------------------
CREATE TABLE kb_chunk (
  id          BIGSERIAL PRIMARY KEY,
  dataset_id  BIGINT,
  document_id BIGINT REFERENCES kb_document (id) ON DELETE CASCADE,
  chunk_no    INT          NOT NULL DEFAULT 1,
  article_no  VARCHAR(32),
  content     TEXT         NOT NULL,
  tokens      INT          DEFAULT 0,
  embedding   vector(1024),
  create_time TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kb_chunk_doc ON kb_chunk (document_id);
CREATE INDEX idx_kb_chunk_dataset ON kb_chunk (dataset_id);
-- 向量索引：数据量上来后启用（ivfflat 需要先有数据）
-- CREATE INDEX idx_kb_chunk_embedding ON kb_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- ----------------------------
-- 4. 法条库
-- ----------------------------
CREATE TABLE legal_article (
  id             BIGSERIAL PRIMARY KEY,
  code           VARCHAR(64) NOT NULL UNIQUE,
  law_name       VARCHAR(255) NOT NULL,
  article_no     VARCHAR(32)  NOT NULL,
  title          VARCHAR(255),
  jurisdiction   VARCHAR(16)  DEFAULT 'CN',
  effective_date DATE,
  status         VARCHAR(32)  DEFAULT '现行有效',
  content        TEXT         NOT NULL,
  tags           VARCHAR(255),
  version        VARCHAR(32)  DEFAULT 'v1',
  embedding      vector(1024),
  create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
  update_time    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_legal_law ON legal_article (law_name);
CREATE INDEX idx_legal_jurisdiction ON legal_article (jurisdiction);

-- ----------------------------
-- 5. 差异记录
-- ----------------------------
CREATE TABLE kb_diff (
  id          BIGSERIAL PRIMARY KEY,
  left_id     BIGINT,
  right_id    BIGINT,
  left_title  VARCHAR(255),
  right_title VARCHAR(255),
  change_type VARCHAR(32),
  similarity  NUMERIC(6, 4),
  diff_json   JSONB,
  create_time TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ----------------------------
-- 5.1 条文变更日志（法条 新增 / 更新 / 删除 留痕）
-- ----------------------------
CREATE TABLE kb_article_log (
  id            BIGSERIAL PRIMARY KEY,
  article_id    BIGINT,
  action        VARCHAR(16) NOT NULL,
  code          VARCHAR(64),
  law_name      VARCHAR(255),
  article_no    VARCHAR(32),
  title         VARCHAR(255),
  summary       VARCHAR(500),
  operator_name VARCHAR(64),
  operate_time  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_article_log_time ON kb_article_log (operate_time DESC);

-- ----------------------------
-- 6. 知识图谱
-- ----------------------------
CREATE TABLE kg_node (
  id          BIGSERIAL PRIMARY KEY,
  code        VARCHAR(128) NOT NULL UNIQUE,
  name        VARCHAR(255) NOT NULL,
  label       VARCHAR(255),
  category    VARCHAR(32),
  jurisdiction VARCHAR(16),
  description TEXT,
  properties  JSONB DEFAULT '{}'::jsonb,
  create_time TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE kg_edge (
  id          BIGSERIAL PRIMARY KEY,
  source_id   BIGINT NOT NULL REFERENCES kg_node (id) ON DELETE CASCADE,
  target_id   BIGINT NOT NULL REFERENCES kg_node (id) ON DELETE CASCADE,
  relation    VARCHAR(32) NOT NULL,
  weight      NUMERIC(5, 2) DEFAULT 1,
  create_time TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE (source_id, target_id, relation)
);

-- ----------------------------
-- 7. 本体
-- ----------------------------
CREATE TABLE ontology_class (
  id          BIGSERIAL PRIMARY KEY,
  code        VARCHAR(64) NOT NULL UNIQUE,
  name        VARCHAR(128) NOT NULL,
  parent_id   BIGINT REFERENCES ontology_class (id) ON DELETE SET NULL,
  description VARCHAR(512),
  jurisdiction VARCHAR(16),
  create_time TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE ontology_property (
  id          BIGSERIAL PRIMARY KEY,
  class_id    BIGINT NOT NULL REFERENCES ontology_class (id) ON DELETE CASCADE,
  code        VARCHAR(64) NOT NULL,
  name        VARCHAR(128) NOT NULL,
  data_type   VARCHAR(32) DEFAULT 'string',
  required    BOOLEAN     DEFAULT FALSE,
  description VARCHAR(512)
);

CREATE TABLE ontology_individual (
  id          BIGSERIAL PRIMARY KEY,
  class_id    BIGINT REFERENCES ontology_class (id) ON DELETE SET NULL,
  name        VARCHAR(255) NOT NULL,
  props       JSONB DEFAULT '{}'::jsonb,
  description VARCHAR(512),
  create_time TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =============================================================
--  初始化数据
-- =============================================================

INSERT INTO kb_dataset (id, code, name, description, jurisdiction) VALUES
(1, 'cn-regulation', '中国金融监管法规库', '人民银行、金融监管总局、证监会发布的现行有效法规', 'CN'),
(2, 'hk-sfc', '香港证监会条例库', '《证券及期货条例》及附属指引', 'HK'),
(3, 'tw-fsc', '台湾金融监管库', '金融消费者保护与银行监管相关规定', 'TW');
SELECT setval(pg_get_serial_sequence('kb_dataset', 'id'), 3);

INSERT INTO kb_document (id, dataset_id, title, source_type, jurisdiction, effective_date, status, content, char_count) VALUES
(1, 1, '中华人民共和国个人信息保护法', 'TEXT', 'CN', '2021-11-01', '现行有效',
'第一条 为了保护个人信息权益，规范个人信息处理活动，促进个人信息合理利用，根据宪法，制定本法。
第十三条 符合下列情形之一的，个人信息处理者方可处理个人信息：
（一）取得个人的同意；
（二）为订立、履行个人作为一方当事人的合同所必需；
（三）为履行法定职责或者法定义务所必需；
（四）为应对突发公共卫生事件，或者紧急情况下为保护自然人的生命健康和财产安全所必需。
第十七条 个人信息处理者在处理个人信息前，应当以显著方式、清晰易懂的语言真实、准确、完整地向个人告知下列事项：处理者的名称和联系方式、处理目的、处理方式、个人信息种类、保存期限。
第二十四条 个人信息处理者利用个人信息进行自动化决策，应当保证决策的透明度和结果公平、公正，不得对个人在交易价格等交易条件上实行不合理的差别待遇。', 700),
(2, 1, '银行保险机构消费者权益保护管理办法', 'TEXT', 'CN', '2023-03-01', '现行有效',
'第二十一条 银行保险机构应当保障消费者知情权，以通俗易懂的语言真实、准确、全面地向消费者披露产品和服务的重要信息。
第二十二条 银行保险机构应当保障消费者自主选择权，不得强制捆绑、强制搭售产品或者服务。
第二十三条 银行保险机构应当保障消费者公平交易权，不得设置不合理的条件或者实施不合理的差别待遇。
第三十条 银行保险机构应当建立消费者适当性管理机制，将合适的产品或者服务提供给合适的消费者。', 480),
(3, 2, '香港证券及期货条例（节选）', 'TEXT', 'HK', '2003-04-01', '现行有效',
'第 4.1 条 证券及期货事务监察委员会应当以维护证券期货市场公平、效率与透明为职责。
第 5.2 条 持牌法团应当确保其客户适当性评估程序符合适用规定，并在提供建议前完成风险评估。
第 6.3 条 任何人士不得披露内幕消息，或利用内幕消息进行证券交易。', 320);
SELECT setval(pg_get_serial_sequence('kb_document', 'id'), 3);

INSERT INTO kb_chunk (dataset_id, document_id, chunk_no, article_no, content, tokens) VALUES
(1, 1, 1, '第十三条', '第十三条 符合下列情形之一的，个人信息处理者方可处理个人信息：（一）取得个人的同意；（二）为订立、履行个人作为一方当事人的合同所必需；（三）为履行法定职责或者法定义务所必需。', 90),
(1, 1, 2, '第十七条', '第十七条 个人信息处理者在处理个人信息前，应当以显著方式、清晰易懂的语言真实、准确、完整地向个人告知处理目的、处理方式、个人信息种类、保存期限等事项。', 80),
(1, 1, 3, '第二十四条', '第二十四条 个人信息处理者利用个人信息进行自动化决策，应当保证决策的透明度和结果公平、公正，不得对个人在交易价格等交易条件上实行不合理的差别待遇。', 78),
(1, 2, 1, '第二十一条', '第二十一条 银行保险机构应当保障消费者知情权，以通俗易懂的语言真实、准确、全面地向消费者披露产品和服务的重要信息。', 60),
(1, 2, 2, '第三十条', '第三十条 银行保险机构应当建立消费者适当性管理机制，将合适的产品或者服务提供给合适的消费者。', 48),
(2, 3, 1, '第 5.2 条', '第 5.2 条 持牌法团应当确保其客户适当性评估程序符合适用规定，并在提供建议前完成风险评估。', 46),
(2, 3, 2, '第 6.3 条', '第 6.3 条 任何人士不得披露内幕消息，或利用内幕消息进行证券交易。', 32);

INSERT INTO legal_article (id, code, law_name, article_no, title, jurisdiction, effective_date, status, content, tags, version) VALUES
(1, 'cn-pipl-13-v1', '中华人民共和国个人信息保护法', '第十三条', '处理个人信息的合法性基础', 'CN', '2021-11-01', '已修订',
'第十三条 符合下列情形之一的，个人信息处理者方可处理个人信息：
（一）取得个人的同意；
（二）为订立、履行个人作为一方当事人的合同所必需；
（三）为履行法定职责或者法定义务所必需。', '个人信息,合法性基础,同意', 'v1'),
(2, 'cn-pipl-13-v2', '中华人民共和国个人信息保护法', '第十三条', '处理个人信息的合法性基础', 'CN', '2021-11-01', '现行有效',
'第十三条 符合下列情形之一的，个人信息处理者方可处理个人信息：
（一）取得个人的同意；
（二）为订立、履行个人作为一方当事人的合同所必需；
（三）为履行法定职责或者法定义务所必需；
（四）为应对突发公共卫生事件，或者紧急情况下为保护自然人的生命健康和财产安全所必需；
（五）法律、行政法规规定的其他情形。', '个人信息,合法性基础,同意,紧急情况', 'v2'),
(3, 'cn-pipl-17', '中华人民共和国个人信息保护法', '第十七条', '告知义务', 'CN', '2021-11-01', '现行有效',
'第十七条 个人信息处理者在处理个人信息前，应当以显著方式、清晰易懂的语言真实、准确、完整地向个人告知下列事项：
（一）个人信息处理者的名称和联系方式；
（二）处理目的、处理方式、个人信息种类、保存期限；
（三）个人行使本法规定权利的方式和程序。', '个人信息,告知义务,透明度', 'v1'),
(4, 'cn-pipl-24', '中华人民共和国个人信息保护法', '第二十四条', '自动化决策', 'CN', '2021-11-01', '现行有效',
'第二十四条 个人信息处理者利用个人信息进行自动化决策，应当保证决策的透明度和结果公平、公正，不得对个人在交易价格等交易条件上实行不合理的差别待遇。', '个人信息,自动化决策,算法', 'v1'),
(5, 'cn-cbirc-21', '银行保险机构消费者权益保护管理办法', '第二十一条', '消费者知情权', 'CN', '2023-03-01', '现行有效',
'第二十一条 银行保险机构应当保障消费者知情权，以通俗易懂的语言真实、准确、全面地向消费者披露产品和服务的重要信息。', '消费者权益,信息披露', 'v1'),
(6, 'cn-cbirc-30', '银行保险机构消费者权益保护管理办法', '第三十条', '适当性管理', 'CN', '2023-03-01', '现行有效',
'第三十条 银行保险机构应当建立消费者适当性管理机制，将合适的产品或者服务提供给合适的消费者。', '消费者权益,适当性,销售管理', 'v1'),
(7, 'cn-dsl-27', '中华人民共和国数据安全法', '第二十七条', '数据安全保护义务', 'CN', '2021-09-01', '现行有效',
'第二十七条 开展数据处理活动应当依照法律、法规的规定，建立健全全流程数据安全管理制度，组织开展数据安全教育培训，采取相应的技术措施和其他必要措施，保障数据安全。', '数据安全,合规义务', 'v1'),
(8, 'hk-sfo-5-2', '香港证券及期货条例', '第 5.2 条', '客户适当性', 'HK', '2003-04-01', '现行有效',
'第 5.2 条 持牌法团应当确保其客户适当性评估程序符合适用规定，并在提供建议前完成风险评估。', '持牌法团,适当性', 'v1'),
(9, 'hk-sfo-6-3', '香港证券及期货条例', '第 6.3 条', '内幕消息', 'HK', '2003-04-01', '现行有效',
'第 6.3 条 任何人士不得披露内幕消息，或利用内幕消息进行证券交易。', '内幕交易,市场失当', 'v1'),
(10, 'tw-fsc-11', '金融消費者保護法', '第十一條', '廣告與招攬', 'TW', '2011-06-29', '现行有效',
'第十一條 金融服務業從事廣告、業務招攬及營業促銷活動，不得有虛偽、詐欺、隱匿或其他足致他人誤信之行為。', '金融消費者,廣告,招攬', 'v1'),
(11, 'tw-fsc-20', '金融消費者保護法', '第二十條', '適合度評估', 'TW', '2011-06-29', '现行有效',
'第二十條 金融服務業於提供金融商品或服務前，應充分了解金融消費者之相關資料，以確保商品或服務對金融消費者之適合度。', '金融消費者,適合度,風險評估', 'v1'),
(12, 'cn-sec-79', '中华人民共和国证券法', '第七十九条', '信息披露', 'CN', '2020-03-01', '现行有效',
'第七十九条 信息披露义务人应当及时依法履行信息披露义务，披露的信息应当真实、准确、完整，简明清晰，通俗易懂，不得有虚假记载、误导性陈述或者重大遗漏。', '证券,信息披露', 'v1');
SELECT setval(pg_get_serial_sequence('legal_article', 'id'), 12);

-- 本体：法规 → 主体 / 义务 / 罚则
INSERT INTO ontology_class (id, code, name, parent_id, description, jurisdiction) VALUES
(1, 'cls-regulation', '监管法规', NULL, '具有约束力的法律、行政法规与部门规章', NULL),
(2, 'cls-law', '法律', 1, '全国人大及其常委会制定的规范性文件', 'CN'),
(3, 'cls-rule', '部门规章', 1, '监管机构制定的规章与规范性文件', 'CN'),
(4, 'cls-entity', '监管对象', NULL, '受监管的机构与个人', NULL),
(5, 'cls-financial', '金融机构', 4, '银行、保险、证券、支付机构等', NULL),
(6, 'cls-consumer', '金融消费者', 4, '接受金融产品与服务的自然人或企业', NULL),
(7, 'cls-obligation', '合规义务', NULL, '法规要求主体必须履行的行为', NULL),
(8, 'cls-disclosure', '信息披露', 7, '向消费者或监管机构披露重要信息', NULL),
(9, 'cls-suitability', '适当性管理', 7, '将合适的产品提供给合适的客户', NULL),
(10, 'cls-penalty', '法律责任', NULL, '违反义务后的行政处罚与民事赔偿', NULL);
SELECT setval(pg_get_serial_sequence('ontology_class', 'id'), 10);

INSERT INTO ontology_property (class_id, code, name, data_type, required, description) VALUES
(2, 'effective_date', '生效日期', 'date', TRUE, '法规开始施行的日期'),
(5, 'license_no', '牌照编号', 'string', FALSE, '监管机构颁发的牌照编号'),
(8, 'channel', '披露渠道', 'string', TRUE, '官网、APP、柜面等'),
(9, 'risk_level', '风险等级', 'string', TRUE, 'R1-R5');

INSERT INTO ontology_individual (class_id, name, props, description) VALUES
(5, '某城市商业银行', '{"license_no":"B1234"}', '持牌城市商业银行'),
(6, '个人客户张某', '{"risk_level":"R2"}', '风险承受能力为稳健型'),
(8, '产品销售页面披露', '{"channel":"APP"}', '移动端销售页面的信息披露'),
(9, '理财产品适当性评估', '{}', '销售理财产品前的风险评估流程');

-- 图谱初始数据（也可以在控制台点「自动构图」按法条库重建）
INSERT INTO kg_node (id, code, name, label, category, jurisdiction, description, properties) VALUES
(1, 'law:中华人民共和国个人信息保护法', '中华人民共和国个人信息保护法', '中华人民共和国个人信息保护法', '法规', 'CN', '个人信息保护基础法律', '{}'),
(2, 'art:cn-pipl-13-v2', '中华人民共和国个人信息保护法 第十三条', '处理个人信息的合法性基础', '条款', 'CN', '合法性基础', '{}'),
(3, 'art:cn-pipl-17', '中华人民共和国个人信息保护法 第十七条', '告知义务', '条款', 'CN', '告知义务', '{}'),
(4, 'tag:个人信息', '个人信息', '个人信息', '主题', 'CN', '监管主题', '{}'),
(5, 'tag:告知义务', '告知义务', '告知义务', '主题', 'CN', '监管主题', '{}'),
(6, 'law:银行保险机构消费者权益保护管理办法', '银行保险机构消费者权益保护管理办法', '消费者权益保护', '法规', 'CN', '部门规章', '{}'),
(7, 'art:cn-cbirc-30', '银行保险机构消费者权益保护管理办法 第三十条', '适当性管理', '条款', 'CN', '适当性', '{}'),
(8, 'tag:适当性', '适当性', '适当性', '主题', 'CN', '监管主题', '{}');
SELECT setval(pg_get_serial_sequence('kg_node', 'id'), 8);

INSERT INTO kg_edge (source_id, target_id, relation, weight) VALUES
(1, 2, '包含', 1), (1, 3, '包含', 1),
(6, 7, '包含', 1),
(2, 4, '涉及', 1), (3, 4, '涉及', 1), (3, 5, '涉及', 1),
(7, 8, '涉及', 1);

-- 回填数据集统计（初始化文档/分块是直接写入的）
UPDATE kb_dataset d
SET doc_count = (SELECT COUNT(*) FROM kb_document x WHERE x.dataset_id = d.id),
    chunk_count = (SELECT COUNT(*) FROM kb_chunk c WHERE c.dataset_id = d.id);

-- 回填条文变更日志：初始化条文视为「新增」
INSERT INTO kb_article_log (article_id, action, code, law_name, article_no, title, summary, operator_name, operate_time)
SELECT id, 'CREATE', code, law_name, article_no, title, '新增条款', '系统初始化', create_time
FROM legal_article;

ANALYZE;
