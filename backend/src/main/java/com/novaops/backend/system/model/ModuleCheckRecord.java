package com.novaops.backend.system.model;

import java.time.LocalDateTime;

public class ModuleCheckRecord {

  private String id;
  private String batchId;
  private String moduleCode;
  private String moduleName;
  private String status;
  private int latencyMs;
  private String message;
  private LocalDateTime checkedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getBatchId() {
    return batchId;
  }

  public void setBatchId(String batchId) {
    this.batchId = batchId;
  }

  public String getModuleCode() {
    return moduleCode;
  }

  public void setModuleCode(String moduleCode) {
    this.moduleCode = moduleCode;
  }

  public String getModuleName() {
    return moduleName;
  }

  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public int getLatencyMs() {
    return latencyMs;
  }

  public void setLatencyMs(int latencyMs) {
    this.latencyMs = latencyMs;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public LocalDateTime getCheckedAt() {
    return checkedAt;
  }

  public void setCheckedAt(LocalDateTime checkedAt) {
    this.checkedAt = checkedAt;
  }
}
