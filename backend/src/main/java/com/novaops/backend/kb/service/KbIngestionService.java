package com.novaops.backend.kb.service;

import com.novaops.backend.kb.mapper.KbMapper;
import com.novaops.backend.kb.model.KbChunkRecord;
import com.novaops.backend.kb.model.KbDocumentRecord;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class KbIngestionService {

  private final KbMapper mapper;
  private final TextChunker chunker;
  private final QdrantVectorGateway vectorStore;
  private final KbChunkWriter chunkWriter;

  public KbIngestionService(KbMapper mapper, TextChunker chunker, QdrantVectorGateway vectorStore, KbChunkWriter chunkWriter) {
    this.mapper = mapper;
    this.chunker = chunker;
    this.vectorStore = vectorStore;
    this.chunkWriter = chunkWriter;
  }

  @Async
  public void process(KbDocumentRecord source) {
    // 载入既有分块：重试/替换时按内容身份复用既有向量，只对变化的分块重新 embedding
    Map<String, KbChunkRecord> existing = new LinkedHashMap<>();
    for (KbChunkRecord record : mapper.listChunks(source.getId())) {
      existing.putIfAbsent(record.getVectorId(), record);
    }

    Set<String> created = new HashSet<>();
    boolean chunksCommitted = false;
    try (InputStream input = Files.newInputStream(Path.of(source.getStoragePath()))) {
      StructuredTextParser.Result parsed = "md".equals(source.getFileType()) ? parseMarkdown(input) : parseWithTika(input);
      List<String> chunks = chunker.split(parsed.blocks(), parsed.plainText());
      if (chunks.isEmpty()) {
        throw new IllegalStateException("文档未解析出有效文本");
      }
      mapper.updateStatus(source.getId(), "VECTORIZING", 0, null);

      List<KbChunkRecord> records = new ArrayList<>();
      List<QdrantVectorGateway.VectorPoint> pending = new ArrayList<>();
      Set<String> reused = new HashSet<>();
      Set<String> seen = new HashSet<>();
      for (String content : chunks) {
        String vectorId = identity(source.getId(), content);
        if (!seen.add(vectorId)) {
          continue; // 同一文档内重复内容只保留一个分块与向量，避免主键冲突与重复向量
        }
        KbChunkRecord record = new KbChunkRecord();
        record.setId("chunk-" + vectorId);
        record.setDocumentId(source.getId());
        record.setChunkIndex(records.size());
        record.setContent(content);
        record.setVectorId(vectorId);
        records.add(record);
        if (existing.containsKey(vectorId)) {
          reused.add(vectorId);
        } else {
          created.add(vectorId);
          Map<String, Object> payload = new HashMap<>();
          payload.put("documentId", source.getId());
          payload.put("chunkId", record.getId());
          payload.put("documentName", source.getTitle());
          pending.add(new QdrantVectorGateway.VectorPoint(vectorId, content, payload));
        }
      }

      if (!pending.isEmpty()) {
        vectorStore.add(pending);
      }
      chunkWriter.replace(source.getId(), records);
      chunksCommitted = true;

      // 只清理本次既未复用也未新建的旧向量（内容已被删改掉的分块）；残留向量不影响新内容检索
      List<String> stale = existing.keySet().stream().filter(id -> !reused.contains(id) && !created.contains(id)).toList();
      if (!stale.isEmpty()) {
        try {
          vectorStore.delete(stale);
        } catch (Exception ignored) {
        }
      }
      mapper.updateStatus(source.getId(), "READY", records.size(), null);
    } catch (Exception ex) {
      // 只回收本次新建的向量：复用向量属于既有内容，失败时必须保留；分块已落库时更不能删
      if (!chunksCommitted && !created.isEmpty()) {
        try {
          vectorStore.delete(new ArrayList<>(created));
        } catch (Exception ignored) {
        }
      }
      String message = ex.getMessage() == null ? "解析或向量化失败" : ex.getMessage();
      mapper.updateStatus(source.getId(), "FAILED", 0, message.substring(0, Math.min(900, message.length())));
    }
  }

  /** 分块身份：由 documentId 与内容派生。内容不变时重解析得到同一个 id，可直接复用既有向量。 */
  static String identity(String documentId, String content) {
    return UUID.nameUUIDFromBytes((documentId + "\u0000" + content).getBytes(StandardCharsets.UTF_8)).toString();
  }

  /** md 文件走原生 Markdown 解析:Tika 标准包无 markdown 解析器,交给它只会退化为纯文本。 */
  private StructuredTextParser.Result parseMarkdown(InputStream input) {
    try {
      return StructuredTextParser.parseMarkdown(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException("文档解析失败: " + ex.getMessage(), ex);
    }
  }

  private StructuredTextParser.Result parseWithTika(InputStream input) {
    // ToXMLContentHandler 保留标题/段落/代码等 XHTML 结构供结构化切片使用,
    // 且不像 BodyContentHandler 那样有 10 万字符截断上限,大文档不会静默丢内容
    ToXMLContentHandler handler = new ToXMLContentHandler();
    AutoDetectParser parser = new AutoDetectParser();
    Metadata metadata = new Metadata();
    try {
      parser.parse(new BufferedInputStream(input), handler, metadata);
    } catch (Exception ex) {
      throw new IllegalStateException("文档解析失败: " + ex.getMessage(), ex);
    }
    return StructuredTextParser.parse(handler.toString());
  }
}
