package com.novaops.backend.ops.asset.mapper;

import com.novaops.backend.ops.asset.model.AssetLogRecord;
import com.novaops.backend.ops.asset.model.AssetRecord;
import com.novaops.backend.ops.asset.dto.RelatedTicket;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AssetMapper {

  long countAssets(
      @Param("status") String status,
      @Param("type") String type,
      @Param("keyword") String keyword
  );

  List<AssetRecord> queryAssets(
      @Param("status") String status,
      @Param("type") String type,
      @Param("keyword") String keyword,
      @Param("offset") long offset,
      @Param("limit") long limit
  );

  AssetRecord findAsset(@Param("id") String id);

  void insertAsset(AssetRecord record);

  int updateAsset(AssetRecord record);

  List<AssetRecord> findByIds(@Param("ids") List<String> ids);

  void insertLog(AssetLogRecord record);

  List<AssetLogRecord> listLogs(@Param("assetId") String assetId);

  List<RelatedTicket> listRelatedTickets(@Param("assetId") String assetId);
}
