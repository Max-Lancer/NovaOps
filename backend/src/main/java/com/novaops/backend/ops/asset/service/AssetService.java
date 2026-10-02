package com.novaops.backend.ops.asset.service;

import com.novaops.backend.ops.asset.dto.AssetActionRequest;
import com.novaops.backend.ops.asset.dto.AssetDetailResponse;
import com.novaops.backend.ops.asset.dto.AssetListItemResponse;
import com.novaops.backend.ops.asset.dto.AssetListQuery;
import com.novaops.backend.ops.asset.dto.AssetSimpleResponse;
import com.novaops.backend.ops.asset.dto.CreateAssetRequest;
import com.novaops.backend.ops.asset.dto.UpdateAssetRequest;
import com.novaops.backend.ops.asset.mapper.AssetMapper;
import com.novaops.backend.ops.asset.model.AssetLogRecord;
import com.novaops.backend.ops.asset.model.AssetRecord;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.util.DateTimeUtils;
import com.novaops.backend.common.util.IdGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetService {

  private final AssetMapper assetMapper;
  private final AuthService authService;

  public AssetService(AssetMapper assetMapper, AuthService authService) {
    this.assetMapper = assetMapper;
    this.authService = authService;
  }

  public PageResult<AssetListItemResponse> list(CurrentSession session, AssetListQuery query) {
    long page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    long offset = (page - 1) * pageSize;

    long total = assetMapper.countAssets(query.getStatus(), query.getType(), query.getKeyword());
    List<AssetRecord> records = assetMapper.queryAssets(
        query.getStatus(), query.getType(), query.getKeyword(), offset, pageSize);
    List<AssetListItemResponse> list = records.stream().map(this::toListItem).toList();
    return new PageResult<>(list, page, pageSize, total);
  }

  public AssetDetailResponse detail(CurrentSession session, String id) {
    AssetRecord record = requireAsset(id);
    return buildDetail(record);
  }

  public List<AssetSimpleResponse> batch(CurrentSession session, List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return assetMapper.findByIds(ids).stream()
        .map(a -> new AssetSimpleResponse(a.getId(), a.getName(), a.getStatus()))
        .toList();
  }

  @Transactional
  public AssetDetailResponse create(CurrentSession session, CreateAssetRequest request) {
    LocalDateTime now = LocalDateTime.now();
    AssetRecord record = new AssetRecord();
    record.setId(IdGenerator.randomId("asset"));
    record.setAssetNo(IdGenerator.assetNo());
    record.setName(request.getName());
    record.setType(request.getType());
    record.setStatus("stock");
    record.setOwnerId(null);
    record.setLocation(request.getLocation());
    record.setSpec(request.getSpec());
    record.setRemark(request.getRemark());
    record.setPurchaseDate(LocalDate.now());
    record.setVersion(0);
    record.setCreatedAt(now);
    record.setUpdatedAt(now);
    assetMapper.insertAsset(record);
    insertLog(record.getId(), "receive", session.getUserId(), null, "入库登记");
    return buildDetail(record);
  }

  @Transactional
  public AssetDetailResponse update(CurrentSession session, String id, UpdateAssetRequest request) {
    AssetRecord record = requireAsset(id);

    if (StringUtils.hasText(request.getName())) {
      record.setName(request.getName());
    }
    if (StringUtils.hasText(request.getType())) {
      record.setType(request.getType());
    }
    if (request.getLocation() != null) {
      record.setLocation(request.getLocation());
    }
    if (StringUtils.hasText(request.getSpec())) {
      record.setSpec(request.getSpec());
    }
    if (request.getRemark() != null) {
      record.setRemark(request.getRemark());
    }
    record.setUpdatedAt(LocalDateTime.now());

    if (assetMapper.updateAsset(record) == 0) {
      throw new BusinessException(409, "资产已被他人修改，请刷新后重试");
    }
    insertLog(record.getId(), "update", session.getUserId(), null, "更新资产信息");
    return buildDetail(requireAsset(id));
  }

  @Transactional
  public AssetDetailResponse action(CurrentSession session, String id, AssetActionRequest request) {
    String action = request.getAction();
    String requiredPermission = switch (action) {
      case "claim", "receive" -> "asset:claim";
      case "scrap" -> "asset:scrap";
      default -> throw new BusinessException(400, "不支持的资产动作");
    };
    authService.requirePermission(session, requiredPermission);

    AssetRecord record = requireAsset(id);
    validateTransition(action, record.getStatus());

    switch (action) {
      case "claim" -> {
        String ownerId = request.getOwnerId();
        if (!StringUtils.hasText(ownerId)) {
          throw new BusinessException(400, "领用需指定领用人");
        }
        authService.requireEnabledUser(ownerId);
        record.setOwnerId(ownerId);
        record.setStatus("in_use");
      }
      case "receive" -> {
        record.setOwnerId(null);
        record.setStatus("stock");
      }
      case "scrap" -> {
        record.setOwnerId(null);
        record.setStatus("scrapped");
      }
      default -> throw new BusinessException(400, "不支持的资产动作");
    }

    record.setUpdatedAt(LocalDateTime.now());
    if (assetMapper.updateAsset(record) == 0) {
      throw new BusinessException(409, "资产已被他人修改，请刷新后重试");
    }
    insertLog(record.getId(), action, session.getUserId(), record.getOwnerId(), request.getRemark());
    return buildDetail(requireAsset(id));
  }

  /** 资产状态机转移矩阵：stock → in_use(claim) → stock(receive)，stock/in_use → scrapped(终态)。 */
  private void validateTransition(String action, String currentStatus) {
    switch (action) {
      case "claim" -> {
        if (!"stock".equals(currentStatus)) {
          throw new BusinessException(409, "仅库存中的资产可领用");
        }
      }
      case "receive" -> {
        if (!"in_use".equals(currentStatus)) {
          throw new BusinessException(409, "仅已领用的资产可回收");
        }
      }
      case "scrap" -> {
        if ("scrapped".equals(currentStatus)) {
          throw new BusinessException(409, "资产已报废，不可重复操作");
        }
        if (!"stock".equals(currentStatus) && !"in_use".equals(currentStatus)) {
          throw new BusinessException(409, "当前状态不可报废");
        }
      }
      default -> throw new BusinessException(400, "不支持的资产动作");
    }
  }

  private AssetRecord requireAsset(String id) {
    AssetRecord record = assetMapper.findAsset(id);
    if (record == null) {
      throw new BusinessException(404, "资产不存在");
    }
    return record;
  }

  private AssetDetailResponse buildDetail(AssetRecord record) {
    AssetDetailResponse response = new AssetDetailResponse();
    response.setId(record.getId());
    response.setAssetNo(record.getAssetNo());
    response.setName(record.getName());
    response.setType(record.getType());
    response.setStatus(record.getStatus());
    response.setOwnerId(record.getOwnerId());
    response.setOwnerName(record.getOwnerName());
    response.setLocation(record.getLocation());
    response.setSpec(record.getSpec());
    response.setRemark(record.getRemark());
    response.setPurchaseDate(record.getPurchaseDate() == null ? null : record.getPurchaseDate().toString());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    response.setUpdatedAt(DateTimeUtils.toIsoString(record.getUpdatedAt()));
    response.setRelatedTickets(assetMapper.listRelatedTickets(record.getId()));
    return response;
  }

  private AssetListItemResponse toListItem(AssetRecord record) {
    AssetListItemResponse response = new AssetListItemResponse();
    response.setId(record.getId());
    response.setAssetNo(record.getAssetNo());
    response.setName(record.getName());
    response.setType(record.getType());
    response.setStatus(record.getStatus());
    response.setOwnerId(record.getOwnerId());
    response.setOwnerName(record.getOwnerName());
    response.setLocation(record.getLocation());
    response.setPurchaseDate(record.getPurchaseDate() == null ? null : record.getPurchaseDate().toString());
    response.setUpdatedAt(DateTimeUtils.toIsoString(record.getUpdatedAt()));
    return response;
  }

  private void insertLog(String assetId, String action, String operatorId, String targetUserId, String remark) {
    AssetLogRecord log = new AssetLogRecord();
    log.setId(IdGenerator.randomId("aslog"));
    log.setAssetId(assetId);
    log.setAction(action);
    log.setOperatorId(operatorId);
    log.setTargetUserId(targetUserId);
    log.setRemark(remark);
    log.setCreatedAt(LocalDateTime.now());
    assetMapper.insertLog(log);
  }
}
