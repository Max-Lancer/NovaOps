delete from sys_role_permission where permission_id = 'perm-system-observe';
delete from sys_permission where id = 'perm-system-observe';
delete from sys_menu where id in ('full-system-overview', 'full-system-logs', 'full-system');
drop table if exists sys_access_log;
drop table if exists sys_module_check;
