package com.novaops.backend.ops.asset.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateAssetRequest {

  @NotBlank(message = "资产名称不能为空")
  private String name;

  @NotBlank(message = "资产类型不能为空")
  private String type;

  private String location;

  @NotBlank(message = "规格说明不能为空")
  private String spec;

  private String remark;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getSpec() {
    return spec;
  }

  public void setSpec(String spec) {
    this.spec = spec;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }
}
