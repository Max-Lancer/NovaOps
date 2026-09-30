SET NAMES utf8mb4;
drop table if exists biz_ticket_attachment;
drop table if exists kb_upload_chunk;
drop table if exists kb_upload_session;
drop table if exists kb_chunk;
drop table if exists kb_document;
drop table if exists agent_audit_log;
drop table if exists agent_task_step;
drop table if exists agent_task;
drop table if exists agent_message;
drop table if exists agent_conversation;
drop table if exists biz_ticket_comment;
drop table if exists biz_ticket_asset_rel;
drop table if exists biz_asset_log;
drop table if exists biz_asset;
drop table if exists biz_ticket_timeline;
drop table if exists biz_ticket;
drop table if exists sys_email_verification;
drop table if exists sys_refresh_token;
drop table if exists sys_user_tenant;
drop table if exists sys_role_permission;
drop table if exists sys_user_role;
drop table if exists sys_menu;
drop table if exists sys_permission;
drop table if exists sys_role;
drop table if exists sys_user;
drop table if exists sys_tenant;

create table sys_user (
  id varchar(64) primary key,
  username varchar(64) not null unique,
  email varchar(128) null,
  password_hash varchar(255) not null,
  display_name varchar(100) not null,
  role_id varchar(64) not null,
  enabled tinyint not null default 1,
  must_change_password tinyint not null default 0,
  deleted tinyint not null default 0,
  created_at datetime not null default current_timestamp,
  unique key uk_user_email (email)
);

create table sys_role (
  id varchar(64) primary key,
  code varchar(64) not null unique,
  name varchar(100) not null,
  description varchar(255) not null,
  sort_order int not null default 0
);

create table sys_permission (
  id varchar(64) primary key,
  code varchar(100) not null unique,
  name varchar(100) not null
);

create table sys_menu (
  id varchar(64) primary key,
  title varchar(100) not null,
  name varchar(100) not null,
  path varchar(200) not null,
  component varchar(100) not null,
  icon varchar(100) null,
  permission_code varchar(100) null,
  keep_alive tinyint not null default 1,
  parent_id varchar(64) null,
  sort_order int not null default 0,
  menu_scope varchar(20) not null
);

create table sys_role_permission (
  role_id varchar(64) not null,
  permission_id varchar(64) not null,
  primary key (role_id, permission_id)
);

create table sys_refresh_token (
  token varchar(128) primary key,
  user_id varchar(64) not null,
  expires_at datetime not null,
  revoked tinyint not null default 0,
  created_at datetime not null default current_timestamp
);

create table sys_email_verification (
  token varchar(128) primary key,
  user_id varchar(64) not null,
  purpose varchar(32) not null,
  expires_at datetime not null,
  used tinyint not null default 0,
  created_at datetime not null default current_timestamp,
  index idx_email_verification_user (user_id, purpose)
);

create table kb_document (
  id varchar(64) primary key,
  title varchar(255) not null,
  file_name varchar(255) not null,
  file_type varchar(16) not null,
  file_size bigint not null,
  storage_path varchar(500) not null,
  status varchar(32) not null,
  chunk_count int not null default 0,
  error_msg varchar(1000) null,
  content_hash varchar(64) null,
  created_by varchar(64) not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp,
  deleted tinyint not null default 0,
  index idx_kb_document_status (status),
  index idx_kb_document_updated (updated_at desc),
  index idx_kb_document_hash (content_hash)
);

create table kb_chunk (
  id varchar(64) primary key,
  document_id varchar(64) not null,
  chunk_index int not null,
  content mediumtext not null,
  vector_id varchar(64) not null,
  index idx_kb_chunk_document (document_id, chunk_index)
);

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

create table agent_conversation (
  id varchar(64) primary key,
  user_id varchar(64) not null,
  title varchar(255) not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp,
  index idx_agent_conversation_owner (user_id,updated_at desc)
);

create table agent_message (
  id varchar(64) primary key,
  conversation_id varchar(64) not null,
  role varchar(16) not null,
  content mediumtext not null,
  citations_json text null,
  validation_passed tinyint null,
  execution_json mediumtext null,
  created_at datetime not null default current_timestamp,
  index idx_agent_message_conversation (conversation_id,created_at)
);

create table agent_task (
  id varchar(64) primary key,
  user_id varchar(64) not null,
  goal varchar(2000) not null,
  status varchar(20) not null,
  plan_json text null,
  result_text text null,
  error_text varchar(1000) null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  index idx_agent_task_user (user_id, created_at desc)
);

create table agent_task_step (
  id varchar(64) primary key,
  task_id varchar(64) not null,
  seq int not null,
  kind varchar(20) not null,
  tool_name varchar(100) null,
  args_json text null,
  observation_json text null,
  status varchar(20) not null,
  revision int not null default 0,
  created_at datetime not null default current_timestamp,
  index idx_agent_task_step_task (task_id, seq)
);

create table agent_audit_log (
  id varchar(64) primary key,
  task_id varchar(64) null,
  user_id varchar(64) not null,
  source varchar(20) not null default 'task',
  tool_name varchar(100) not null,
  args_digest varchar(2000) null,
  result_digest varchar(2000) null,
  write_operation tinyint not null default 0,
  confirmed tinyint null,
  allowed tinyint not null default 1,
  detail varchar(500) null,
  created_at datetime not null default current_timestamp,
  index idx_agent_audit_user (user_id, created_at desc),
  index idx_agent_audit_tool (tool_name, created_at desc)
);

create table biz_ticket (
  id varchar(64) primary key,
  title varchar(255) not null,
  description text not null,
  status varchar(32) not null,
  priority varchar(32) not null,
  assignee_id varchar(64) null,
  claimant_id varchar(64) null,
  creator_id varchar(64) not null,
  due_date datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  done_at datetime null,
  index idx_ticket_updated (updated_at desc),
  index idx_ticket_status (status),
  index idx_ticket_priority (priority),
  index idx_ticket_assignee (assignee_id),
  index idx_ticket_creator (creator_id),
  index idx_ticket_done_at (done_at),
  constraint fk_ticket_assignee foreign key (assignee_id) references sys_user (id),
  constraint fk_ticket_claimant foreign key (claimant_id) references sys_user (id),
  constraint fk_ticket_creator foreign key (creator_id) references sys_user (id)
);

create table biz_ticket_timeline (
  id varchar(64) primary key,
  ticket_id varchar(64) not null,
  action varchar(32) not null,
  operator_id varchar(64) null,
  remark varchar(255) null,
  from_status varchar(32) null,
  to_status varchar(32) null,
  created_at datetime not null,
  index idx_ticket_timeline_ticket (ticket_id, created_at desc),
  constraint fk_ticket_timeline_operator foreign key (operator_id) references sys_user (id)
);

create table biz_ticket_asset_rel (
  ticket_id varchar(64) not null,
  asset_id varchar(64) not null,
  primary key (ticket_id, asset_id)
);

create table biz_ticket_comment (
  id varchar(64) primary key,
  ticket_id varchar(64) not null,
  author_id varchar(64) null,
  content text not null,
  created_at datetime not null,
  index idx_ticket_comment_ticket (ticket_id, created_at desc),
  constraint fk_ticket_comment_author foreign key (author_id) references sys_user (id)
);

create table biz_ticket_attachment (
  id varchar(64) primary key,
  ticket_id varchar(64) not null,
  name varchar(255) not null,
  url varchar(255) not null,
  size bigint not null default 0,
  created_at datetime not null,
  index idx_ticket_attachment_ticket (ticket_id, created_at desc)
);

create table biz_asset (
  id varchar(64) primary key,
  asset_no varchar(64) not null,
  name varchar(200) not null,
  type varchar(32) not null,
  status varchar(32) not null,
  owner_id varchar(64) null,
  location varchar(200),
  spec text,
  remark text,
  purchase_date date,
  version int not null default 0,
  created_at datetime not null,
  updated_at datetime not null,
  deleted tinyint not null default 0,
  unique key uk_asset_no (asset_no),
  index idx_asset_status (status),
  index idx_asset_owner (owner_id)
);

create table biz_asset_log (
  id varchar(64) primary key,
  asset_id varchar(64) not null,
  action varchar(32) not null,
  operator_id varchar(64) not null,
  target_user_id varchar(64) null,
  remark varchar(255) null,
  created_at datetime not null,
  index idx_asset_log_asset (asset_id, created_at desc)
);

insert into sys_user (id, username, email, password_hash, display_name, role_id, enabled, must_change_password, deleted) values
  ('u-admin', 'admin', 'admin@novaops.local', '$2a$10$t4amKqsqabkgwLhaZpj0F.wDk7mpyJgZokQRAdTfrxaIwPilCrHoq', 'System Admin', 'role-admin', 1, 0, 0),
  ('u-staff', 'staff', 'staff@novaops.local', '$2a$10$t4amKqsqabkgwLhaZpj0F.wDk7mpyJgZokQRAdTfrxaIwPilCrHoq', 'Support Staff', 'role-staff', 1, 0, 0),
  ('u-guest', 'guest', 'guest@novaops.local', '$2a$10$t4amKqsqabkgwLhaZpj0F.wDk7mpyJgZokQRAdTfrxaIwPilCrHoq', 'Read-only Guest', 'role-guest', 1, 0, 0),
  ('u-tom', 'tom', 'tom@novaops.local', '$2a$10$t4amKqsqabkgwLhaZpj0F.wDk7mpyJgZokQRAdTfrxaIwPilCrHoq', 'Tom', 'role-staff', 1, 0, 0),
  ('u-jerry', 'jerry', 'jerry@novaops.local', '$2a$10$t4amKqsqabkgwLhaZpj0F.wDk7mpyJgZokQRAdTfrxaIwPilCrHoq', 'Jerry', 'role-staff', 1, 0, 0),
  ('u-alice', 'alice', 'alice@novaops.local', '$2a$10$t4amKqsqabkgwLhaZpj0F.wDk7mpyJgZokQRAdTfrxaIwPilCrHoq', 'Alice', 'role-staff', 1, 0, 0);

insert into sys_role (id, code, name, description, sort_order) values
  ('role-admin', 'admin', '管理员', '管理用户、身份、知识库以及全部业务数据', 10),
  ('role-staff', 'staff', '运维专员', '处理工单、资产、审批接单并使用智能问答', 20),
  ('role-guest', 'guest', '访客', '使用工作台中的智能问答', 30),
  ('role-member', 'member', '员工', '提问、报故障并发起接单申请', 40);

insert into sys_permission (id, code, name) values
  ('perm-dashboard-view', 'dashboard:view', '查看看板'),
  ('perm-ticket-view', 'ticket:view', '查看工单'),
  ('perm-ticket-create', 'ticket:create', '新建工单'),
  ('perm-ticket-edit', 'ticket:edit', '编辑工单'),
  ('perm-ticket-assign', 'ticket:assign', '指派工单'),
  ('perm-ticket-transfer', 'ticket:transfer', '转派工单'),
  ('perm-ticket-close', 'ticket:close', '关闭工单'),
  ('perm-ticket-comment', 'ticket:comment', '评论工单'),
  ('perm-ticket-advance', 'ticket:advance', '提交复核工单'),
  ('perm-ticket-approve', 'ticket:approve', '复核通过工单'),
  ('perm-ticket-reject', 'ticket:reject', '驳回复核工单'),
  ('perm-asset-view', 'asset:view', '查看资产'),
  ('perm-asset-create', 'asset:create', '新增资产'),
  ('perm-asset-edit', 'asset:edit', '编辑资产'),
  ('perm-asset-claim', 'asset:claim', '领用资产'),
  ('perm-asset-scrap', 'asset:scrap', '报废资产'),
  ('perm-kb-view', 'kb:view', '查看知识库'),
  ('perm-kb-edit', 'kb:edit', '编辑知识库'),
  ('perm-auth-user-manage', 'auth:user:manage', '管理用户与身份'),
  ('perm-agent-chat', 'agent:chat', '使用智能问答'),
  ('perm-agent-task', 'agent:task', '运行智能体任务'),
  ('perm-ticket-claim', 'ticket:claim', '申请接单'),
  ('perm-ticket-claim-approve', 'ticket:claim:approve', '审批接单');

insert into sys_role_permission (role_id, permission_id) values
  -- admin：全部权限
  ('role-admin', 'perm-agent-chat'),
  ('role-admin', 'perm-agent-task'),
  ('role-admin', 'perm-auth-user-manage'),
  ('role-admin', 'perm-dashboard-view'),
  ('role-admin', 'perm-ticket-view'),
  ('role-admin', 'perm-ticket-create'),
  ('role-admin', 'perm-ticket-edit'),
  ('role-admin', 'perm-ticket-assign'),
  ('role-admin', 'perm-ticket-transfer'),
  ('role-admin', 'perm-ticket-close'),
  ('role-admin', 'perm-ticket-comment'),
  ('role-admin', 'perm-ticket-advance'),
  ('role-admin', 'perm-ticket-approve'),
  ('role-admin', 'perm-ticket-reject'),
  ('role-admin', 'perm-asset-view'),
  ('role-admin', 'perm-asset-create'),
  ('role-admin', 'perm-asset-edit'),
  ('role-admin', 'perm-asset-claim'),
  ('role-admin', 'perm-asset-scrap'),
  ('role-admin', 'perm-kb-view'),
  ('role-admin', 'perm-kb-edit'),
  ('role-admin', 'perm-ticket-claim'),
  ('role-admin', 'perm-ticket-claim-approve'),
  -- staff：运维专员
  ('role-staff', 'perm-agent-chat'),
  ('role-staff', 'perm-agent-task'),
  ('role-staff', 'perm-dashboard-view'),
  ('role-staff', 'perm-ticket-view'),
  ('role-staff', 'perm-ticket-create'),
  ('role-staff', 'perm-ticket-assign'),
  ('role-staff', 'perm-ticket-transfer'),
  ('role-staff', 'perm-ticket-advance'),
  ('role-staff', 'perm-ticket-comment'),
  ('role-staff', 'perm-asset-view'),
  ('role-staff', 'perm-asset-claim'),
  ('role-staff', 'perm-ticket-claim-approve'),
  -- guest：访客
  ('role-guest', 'perm-agent-chat'),
  -- member：员工（注册默认）
  ('role-member', 'perm-ticket-create'),
  ('role-member', 'perm-ticket-claim'),
  ('role-member', 'perm-agent-chat');

insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope) values
  ('full-home', '工作台', 'Home', '/home', 'HomeView', 'home', 'agent:chat', 1, null, 10, 'full'),
  ('full-ops', '运维', 'OpsRoot', '/ops', 'RouteView', 'ticket', null, 1, null, 20, 'full'),
  ('full-dashboard', '运维看板', 'Dashboard', '/ops/dashboard', 'DashboardView', 'dashboard', 'dashboard:view', 1, 'full-ops', 21, 'full'),
  ('full-ticket-list', '工单列表', 'TicketList', '/ops/ticket/list', 'TicketListView', null, 'ticket:view', 1, 'full-ops', 22, 'full'),
  ('full-claim', '待接工单', 'ClaimQueue', '/ops/ticket/claim', 'ClaimQueueView', null, 'ticket:claim', 1, 'full-ops', 24, 'full'),
  ('full-asset-list', '资产列表', 'AssetList', '/ops/asset/list', 'AssetListView', null, 'asset:view', 1, 'full-ops', 23, 'full'),
  ('full-kb', '知识库', 'KbRoot', '/kb', 'RouteView', 'kb', null, 1, null, 40, 'full'),
  ('full-kb-list', '文章列表', 'KbList', '/kb/list', 'KbListView', null, 'kb:view', 1, 'full-kb', 41, 'full'),
  ('full-user', '用户与身份', 'UserManagement', '/system/users', 'UserManagementView', 'user', 'auth:user:manage', 1, null, 50, 'full'),
  ('staff-home', '工作台', 'Home', '/home', 'HomeView', 'home', 'agent:chat', 1, null, 10, 'staff'),
  ('staff-ops', '运维', 'OpsRoot', '/ops', 'RouteView', 'ticket', null, 1, null, 20, 'staff'),
  ('staff-dashboard', '运维看板', 'Dashboard', '/ops/dashboard', 'DashboardView', 'dashboard', 'dashboard:view', 1, 'staff-ops', 21, 'staff'),
  ('staff-ticket-list', '工单列表', 'TicketList', '/ops/ticket/list', 'TicketListView', null, 'ticket:view', 1, 'staff-ops', 22, 'staff'),
  ('staff-asset-list', '资产列表', 'AssetList', '/ops/asset/list', 'AssetListView', null, 'asset:view', 1, 'staff-ops', 23, 'staff'),
  ('full-agent-console', '智能体工作台', 'AgentConsole', '/agent/console', 'AgentConsoleView', 'robot', 'agent:task', 1, null, 45, 'full'),
  ('staff-agent-console', '智能体工作台', 'AgentConsole', '/agent/console', 'AgentConsoleView', 'robot', 'agent:task', 1, null, 45, 'staff'),
  ('full-agent-tasks', '任务中心', 'AgentTasks', '/agent/tasks', 'AgentTasksView', 'robot', 'agent:task', 1, null, 46, 'full'),
  ('staff-agent-tasks', '任务中心', 'AgentTasks', '/agent/tasks', 'AgentTasksView', 'robot', 'agent:task', 1, null, 46, 'staff'),
  ('member-home', '工作台', 'Home', '/home', 'HomeView', 'home', 'agent:chat', 1, null, 10, 'member'),
  ('member-claim', '待接工单', 'ClaimQueue', '/ops/ticket/claim', 'ClaimQueueView', 'ticket', 'ticket:claim', 1, null, 20, 'member'),
  ('member-mine', '我的工单', 'MyTickets', '/ops/ticket/mine', 'MyTicketsView', 'ticket', 'ticket:claim', 1, null, 21, 'member'),
  ('guest-home', '工作台', 'Home', '/home', 'HomeView', 'home', 'agent:chat', 1, null, 10, 'guest');

insert into biz_ticket (id, title, description, status, priority, assignee_id, creator_id, due_date, created_at, updated_at, done_at) values
  ('A-TICKET-0001', 'TENANT-A 网络与终端巡检异常 #1', '巡检发现交换机端口丢包，需要排查链路质量。', 'pending', 'medium', 'u-tom', 'u-admin', '2026-04-25 18:00:00', '2026-04-20 09:00:00', '2026-04-20 11:00:00', null),
  ('A-TICKET-0002', 'TENANT-A VPN 访问波动 #2', '多名员工反馈 VPN 间歇性掉线，需排查网关与策略。', 'processing', 'high', 'u-jerry', 'u-admin', '2026-04-24 18:00:00', '2026-04-19 10:00:00', '2026-04-20 12:30:00', null),
  ('A-TICKET-0003', 'TENANT-A 终端补丁异常 #3', 'Windows 补丁安装失败，影响办公终端安全合规。', 'review', 'urgent', 'u-alice', 'u-staff', '2026-04-23 18:00:00', '2026-04-18 13:00:00', '2026-04-20 14:00:00', null),
  ('A-TICKET-0004', 'TENANT-A 日志采集恢复验证 #4', '采集链路已恢复，需要复核日志完整性与时间同步。', 'done', 'low', 'u-admin', 'u-staff', '2026-04-22 18:00:00', '2026-04-17 14:00:00', '2026-04-20 15:00:00', '2026-04-20 15:00:00');

insert into biz_ticket_asset_rel (ticket_id, asset_id) values
  ('A-TICKET-0001', 'ASSET-1'),
  ('A-TICKET-0001', 'ASSET-101'),
  ('A-TICKET-0002', 'ASSET-2'),
  ('A-TICKET-0002', 'ASSET-102'),
  ('A-TICKET-0003', 'ASSET-3'),
  ('A-TICKET-0004', 'ASSET-4');

insert into biz_ticket_timeline (id, ticket_id, action, operator_id, remark, from_status, to_status, created_at) values
  ('tl-a1-1', 'A-TICKET-0001', 'create', 'u-admin', '创建工单', null, 'pending', '2026-04-20 09:00:00'),
  ('tl-a2-1', 'A-TICKET-0002', 'create', 'u-admin', '创建工单', null, 'pending', '2026-04-19 10:00:00'),
  ('tl-a2-2', 'A-TICKET-0002', 'assign', 'u-admin', '列表页指派', 'pending', 'processing', '2026-04-20 12:30:00'),
  ('tl-a3-1', 'A-TICKET-0003', 'create', 'u-staff', '创建工单', null, 'pending', '2026-04-18 13:00:00'),
  ('tl-a3-2', 'A-TICKET-0003', 'advance', 'u-alice', '进入复核', 'processing', 'review', '2026-04-20 14:00:00'),
  ('tl-a4-1', 'A-TICKET-0004', 'create', 'u-staff', '创建工单', null, 'pending', '2026-04-17 14:00:00'),
  ('tl-a4-2', 'A-TICKET-0004', 'close', 'u-admin', '处理完成', 'review', 'done', '2026-04-20 15:00:00');

insert into biz_ticket_comment (id, ticket_id, author_id, content, created_at) values
  ('cm-a1-1', 'A-TICKET-0001', 'u-tom', '收到，正在排查核心交换机日志。', '2026-04-20 11:20:00'),
  ('cm-a1-2', 'A-TICKET-0001', 'u-alice', '已补充现场截图。', '2026-04-20 12:10:00');

insert into biz_ticket_attachment (id, ticket_id, name, url, size, created_at) values
  ('att-a1-1', 'A-TICKET-0001', 'switch-log.txt', '/mock-attachments/A-TICKET-0001/switch-log.txt', 20480, '2026-04-20 12:00:00');
