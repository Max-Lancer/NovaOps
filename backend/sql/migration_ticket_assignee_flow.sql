-- 接单通过后的负责人入口，以及看板使用的完成时间。
-- 依赖 migration_workbench_ops.sql。全新安装直接使用 novaops_init.sql。
-- 只能执行一次：重复执行会因列或菜单主键已存在而失败。
-- 回滚见 rollback_ticket_assignee_flow.sql。

alter table biz_ticket
  add column done_at datetime null after updated_at,
  add index idx_ticket_done_at (done_at);

-- 完成时间取时间线里进入 done 的时刻；没有时间线时退回 updated_at。
update biz_ticket t
join (
  select ticket_id, max(created_at) as done_at
  from biz_ticket_timeline
  where to_status = 'done'
  group by ticket_id
) tl on tl.ticket_id = t.id
set t.done_at = tl.done_at
where t.status = 'done' and t.done_at is null;

update biz_ticket
set done_at = updated_at
where status = 'done' and done_at is null;

insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope) values
  ('full-claim', '待接工单', 'ClaimQueue', '/ops/ticket/claim', 'ClaimQueueView', null, 'ticket:claim', 1, 'full-ops', 24, 'full'),
  ('member-mine', '我的工单', 'MyTickets', '/ops/ticket/mine', 'MyTicketsView', 'ticket', 'ticket:claim', 1, null, 21, 'member');
