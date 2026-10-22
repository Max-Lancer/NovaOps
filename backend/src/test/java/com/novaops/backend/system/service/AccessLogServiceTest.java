package com.novaops.backend.system.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.system.dto.AccessLogQuery;
import com.novaops.backend.system.mapper.SystemObserveMapper;
import com.novaops.backend.system.model.AccessLogRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessLogServiceTest {

  @Mock
  private SystemObserveMapper observeMapper;

  @InjectMocks
  private AccessLogService service;

  @Test
  void recordInsertsWithoutPruning() {
    AccessLogRecord record = new AccessLogRecord();
    record.setPath("/api/tickets");
    record.setMethod("GET");
    record.setRequestId("req-1");

    service.record(record);

    verify(observeMapper).insertAccessLog(record);
    verify(observeMapper, never()).deleteAccessLogsBefore(any());
    verify(observeMapper, never()).countAllAccessLogs();
    verify(observeMapper, never()).deleteOldestAccessLogs(anyInt());
  }

  @Test
  void recordSwallowsInsertFailure() {
    doThrow(new RuntimeException("db down")).when(observeMapper).insertAccessLog(any());
    AccessLogRecord record = new AccessLogRecord();
    record.setPath("/api/tickets");

    assertThatCode(() -> service.record(record)).doesNotThrowAnyException();
  }

  @Test
  void queryFiltersByBusinessResultCode() {
    when(observeMapper.countAccessLogs(null, null, null, 403, null, null)).thenReturn(1L);
    when(observeMapper.queryAccessLogs(null, null, null, 403, null, null, 0L, 10L)).thenReturn(List.of());
    AccessLogQuery query = new AccessLogQuery();
    query.setResultCode(403);

    service.query(query);

    verify(observeMapper).countAccessLogs(null, null, null, 403, null, null);
    verify(observeMapper).queryAccessLogs(null, null, null, 403, null, null, 0L, 10L);
  }

  @Test
  void pruneDeletesExpiredAndOverflowOffTheRequestPath() {
    when(observeMapper.deleteAccessLogsBefore(any())).thenReturn(2);
    when(observeMapper.countAllAccessLogs()).thenReturn(20_010L);
    when(observeMapper.deleteOldestAccessLogs(10)).thenReturn(10);

    service.prune();

    verify(observeMapper).deleteAccessLogsBefore(any());
    verify(observeMapper).deleteOldestAccessLogs(10);
  }

  @Test
  void pruneSwallowsDatabaseFailure() {
    doThrow(new RuntimeException("db down")).when(observeMapper).deleteAccessLogsBefore(any());

    assertThatCode(() -> service.prune()).doesNotThrowAnyException();
  }
}
