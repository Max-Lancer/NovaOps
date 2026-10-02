package com.novaops.backend.ops.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.auth.dto.UserOptionResponse;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.ops.ticket.dto.TicketActionRequest;
import com.novaops.backend.ops.ticket.dto.TicketDetailResponse;
import com.novaops.backend.ops.ticket.service.TicketService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TicketWriteToolsTest {

  private static final ToolContext CTX = new ToolContext("u-1", "tester", Set.of("ticket:assign"));

  private static UserOptionResponse user(String id, String username, String displayName) {
    UserOptionResponse option = new UserOptionResponse();
    option.setId(id);
    option.setUsername(username);
    option.setDisplayName(displayName);
    return option;
  }

  private static TicketDetailResponse detail(String id, String title, String status) {
    TicketDetailResponse detail = new TicketDetailResponse();
    detail.setId(id);
    detail.setTitle(title);
    detail.setStatus(status);
    return detail;
  }

  @Test
  void assignPreviewResolvesUserAndRequiresConfirmation() {
    TicketService ticketService = mock(TicketService.class);
    AuthService authService = mock(AuthService.class);
    when(authService.listUserOptions()).thenReturn(List.of(user("u-jerry", "jerry", "Jerry")));
    when(ticketService.detail(any(), eq("A-1"))).thenReturn(detail("A-1", "VPN 掉线", "pending"));
    var tool = new TicketWriteTools.TicketAssignTool(ticketService, authService);

    ToolResult result = tool.execute(CTX,
        Map.of("ticketId", "A-1", "assignee", "Jerry", "remark", "尽快处理"), false);

    assertEquals(ToolResult.Status.CONFIRM_REQUIRED, result.status());
    String preview = result.payload().toString();
    assertTrue(preview.contains("VPN 掉线"));
    assertTrue(preview.contains("Jerry"));
    assertTrue(preview.contains("pending"));
    // 预览阶段不得产生任何写操作
    verify(ticketService, never()).action(any(), anyString(), any());
  }

  @Test
  void assignFailsWhenAssigneeCannotBeResolved() {
    TicketService ticketService = mock(TicketService.class);
    AuthService authService = mock(AuthService.class);
    when(authService.listUserOptions()).thenReturn(List.of(user("u-jerry", "jerry", "Jerry")));
    var tool = new TicketWriteTools.TicketAssignTool(ticketService, authService);

    ToolResult result = tool.execute(CTX, Map.of("ticketId", "A-1", "assignee", "不存在的名字"), false);

    assertEquals(ToolResult.Status.FAILED, result.status());
    assertTrue(result.message().contains("找不到用户"));
    verify(ticketService, never()).detail(any(), anyString());
  }

  @Test
  void assignConfirmedExecutesActionWithResolvedUserId() {
    TicketService ticketService = mock(TicketService.class);
    AuthService authService = mock(AuthService.class);
    when(authService.listUserOptions()).thenReturn(List.of(user("u-jerry", "jerry", "Jerry")));
    when(ticketService.action(any(), eq("A-1"), any())).thenReturn(detail("A-1", "VPN 掉线", "processing"));
    var tool = new TicketWriteTools.TicketAssignTool(ticketService, authService);

    ToolResult result = tool.execute(CTX,
        Map.of("ticketId", "A-1", "assignee", "jerry", "remark", "尽快"), true);

    assertEquals(ToolResult.Status.OK, result.status());
    ArgumentCaptor<TicketActionRequest> captor = ArgumentCaptor.forClass(TicketActionRequest.class);
    verify(ticketService).action(any(), eq("A-1"), captor.capture());
    assertEquals("assign", captor.getValue().getAction());
    assertEquals("u-jerry", captor.getValue().getAssigneeId());
    assertEquals("尽快", captor.getValue().getRemark());
  }

  @Test
  void assignTranslatesBusinessExceptionToFailed() {
    TicketService ticketService = mock(TicketService.class);
    AuthService authService = mock(AuthService.class);
    when(authService.listUserOptions()).thenReturn(List.of(user("u-jerry", "jerry", "Jerry")));
    when(ticketService.detail(any(), eq("A-404"))).thenThrow(new BusinessException(404, "工单不存在"));
    var tool = new TicketWriteTools.TicketAssignTool(ticketService, authService);

    ToolResult result = tool.execute(CTX, Map.of("ticketId", "A-404", "assignee", "jerry"), false);

    assertEquals(ToolResult.Status.FAILED, result.status());
    assertTrue(result.message().contains("工单不存在"));
  }

  @Test
  void missingTicketIdFailsWithoutTouchingServices() {
    TicketService ticketService = mock(TicketService.class);
    AuthService authService = mock(AuthService.class);
    var tool = new TicketWriteTools.TicketAssignTool(ticketService, authService);

    assertEquals(ToolResult.Status.FAILED, tool.execute(CTX, Map.of(), false).status());
    verify(ticketService, never()).detail(any(), anyString());
    verify(authService, never()).listUserOptions();
  }

  @Test
  void closeToolDoesNotRequireAssigneeAndExecutesOnConfirm() {
    TicketService ticketService = mock(TicketService.class);
    AuthService authService = mock(AuthService.class);
    when(ticketService.detail(any(), eq("A-2"))).thenReturn(detail("A-2", "补丁异常", "processing"));
    when(ticketService.action(any(), eq("A-2"), any())).thenReturn(detail("A-2", "补丁异常", "done"));
    var tool = new TicketWriteTools.TicketCloseTool(ticketService, authService);

    ToolResult preview = tool.execute(CTX, Map.of("ticketId", "A-2"), false);
    assertEquals(ToolResult.Status.CONFIRM_REQUIRED, preview.status());

    ToolResult result = tool.execute(CTX, Map.of("ticketId", "A-2", "remark", "已恢复"), true);
    assertEquals(ToolResult.Status.OK, result.status());
    ArgumentCaptor<TicketActionRequest> captor = ArgumentCaptor.forClass(TicketActionRequest.class);
    verify(ticketService).action(any(), eq("A-2"), captor.capture());
    assertEquals("close", captor.getValue().getAction());
  }

  @Test
  void commentToolTwoPhaseFlow() {
    TicketService ticketService = mock(TicketService.class);
    var tool = new TicketWriteTools.TicketCommentTool(ticketService);

    ToolResult preview = tool.execute(CTX, Map.of("ticketId", "A-1", "content", "已更换网关"), false);
    assertEquals(ToolResult.Status.CONFIRM_REQUIRED, preview.status());

    ToolResult result = tool.execute(CTX, Map.of("ticketId", "A-1", "content", "已更换网关"), true);
    assertEquals(ToolResult.Status.OK, result.status());
    verify(ticketService).createComment(any(), eq("A-1"), any());
  }
}
