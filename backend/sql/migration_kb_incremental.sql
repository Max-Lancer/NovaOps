-- =====================================================================
-- 知识库增量向量化迁移（针对已用旧版 novaops_init.sql 初始化过的库）
--   kb_document 增加 content_hash：记录源文件内容 SHA-256，用于
--     1) 替换时判断内容是否变化（未变化直接跳过解析与 embedding）
--     2) 分片上传的秒传预检
-- 幂等说明：本脚本只执行一次，重复执行会在 add column / create index 处报
--   1060（列已存在）或 1061（索引已存在），不影响数据。
-- 历史数据：content_hash 为 null，替换/重试时会重新解析并回填；
--   kb_chunk 里旧的随机 vector_id 不会被复用，首次重解析相当于全量一次，
--   之后即按内容身份增量复用。
-- =====================================================================
SET NAMES utf8mb4;

alter table kb_document
  add column content_hash varchar(64) null after error_msg;

create index idx_kb_document_hash on kb_document (content_hash);
