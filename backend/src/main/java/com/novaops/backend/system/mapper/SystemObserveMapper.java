package com.novaops.backend.system.mapper;

import com.novaops.backend.system.model.AccessLogRecord;
import com.novaops.backend.system.model.ModuleCheckRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SystemObserveMapper {

  void insertCheck(ModuleCheckRecord record);

  String findLatestBatchId();

  List<ModuleCheckRecord> listChecksByBatch(@Param("batchId") String batchId);

  List<ModuleCheckRecord> listRecentChecks(@Param("limit") int limit);

  void deleteChecksBefore(@Param("cutoff") LocalDateTime cutoff);

  void insertAccessLog(AccessLogRecord record);

  long countAccessLogs(
      @Param("keyword") String keyword,
      @Param("method") String method,
      @Param("httpStatus") Integer httpStatus,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate
  );

  List<AccessLogRecord> queryAccessLogs(
      @Param("keyword") String keyword,
      @Param("method") String method,
      @Param("httpStatus") Integer httpStatus,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("offset") long offset,
      @Param("limit") long limit
  );

  void deleteAccessLogsBefore(@Param("cutoff") LocalDateTime cutoff);

  long countAllAccessLogs();

  void deleteOldestAccessLogs(@Param("limit") int limit);
}
