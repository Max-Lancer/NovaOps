package com.novaops.backend.kb.mapper;

import com.novaops.backend.kb.model.KbUploadSessionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 分片上传会话与已接收分片的持久化：断点续传靠这张表识别「哪些片已经在服务端」。 */
public interface KbUploadMapper {
  void insertSession(KbUploadSessionRecord record);
  KbUploadSessionRecord findSession(@Param("id") String id);
  /** 同一内容摘要只保留一个会话，重复上传同一文件天然命中同一个会话。 */
  KbUploadSessionRecord findSessionByHash(@Param("contentHash") String contentHash);
  /** 分片参数或上一轮会话已结束时，清空进度重新开始一轮干净的上传。 */
  void resetSession(@Param("id") String id,@Param("chunkSize") int chunkSize,@Param("chunkCount") int chunkCount);
  void updateSessionStatus(@Param("id") String id,@Param("status") String status,@Param("documentId") String documentId);
  void markChunkUploaded(@Param("sessionId") String sessionId,@Param("chunkIndex") int chunkIndex,@Param("size") int size);
  List<Integer> listUploadedIndexes(@Param("sessionId") String sessionId);
  int countUploaded(@Param("sessionId") String sessionId);
  void deleteUploadedChunks(@Param("sessionId") String sessionId);
}
