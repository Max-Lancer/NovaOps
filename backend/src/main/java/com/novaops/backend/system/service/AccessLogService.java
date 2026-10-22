package com.novaops.backend.system.service;

import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.util.DateTimeUtils;
import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.system.dto.AccessLogQuery;
import com.novaops.backend.system.mapper.SystemObserveMapper;
import com.novaops.backend.system.model.AccessLogRecord;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccessLogService {

  private static final Logger log = LoggerFactory.getLogger(AccessLogService.class);
  private static final int MAX_ROWS = 20000;

  private final SystemObserveMapper observeMapper;

  public AccessLogService(SystemObserveMapper observeMapper) {
    this.observeMapper = observeMapper;
  }

  /**
   * 异步落库，返回后请求线程不再等待 INSERT。
   * 必须经 Spring 代理调用（过滤器注入的就是代理）；同类里直接调不会异步。
   * 队列满或写入失败只丢这一条日志，不影响原请求。
   */
  @Async("accessLogExecutor")
  public void record(AccessLogRecord record) {
    try {
      record.setId(IdGenerator.randomId("log"));
      record.setCreatedAt(LocalDateTime.now());
      record.setPath(truncate(record.getPath(), 200));
      record.setResultMessage(truncate(record.getResultMessage(), 500));
      record.setRequestId(truncate(record.getRequestId(), 64));
      record.setUsername(truncate(record.getUsername(), 64));
      record.setExceptionType(truncate(record.getExceptionType(), 128));
      observeMapper.insertAccessLog(record);
    } catch (Exception exception) {
      log.warn("write access log failed", exception);
    }
  }

  public PageResult<AccessLogRecord> query(AccessLogQuery query) {
    LocalDateTime start = DateTimeUtils.parseIsoDateTime(query.getStartDate());
    LocalDateTime end = DateTimeUtils.parseIsoDateTime(query.getEndDate());
    String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
    String method = StringUtils.hasText(query.getMethod()) ? query.getMethod().trim().toUpperCase() : null;
    long total = observeMapper.countAccessLogs(
        keyword, method, query.getHttpStatus(), query.getResultCode(), start, end);
    long offset = (query.getPage() - 1) * query.getPageSize();
    return new PageResult<>(
        observeMapper.queryAccessLogs(
            keyword, method, query.getHttpStatus(), query.getResultCode(), start, end, offset, query.getPageSize()),
        query.getPage(),
        query.getPageSize(),
        total
    );
  }

  /** 由定时任务调用，不放在请求路径上。保留近 7 天，且总行数不超过 {@link #MAX_ROWS}。 */
  public int prune() {
    try {
      LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
      int expired = observeMapper.deleteAccessLogsBefore(cutoff);
      long count = observeMapper.countAllAccessLogs();
      int overflowDeleted = 0;
      if (count > MAX_ROWS) {
        int overflow = (int) Math.min(count - MAX_ROWS, 1000);
        overflowDeleted = observeMapper.deleteOldestAccessLogs(overflow);
      }
      return expired + overflowDeleted;
    } catch (Exception exception) {
      log.warn("prune access logs failed", exception);
      return 0;
    }
  }

  private String truncate(String value, int max) {
    if (!StringUtils.hasText(value)) {
      return value;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
