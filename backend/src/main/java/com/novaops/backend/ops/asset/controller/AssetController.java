package com.novaops.backend.ops.asset.controller;

import com.novaops.backend.ops.asset.dto.AssetActionRequest;
import com.novaops.backend.ops.asset.dto.AssetDetailResponse;
import com.novaops.backend.ops.asset.dto.AssetListItemResponse;
import com.novaops.backend.ops.asset.dto.AssetListQuery;
import com.novaops.backend.ops.asset.dto.AssetSimpleResponse;
import com.novaops.backend.ops.asset.dto.CreateAssetRequest;
import com.novaops.backend.ops.asset.dto.UpdateAssetRequest;
import com.novaops.backend.ops.asset.service.AssetService;
import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.security.RequestContext;
import com.novaops.backend.common.security.RequirePermission;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

  private final AssetService assetService;

  public AssetController(AssetService assetService) {
    this.assetService = assetService;
  }

  @GetMapping
  @RequirePermission("asset:view")
  public ApiResponse<PageResult<AssetListItemResponse>> list(@Valid @ModelAttribute AssetListQuery query) {
    return ApiResponse.success(assetService.list(RequestContext.getRequired(), query));
  }

  @GetMapping("/batch")
  @RequirePermission("asset:view")
  public ApiResponse<List<AssetSimpleResponse>> batch(@RequestParam("ids") String ids) {
    List<String> idList = Arrays.stream(ids.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
    return ApiResponse.success(assetService.batch(RequestContext.getRequired(), idList));
  }

  @GetMapping("/{id}")
  @RequirePermission("asset:view")
  public ApiResponse<AssetDetailResponse> detail(@PathVariable("id") String id) {
    return ApiResponse.success(assetService.detail(RequestContext.getRequired(), id));
  }

  @PostMapping
  @RequirePermission("asset:create")
  public ApiResponse<AssetDetailResponse> create(@Valid @RequestBody CreateAssetRequest request) {
    return ApiResponse.success(assetService.create(RequestContext.getRequired(), request), "资产入库成功");
  }

  @PutMapping("/{id}")
  @RequirePermission("asset:edit")
  public ApiResponse<AssetDetailResponse> update(@PathVariable("id") String id, @Valid @RequestBody UpdateAssetRequest request) {
    return ApiResponse.success(assetService.update(RequestContext.getRequired(), id, request), "资产更新成功");
  }

  // 资产流转按动作细分权限（claim/receive 走 asset:claim，scrap 走 asset:scrap），在 Service 内校验
  @PostMapping("/{id}/actions")
  public ApiResponse<AssetDetailResponse> action(@PathVariable("id") String id, @Valid @RequestBody AssetActionRequest request) {
    return ApiResponse.success(assetService.action(RequestContext.getRequired(), id, request), "资产状态更新成功");
  }
}
