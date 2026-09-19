package com.novaops.backend.kb.service;

import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.kb.dto.KbDocumentQuery;
import com.novaops.backend.kb.mapper.KbMapper;
import com.novaops.backend.kb.model.KbChunkRecord;
import com.novaops.backend.kb.model.KbDocumentRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KbDocumentService {

  private final KbMapper mapper;
  private final AuthService authService;
  private final KbFileStorage storage;
  private final KbIngestionService ingestion;
  private final KbDocumentRemovalService removal;

  public KbDocumentService(KbMapper mapper, AuthService authService, KbFileStorage storage, KbIngestionService ingestion, KbDocumentRemovalService removal) {
    this.mapper = mapper;
    this.authService = authService;
    this.storage = storage;
    this.ingestion = ingestion;
    this.removal = removal;
  }

  public KbDocumentRecord upload(CurrentSession session, String title, MultipartFile file) {
    authService.assertAdmin(session);
    String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
    String id = IdGenerator.randomId("doc");
    // 落盘同时算出内容摘要：文件名与摘要绑定，同一内容不会写第二份文件
    KbFileStorage.StoredFile stored = storage.save(id, file);
    return register(session, id, title, original, stored, file.getSize());
  }

  /**
   * 建档并触发异步解析。分片合并（{@code KbUploadSessionService}）与直传共用这一段，
   * 保证两条上传路径的落库、孤儿文件回收与解析触发行为完全一致。
   */
  public KbDocumentRecord register(CurrentSession session, String id, String title, String fileName, KbFileStorage.StoredFile stored, long fileSize) {
    KbDocumentRecord document = new KbDocumentRecord();
    document.setId(id);
    document.setTitle(title == null || title.isBlank() ? fileName : title.trim());
    document.setFileName(fileName);
    document.setFileType(stored.extension());
    document.setFileSize(fileSize);
    document.setStoragePath(stored.path().toString());
    document.setContentHash(stored.contentHash());
    document.setStatus("PARSING");
    document.setChunkCount(0);
    document.setCreatedBy(session.getUserId());

    try {
      mapper.insertDocument(document);
    } catch (RuntimeException ex) {
      // 建档失败必须回收已落盘文件，否则磁盘会留下没有元数据的孤儿文件
      storage.delete(stored.path());
      throw ex;
    }
    ingestion.process(document);
    return mapper.findDocument(id);
  }

  public PageResult<KbDocumentRecord> list(CurrentSession session, KbDocumentQuery query) {
    authService.assertAdmin(session);
    int offset = (query.getPage() - 1) * query.getPageSize();
    return new PageResult<>(
        mapper.listDocuments(query.getKeyword(), query.getFileType(), query.getStatus(), offset, query.getPageSize()),
        query.getPage(),
        query.getPageSize(),
        mapper.countDocuments(query.getKeyword(), query.getFileType(), query.getStatus()));
  }

  public KbDocumentRecord detail(CurrentSession session, String id) {
    authService.assertAdmin(session);
    return requireDocument(id);
  }

  public List<KbChunkRecord> chunks(CurrentSession session, String id) {
    authService.assertAdmin(session);
    requireDocument(id);
    return mapper.listChunks(id);
  }

  public void updateTitle(CurrentSession session, String id, String title) {
    authService.assertAdmin(session);
    requireDocument(id);
    mapper.updateTitle(id, title.trim());
  }

  public void delete(CurrentSession session, String id) {
    authService.assertAdmin(session);
    // 经独立 bean 调用，保证 remove 上的 @Transactional 生效（自调用会绕过 Spring 代理）
    removal.remove(requireDocument(id));
  }

  /** 仅允许 FAILED 文档重试：源文件仍在校验通过后重置状态并重新走解析流水线。 */
  public KbDocumentRecord retry(CurrentSession session, String id) {
    authService.assertAdmin(session);
    KbDocumentRecord document = requireDocument(id);
    if (!"FAILED".equals(document.getStatus())) {
      throw new BusinessException(409, "仅解析失败的文档可以重试");
    }
    if (document.getStoragePath() == null || !Files.exists(Path.of(document.getStoragePath()))) {
      throw new BusinessException(410, "源文件已丢失，无法重试，请重新上传");
    }
    mapper.updateStatus(id, "PARSING", 0, null);
    KbDocumentRecord refreshed = requireDocument(id);
    ingestion.process(refreshed);
    return refreshed;
  }

  /**
   * 原地替换源文件：沿用同一个文档 id，使分块内容身份保持稳定，
   * 解析时才能复用未变分块的既有向量，而不是整篇重新 embedding。
   */
  public KbDocumentRecord replace(CurrentSession session, String id, String title, MultipartFile file) {
    authService.assertAdmin(session);
    KbDocumentRecord document = requireDocument(id);
    String original = file.getOriginalFilename() == null ? document.getFileName() : file.getOriginalFilename();
    // 落盘会先做类型/大小/非空校验：校验失败时数据库不动，旧文档原样保留
    KbFileStorage.StoredFile stored = storage.save(id, file);
    Path previousPath = document.getStoragePath() == null ? null : Path.of(document.getStoragePath());
    String nextTitle = title == null || title.isBlank() ? document.getTitle() : title.trim();
    boolean sameContent = stored.contentHash().equals(document.getContentHash());

    mapper.updateFileMeta(id, original, stored.extension(), file.getSize(), stored.path().toString(), stored.contentHash());
    if (!nextTitle.equals(document.getTitle())) {
      mapper.updateTitle(id, nextTitle);
    }
    if (previousPath != null && !previousPath.equals(stored.path())) {
      storage.delete(previousPath);
    }
    // 内容与已就绪版本完全一致：直接复用既有分块与向量，跳过解析与 embedding
    if (sameContent && "READY".equals(document.getStatus())) {
      return requireDocument(id);
    }
    mapper.updateStatus(id, "PARSING", document.getChunkCount() == null ? 0 : document.getChunkCount(), null);
    ingestion.process(requireDocument(id));
    return requireDocument(id);
  }

  private KbDocumentRecord requireDocument(String id) {
    KbDocumentRecord document = mapper.findDocument(id);
    if (document == null) {
      throw new BusinessException(404, "知识库文档不存在");
    }
    return document;
  }
}
