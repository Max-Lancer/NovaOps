package com.novaops.backend.ops.dashboard.model;

public class TicketOverviewRow {
  private long total;
  private long doneCount;
  private long urgentCount;
  private Double avgDoneHours;

  public long getTotal() { return total; }
  public void setTotal(long total) { this.total = total; }
  public long getDoneCount() { return doneCount; }
  public void setDoneCount(long doneCount) { this.doneCount = doneCount; }
  public long getUrgentCount() { return urgentCount; }
  public void setUrgentCount(long urgentCount) { this.urgentCount = urgentCount; }
  public Double getAvgDoneHours() { return avgDoneHours; }
  public void setAvgDoneHours(Double avgDoneHours) { this.avgDoneHours = avgDoneHours; }
}
