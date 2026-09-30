package com.novaops.backend.ops.dashboard.model;

public class NamedCount {
  private String name;
  private long value;
  private Double hours;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public long getValue() { return value; }
  public void setValue(long value) { this.value = value; }
  public Double getHours() { return hours; }
  public void setHours(Double hours) { this.hours = hours; }
}
