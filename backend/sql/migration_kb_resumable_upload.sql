-- =====================================================================
-- 知识库分片上传（断点续传 + 秒传）迁移（针对已用旧版 novaops_init.sql 初始化的库）
--   kb_upload_session：一次上传会话，按 content_hash 唯一；记录分片口径与最终文档
--   kb_upload_chunk  ：已完整接收的分片序号，断点续传据此只补传缺失分片
-- 幂等说明：本脚本只执行一次；重复执行会在 create table 处报 1050（表已存在）。
-- 说明：分片实体文件存在 ${NOVAOPS_KB_STORAGE}/_uploads/{sessionId}/ 下，
--   合并成功后连同 chunk 记录一起清理；未完成的会话会保留分片以支持续传。
-- =====================================================================
SET NAMES utf8mb4;

create table kb_upload_session (
  id varchar(64) primary key,
  content_hash varchar(64) not null,
  file_name varchar(255) not null,
  file_type varchar(16) not null,
  file_size bigint not null,
  chunk_size int not null,
  chunk_count int not null,
  status varchar(32) not null,
  document_id varchar(64) null,
  created_by varchar(64) not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp,
  deleted tinyint not null default 0,
  unique key uk_kb_upload_hash (content_hash),
  index idx_kb_upload_status (status)
);

create table kb_upload_chunk (
  session_id varchar(64) not null,
  chunk_index int not null,
  size int not null,
  created_at datetime not null default current_timestamp,
  primary key (session_id, chunk_index)
);
