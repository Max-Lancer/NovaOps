package com.novaops.backend.ops.ticket.model;

import java.time.LocalDateTime;

public class TicketRecord {

  private String id;
  private String title;
  private String description;
  private String status;
  private String priority;
  private String assigneeId;
  private String assigneeName;
  private String claimantId;
  private String claimantName;
  private String creatorId;
  private String creatorName;
  private LocalDateTime dueDate;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime doneAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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

  public String getAssigneeName() {
    return assigneeName;
  }

  public void setAssigneeName(String assigneeName) {
    this.assigneeName = assigneeName;
  }

  public String getClaimantId() {
    return claimantId;
  }

  public void setClaimantId(String claimantId) {
    this.claimantId = claimantId;
  }

  public String getClaimantName() {
    return claimantName;
  }

  public void setClaimantName(String claimantName) {
    this.claimantName = claimantName;
  }

  public String getCreatorId() {
    return creatorId;
  }

  public void setCreatorId(String creatorId) {
    this.creatorId = creatorId;
  }

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String creatorName) {
    this.creatorName = creatorName;
  }

  public LocalDateTime getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDateTime dueDate) {
    this.dueDate = dueDate;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public LocalDateTime getDoneAt() {
    return doneAt;
  }

  public void setDoneAt(LocalDateTime doneAt) {
    this.doneAt = doneAt;
  }
}
