package com.novaops.backend.ops.ticket.mapper;

import com.novaops.backend.ops.ticket.model.TicketAssetRelationRecord;
import com.novaops.backend.ops.ticket.model.TicketAttachmentRecord;
import com.novaops.backend.ops.ticket.model.TicketCommentRecord;
import com.novaops.backend.ops.ticket.model.TicketRecord;
import com.novaops.backend.ops.ticket.model.TicketTimelineRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TicketMapper {

  long countTickets(
      @Param("status") String status,
      @Param("priority") String priority,
      @Param("keyword") String keyword,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate
  );

  List<TicketRecord> queryTickets(
      @Param("status") String status,
      @Param("priority") String priority,
      @Param("keyword") String keyword,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("offset") long offset,
      @Param("limit") long limit
  );

  TicketRecord findTicket(@Param("ticketId") String ticketId);

  long countClaimQueue(@Param("userId") String userId);

  List<TicketRecord> queryClaimQueue(
      @Param("userId") String userId,
      @Param("offset") long offset,
      @Param("limit") long limit
  );

  long countMine(@Param("userId") String userId);

  List<TicketRecord> queryMine(
      @Param("userId") String userId,
      @Param("offset") long offset,
      @Param("limit") long limit
  );

  void insertTicket(TicketRecord record);

  /** 只改标题、描述、优先级和截止时间，不回写状态与负责人。 */
  int updateTicket(TicketRecord record);

  /**
   * 按预期状态做条件更新。影响行数为 0 表示状态已被别人改掉。
   */
  int transitionTicket(@Param("ticket") TicketRecord ticket, @Param("expectedStatus") String expectedStatus);

  int touchUpdatedAt(@Param("ticketId") String ticketId, @Param("updatedAt") LocalDateTime updatedAt);

  void deleteAssetRelations(@Param("ticketId") String ticketId);

  void insertAssetRelation(@Param("ticketId") String ticketId, @Param("assetId") String assetId);

  List<String> listAssetIds(@Param("ticketId") String ticketId);

  List<TicketAssetRelationRecord> listAssetRelationsByTicketIds(@Param("ticketIds") List<String> ticketIds);

  List<TicketTimelineRecord> listTimeline(@Param("ticketId") String ticketId);

  void insertTimeline(TicketTimelineRecord record);

  List<TicketCommentRecord> listComments(@Param("ticketId") String ticketId);

  void insertComment(TicketCommentRecord record);

  List<TicketAttachmentRecord> listAttachments(@Param("ticketId") String ticketId);

  TicketAttachmentRecord findAttachment(@Param("ticketId") String ticketId, @Param("attachmentId") String attachmentId);

  void insertAttachment(TicketAttachmentRecord record);
}
