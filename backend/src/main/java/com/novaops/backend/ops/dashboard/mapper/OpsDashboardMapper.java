package com.novaops.backend.ops.dashboard.mapper;

import com.novaops.backend.ops.dashboard.model.NamedCount;
import com.novaops.backend.ops.dashboard.model.TicketOverviewRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface OpsDashboardMapper {

  TicketOverviewRow summarize(
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end
  );

  List<NamedCount> countCreatedByDay(
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end
  );

  List<NamedCount> countClosedByDay(
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end
  );

  List<NamedCount> countByStatus(
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end
  );

  List<NamedCount> avgHoursByPriority(
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end
  );
}
