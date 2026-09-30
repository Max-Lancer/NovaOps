package com.novaops.backend.ops.ticket.controller;

import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.security.RequestContext;
import com.novaops.backend.common.security.RequirePermission;
import com.novaops.backend.ops.ticket.dto.AttachmentDownload;
import com.novaops.backend.ops.ticket.dto.CreateCommentRequest;
import com.novaops.backend.ops.ticket.dto.CreateTicketRequest;
import com.novaops.backend.ops.ticket.dto.TicketActionRequest;
import com.novaops.backend.ops.ticket.dto.TicketAttachmentResponse;
import com.novaops.backend.ops.ticket.dto.TicketCommentResponse;
import com.novaops.backend.ops.ticket.dto.TicketDetailResponse;
import com.novaops.backend.ops.ticket.dto.TicketListItemResponse;
import com.novaops.backend.ops.ticket.dto.TicketListQuery;
import com.novaops.backend.ops.ticket.dto.UpdateTicketRequest;
import com.novaops.backend.ops.ticket.service.TicketService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

  private final TicketService ticketService;

  public TicketController(TicketService ticketService) {
    this.ticketService = ticketService;
  }

  @GetMapping
  @RequirePermission("ticket:view")
  public ApiResponse<PageResult<TicketListItemResponse>> list(@Valid @ModelAttribute TicketListQuery query) {
    return ApiResponse.success(ticketService.list(RequestContext.getRequired(), query));
  }

  @GetMapping("/claim-queue")
  @RequirePermission("ticket:claim")
  public ApiResponse<PageResult<TicketListItemResponse>> claimQueue(@Valid @ModelAttribute TicketListQuery query) {
    return ApiResponse.success(ticketService.listClaimQueue(RequestContext.getRequired(), query));
  }

  @GetMapping("/{id}")
  @RequirePermission("ticket:view")
  public ApiResponse<TicketDetailResponse> detail(@PathVariable("id") String id) {
    return ApiResponse.success(ticketService.detail(RequestContext.getRequired(), id));
  }

  @PostMapping
  @RequirePermission("ticket:create")
  public ApiResponse<TicketDetailResponse> create(@Valid @RequestBody CreateTicketRequest request) {
    return ApiResponse.success(ticketService.create(RequestContext.getRequired(), request), "工单创建成功");
  }

  @PutMapping("/{id}")
  @RequirePermission("ticket:edit")
  public ApiResponse<TicketDetailResponse> update(@PathVariable("id") String id, @Valid @RequestBody UpdateTicketRequest request) {
    return ApiResponse.success(ticketService.update(RequestContext.getRequired(), id, request), "工单更新成功");
  }

  // 工单流转按动作细分权限（assign/transfer/close/advance），在 Service 内校验
  @PostMapping("/{id}/actions")
  public ApiResponse<TicketDetailResponse> action(@PathVariable("id") String id, @Valid @RequestBody TicketActionRequest request) {
    return ApiResponse.success(ticketService.action(RequestContext.getRequired(), id, request), "工单流转成功");
  }

  @GetMapping("/{id}/comments")
  @RequirePermission("ticket:view")
  public ApiResponse<List<TicketCommentResponse>> comments(@PathVariable("id") String id) {
    return ApiResponse.success(ticketService.comments(RequestContext.getRequired(), id));
  }

  @PostMapping("/{id}/comments")
  @RequirePermission("ticket:comment")
  public ApiResponse<TicketCommentResponse> createComment(@PathVariable("id") String id, @Valid @RequestBody CreateCommentRequest request) {
    return ApiResponse.success(ticketService.createComment(RequestContext.getRequired(), id, request), "评论创建成功");
  }

  @PostMapping("/{id}/attachments")
  @RequirePermission("ticket:comment")
  public ApiResponse<TicketAttachmentResponse> uploadAttachment(
      @PathVariable("id") String id,
      @RequestPart("file") MultipartFile file
  ) {
    return ApiResponse.success(ticketService.uploadAttachment(RequestContext.getRequired(), id, file), "附件上传成功");
  }

  /** 附件实体不走 JSON 包装，直接回文件流；下载要带 Authorization 头，前端按 blob 取回再落盘。 */
  @GetMapping("/{id}/attachments/{attachmentId}/download")
  @RequirePermission("ticket:view")
  public ResponseEntity<Resource> downloadAttachment(@PathVariable("id") String id, @PathVariable("attachmentId") String attachmentId) {
    AttachmentDownload download = ticketService.loadAttachment(RequestContext.getRequired(), id, attachmentId);
    ContentDisposition disposition = ContentDisposition.attachment()
        .filename(download.getFileName(), StandardCharsets.UTF_8)
        .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(download.getSize())
        .body(download.getResource());
  }
}
