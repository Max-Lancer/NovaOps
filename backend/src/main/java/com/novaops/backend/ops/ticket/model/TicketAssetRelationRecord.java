package com.novaops.backend.ops.ticket.model;

public class TicketAssetRelationRecord {

  private String ticketId;
  private String assetId;

  public String getTicketId() {
    return ticketId;
  }

  public void setTicketId(String ticketId) {
    this.ticketId = ticketId;
  }

  public String getAssetId() {
    return assetId;
  }

  public void setAssetId(String assetId) {
    this.assetId = assetId;
  }
}
