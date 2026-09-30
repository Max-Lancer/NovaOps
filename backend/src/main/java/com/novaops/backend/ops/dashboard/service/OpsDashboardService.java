package com.novaops.backend.ops.dashboard.service;

import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.util.DateTimeUtils;
import com.novaops.backend.ops.dashboard.dto.DashboardMetricsResponse;
import com.novaops.backend.ops.dashboard.mapper.OpsDashboardMapper;
import com.novaops.backend.ops.dashboard.model.NamedCount;
import com.novaops.backend.ops.dashboard.model.TicketOverviewRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpsDashboardService {

  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");
  private static final List<String> STATUSES = List.of("pending", "claiming", "processing", "review", "done");
  private static final Map<String, String> STATUS_LABELS = Map.of(
      "pending", "待处理",
      "claiming", "接单待审",
      "processing", "处理中",
      "review", "待复核",
      "done", "已完成"
  );
  private static final List<String> PRIORITIES = List.of("urgent", "high", "medium", "low");
  private static final Map<String, String> PRIORITY_LABELS = Map.of(
      "urgent", "紧急",
      "high", "高优先级",
      "medium", "中优先级",
      "low", "低优先级"
  );

  private final OpsDashboardMapper dashboardMapper;

  public OpsDashboardService(OpsDashboardMapper dashboardMapper) {
    this.dashboardMapper = dashboardMapper;
  }

  public DashboardMetricsResponse metrics(String startDate, String endDate) {
    LocalDateTime start = DateTimeUtils.parseIsoDateTime(startDate);
    LocalDateTime end = DateTimeUtils.parseIsoDateTime(endDate);
    if (start == null || end == null) {
      throw new BusinessException(400, "请选择统计日期");
    }
    if (end.isBefore(start)) {
      throw new BusinessException(400, "结束日期不能早于开始日期");
    }

    TicketOverviewRow overview = dashboardMapper.summarize(start, end);
    if (overview == null) {
      overview = new TicketOverviewRow();
    }
    long total = overview.getTotal();
    List<String> dates = dateAxis(start.toLocalDate(), end.toLocalDate());
    Map<String, Long> created = toCountMap(dashboardMapper.countCreatedByDay(start, end));
    Map<String, Long> closed = toCountMap(dashboardMapper.countClosedByDay(start, end));
    Map<String, Long> statusCounts = toCountMap(dashboardMapper.countByStatus(start, end));
    Map<String, Double> durationHours = new LinkedHashMap<>();
    for (NamedCount row : dashboardMapper.avgHoursByPriority(start, end)) {
      durationHours.put(row.getName(), row.getHours() == null ? 0 : row.getHours());
    }

    return new DashboardMetricsResponse(
        new DashboardMetricsResponse.Range(DateTimeUtils.toIsoString(start), DateTimeUtils.toIsoString(end)),
        new DashboardMetricsResponse.Overview(
            total,
            total == 0 ? 0 : round(overview.getDoneCount() * 100.0 / total),
            round(overview.getAvgDoneHours() == null ? 0 : overview.getAvgDoneHours()),
            total == 0 ? 0 : round(overview.getUrgentCount() * 100.0 / total)
        ),
        new DashboardMetricsResponse.Trend(
            dates,
            dates.stream().map(date -> created.getOrDefault(date, 0L)).toList(),
            dates.stream().map(date -> closed.getOrDefault(date, 0L)).toList()
        ),
        STATUSES.stream()
            .map(status -> new DashboardMetricsResponse.Category(STATUS_LABELS.get(status), statusCounts.getOrDefault(status, 0L)))
            .toList(),
        PRIORITIES.stream()
            .map(priority -> new DashboardMetricsResponse.DurationItem(
                PRIORITY_LABELS.get(priority),
                round(durationHours.getOrDefault(priority, 0.0))
            ))
            .toList()
    );
  }

  private static List<String> dateAxis(LocalDate start, LocalDate end) {
    List<String> dates = new ArrayList<>();
    for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
      dates.add(cursor.format(DAY));
    }
    return dates;
  }

  private static Map<String, Long> toCountMap(List<NamedCount> rows) {
    Map<String, Long> counts = new LinkedHashMap<>();
    if (rows == null) {
      return counts;
    }
    for (NamedCount row : rows) {
      counts.put(row.getName(), row.getValue());
    }
    return counts;
  }

  private static double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
