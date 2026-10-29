package com.novaops.backend.ops.ticket.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateCommentRequest {

  @NotBlank(message = "评论内容不能为空")
  private String content;

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
