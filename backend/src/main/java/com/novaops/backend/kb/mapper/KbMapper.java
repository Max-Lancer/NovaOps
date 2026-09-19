package com.novaops.backend.kb.mapper;
import com.novaops.backend.kb.model.KbChunkRecord; import com.novaops.backend.kb.model.KbDocumentRecord; import java.util.List; import org.apache.ibatis.annotations.Param;
public interface KbMapper {
  void insertDocument(KbDocumentRecord document); KbDocumentRecord findDocument(@Param("id") String id);
  /** 按内容摘要查已就绪文档，用于秒传预检。 */
  KbDocumentRecord findReadyDocumentByHash(@Param("contentHash") String contentHash);
  /** 按内容摘要查任意状态的文档，用于分片合并的幂等兜底。 */
  KbDocumentRecord findDocumentByHash(@Param("contentHash") String contentHash);
  List<KbDocumentRecord> listDocuments(@Param("keyword") String keyword,@Param("fileType") String fileType,@Param("status") String status,@Param("offset") int offset,@Param("pageSize") int pageSize);
  long countDocuments(@Param("keyword") String keyword,@Param("fileType") String fileType,@Param("status") String status);
  void updateStatus(@Param("id") String id, @Param("status") String status, @Param("chunkCount") int chunkCount, @Param("errorMsg") String errorMsg);
  void updateTitle(@Param("id") String id, @Param("title") String title);
  /** 原地替换源文件时更新文件元信息与内容摘要，不触碰分块与状态。 */
  void updateFileMeta(@Param("id") String id, @Param("fileName") String fileName, @Param("fileType") String fileType, @Param("fileSize") long fileSize, @Param("storagePath") String storagePath, @Param("contentHash") String contentHash);
  void insertChunks(@Param("records") List<KbChunkRecord> records);
  List<KbChunkRecord> listChunks(@Param("documentId") String documentId); void deleteChunks(@Param("documentId") String documentId); void softDeleteDocument(@Param("id") String id);
}
