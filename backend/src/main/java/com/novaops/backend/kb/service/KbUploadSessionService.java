package com.novaops.backend.kb.service;

import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.kb.config.KbProperties;
import com.novaops.backend.kb.dto.UploadInitRequest;
import com.novaops.backend.kb.dto.UploadInitResponse;
import com.novaops.backend.kb.mapper.KbMapper;
import com.novaops.backend.kb.mapper.KbUploadMapper;
import com.novaops.backend.kb.model.KbDocumentRecord;
import com.novaops.backend.kb.model.KbUploadSessionRecord;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传的三个阶段：init（判定秒传 / 返回续传进度）→ chunk（逐片接收，可乱序可重传）→ merge（校验并建档）。
 * 断点续传的依据是 kb_upload_chunk 里已落库的分片序号，服务端分片文件先写临时名再原子改名，
 * 因此已记录的分片一定是完整的。
 */
@Service
public class KbUploadSessionService {

  private static final int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;
  private static final int MIN_CHUNK_SIZE = 256 * 1024;
  private static final int MAX_CHUNK_SIZE = 8 * 1024 * 1024;
  private static final int MAX_CHUNK_COUNT = 4096;
  private static final String PART_DIRECTORY = "_uploads";

  private final KbUploadMapper uploadMapper;
  private final KbMapper documentMapper;
  private final KbDocumentService documentService;
  private final KbFileStorage storage;
  private final AuthService authService;
  private final KbProperties properties;

  public KbUploadSessionService(KbUploadMapper uploadMapper, KbMapper documentMapper, KbDocumentService documentService, KbFileStorage storage, AuthService authService, KbProperties properties) {
    this.uploadMapper = uploadMapper;
    this.documentMapper = documentMapper;
    this.documentService = documentService;
    this.storage = storage;
    this.authService = authService;
    this.properties = properties;
  }

  public UploadInitResponse init(CurrentSession session, UploadInitRequest request) {
    authService.assertAdmin(session);
    String extension = storage.extension(request.getFileName());
    String hash = normalizeHash(request.getContentHash());
    if (request.getFileSize() > properties.getMaxFileSizeBytes()) {
      throw new BusinessException(400, "文件超过允许的大小上限");
    }
    // 秒传：同内容已有就绪文档，直接复用，一个字节都不用传
    KbDocumentRecord ready = documentMapper.findReadyDocumentByHash(hash);
    if (ready != null) {
      return UploadInitResponse.instant(ready.getId());
    }

    int chunkSize = normalizeChunkSize(request.getChunkSize());
    int chunkCount = (int) ((request.getFileSize() + chunkSize - 1) / chunkSize);
    if (chunkCount < 1 || chunkCount > MAX_CHUNK_COUNT) {
      throw new BusinessException(400, "分片数量超出上限");
    }

    KbUploadSessionRecord record = uploadMapper.findSessionByHash(hash);
    if (record == null) {
      record = new KbUploadSessionRecord();
      record.setId(IdGenerator.randomId("up"));
      record.setContentHash(hash);
      record.setFileName(request.getFileName());
      record.setFileType(extension);
      record.setFileSize(request.getFileSize());
      record.setChunkSize(chunkSize);
      record.setChunkCount(chunkCount);
      record.setStatus("UPLOADING");
      record.setCreatedBy(session.getUserId());
      uploadMapper.insertSession(record);
    } else if (needRestart(record, chunkSize, chunkCount, request.getFileSize())) {
      // 上一轮会话已结束，或分片口径变了：丢掉旧进度，避免把不同切法的分片拼在一起
      uploadMapper.deleteUploadedChunks(record.getId());
      deleteParts(record.getId());
      uploadMapper.resetSession(record.getId(), chunkSize, chunkCount);
    }
    return UploadInitResponse.resume(record.getId(), uploadMapper.listUploadedIndexes(record.getId()));
  }

  private static boolean needRestart(KbUploadSessionRecord record, int chunkSize, int chunkCount, long fileSize) {
    if (!"UPLOADING".equals(record.getStatus())) {
      return true;
    }
    return record.getChunkSize() == null || record.getChunkSize() != chunkSize
        || record.getChunkCount() == null || record.getChunkCount() != chunkCount
        || record.getFileSize() == null || record.getFileSize() != fileSize;
  }

  public void uploadChunk(CurrentSession session, String sessionId, int index, MultipartFile file) {
    authService.assertAdmin(session);
    KbUploadSessionRecord record = requireSession(sessionId);
    if (!"UPLOADING".equals(record.getStatus())) {
      throw new BusinessException(409, "上传会话已结束，请重新初始化");
    }
    if (index < 0 || index >= record.getChunkCount()) {
      throw new BusinessException(400, "分片序号超出范围");
    }
    if (file == null || file.isEmpty()) {
      throw new BusinessException(400, "分片内容不能为空");
    }
    if (file.getSize() > record.getChunkSize()) {
      throw new BusinessException(400, "分片大小超过声明值");
    }
    Path directory = partDirectory(sessionId);
    Path temporary = directory.resolve(index + ".part.tmp");
    try {
      Files.createDirectories(directory);
      try (InputStream input = file.getInputStream()) {
        Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      KbFileStorage.commit(temporary, directory.resolve(index + ".part"));
    } catch (IOException ex) {
      KbFileStorage.deleteQuietly(temporary);
      throw new BusinessException(500, "分片保存失败");
    }
    // 分片文件已经原子落位，才把序号记进库；重传同一片是幂等的
    uploadMapper.markChunkUploaded(sessionId, index, (int) file.getSize());
  }

  public KbDocumentRecord merge(CurrentSession session, String sessionId) {
    authService.assertAdmin(session);
    KbUploadSessionRecord record = requireSession(sessionId);
    if (record.getDocumentId() != null) {
      return documentMapper.findDocument(record.getDocumentId());
    }
    if (!"UPLOADING".equals(record.getStatus())) {
      throw new BusinessException(409, "上传会话状态不允许合并");
    }
    // 建档成功但会话状态没来得及更新时的幂等兜底：同内容文档已存在则直接复用
    KbDocumentRecord existing = documentMapper.findDocumentByHash(record.getContentHash());
    if (existing != null) {
      finishSession(sessionId, existing.getId());
      return existing;
    }
    int uploaded = uploadMapper.countUploaded(sessionId);
    if (uploaded < record.getChunkCount()) {
      throw new BusinessException(409, "分片不完整，还缺少 " + (record.getChunkCount() - uploaded) + " 片");
    }

    String documentId = IdGenerator.randomId("doc");
    Path temporary = storage.temporaryPath(documentId);
    String hash;
    try (OutputStream output = Files.newOutputStream(temporary)) {
      MessageDigest digest = KbFileStorage.sha256();
      for (int index = 0; index < record.getChunkCount(); index++) {
        Path part = partDirectory(sessionId).resolve(index + ".part");
        if (!Files.exists(part)) {
          throw new BusinessException(409, "缺少分片 " + index);
        }
        try (InputStream input = Files.newInputStream(part)) {
          KbFileStorage.transfer(input, output, digest);
        }
      }
      hash = HexFormat.of().formatHex(digest.digest());
    } catch (IOException ex) {
      KbFileStorage.deleteQuietly(temporary);
      throw new BusinessException(500, "分片合并失败");
    } catch (RuntimeException ex) {
      KbFileStorage.deleteQuietly(temporary);
      throw ex;
    }

    if (!hash.equalsIgnoreCase(record.getContentHash())) {
      KbFileStorage.deleteQuietly(temporary);
      uploadMapper.updateSessionStatus(sessionId, "FAILED", null);
      throw new BusinessException(400, "文件校验失败：合并结果与客户端声明的摘要不一致，请重新上传");
    }

    KbFileStorage.StoredFile stored = storage.commitTemporary(documentId, record.getFileType(), temporary, hash);
    KbDocumentRecord document = documentService.register(session, documentId, null, record.getFileName(), stored, record.getFileSize());
    finishSession(sessionId, document.getId());
    return document;
  }

  private void finishSession(String sessionId, String documentId) {
    uploadMapper.updateSessionStatus(sessionId, "DONE", documentId);
    uploadMapper.deleteUploadedChunks(sessionId);
    deleteParts(sessionId);
  }

  private KbUploadSessionRecord requireSession(String sessionId) {
    KbUploadSessionRecord record = uploadMapper.findSession(sessionId);
    if (record == null) {
      throw new BusinessException(404, "上传会话不存在或已过期");
    }
    return record;
  }

  private static String normalizeHash(String raw) {
    String hash = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (!hash.matches("[0-9a-f]{64}")) {
      throw new BusinessException(400, "内容摘要必须是 64 位十六进制 SHA-256");
    }
    return hash;
  }

  private static int normalizeChunkSize(Integer requested) {
    if (requested == null) {
      return DEFAULT_CHUNK_SIZE;
    }
    if (requested < MIN_CHUNK_SIZE || requested > MAX_CHUNK_SIZE) {
      throw new BusinessException(400, "分片大小须在 256KB ~ 8MB 之间");
    }
    return requested;
  }

  /** 分片目录固定落在存储根目录的 _uploads/{sessionId} 下，并做一次目录穿越校验。 */
  private Path partDirectory(String sessionId) {
    Path root = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
    Path directory = root.resolve(PART_DIRECTORY).resolve(sessionId).normalize();
    if (!directory.startsWith(root)) {
      throw new BusinessException(400, "非法存储路径");
    }
    return directory;
  }

  private void deleteParts(String sessionId) {
    Path directory = partDirectory(sessionId);
    if (!Files.exists(directory)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(directory)) {
      walk.sorted(Comparator.reverseOrder()).forEach(KbFileStorage::deleteQuietly);
    } catch (IOException ignored) {
    }
  }
}
