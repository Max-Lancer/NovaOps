package com.novaops.backend.ops.asset.dto;

import jakarta.validation.constraints.NotBlank;

public class AssetActionRequest {

  @NotBlank(message = "action 不能为空")
  private String action;

  private String ownerId;

  private String remark;

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(String ownerId) {
    this.ownerId = ownerId;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }
}
