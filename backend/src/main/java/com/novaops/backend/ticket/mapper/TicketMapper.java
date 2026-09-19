package com.novaops.backend.ticket.mapper;

import com.novaops.backend.ticket.model.TicketAssetRelationRecord;
import com.novaops.backend.ticket.model.TicketAttachmentRecord;
import com.novaops.backend.ticket.model.TicketCommentRecord;
import com.novaops.backend.ticket.model.TicketRecord;
import com.novaops.backend.ticket.model.TicketTimelineRecord;
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

  void insertTicket(TicketRecord record);

  int updateTicket(TicketRecord record);

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
