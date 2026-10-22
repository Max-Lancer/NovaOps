package com.novaops.backend.system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 访问日志清理离开请求线程：按保留期和行数上限删除，失败不影响在途请求。
 */
@Component
public class AccessLogCleanupTask {

  private static final Logger log = LoggerFactory.getLogger(AccessLogCleanupTask.class);

  private final AccessLogService accessLogService;

  public AccessLogCleanupTask(AccessLogService accessLogService) {
    this.accessLogService = accessLogService;
  }

  @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
  public void pruneAccessLogs() {
    try {
      int deleted = accessLogService.prune();
      if (deleted > 0) {
        log.info("pruned {} access logs", deleted);
      }
    } catch (Exception exception) {
      log.warn("access log cleanup failed", exception);
    }
  }
}
