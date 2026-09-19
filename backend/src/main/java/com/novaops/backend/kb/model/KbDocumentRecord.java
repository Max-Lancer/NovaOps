package com.novaops.backend.kb.model;
import java.time.LocalDateTime;
public class KbDocumentRecord {
  private String id,title,fileName,fileType,storagePath,status,errorMsg,contentHash,createdBy; private Long fileSize; private Integer chunkCount; private LocalDateTime createdAt,updatedAt;
  public String getId(){return id;} public void setId(String v){id=v;}
  public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getFileName(){return fileName;} public void setFileName(String v){fileName=v;}
  public String getFileType(){return fileType;} public void setFileType(String v){fileType=v;} public Long getFileSize(){return fileSize;} public void setFileSize(Long v){fileSize=v;}
  public String getStoragePath(){return storagePath;} public void setStoragePath(String v){storagePath=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
  public Integer getChunkCount(){return chunkCount;} public void setChunkCount(Integer v){chunkCount=v;} public String getErrorMsg(){return errorMsg;} public void setErrorMsg(String v){errorMsg=v;}
  public String getContentHash(){return contentHash;} public void setContentHash(String v){contentHash=v;}
  public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
  public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
