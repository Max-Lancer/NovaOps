package com.novaops.backend.kb.dto;

import java.util.List;

/** 分片上传初始化响应：秒传命中时直接给 documentId；否则给出会话 id 与已接收的分片序号。 */
public class UploadInitResponse {

  /** true 表示内容已在知识库中（秒传命中），客户端不需要再传任何分片。 */
  private boolean instant;
  private String sessionId;
  private String documentId;
  private List<Integer> uploaded;

  public static UploadInitResponse instant(String documentId) {
    UploadInitResponse response = new UploadInitResponse();
    response.instant = true;
    response.documentId = documentId;
    response.uploaded = List.of();
    return response;
  }

  public static UploadInitResponse resume(String sessionId, List<Integer> uploaded) {
    UploadInitResponse response = new UploadInitResponse();
    response.instant = false;
    response.sessionId = sessionId;
    response.uploaded = uploaded;
    return response;
  }

  public boolean isInstant() {
    return instant;
  }

  public void setInstant(boolean instant) {
    this.instant = instant;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  public List<Integer> getUploaded() {
    return uploaded;
  }

  public void setUploaded(List<Integer> uploaded) {
    this.uploaded = uploaded;
  }
}
