package com.novaops.backend.ops.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.tools.KbReadTools;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.ops.asset.dto.AssetListItemResponse;
import com.novaops.backend.ops.asset.dto.AssetListQuery;
import com.novaops.backend.ops.asset.service.AssetService;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.kb.dto.RetrievalChunk;
import com.novaops.backend.kb.dto.RetrievalResult;
import com.novaops.backend.kb.service.KbRetrievalService;
import com.novaops.backend.ops.ticket.dto.TicketCommentResponse;
import com.novaops.backend.ops.ticket.dto.TicketDetailResponse;
import com.novaops.backend.ops.ticket.dto.TicketListItemResponse;
import com.novaops.backend.ops.ticket.dto.TicketListQuery;
import com.novaops.backend.ops.ticket.dto.TicketTimelineItemResponse;
import com.novaops.backend.ops.ticket.service.TicketService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BuiltinReadToolsTest {

  private static final ToolContext CTX = new ToolContext("u-1", "tester", Set.of("ticket:view"));

  private static TicketListItemResponse ticket(String id, String title, String status) {
    TicketListItemResponse item = new TicketListItemResponse();
    item.setId(id);
    item.setTitle(title);
    item.setStatus(status);
    item.setPriority("high");
    item.setAssigneeName("Jerry");
    item.setUpdatedAt("2026-08-30T10:00:00Z");
    return item;
  }

  @Test
  void ticketSearchMapsArgsAndShapesPayload() {
    TicketService service = mock(TicketService.class);
    when(service.list(any(), any())).thenReturn(
        new PageResult<>(List.of(ticket("A-1", "VPN 掉线", "pending")), 1, 10, 1));
    var tool = new TicketReadTools.TicketSearchTool(service);

    ToolResult result = tool.execute(CTX, Map.of("status", "pending", "keyword", "VPN", "maxResults", 5), false);

    ArgumentCaptor<TicketListQuery> captor = ArgumentCaptor.forClass(TicketListQuery.class);
    verify(service).list(any(), captor.capture());
    assertEquals("pending", captor.getValue().getStatus());
    assertEquals("VPN", captor.getValue().getKeyword());
    assertEquals(5L, captor.getValue().getPageSize());
    assertEquals(ToolResult.Status.OK, result.status());
    assertTrue(result.payload().toString().contains("VPN 掉线"));
  }

  @Test
  void ticketSearchEmptyReturnsEmptyResult() {
    TicketService service = mock(TicketService.class);
    when(service.list(any(), any())).thenReturn(new PageResult<>(List.of(), 1, 10, 0));
    var tool = new TicketReadTools.TicketSearchTool(service);

    assertEquals(ToolResult.Status.EMPTY, tool.execute(CTX, Map.of(), false).status());
  }

  @Test
  void ticketDetailRequiresTicketId() {
    TicketService service = mock(TicketService.class);
    var tool = new TicketReadTools.TicketDetailTool(service);

    ToolResult result = tool.execute(CTX, Map.of(), false);

    assertEquals(ToolResult.Status.FAILED, result.status());
    verify(service, never()).detail(any(), anyString());
  }

  @Test
  void ticketDetailTranslatesBusinessExceptionToFailed() {
    TicketService service = mock(TicketService.class);
    when(service.detail(any(), eq("A-404"))).thenThrow(new BusinessException(404, "工单不存在"));
    var tool = new TicketReadTools.TicketDetailTool(service);

    ToolResult result = tool.execute(CTX, Map.of("ticketId", "A-404"), false);

    assertEquals(ToolResult.Status.FAILED, result.status());
    assertTrue(result.message().contains("工单不存在"));
  }

  @Test
  void ticketDetailShapesTimelineAndComments() {
    TicketService service = mock(TicketService.class);
    TicketDetailResponse detail = new TicketDetailResponse();
    TicketListItemResponse base = ticket("A-1", "VPN 掉线", "processing");
    detail.setId(base.getId());
    detail.setTitle(base.getTitle());
    detail.setStatus(base.getStatus());
    detail.setDescription("多名员工反馈掉线");
    TicketTimelineItemResponse timelineItem = new TicketTimelineItemResponse();
    timelineItem.setAction("assign");
    timelineItem.setOperatorName("Admin");
    timelineItem.setFromStatus("pending");
    timelineItem.setToStatus("processing");
    detail.setTimeline(List.of(timelineItem));
    TicketCommentResponse comment = new TicketCommentResponse();
    comment.setAuthorName("Jerry");
    comment.setContent("已更换网关");
    detail.setComments(List.of(comment));
    when(service.detail(any(), eq("A-1"))).thenReturn(detail);
    var tool = new TicketReadTools.TicketDetailTool(service);

    ToolResult result = tool.execute(CTX, Map.of("ticketId", "A-1"), false);

    assertEquals(ToolResult.Status.OK, result.status());
    String observation = result.payload().toString();
    assertTrue(observation.contains("assign"));
    assertTrue(observation.contains("已更换网关"));
    assertTrue(observation.contains("多名员工反馈掉线"));
  }

  @Test
  void assetSearchMapsArgsAndHandlesEmpty() {
    AssetService service = mock(AssetService.class);
    when(service.list(any(), any())).thenReturn(new PageResult<>(List.of(), 1, 10, 0));
    var tool = new AssetReadTools.AssetSearchTool(service);

    assertEquals(ToolResult.Status.EMPTY, tool.execute(CTX, Map.of("keyword", "交换机"), false).status());

    ArgumentCaptor<AssetListQuery> captor = ArgumentCaptor.forClass(AssetListQuery.class);
    verify(service).list(any(), captor.capture());
    assertEquals("交换机", captor.getValue().getKeyword());

    AssetListItemResponse item = new AssetListItemResponse();
    item.setId("ASSET-1");
    item.setAssetNo("NW-001");
    item.setName("核心交换机");
    item.setStatus("in_use");
    when(service.list(any(), any())).thenReturn(new PageResult<>(List.of(item), 1, 10, 1));
    ToolResult result = tool.execute(CTX, Map.of(), false);
    assertEquals(ToolResult.Status.OK, result.status());
    assertTrue(result.payload().toString().contains("核心交换机"));
  }

  @Test
  void kbSearchRequiresQueryAndShapesChunks() {
    KbRetrievalService retrieval = mock(KbRetrievalService.class);
    var tool = new KbReadTools.KbSearchTool(retrieval, 0.55);

    assertEquals(ToolResult.Status.FAILED, tool.execute(CTX, Map.of(), false).status());
    verify(retrieval, never()).retrieve(anyString(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyDouble());

    when(retrieval.retrieve("vpn", 3, 0.55)).thenReturn(new RetrievalResult(List.of(
        new RetrievalChunk("chunk-1", "doc-1", "VPN 手册", "c".repeat(500), 0.9))));
    ToolResult result = tool.execute(CTX, Map.of("query", "vpn", "topK", 3), false);

    assertEquals(ToolResult.Status.OK, result.status());
    String payload = result.payload().toString();
    assertTrue(payload.contains("chunk-1"));
    assertTrue(payload.contains("…(片段截断)"));
  }

  @Test
  void kbSearchEmptyTranslatesToEmptyResult() {
    KbRetrievalService retrieval = mock(KbRetrievalService.class);
    when(retrieval.retrieve(anyString(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyDouble())).thenReturn(new RetrievalResult(List.of()));
    var tool = new KbReadTools.KbSearchTool(retrieval, 0.55);

    assertEquals(ToolResult.Status.EMPTY, tool.execute(CTX, Map.of("query", "vpn"), false).status());
  }

  @Test
  void kbMetadataShapesSnapshot() {
    var metadataService = mock(com.novaops.backend.kb.service.KbMetadataService.class);
    var document = new com.novaops.backend.kb.dto.KnowledgeBaseMetadataDocument(
        "doc-1", "VPN 手册", "vpn.pdf", "pdf", "READY", 12, java.time.LocalDateTime.of(2026, 8, 30, 10, 0));
    when(metadataService.snapshot()).thenReturn(new com.novaops.backend.kb.dto.KnowledgeBaseMetadataSnapshot(
        1, 1, false, List.of(document)));
    var tool = new KbReadTools.KbMetadataTool(metadataService);

    ToolResult result = tool.execute(CTX, Map.of(), false);

    assertEquals(ToolResult.Status.OK, result.status());
    assertTrue(result.payload().toString().contains("VPN 手册"));
    assertTrue(result.payload().toString().contains("totalDocuments=1"));
  }
}
