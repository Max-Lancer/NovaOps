-- =====================================================================
-- migration_workbench_ops.sql 的回滚脚本。
-- 恢复工作台改造前的角色文案、看板权限和菜单分组，并删除接单申请人列。
-- 若已执行 migration_ticket_assignee_flow.sql，请先执行 rollback_ticket_assignee_flow.sql。
-- =====================================================================
SET NAMES utf8mb4;

-- 1. 恢复被改写的菜单，并补回本迁移删除的分组
update sys_menu set title = 'Dashboard', path = '/dashboard', parent_id = null, sort_order = 10 where id = 'full-dashboard';
update sys_menu set title = '工单列表', path = '/ticket/list', parent_id = 'full-ticket', sort_order = 21 where id = 'full-ticket-list';
update sys_menu set title = '资产列表', path = '/asset/list', parent_id = 'full-asset', sort_order = 31 where id = 'full-asset-list';
update sys_menu set title = 'Dashboard', path = '/dashboard', parent_id = null, sort_order = 10 where id = 'staff-dashboard';
update sys_menu set title = '工单列表', path = '/ticket/list', parent_id = 'staff-ticket', sort_order = 21 where id = 'staff-ticket-list';

insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope) values
  ('full-ticket', '工单', 'TicketRoot', '/ticket', 'RouteView', 'ticket', null, 1, null, 20, 'full'),
  ('full-asset', '资产', 'AssetRoot', '/asset', 'RouteView', 'asset', null, 1, null, 30, 'full'),
  ('staff-ticket', '工单', 'TicketRoot', '/ticket', 'RouteView', 'ticket', null, 1, null, 20, 'staff'),
  ('guest-dashboard', 'Dashboard', 'Dashboard', '/dashboard', 'DashboardView', 'dashboard', 'dashboard:view', 1, null, 10, 'guest');

delete from sys_menu where id in (
  'full-home',
  'full-ops',
  'staff-home',
  'staff-ops',
  'member-home',
  'member-claim',
  'guest-home',
  'staff-asset-list'
);

-- 2. 恢复角色文案，收回接单权限，交还访客/员工的看板权限
update sys_role set name = '运维人员', description = '处理工单、资产与使用智能问答' where id = 'role-staff';
update sys_role set name = '访客', description = '只读访问授权看板与智能问答' where id = 'role-guest';
update sys_role set name = '普通成员', description = '注册用户默认身份：只读看板、提交工单与智能问答' where id = 'role-member';

insert into sys_role_permission (role_id, permission_id) values
  ('role-guest', 'perm-dashboard-view'),
  ('role-member', 'perm-dashboard-view');

delete from sys_role_permission
where permission_id in ('perm-ticket-claim', 'perm-ticket-claim-approve');

delete from sys_permission
where id in ('perm-ticket-claim', 'perm-ticket-claim-approve');

-- 3. 删除接单申请人。已写入的 claimant_id 随列一并丢弃。
alter table biz_ticket drop foreign key fk_ticket_claimant;
alter table biz_ticket drop column claimant_id;
