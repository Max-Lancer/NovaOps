package com.novaops.backend.ops.dashboard.dto;

import java.util.List;

public record DashboardMetricsResponse(
    Range range,
    Overview overview,
    Trend trend,
    List<Category> categories,
    List<DurationItem> durations
) {
  public record Range(String startDate, String endDate) {}

  public record Overview(long ticketTotal, double doneRate, double avgHandleHours, double urgentRate) {}

  public record Trend(List<String> dates, List<Long> created, List<Long> closed) {}

  public record Category(String name, long value) {}

  public record DurationItem(String name, double hours) {}
}
