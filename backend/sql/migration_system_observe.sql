insert into sys_permission (id, code, name) values
  ('perm-system-observe', 'system:observe', '查看系统运行情况');

insert into sys_role_permission (role_id, permission_id) values
  ('role-admin', 'perm-system-observe');

insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope) values
  ('full-system', '系统详情', 'SystemRoot', '/system/observe', 'RouteView', 'monitor', null, 1, null, 55, 'full'),
  ('full-system-overview', '运行概览', 'SystemOverview', '/system/overview', 'SystemOverviewView', null, 'system:observe', 1, 'full-system', 56, 'full'),
  ('full-system-logs', '运行日志', 'SystemLogs', '/system/logs', 'SystemLogsView', null, 'system:observe', 1, 'full-system', 57, 'full');

create table if not exists sys_module_check (
  id varchar(64) primary key,
  batch_id varchar(64) not null,
  module_code varchar(32) not null,
  module_name varchar(64) not null,
  status varchar(16) not null,
  latency_ms int not null,
  message varchar(500) not null,
  checked_at datetime not null,
  index idx_module_check_time (checked_at),
  index idx_module_check_batch (batch_id)
);

create table if not exists sys_access_log (
  id varchar(64) primary key,
  request_id varchar(64) not null,
  method varchar(16) not null,
  path varchar(200) not null,
  http_status int not null,
  result_code int null,
  result_message varchar(500) null,
  exception_type varchar(128) null,
  duration_ms int not null,
  username varchar(64) null,
  created_at datetime not null,
  index idx_access_log_time (created_at),
  index idx_access_log_path (path)
);
