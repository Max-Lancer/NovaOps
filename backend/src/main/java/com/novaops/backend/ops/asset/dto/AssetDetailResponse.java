package com.novaops.backend.ops.asset.dto;

import java.util.List;

public class AssetDetailResponse {

  private String id;
  private String assetNo;
  private String name;
  private String type;
  private String status;
  private String ownerId;
  private String ownerName;
  private String location;
  private String spec;
  private String remark;
  private String purchaseDate;
  private String createdAt;
  private String updatedAt;
  private List<RelatedTicket> relatedTickets;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAssetNo() {
    return assetNo;
  }

  public void setAssetNo(String assetNo) {
    this.assetNo = assetNo;
  }

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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(String ownerId) {
    this.ownerId = ownerId;
  }

  public String getOwnerName() {
    return ownerName;
  }

  public void setOwnerName(String ownerName) {
    this.ownerName = ownerName;
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

  public String getPurchaseDate() {
    return purchaseDate;
  }

  public void setPurchaseDate(String purchaseDate) {
    this.purchaseDate = purchaseDate;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }

  public List<RelatedTicket> getRelatedTickets() {
    return relatedTickets;
  }

  public void setRelatedTickets(List<RelatedTicket> relatedTickets) {
    this.relatedTickets = relatedTickets;
  }
}
