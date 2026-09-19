package com.novaops.backend.kb.model;

import java.time.LocalDateTime;

public class KbUploadSessionRecord {
  private String id,contentHash,fileName,fileType,status,documentId,createdBy;
  private Long fileSize; private Integer chunkSize,chunkCount;
  private LocalDateTime createdAt,updatedAt;
  public String getId(){return id;} public void setId(String v){id=v;}
  public String getContentHash(){return contentHash;} public void setContentHash(String v){contentHash=v;}
  public String getFileName(){return fileName;} public void setFileName(String v){fileName=v;}
  public String getFileType(){return fileType;} public void setFileType(String v){fileType=v;}
  public String getStatus(){return status;} public void setStatus(String v){status=v;}
  public String getDocumentId(){return documentId;} public void setDocumentId(String v){documentId=v;}
  public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;}
  public Long getFileSize(){return fileSize;} public void setFileSize(Long v){fileSize=v;}
  public Integer getChunkSize(){return chunkSize;} public void setChunkSize(Integer v){chunkSize=v;}
  public Integer getChunkCount(){return chunkCount;} public void setChunkCount(Integer v){chunkCount=v;}
  public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
  public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
