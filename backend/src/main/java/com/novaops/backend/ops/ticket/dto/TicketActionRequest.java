package com.novaops.backend.ops.ticket.dto;

import jakarta.validation.constraints.NotBlank;

public class TicketActionRequest {

  @NotBlank(message = "action 不能为空")
  private String action;

  private String assigneeId;
  private String remark;

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getAssigneeId() {
    return assigneeId;
  }

  public void setAssigneeId(String assigneeId) {
    this.assigneeId = assigneeId;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }
}
