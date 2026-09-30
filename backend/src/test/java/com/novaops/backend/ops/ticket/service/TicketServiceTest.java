package com.novaops.backend.ops.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.auth.model.UserRecord;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.ops.ticket.dto.CreateCommentRequest;
import com.novaops.backend.ops.ticket.dto.TicketActionRequest;
import com.novaops.backend.ops.ticket.dto.TicketDetailResponse;
import com.novaops.backend.ops.ticket.mapper.TicketMapper;
import com.novaops.backend.ops.ticket.model.TicketRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketServiceTest {

  private static final String TICKET_ID = "A-TICKET-0001";
  private static final CurrentSession SESSION = new CurrentSession("u-admin", "admin", "System Admin");

  private TicketMapper ticketMapper;
  private AuthService authService;
  private TicketService ticketService;

  @BeforeEach
  void setUp() {
    ticketMapper = mock(TicketMapper.class);
    authService = mock(AuthService.class);
    ticketService = new TicketService(ticketMapper, authService, mock(TicketAttachmentStorage.class));
  }

  private TicketRecord recordOf(String status) {
    TicketRecord record = new TicketRecord();
    record.setId(TICKET_ID);
    record.setTitle("工单");
    record.setDescription("描述");
    record.setStatus(status);
    record.setPriority("medium");
    return record;
  }

  private TicketActionRequest request(String action, String assigneeId) {
    TicketActionRequest request = new TicketActionRequest();
    request.setAction(action);
    request.setAssigneeId(assigneeId);
    return request;
  }

  private void stubCommon(TicketRecord record, String assigneeId) {
    when(ticketMapper.findTicket(TICKET_ID)).thenReturn(record);
    when(ticketMapper.listAssetIds(TICKET_ID)).thenReturn(List.of());
    when(ticketMapper.listTimeline(TICKET_ID)).thenReturn(List.of());
    when(ticketMapper.listComments(TICKET_ID)).thenReturn(List.of());
    when(ticketMapper.listAttachments(TICKET_ID)).thenReturn(List.of());
    if (assigneeId != null) {
      UserRecord user = new UserRecord();
      user.setId(assigneeId);
      when(authService.requireEnabledUser(assigneeId)).thenReturn(user);
    }
    when(ticketMapper.transitionTicket(any(), any())).thenReturn(1);
  }

  @Test
  void assignOnPendingAssignsAndAdvancesToProcessing() {
    TicketRecord record = recordOf("pending");
    stubCommon(record, "u-staff");

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("assign", "u-staff"));

    assertThat(response.getStatus()).isEqualTo("processing");
    assertThat(response.getAssigneeId()).isEqualTo("u-staff");
    verify(ticketMapper).transitionTicket(record, "pending");
    verify(ticketMapper, never()).updateTicket(any());
  }

  @Test
  void assignOnProcessingIsRejected() {
    stubCommon(recordOf("processing"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("assign", "u-staff")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅待处理的工单可指派");
    verify(ticketMapper, never()).transitionTicket(any(), any());
  }

  @Test
  void assignOnReviewIsRejected() {
    stubCommon(recordOf("review"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("assign", "u-staff")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅待处理的工单可指派");
  }

  @Test
  void assignOnDoneIsRejected() {
    stubCommon(recordOf("done"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("assign", "u-staff")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅待处理的工单可指派");
  }

  @Test
  void assignWithoutAssigneeIdIsRejected() {
    stubCommon(recordOf("pending"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("assign", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("请选择指派对象");
  }

  @Test
  void transferOnProcessingKeepsStatusAndSwitchesAssignee() {
    TicketRecord record = recordOf("processing");
    stubCommon(record, "u-jerry");

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("transfer", "u-jerry"));

    assertThat(response.getStatus()).isEqualTo("processing");
    assertThat(response.getAssigneeId()).isEqualTo("u-jerry");
  }

  @Test
  void transferOnReviewRollsBackToProcessing() {
    TicketRecord record = recordOf("review");
    stubCommon(record, "u-tom");

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("transfer", "u-tom"));

    assertThat(response.getStatus()).isEqualTo("processing");
    assertThat(response.getAssigneeId()).isEqualTo("u-tom");
  }

  @Test
  void transferOnPendingIsRejected() {
    stubCommon(recordOf("pending"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("transfer", "u-tom")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅处理中或待复核的工单可转派");
  }

  @Test
  void transferOnDoneIsRejected() {
    stubCommon(recordOf("done"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("transfer", "u-tom")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅处理中或待复核的工单可转派");
  }

  @Test
  void transferWithoutAssigneeIdIsRejected() {
    stubCommon(recordOf("processing"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("transfer", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("请选择指派对象");
  }

  @Test
  void advanceOnProcessingMovesToReview() {
    stubCommon(recordOf("processing"), null);

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("advance", null));

    assertThat(response.getStatus()).isEqualTo("review");
  }

  @Test
  void advanceOnReviewIsRejected() {
    stubCommon(recordOf("review"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("advance", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅处理中的工单可提交复核");
  }

  @Test
  void approveOnReviewMovesToDone() {
    stubCommon(recordOf("review"), null);

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("approve", null));

    assertThat(response.getStatus()).isEqualTo("done");
  }

  @Test
  void approveOnProcessingIsRejected() {
    stubCommon(recordOf("processing"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("approve", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅待复核的工单可复核通过");
  }

  @Test
  void rejectOnReviewMovesBackToProcessing() {
    stubCommon(recordOf("review"), null);

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("reject", null));

    assertThat(response.getStatus()).isEqualTo("processing");
  }

  @Test
  void rejectOnProcessingIsRejected() {
    stubCommon(recordOf("processing"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("reject", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅待复核的工单可驳回");
  }

  @Test
  void closeOnProcessingMovesToDone() {
    stubCommon(recordOf("processing"), null);

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("close", null));

    assertThat(response.getStatus()).isEqualTo("done");
  }

  @Test
  void closeOnReviewMovesToDone() {
    stubCommon(recordOf("review"), null);

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("close", null));

    assertThat(response.getStatus()).isEqualTo("done");
  }

  @Test
  void closeOnPendingIsRejected() {
    stubCommon(recordOf("pending"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("close", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("待处理的工单不可直接关闭");
  }

  @Test
  void closeOnDoneIsRejected() {
    stubCommon(recordOf("done"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("close", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不可重复关闭");
  }

  @Test
  void claimOnPendingRecordsClaimant() {
    TicketRecord record = recordOf("pending");
    stubCommon(record, null);

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("claim", null));

    assertThat(response.getStatus()).isEqualTo("claiming");
    assertThat(record.getClaimantId()).isEqualTo(SESSION.getUserId());
  }

  @Test
  void secondClaimIsRejected() {
    stubCommon(recordOf("claiming"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("claim", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅待处理的工单可申请接单");
  }

  @Test
  void approveClaimAssignsClaimant() {
    TicketRecord record = recordOf("claiming");
    record.setClaimantId("u-member");
    stubCommon(record, "u-member");

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("approve_claim", null));

    assertThat(response.getStatus()).isEqualTo("processing");
    assertThat(response.getAssigneeId()).isEqualTo("u-member");
  }

  @Test
  void rejectClaimReturnsToPending() {
    TicketRecord record = recordOf("claiming");
    record.setClaimantId("u-member");
    stubCommon(record, null);

    TicketDetailResponse response = ticketService.action(SESSION, TICKET_ID, request("reject_claim", null));

    assertThat(response.getStatus()).isEqualTo("pending");
    assertThat(record.getClaimantId()).isNull();
  }

  @Test
  void claimantCannotApproveOwnClaim() {
    TicketRecord record = recordOf("claiming");
    record.setClaimantId(SESSION.getUserId());
    stubCommon(record, null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("approve_claim", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不能审批自己的接单申请");
  }

  @Test
  void closeOnClaimingIsRejected() {
    stubCommon(recordOf("claiming"), null);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("close", null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("接单待审的工单不可直接关闭");
    verify(ticketMapper, never()).transitionTicket(any(), any());
  }

  @Test
  void transitionConflictReturns409() {
    TicketRecord record = recordOf("pending");
    stubCommon(record, null);
    when(ticketMapper.transitionTicket(any(), eq("pending"))).thenReturn(0);

    assertThatThrownBy(() -> ticketService.action(SESSION, TICKET_ID, request("claim", null)))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getCode()).isEqualTo(409);
          assertThat(ex.getMessage()).contains("状态已变化");
        });
    verify(ticketMapper, never()).insertTimeline(any());
  }

  @Test
  void memberClaimThenStaffApproveThenMemberAdvance() {
    CurrentSession member = new CurrentSession("u-member", "member", "员工");
    CurrentSession staff = new CurrentSession("u-staff", "staff", "运维专员");
    TicketRecord record = recordOf("pending");
    stubCommon(record, "u-member");
    doThrow(new BusinessException(403, "无权限执行该操作"))
        .when(authService).requirePermission(member, "ticket:view");
    doThrow(new BusinessException(403, "无权限执行该操作"))
        .when(authService).requirePermission(member, "ticket:comment");
    doThrow(new BusinessException(403, "无权限执行该操作"))
        .when(authService).requirePermission(member, "ticket:advance");

    assertThatThrownBy(() -> ticketService.detail(member, TICKET_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("无权限");

    TicketDetailResponse claimed = ticketService.action(member, TICKET_ID, request("claim", null));
    assertThat(claimed.getStatus()).isEqualTo("claiming");
    assertThat(claimed.getClaimantId()).isEqualTo("u-member");

    TicketDetailResponse approved = ticketService.action(staff, TICKET_ID, request("approve_claim", null));
    assertThat(approved.getStatus()).isEqualTo("processing");
    assertThat(approved.getAssigneeId()).isEqualTo("u-member");

    assertThat(ticketService.detail(member, TICKET_ID).getId()).isEqualTo(TICKET_ID);

    CreateCommentRequest comment = new CreateCommentRequest();
    comment.setContent("已开始处理");
    assertThat(ticketService.createComment(member, TICKET_ID, comment).getContent()).isEqualTo("已开始处理");
    verify(ticketMapper).touchUpdatedAt(eq(TICKET_ID), any());
    verify(ticketMapper, never()).updateTicket(any());

    TicketDetailResponse advanced = ticketService.action(member, TICKET_ID, request("advance", null));
    assertThat(advanced.getStatus()).isEqualTo("review");
    verify(authService, never()).requirePermission(member, "ticket:advance");
  }
}
