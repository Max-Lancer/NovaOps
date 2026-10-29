package com.novaops.backend.ops.ticket.dto;

import java.util.List;

public class TicketDetailResponse extends TicketListItemResponse {

  private String description;
  private String dueDate;
  private List<TicketTimelineItemResponse> timeline;
  private List<TicketCommentResponse> comments;
  private List<TicketAttachmentResponse> attachments;

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDueDate() {
    return dueDate;
  }

  public void setDueDate(String dueDate) {
    this.dueDate = dueDate;
  }

  public List<TicketTimelineItemResponse> getTimeline() {
    return timeline;
  }

  public void setTimeline(List<TicketTimelineItemResponse> timeline) {
    this.timeline = timeline;
  }

  public List<TicketCommentResponse> getComments() {
    return comments;
  }

  public void setComments(List<TicketCommentResponse> comments) {
    this.comments = comments;
  }

  public List<TicketAttachmentResponse> getAttachments() {
    return attachments;
  }

  public void setAttachments(List<TicketAttachmentResponse> attachments) {
    this.attachments = attachments;
  }
}
