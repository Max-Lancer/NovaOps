package com.novaops.backend.kb.service;

import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.kb.mapper.KbMapper;
import com.novaops.backend.kb.model.KbChunkRecord;
import com.novaops.backend.kb.model.KbDocumentRecord;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档删除的独立事务边界。单独拆成 bean 是为了让 {@code @Transactional} 真正生效：
 * 在 {@link KbDocumentService} 内部自调用会绕过 Spring 代理，导致分块删除与文档软删不再原子。
 */
@Service
public class KbDocumentRemovalService {

  private final KbMapper mapper;
  private final KbFileStorage storage;
  private final QdrantVectorGateway vectorStore;

  public KbDocumentRemovalService(KbMapper mapper, KbFileStorage storage, QdrantVectorGateway vectorStore) {
    this.mapper = mapper;
    this.storage = storage;
    this.vectorStore = vectorStore;
  }

  @Transactional
  public void remove(KbDocumentRecord document) {
    List<KbChunkRecord> chunks = mapper.listChunks(document.getId());
    // 向量删除在事务之外且不可回滚：失败直接中止，文档保留、可稍后重试
    try {
      if (!chunks.isEmpty()) {
        vectorStore.delete(chunks.stream().map(KbChunkRecord::getVectorId).toList());
      }
    } catch (Exception ex) {
      throw new BusinessException(503, "向量库不可用，文档未删除，可稍后重试");
    }
    mapper.deleteChunks(document.getId());
    mapper.softDeleteDocument(document.getId());
    storage.delete(Path.of(document.getStoragePath()));
  }
}
