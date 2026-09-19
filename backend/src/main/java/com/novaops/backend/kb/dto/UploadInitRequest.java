package com.novaops.backend.kb.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 分片上传初始化请求：客户端先算好整文件 SHA-256 与分片大小，服务端据此判定秒传与续传进度。 */
public class UploadInitRequest {

  @NotBlank(message = "文件名不能为空")
  private String fileName;

  @NotNull(message = "文件大小不能为空")
  @Min(value = 1, message = "文件大小必须大于 0")
  private Long fileSize;

  @NotBlank(message = "文件摘要不能为空")
  private String contentHash;

  private Integer chunkSize;

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public void setFileSize(Long fileSize) {
    this.fileSize = fileSize;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public Integer getChunkSize() {
    return chunkSize;
  }

  public void setChunkSize(Integer chunkSize) {
    this.chunkSize = chunkSize;
  }
}
