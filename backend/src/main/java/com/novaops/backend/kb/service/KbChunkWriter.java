package com.novaops.backend.kb.service;

import com.novaops.backend.kb.mapper.KbMapper;
import com.novaops.backend.kb.model.KbChunkRecord;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 分块重建的独立事务边界：删除旧分块与写入新分块必须同成同败。 */
@Service
public class KbChunkWriter {

  private final KbMapper mapper;

  public KbChunkWriter(KbMapper mapper) {
    this.mapper = mapper;
  }

  @Transactional
  public void replace(String documentId, List<KbChunkRecord> records) {
    mapper.deleteChunks(documentId);
    if (!records.isEmpty()) {
      mapper.insertChunks(records);
    }
  }
}
