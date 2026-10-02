package com.novaops.backend.ops.tools;

import com.novaops.backend.agent.engine.AgentTool;
import com.novaops.backend.agent.tools.ToolArgs;
import com.novaops.backend.agent.engine.AgentToolCategory;
import com.novaops.backend.agent.engine.AgentToolExecutor;
import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.agent.engine.ToolSchema;
import com.novaops.backend.auth.dto.UserOptionResponse;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.ops.ticket.dto.TicketActionRequest;
import com.novaops.backend.ops.ticket.dto.TicketDetailResponse;
import com.novaops.backend.ops.ticket.service.TicketService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 工单写操作工具（两段式人工确认）。
 *
 * <p>confirmed=false 时只做参数校验、目标用户解析与工单存在性检查（全部只读），
 * 返回效果预览等待人工确认；confirmed=true 才真正调用 TicketService 执行。
 * 状态机与动作级权限由 TicketService 强制执行——工具层不复制状态机规则，
 * 预览中展示当前状态供用户自行判断。
 */
public final class TicketWriteTools {

  private TicketWriteTools() {
  }

  private static CurrentSession toSession(ToolContext ctx) {
    return new CurrentSession(ctx.userId(), ctx.username(), ctx.username());
  }

  private static String missingArg(String key) {
    return "缺少必填参数：" + key;
  }

  abstract static class TicketActionTool implements AgentToolExecutor {

    protected final TicketService ticketService;
    protected final AuthService authService;

    TicketActionTool(TicketService ticketService, AuthService authService) {
      this.ticketService = ticketService;
      this.authService = authService;
    }

    protected abstract String action();

    protected abstract boolean requireAssignee();

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      String ticketId = ToolArgs.asString(args, "ticketId");
      if (ticketId == null) {
        return ToolResult.failed(missingArg("ticketId"));
      }
      String remark = ToolArgs.asString(args, "remark");
      String assigneeInput = ToolArgs.asString(args, "assignee");
      UserOptionResponse assignee = null;
      if (requireAssignee()) {
        if (assigneeInput == null) {
          return ToolResult.failed(missingArg("assignee"));
        }
        assignee = resolveAssignee(assigneeInput);
        if (assignee == null) {
          return ToolResult.failed("找不到用户：" + assigneeInput + "，请用用户的账号或姓名");
        }
      }
      try {
        if (!confirmed) {
          TicketDetailResponse detail = ticketService.detail(toSession(ctx), ticketId);
          if (detail == null) {
            return ToolResult.failed("工单不存在：" + ticketId);
          }
          Map<String, Object> preview = new LinkedHashMap<>();
          preview.put("action", action());
          preview.put("ticketId", ticketId);
          preview.put("title", detail.getTitle());
          preview.put("currentStatus", detail.getStatus());
          if (assignee != null) {
            preview.put("assignee", assignee.getDisplayName() == null
                ? assignee.getUsername() : assignee.getDisplayName());
          }
          if (remark != null) {
            preview.put("remark", remark);
          }
          preview.put("note", "确认后才会执行，可拒绝");
          return ToolResult.needsConfirmation(preview);
        }
        TicketActionRequest request = new TicketActionRequest();
        request.setAction(action());
        request.setAssigneeId(assignee == null ? null : assignee.getId());
        request.setRemark(remark);
        TicketDetailResponse updated = ticketService.action(toSession(ctx), ticketId, request);
        return ToolResult.ok(Map.of(
            "done", true,
            "ticketId", ticketId,
            "action", action(),
            "newStatus", updated.getStatus() == null ? "" : updated.getStatus()));
      } catch (BusinessException ex) {
        return ToolResult.failed(ex.getMessage());
      }
    }

    private UserOptionResponse resolveAssignee(String assignee) {
      List<UserOptionResponse> options = authService.listUserOptions();
      if (options == null) {
        return null;
      }
      for (UserOptionResponse option : options) {
        if (assignee.equalsIgnoreCase(option.getUsername())
            || (option.getDisplayName() != null && assignee.equalsIgnoreCase(option.getDisplayName()))
            || assignee.equals(option.getId())) {
          return option;
        }
      }
      return null;
    }
  }

  @Component
  @AgentTool(name = "ticket.assign", title = "指派工单", description = "把待处理（pending）的工单指派给指定人员",
      permission = "ticket:assign", category = AgentToolCategory.WRITE)
  public static class TicketAssignTool extends TicketActionTool {

    public TicketAssignTool(TicketService ticketService, AuthService authService) {
      super(ticketService, authService);
    }

    @Override
    protected String action() {
      return "assign";
    }

    @Override
    protected boolean requireAssignee() {
      return true;
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object()
          .string("ticketId", "工单 ID，如 A-TICKET-0001", true)
          .string("assignee", "指派对象：账号、姓名或用户 ID", true)
          .string("remark", "备注（可选）", false);
    }
  }

  @Component
  @AgentTool(name = "ticket.transfer", title = "转派工单", description = "把处理中或待复核的工单转派给其他人员",
      permission = "ticket:transfer", category = AgentToolCategory.WRITE)
  public static class TicketTransferTool extends TicketActionTool {

    public TicketTransferTool(TicketService ticketService, AuthService authService) {
      super(ticketService, authService);
    }

    @Override
    protected String action() {
      return "transfer";
    }

    @Override
    protected boolean requireAssignee() {
      return true;
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object()
          .string("ticketId", "工单 ID，如 A-TICKET-0001", true)
          .string("assignee", "转派对象：账号、姓名或用户 ID", true)
          .string("remark", "备注（可选）", false);
    }
  }

  @Component
  @AgentTool(name = "ticket.close", title = "关闭工单", description = "关闭处理中或待复核的工单",
      permission = "ticket:close", category = AgentToolCategory.WRITE)
  public static class TicketCloseTool extends TicketActionTool {

    public TicketCloseTool(TicketService ticketService, AuthService authService) {
      super(ticketService, authService);
    }

    @Override
    protected String action() {
      return "close";
    }

    @Override
    protected boolean requireAssignee() {
      return false;
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object()
          .string("ticketId", "工单 ID，如 A-TICKET-0001", true)
          .string("remark", "关闭原因（可选）", false);
    }
  }

  @Component
  @AgentTool(name = "ticket.comment", title = "评论工单", description = "给工单添加一条处置备注",
      permission = "ticket:comment", category = AgentToolCategory.WRITE)
  public static class TicketCommentTool implements AgentToolExecutor {

    private final TicketService ticketService;

    public TicketCommentTool(TicketService ticketService) {
      this.ticketService = ticketService;
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      String ticketId = ToolArgs.asString(args, "ticketId");
      String content = ToolArgs.asString(args, "content");
      if (ticketId == null) {
        return ToolResult.failed(missingArg("ticketId"));
      }
      if (content == null) {
        return ToolResult.failed(missingArg("content"));
      }
      try {
        if (!confirmed) {
          return ToolResult.needsConfirmation(Map.of(
              "action", "comment",
              "ticketId", ticketId,
              "content", content,
              "note", "确认后才会执行，可拒绝"));
        }
        var request = new com.novaops.backend.ops.ticket.dto.CreateCommentRequest();
        request.setContent(content);
        ticketService.createComment(toSession(ctx), ticketId, request);
        return ToolResult.ok(Map.of("done", true, "ticketId", ticketId, "action", "comment"));
      } catch (BusinessException ex) {
        return ToolResult.failed(ex.getMessage());
      }
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object()
          .string("ticketId", "工单 ID，如 A-TICKET-0001", true)
          .string("content", "评论内容", true);
    }
  }
}
