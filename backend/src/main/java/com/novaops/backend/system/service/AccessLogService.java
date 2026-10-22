package com.novaops.backend.system.service;

import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.util.DateTimeUtils;
import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.system.dto.AccessLogQuery;
import com.novaops.backend.system.mapper.SystemObserveMapper;
import com.novaops.backend.system.model.AccessLogRecord;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccessLogService {

  private static final Logger log = LoggerFactory.getLogger(AccessLogService.class);
  private static final int MAX_ROWS = 20000;
  private static final AtomicInteger WRITES = new AtomicInteger();

  private final SystemObserveMapper observeMapper;

  public AccessLogService(SystemObserveMapper observeMapper) {
    this.observeMapper = observeMapper;
  }

  public void record(AccessLogRecord record) {
    record.setId(IdGenerator.randomId("log"));
    record.setCreatedAt(LocalDateTime.now());
    record.setPath(truncate(record.getPath(), 200));
    record.setResultMessage(truncate(record.getResultMessage(), 500));
    record.setRequestId(truncate(record.getRequestId(), 64));
    record.setUsername(truncate(record.getUsername(), 64));
    record.setExceptionType(truncate(record.getExceptionType(), 128));
    observeMapper.insertAccessLog(record);
    if (WRITES.incrementAndGet() % 50 == 0) {
      prune();
    }
  }

  public PageResult<AccessLogRecord> query(AccessLogQuery query) {
    LocalDateTime start = DateTimeUtils.parseIsoDateTime(query.getStartDate());
    LocalDateTime end = DateTimeUtils.parseIsoDateTime(query.getEndDate());
    String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
    String method = StringUtils.hasText(query.getMethod()) ? query.getMethod().trim().toUpperCase() : null;
    long total = observeMapper.countAccessLogs(keyword, method, query.getHttpStatus(), start, end);
    long offset = (query.getPage() - 1) * query.getPageSize();
    return new PageResult<>(
        observeMapper.queryAccessLogs(keyword, method, query.getHttpStatus(), start, end, offset, query.getPageSize()),
        query.getPage(),
        query.getPageSize(),
        total
    );
  }

  private void prune() {
    try {
      LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
      observeMapper.deleteAccessLogsBefore(cutoff);
      long count = observeMapper.countAllAccessLogs();
      if (count > MAX_ROWS) {
        int overflow = (int) Math.min(count - MAX_ROWS, 1000);
        observeMapper.deleteOldestAccessLogs(overflow);
      }
    } catch (Exception exception) {
      log.warn("prune access logs failed", exception);
    }
  }

  private String truncate(String value, int max) {
    if (!StringUtils.hasText(value)) {
      return value;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
