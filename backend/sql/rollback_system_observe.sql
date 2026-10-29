-- 撤回 migration_system_observe.sql：去掉系统详情权限、菜单，以及检查记录和访问日志表。
-- 表内已有的检查记录与访问日志会一并删除。可重复执行。
delete from sys_role_permission where permission_id = 'perm-system-observe';
delete from sys_permission where id = 'perm-system-observe';
delete from sys_menu where id in ('full-system-overview', 'full-system-logs', 'full-system');
drop table if exists sys_access_log;
drop table if exists sys_module_check;
