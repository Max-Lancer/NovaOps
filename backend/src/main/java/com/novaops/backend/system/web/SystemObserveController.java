package com.novaops.backend.system.web;

import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.security.RequirePermission;
import com.novaops.backend.system.dto.AccessLogQuery;
import com.novaops.backend.system.dto.ModuleHealthResponse;
import com.novaops.backend.system.model.AccessLogRecord;
import com.novaops.backend.system.service.AccessLogService;
import com.novaops.backend.system.service.ModuleHealthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemObserveController {

  private final ModuleHealthService moduleHealthService;
  private final AccessLogService accessLogService;

  public SystemObserveController(ModuleHealthService moduleHealthService, AccessLogService accessLogService) {
    this.moduleHealthService = moduleHealthService;
    this.accessLogService = accessLogService;
  }

  @GetMapping("/health")
  @RequirePermission("system:observe")
  public ApiResponse<ModuleHealthResponse> health() {
    return ApiResponse.success(moduleHealthService.snapshot());
  }

  @PostMapping("/health/check")
  @RequirePermission("system:observe")
  public ApiResponse<ModuleHealthResponse> check() {
    return ApiResponse.success(moduleHealthService.checkAll(), "检查完成");
  }

  @GetMapping("/logs")
  @RequirePermission("system:observe")
  public ApiResponse<PageResult<AccessLogRecord>> logs(@Valid @ModelAttribute AccessLogQuery query) {
    return ApiResponse.success(accessLogService.query(query));
  }
}
