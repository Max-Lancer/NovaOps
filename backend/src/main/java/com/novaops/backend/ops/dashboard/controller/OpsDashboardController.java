package com.novaops.backend.ops.dashboard.controller;

import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.security.RequirePermission;
import com.novaops.backend.ops.dashboard.dto.DashboardMetricsResponse;
import com.novaops.backend.ops.dashboard.service.OpsDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops/dashboard")
public class OpsDashboardController {

  private final OpsDashboardService dashboardService;

  public OpsDashboardController(OpsDashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping("/metrics")
  @RequirePermission("dashboard:view")
  public ApiResponse<DashboardMetricsResponse> metrics(
      @RequestParam("startDate") String startDate,
      @RequestParam("endDate") String endDate
  ) {
    return ApiResponse.success(dashboardService.metrics(startDate, endDate));
  }
}
