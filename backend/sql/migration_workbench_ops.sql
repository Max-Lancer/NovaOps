-- 工作台入口与运维模块：接单申请人、菜单分组、员工菜单范围。
-- 在已有库上执行；全新安装直接使用 novaops_init.sql。
-- 只能执行一次：重复执行会因列或主键已存在而失败。回滚见 rollback_workbench_ops.sql。
-- 若已继续执行 migration_ticket_assignee_flow.sql，请先回滚后者。

alter table biz_ticket
  add column claimant_id varchar(64) null after assignee_id,
  add constraint fk_ticket_claimant foreign key (claimant_id) references sys_user (id);

update sys_role set name = '运维专员', description = '处理工单、资产、审批接单并使用智能问答' where id = 'role-staff';
update sys_role set name = '访客', description = '使用工作台中的智能问答' where id = 'role-guest';
update sys_role set name = '员工', description = '提问、报故障并发起接单申请' where id = 'role-member';

insert into sys_permission (id, code, name) values
  ('perm-ticket-claim', 'ticket:claim', '申请接单'),
  ('perm-ticket-claim-approve', 'ticket:claim:approve', '审批接单');

insert into sys_role_permission (role_id, permission_id) values
  ('role-admin', 'perm-ticket-claim'),
  ('role-admin', 'perm-ticket-claim-approve'),
  ('role-staff', 'perm-ticket-claim-approve'),
  ('role-member', 'perm-ticket-claim');

delete from sys_role_permission where role_id = 'role-guest' and permission_id = 'perm-dashboard-view';
delete from sys_role_permission where role_id = 'role-member' and permission_id = 'perm-dashboard-view';

insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope) values
  ('full-home', '工作台', 'Home', '/home', 'HomeView', 'home', 'agent:chat', 1, null, 10, 'full'),
  ('full-ops', '运维', 'OpsRoot', '/ops', 'RouteView', 'ticket', null, 1, null, 20, 'full'),
  ('staff-home', '工作台', 'Home', '/home', 'HomeView', 'home', 'agent:chat', 1, null, 10, 'staff'),
  ('staff-ops', '运维', 'OpsRoot', '/ops', 'RouteView', 'ticket', null, 1, null, 20, 'staff'),
  ('member-home', '工作台', 'Home', '/home', 'HomeView', 'home', 'agent:chat', 1, null, 10, 'member'),
  ('member-claim', '待接工单', 'ClaimQueue', '/ops/ticket/claim', 'ClaimQueueView', 'ticket', 'ticket:claim', 1, null, 20, 'member'),
  ('guest-home', '工作台', 'Home', '/home', 'HomeView', 'home', 'agent:chat', 1, null, 10, 'guest');

update sys_menu set title = '运维看板', path = '/ops/dashboard', parent_id = 'full-ops', sort_order = 21 where id = 'full-dashboard';
update sys_menu set title = '工单列表', path = '/ops/ticket/list', parent_id = 'full-ops', sort_order = 22 where id = 'full-ticket-list';
update sys_menu set title = '资产列表', path = '/ops/asset/list', parent_id = 'full-ops', sort_order = 23 where id = 'full-asset-list';
update sys_menu set title = '运维看板', path = '/ops/dashboard', parent_id = 'staff-ops', sort_order = 21 where id = 'staff-dashboard';
update sys_menu set title = '工单列表', path = '/ops/ticket/list', parent_id = 'staff-ops', sort_order = 22 where id = 'staff-ticket-list';

insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope) values
  ('staff-asset-list', '资产列表', 'AssetList', '/ops/asset/list', 'AssetListView', null, 'asset:view', 1, 'staff-ops', 23, 'staff');

delete from sys_menu where id in ('full-ticket', 'full-asset', 'staff-ticket', 'guest-dashboard');
