-- =============================================================================
-- 系统详情（运行概览 + 运行日志）增量迁移
--
-- 【使用前必读】已有数据库在打开「系统详情」之前必须先执行本脚本。
-- 未执行时，/api/system/health 与 /api/system/logs 会因缺少
-- system:observe 权限、sys_module_check、sys_access_log 而失败。
--
-- 全新库请只导入 novaops_init.sql（已包含同等结构），不必再依赖本脚本。
-- Docker 数据卷若在本功能加入前已经初始化，compose 不会改旧卷，仍需手工执行本脚本。
--
-- 本脚本可重复执行：权限和菜单按主键/编码存在性插入，表使用 IF NOT EXISTS，
-- checked_at 若仍是秒精度会升到 datetime(3)。
-- 撤回见 rollback_system_observe.sql。
-- =============================================================================

insert into sys_permission (id, code, name)
select 'perm-system-observe', 'system:observe', '查看系统运行情况'
where not exists (
  select 1 from sys_permission
  where id = 'perm-system-observe' or code = 'system:observe'
);

insert into sys_role_permission (role_id, permission_id)
select 'role-admin', 'perm-system-observe'
where not exists (
  select 1 from sys_role_permission
  where role_id = 'role-admin' and permission_id = 'perm-system-observe'
);

insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope)
select 'full-system', '系统详情', 'SystemRoot', '/system/observe', 'RouteView', 'monitor', null, 1, null, 55, 'full'
where not exists (select 1 from sys_menu where id = 'full-system');

insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope)
select 'full-system-overview', '运行概览', 'SystemOverview', '/system/overview', 'SystemOverviewView', null, 'system:observe', 1, 'full-system', 56, 'full'
where not exists (select 1 from sys_menu where id = 'full-system-overview');

insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope)
select 'full-system-logs', '运行日志', 'SystemLogs', '/system/logs', 'SystemLogsView', null, 'system:observe', 1, 'full-system', 57, 'full'
where not exists (select 1 from sys_menu where id = 'full-system-logs');

create table if not exists sys_module_check (
  id varchar(64) primary key,
  batch_id varchar(64) not null,
  module_code varchar(32) not null,
  module_name varchar(64) not null,
  status varchar(16) not null,
  latency_ms int not null,
  message varchar(500) not null,
  checked_at datetime(3) not null,
  index idx_module_check_time (checked_at),
  index idx_module_check_batch (batch_id)
);

-- 早期草稿若已建成秒精度 datetime，升到毫秒，避免同一秒内连续检查撞时间。
set @checked_at_precision = (
  select datetime_precision
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sys_module_check'
    and column_name = 'checked_at'
);

set @widen_checked_at_sql = if(
  @checked_at_precision is null or @checked_at_precision >= 3,
  'select ''sys_module_check.checked_at already datetime(3)'' as migration_status',
  'alter table sys_module_check modify column checked_at datetime(3) not null'
);

prepare widen_checked_at_stmt from @widen_checked_at_sql;
execute widen_checked_at_stmt;
deallocate prepare widen_checked_at_stmt;

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
