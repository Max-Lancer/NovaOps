-- =====================================================================
-- migration_ticket_assignee_flow.sql 的回滚脚本。
-- 删除完成时间列，以及管理员待接工单、员工「我的工单」菜单。
-- =====================================================================
SET NAMES utf8mb4;

delete from sys_menu where id in ('full-claim', 'member-mine');

alter table biz_ticket drop index idx_ticket_done_at;
alter table biz_ticket drop column done_at;
