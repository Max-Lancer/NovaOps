package com.novaops.backend.ops.tools;

import com.novaops.backend.agent.engine.AgentTool;
import com.novaops.backend.agent.tools.ToolArgs;
import com.novaops.backend.agent.engine.AgentToolExecutor;
import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.agent.engine.ToolSchema;
import com.novaops.backend.ops.asset.dto.AssetListItemResponse;
import com.novaops.backend.ops.asset.service.AssetService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 资产只读工具。
 */
public final class AssetReadTools {

  private AssetReadTools() {
  }

  @Component
  @AgentTool(name = "asset.search", title = "检索资产", description = "按关键词、类型或状态检索资产清单",
      permission = "asset:view")
  public static class AssetSearchTool implements AgentToolExecutor {

    private final AssetService assetService;

    public AssetSearchTool(AssetService assetService) {
      this.assetService = assetService;
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      var query = new com.novaops.backend.ops.asset.dto.AssetListQuery();
      query.setKeyword(ToolArgs.asString(args, "keyword"));
      query.setType(ToolArgs.asString(args, "type"));
      query.setStatus(ToolArgs.asString(args, "status"));
      int maxResults = ToolArgs.asInt(args, "maxResults", 10, 1, 20);
      query.setPage(1);
      query.setPageSize(maxResults);
      try {
        var page = assetService.list(
            new CurrentSession(ctx.userId(), ctx.username(), ctx.username()), query);
        if (page.getList() == null || page.getList().isEmpty()) {
          return ToolResult.empty("没有匹配的资产");
        }
        List<Map<String, Object>> assets = new ArrayList<>();
        for (AssetListItemResponse item : page.getList()) {
          Map<String, Object> asset = new LinkedHashMap<>();
          asset.put("id", item.getId());
          asset.put("assetNo", item.getAssetNo());
          asset.put("name", item.getName());
          asset.put("type", item.getType());
          asset.put("status", item.getStatus());
          asset.put("ownerName", item.getOwnerName());
          asset.put("location", item.getLocation());
          assets.add(asset);
        }
        return ToolResult.ok(Map.of(
            "total", page.getTotal(),
            "returned", assets.size(),
            "assets", assets));
      } catch (BusinessException ex) {
        return ToolResult.failed(ex.getMessage());
      }
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object()
          .string("keyword", "资产名称/编号关键词", false)
          .string("type", "资产类型", false)
          .string("status", "资产状态", false)
          .integer("maxResults", "返回条数上限（1-20，默认 10）", false);
    }
  }
}
