package com.novaops.backend.ops.tools;

import com.novaops.backend.agent.engine.AgentTool;
import com.novaops.backend.agent.tools.ToolArgs;
import com.novaops.backend.agent.engine.AgentToolExecutor;
import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.agent.engine.ToolSchema;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.ops.ticket.dto.TicketDetailResponse;
import com.novaops.backend.ops.ticket.dto.TicketListItemResponse;
import com.novaops.backend.ops.ticket.dto.TicketTimelineItemResponse;
import com.novaops.backend.ops.ticket.service.TicketService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 工单只读工具。所有数据访问经由 TicketService（状态机与业务校验所在层）。
 */
public final class TicketReadTools {

  private TicketReadTools() {
  }

  static CurrentSession toSession(ToolContext ctx) {
    return new CurrentSession(ctx.userId(), ctx.username(), ctx.username());
  }

  @Component
  @AgentTool(name = "ticket.search", title = "检索工单", description = "按状态、优先级或关键词检索工单列表",
      permission = "ticket:view")
  public static class TicketSearchTool implements AgentToolExecutor {

    private final TicketService ticketService;

    public TicketSearchTool(TicketService ticketService) {
      this.ticketService = ticketService;
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      var query = new com.novaops.backend.ops.ticket.dto.TicketListQuery();
      query.setStatus(ToolArgs.asString(args, "status"));
      query.setPriority(ToolArgs.asString(args, "priority"));
      query.setKeyword(ToolArgs.asString(args, "keyword"));
      int maxResults = ToolArgs.asInt(args, "maxResults", 10, 1, 20);
      query.setPage(1L);
      query.setPageSize((long) maxResults);
      try {
        var page = ticketService.list(toSession(ctx), query);
        if (page.getList() == null || page.getList().isEmpty()) {
          return ToolResult.empty("没有匹配的工单");
        }
        List<Map<String, Object>> tickets = new ArrayList<>();
        for (TicketListItemResponse item : page.getList()) {
          Map<String, Object> ticket = new LinkedHashMap<>();
          ticket.put("id", item.getId());
          ticket.put("title", item.getTitle());
          ticket.put("status", item.getStatus());
          ticket.put("priority", item.getPriority());
          ticket.put("assigneeName", item.getAssigneeName());
          ticket.put("updatedAt", item.getUpdatedAt());
          tickets.add(ticket);
        }
        return ToolResult.ok(Map.of(
            "total", page.getTotal(),
            "returned", tickets.size(),
            "tickets", tickets));
      } catch (com.novaops.backend.common.exception.BusinessException ex) {
        return ToolResult.failed(ex.getMessage());
      }
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object()
          .enumString("status", "工单状态过滤", List.of("pending", "processing", "review", "done"), false)
          .enumString("priority", "优先级过滤", List.of("low", "medium", "high", "urgent"), false)
          .string("keyword", "标题/描述关键词", false)
          .integer("maxResults", "返回条数上限（1-20，默认 10）", false);
    }
  }

  @Component
  @AgentTool(name = "ticket.detail", title = "查看工单详情", description = "查看工单详情、流转时间线与评论",
      permission = "ticket:view")
  public static class TicketDetailTool implements AgentToolExecutor {

    private final TicketService ticketService;

    public TicketDetailTool(TicketService ticketService) {
      this.ticketService = ticketService;
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      String ticketId;
      try {
        ticketId = ToolArgs.requireString(args, "ticketId");
      } catch (IllegalArgumentException ex) {
        return ToolResult.failed(ex.getMessage());
      }
      try {
        TicketDetailResponse detail = ticketService.detail(toSession(ctx), ticketId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        if (detail.getTimeline() != null) {
          detail.getTimeline().stream()
              .skip(Math.max(0, detail.getTimeline().size() - 5))
              .forEach(item -> timeline.add(timelineView(item)));
        }
        List<Map<String, Object>> comments = new ArrayList<>();
        if (detail.getComments() != null) {
          detail.getComments().stream()
              .limit(3)
              .forEach(comment -> comments.add(Map.of(
                  "author", comment.getAuthorName() == null ? "" : comment.getAuthorName(),
                  "content", comment.getContent() == null ? "" : comment.getContent())));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", detail.getId());
        payload.put("title", detail.getTitle());
        payload.put("status", detail.getStatus());
        payload.put("priority", detail.getPriority());
        payload.put("assigneeName", detail.getAssigneeName());
        payload.put("creatorName", detail.getCreatorName());
        payload.put("description", detail.getDescription());
        payload.put("assetIds", detail.getAssetIds());
        payload.put("recentTimeline", timeline);
        payload.put("recentComments", comments);
        return ToolResult.ok(payload);
      } catch (com.novaops.backend.common.exception.BusinessException ex) {
        return ToolResult.failed(ex.getMessage());
      }
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object().string("ticketId", "工单 ID，如 A-TICKET-0001", true);
    }

    private static Map<String, Object> timelineView(TicketTimelineItemResponse item) {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("action", item.getAction());
      view.put("operator", item.getOperatorName());
      view.put("fromStatus", item.getFromStatus());
      view.put("toStatus", item.getToStatus());
      view.put("createdAt", item.getCreatedAt());
      return view;
    }
  }
}
