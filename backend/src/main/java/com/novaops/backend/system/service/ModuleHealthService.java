package com.novaops.backend.system.service;

import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.kb.config.KbProperties;
import com.novaops.backend.system.dto.ModuleCheckItemResponse;
import com.novaops.backend.system.dto.ModuleHealthResponse;
import com.novaops.backend.system.mapper.SystemObserveMapper;
import com.novaops.backend.system.model.ModuleCheckRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ModuleHealthService {

  private static final Logger log = LoggerFactory.getLogger(ModuleHealthService.class);
  private static final int PROBE_TIMEOUT_MS = 2000;
  private static final int HISTORY_LIMIT = 48;

  private final SystemObserveMapper observeMapper;
  private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
  private final KbProperties kbProperties;
  private final String modelApiKey;
  private final String modelBaseUrl;

  public ModuleHealthService(
      SystemObserveMapper observeMapper,
      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
      KbProperties kbProperties,
      @Value("${spring.ai.openai.api-key:}") String modelApiKey,
      @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}") String modelBaseUrl
  ) {
    this.observeMapper = observeMapper;
    this.jdbcTemplate = jdbcTemplate;
    this.kbProperties = kbProperties;
    this.modelApiKey = modelApiKey;
    this.modelBaseUrl = modelBaseUrl;
  }

  public ModuleHealthResponse checkAll() {
    String batchId = IdGenerator.randomId("chk");
    LocalDateTime checkedAt = LocalDateTime.now();
    List<ProbeResult> results = List.of(probeTicket(), probeAsset(), probeKb(), probeAgent());
    for (ProbeResult result : results) {
      ModuleCheckRecord record = new ModuleCheckRecord();
      record.setId(IdGenerator.randomId("mc"));
      record.setBatchId(batchId);
      record.setModuleCode(result.code());
      record.setModuleName(result.name());
      record.setStatus(result.status());
      record.setLatencyMs((int) Math.min(result.latencyMs(), Integer.MAX_VALUE));
      record.setMessage(truncate(result.message(), 500));
      record.setCheckedAt(checkedAt);
      observeMapper.insertCheck(record);
    }
    try {
      observeMapper.deleteChecksBefore(checkedAt.minusDays(7));
    } catch (Exception exception) {
      log.warn("prune module checks failed", exception);
    }
    return snapshot();
  }

  public ModuleHealthResponse snapshot() {
    ModuleHealthResponse response = new ModuleHealthResponse();
    String batchId = observeMapper.findLatestBatchId();
    if (StringUtils.hasText(batchId)) {
      response.setLatest(observeMapper.listChecksByBatch(batchId).stream().map(this::toItem).toList());
    }
    List<ModuleCheckItemResponse> history = new ArrayList<>(observeMapper.listRecentChecks(HISTORY_LIMIT).stream().map(this::toItem).toList());
    history.sort(Comparator.comparing(ModuleCheckItemResponse::getCheckedAt));
    response.setHistory(history);
    return response;
  }

  private ProbeResult probeTicket() {
    return probeDatabase("ticket", "工单", "select count(1) from biz_ticket", "工单数据可查询");
  }

  private ProbeResult probeAsset() {
    return probeDatabase("asset", "资产", "select count(1) from biz_asset", "资产数据可查询");
  }

  private ProbeResult probeDatabase(String code, String name, String sql, String okMessage) {
    long started = System.nanoTime();
    try {
      jdbcTemplate.queryForObject(sql, Number.class);
      return new ProbeResult(code, name, "up", elapsed(started), okMessage);
    } catch (Exception exception) {
      return new ProbeResult(code, name, "down", elapsed(started), "数据查询失败：" + exception.getClass().getSimpleName());
    }
  }

  private ProbeResult probeKb() {
    long started = System.nanoTime();
    try {
      jdbcTemplate.queryForObject("select count(1) from kb_document", Number.class);
    } catch (Exception exception) {
      return new ProbeResult("kb", "知识库", "down", elapsed(started), "知识库数据查询失败");
    }
    Path storage = Path.of(kbProperties.getStoragePath());
    try {
      Files.createDirectories(storage);
      if (!Files.isWritable(storage)) {
        return new ProbeResult("kb", "知识库", "down", elapsed(started), "知识库存储目录不可写");
      }
    } catch (Exception exception) {
      return new ProbeResult("kb", "知识库", "down", elapsed(started), "知识库存储目录不可用");
    }
    String qdrantMessage = probeHttp(trimSlash(kbProperties.getQdrantBaseUrl()) + "/collections/" + kbProperties.getQdrantCollection(), kbProperties.getQdrantApiKey());
    if (qdrantMessage != null) {
      return new ProbeResult("kb", "知识库", "degraded", elapsed(started), "文档库可用，Qdrant 不可用：" + qdrantMessage);
    }
    return new ProbeResult("kb", "知识库", "up", elapsed(started), "文档库可用，Qdrant 地址可达（未校验鉴权）");
  }

  private ProbeResult probeAgent() {
    long started = System.nanoTime();
    boolean configured = StringUtils.hasText(modelApiKey) && !"local-not-configured".equals(modelApiKey);
    String failure = probeHttp(trimSlash(modelBaseUrl), null);
    if (!configured && failure != null) {
      return new ProbeResult("agent", "智能体工作台", "degraded", elapsed(started), "模型密钥未配置，且网关不可达");
    }
    if (!configured) {
      return new ProbeResult("agent", "智能体工作台", "degraded", elapsed(started), "模型密钥未配置；网关地址可达（未校验鉴权）");
    }
    if (failure != null) {
      return new ProbeResult("agent", "智能体工作台", "down", elapsed(started), "模型网关不可达：" + failure);
    }
    return new ProbeResult("agent", "智能体工作台", "up", elapsed(started), "模型网关地址可达（未校验鉴权）");
  }

  /** 返回 null 表示地址有 HTTP 响应（含 401/403，只说明可达，不代表鉴权通过）；返回说明表示连接失败。 */
  private String probeHttp(String url, String apiKey) {
    try {
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(PROBE_TIMEOUT_MS);
      factory.setReadTimeout(PROBE_TIMEOUT_MS);
      RestClient.Builder builder = RestClient.builder().requestFactory(factory);
      if (StringUtils.hasText(apiKey)) {
        builder.defaultHeader("api-key", apiKey);
      }
      builder.build().get().uri(url).retrieve().toBodilessEntity();
      return null;
    } catch (RestClientResponseException exception) {
      return null;
    } catch (Exception exception) {
      return exception.getClass().getSimpleName();
    }
  }

  private ModuleCheckItemResponse toItem(ModuleCheckRecord record) {
    ModuleCheckItemResponse item = new ModuleCheckItemResponse();
    item.setBatchId(record.getBatchId());
    item.setModuleCode(record.getModuleCode());
    item.setModuleName(record.getModuleName());
    item.setStatus(record.getStatus());
    item.setLatencyMs(record.getLatencyMs());
    item.setMessage(record.getMessage());
    item.setCheckedAt(record.getCheckedAt());
    return item;
  }

  private long elapsed(long startedNanos) {
    return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
  }

  private String trimSlash(String value) {
    if (value == null) {
      return "";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private String truncate(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  private record ProbeResult(String code, String name, String status, long latencyMs, String message) {
  }
}
