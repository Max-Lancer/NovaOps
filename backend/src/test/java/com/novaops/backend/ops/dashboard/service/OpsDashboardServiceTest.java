package com.novaops.backend.ops.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.novaops.backend.ops.dashboard.dto.DashboardMetricsResponse;
import com.novaops.backend.ops.dashboard.mapper.OpsDashboardMapper;
import com.novaops.backend.ops.dashboard.model.NamedCount;
import com.novaops.backend.ops.dashboard.model.TicketOverviewRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpsDashboardServiceTest {

  private OpsDashboardMapper dashboardMapper;
  private OpsDashboardService dashboardService;

  @BeforeEach
  void setUp() {
    dashboardMapper = mock(OpsDashboardMapper.class);
    dashboardService = new OpsDashboardService(dashboardMapper);
    when(dashboardMapper.countCreatedByDay(any(), any())).thenReturn(List.of());
    when(dashboardMapper.countClosedByDay(any(), any())).thenReturn(List.of());
    when(dashboardMapper.countByStatus(any(), any())).thenReturn(List.of());
    when(dashboardMapper.avgHoursByPriority(any(), any())).thenReturn(List.of());
  }

  @Test
  void emptyRangeReturnsZeroOverviewAndClaimingBucket() {
    when(dashboardMapper.summarize(any(), any())).thenReturn(new TicketOverviewRow());

    DashboardMetricsResponse metrics = dashboardService.metrics("2026-04-20T00:00:00", "2026-04-20T23:59:59");

    assertThat(metrics.overview().ticketTotal()).isZero();
    assertThat(metrics.overview().doneRate()).isZero();
    assertThat(metrics.categories()).extracting(DashboardMetricsResponse.Category::name).contains("接单待审");
    assertThat(metrics.trend().dates()).containsExactly("04-20");
    assertThat(metrics.trend().created()).containsExactly(0L);
  }

  @Test
  void doneRateAndDailyBucketsUseCreatedRange() {
    TicketOverviewRow row = new TicketOverviewRow();
    row.setTotal(4);
    row.setDoneCount(1);
    row.setUrgentCount(2);
    row.setAvgDoneHours(1.26);
    when(dashboardMapper.summarize(any(), any())).thenReturn(row);
    NamedCount created = new NamedCount();
    created.setName("04-20");
    created.setValue(3);
    NamedCount claiming = new NamedCount();
    claiming.setName("claiming");
    claiming.setValue(1);
    when(dashboardMapper.countCreatedByDay(any(), any())).thenReturn(List.of(created));
    when(dashboardMapper.countByStatus(any(), any())).thenReturn(List.of(claiming));

    DashboardMetricsResponse metrics = dashboardService.metrics("2026-04-20T00:00:00", "2026-04-21T23:59:59");

    assertThat(metrics.overview().doneRate()).isEqualTo(25.0);
    assertThat(metrics.overview().urgentRate()).isEqualTo(50.0);
    assertThat(metrics.overview().avgHandleHours()).isEqualTo(1.3);
    assertThat(metrics.trend().dates()).containsExactly("04-20", "04-21");
    assertThat(metrics.trend().created()).containsExactly(3L, 0L);
    assertThat(metrics.categories())
        .filteredOn(item -> "接单待审".equals(item.name()))
        .extracting(DashboardMetricsResponse.Category::value)
        .containsExactly(1L);
  }
}
