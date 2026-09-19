package com.novaops.backend.kb.controller;

import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.security.RequestContext;
import com.novaops.backend.kb.dto.KbDocumentQuery;
import com.novaops.backend.kb.dto.UpdateDocumentTitleRequest;
import com.novaops.backend.kb.model.KbChunkRecord;
import com.novaops.backend.kb.model.KbDocumentRecord;
import com.novaops.backend.kb.service.KbDocumentService;
import com.novaops.backend.kb.service.QdrantVectorGateway;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/kb/documents")
public class KbController {
  private final KbDocumentService service; private final QdrantVectorGateway qdrant; public KbController(KbDocumentService service,QdrantVectorGateway qdrant){this.service=service;this.qdrant=qdrant;}
  @GetMapping("/health") public ApiResponse<java.util.Map<String,Object>> health(){return ApiResponse.success(java.util.Map.of("qdrantAvailable",qdrant.healthy()));}
  @PostMapping public ApiResponse<KbDocumentRecord> upload(@RequestParam(required=false) String title,@RequestPart("file") MultipartFile file){return ApiResponse.success(service.upload(RequestContext.getRequired(),title,file),"文件已上传，正在解析");}
  @GetMapping public ApiResponse<PageResult<KbDocumentRecord>> list(@Valid KbDocumentQuery query){return ApiResponse.success(service.list(RequestContext.getRequired(),query));}
  @GetMapping("/{id}") public ApiResponse<KbDocumentRecord> detail(@PathVariable String id){return ApiResponse.success(service.detail(RequestContext.getRequired(),id));}
  @GetMapping("/{id}/chunks") public ApiResponse<List<KbChunkRecord>> chunks(@PathVariable String id){return ApiResponse.success(service.chunks(RequestContext.getRequired(),id));}
  @PutMapping("/{id}") public ApiResponse<Void> title(@PathVariable String id,@Valid @RequestBody UpdateDocumentTitleRequest request){service.updateTitle(RequestContext.getRequired(),id,request.getTitle());return ApiResponse.success(null,"标题已更新");}
  @PostMapping("/{id}/replace") public ApiResponse<KbDocumentRecord> replace(@PathVariable String id,@RequestParam(required=false) String title,@RequestPart("file") MultipartFile file){return ApiResponse.success(service.replace(RequestContext.getRequired(),id,title,file),"文件已替换，正在解析");}
  @PostMapping("/{id}/retry") public ApiResponse<KbDocumentRecord> retry(@PathVariable String id){return ApiResponse.success(service.retry(RequestContext.getRequired(),id),"已重新提交解析");}
  @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable String id){service.delete(RequestContext.getRequired(),id);return ApiResponse.success(null,"文档已删除");}
}
