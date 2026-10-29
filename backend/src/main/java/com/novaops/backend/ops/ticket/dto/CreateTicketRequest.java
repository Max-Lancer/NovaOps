package com.novaops.backend.ops.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class CreateTicketRequest {

  @NotBlank(message = "标题与描述为必填项")
  private String title;

  @NotBlank(message = "标题与描述为必填项")
  private String description;

  private String priority;
  private String assigneeId;
  private String dueDate;
  private List<String> assetIds;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String priority) {
    this.priority = priority;
  }

  public String getAssigneeId() {
    return assigneeId;
  }

  public void setAssigneeId(String assigneeId) {
    this.assigneeId = assigneeId;
  }

  public String getDueDate() {
    return dueDate;
  }

  public void setDueDate(String dueDate) {
    this.dueDate = dueDate;
  }

  public List<String> getAssetIds() {
    return assetIds;
  }

  public void setAssetIds(List<String> assetIds) {
    this.assetIds = assetIds;
  }
}
