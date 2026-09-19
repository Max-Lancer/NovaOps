package com.novaops.backend.kb.controller;

import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.security.RequestContext;
import com.novaops.backend.kb.dto.UploadInitRequest;
import com.novaops.backend.kb.dto.UploadInitResponse;
import com.novaops.backend.kb.model.KbDocumentRecord;
import com.novaops.backend.kb.service.KbUploadSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 知识库分片上传：init 判定秒传与续传进度，chunk 逐片接收，merge 校验摘要并建档解析。 */
@RestController
@RequestMapping("/api/kb/uploads")
public class KbUploadController {

  private final KbUploadSessionService service;

  public KbUploadController(KbUploadSessionService service) {
    this.service = service;
  }

  @PostMapping("/init")
  public ApiResponse<UploadInitResponse> init(@Valid @RequestBody UploadInitRequest request) {
    return ApiResponse.success(service.init(RequestContext.getRequired(), request));
  }

  @PostMapping("/{sessionId}/chunks")
  public ApiResponse<Void> chunk(@PathVariable String sessionId, @RequestParam("index") int index, @RequestPart("file") MultipartFile file) {
    service.uploadChunk(RequestContext.getRequired(), sessionId, index, file);
    return ApiResponse.success(null, "分片已接收");
  }

  @PostMapping("/{sessionId}/merge")
  public ApiResponse<KbDocumentRecord> merge(@PathVariable String sessionId) {
    return ApiResponse.success(service.merge(RequestContext.getRequired(), sessionId), "文件已上传，正在解析");
  }
}
