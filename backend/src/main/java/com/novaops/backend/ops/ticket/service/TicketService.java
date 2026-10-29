package com.novaops.backend.ops.ticket.service;

import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.util.DateTimeUtils;
import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.ops.ticket.dto.AttachmentDownload;
import com.novaops.backend.ops.ticket.dto.CreateCommentRequest;
import com.novaops.backend.ops.ticket.dto.CreateTicketRequest;
import com.novaops.backend.ops.ticket.dto.TicketActionRequest;
import com.novaops.backend.ops.ticket.dto.TicketAttachmentResponse;
import com.novaops.backend.ops.ticket.dto.TicketCommentResponse;
import com.novaops.backend.ops.ticket.dto.TicketDetailResponse;
import com.novaops.backend.ops.ticket.dto.TicketListItemResponse;
import com.novaops.backend.ops.ticket.dto.TicketListQuery;
import com.novaops.backend.ops.ticket.dto.TicketTimelineItemResponse;
import com.novaops.backend.ops.ticket.dto.UpdateTicketRequest;
import com.novaops.backend.ops.ticket.mapper.TicketMapper;
import com.novaops.backend.ops.ticket.model.TicketAssetRelationRecord;
import com.novaops.backend.ops.ticket.model.TicketAttachmentRecord;
import com.novaops.backend.ops.ticket.model.TicketCommentRecord;
import com.novaops.backend.ops.ticket.model.TicketRecord;
import com.novaops.backend.ops.ticket.model.TicketTimelineRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TicketService {

  private final TicketMapper ticketMapper;
  private final AuthService authService;
  private final TicketAttachmentStorage attachmentStorage;

  public TicketService(TicketMapper ticketMapper, AuthService authService, TicketAttachmentStorage attachmentStorage) {
    this.ticketMapper = ticketMapper;
    this.authService = authService;
    this.attachmentStorage = attachmentStorage;
  }

  public PageResult<TicketListItemResponse> list(CurrentSession session, TicketListQuery query) {
    long page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    long offset = (page - 1) * pageSize;

    LocalDateTime startDate = DateTimeUtils.parseIsoDateTime(query.getStartDate());
    LocalDateTime endDate = DateTimeUtils.parseIsoDateTime(query.getEndDate());

    long total = ticketMapper.countTickets(
        query.getStatus(),
        query.getPriority(),
        query.getKeyword(),
        startDate,
        endDate
    );

    List<TicketRecord> records = ticketMapper.queryTickets(
        query.getStatus(),
        query.getPriority(),
        query.getKeyword(),
        startDate,
        endDate,
        offset,
        pageSize
    );

    Map<String, List<String>> assetMap = buildAssetMap(records.stream().map(TicketRecord::getId).toList());
    List<TicketListItemResponse> list = records.stream()
        .map(record -> toListItem(record, assetMap.getOrDefault(record.getId(), Collections.emptyList())))
        .toList();

    return new PageResult<>(list, page, pageSize, total);
  }

  public PageResult<TicketListItemResponse> listClaimQueue(CurrentSession session, TicketListQuery query) {
    authService.requirePermission(session, "ticket:claim");
    long page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    long offset = (page - 1) * pageSize;
    long total = ticketMapper.countClaimQueue(session.getUserId());
    List<TicketRecord> records = ticketMapper.queryClaimQueue(session.getUserId(), offset, pageSize);
    Map<String, List<String>> assetMap = buildAssetMap(records.stream().map(TicketRecord::getId).toList());
    List<TicketListItemResponse> list = records.stream()
        .map(record -> toListItem(record, assetMap.getOrDefault(record.getId(), Collections.emptyList())))
        .toList();
    return new PageResult<>(list, page, pageSize, total);
  }

  /** 当前用户负责的工单。不要求 ticket:view，避免把全部工单开放给员工。 */
  public PageResult<TicketListItemResponse> listMine(CurrentSession session, TicketListQuery query) {
    long page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    long offset = (page - 1) * pageSize;
    long total = ticketMapper.countMine(session.getUserId());
    List<TicketRecord> records = ticketMapper.queryMine(session.getUserId(), offset, pageSize);
    Map<String, List<String>> assetMap = buildAssetMap(records.stream().map(TicketRecord::getId).toList());
    List<TicketListItemResponse> list = records.stream()
        .map(record -> toListItem(record, assetMap.getOrDefault(record.getId(), Collections.emptyList())))
        .toList();
    return new PageResult<>(list, page, pageSize, total);
  }

  public TicketDetailResponse detail(CurrentSession session, String ticketId) {
    TicketRecord record = requireTicket(ticketId);
    requireAssigneeOrPermission(session, record, "ticket:view");
    return buildDetail(record);
  }

  @Transactional
  public TicketDetailResponse create(CurrentSession session, CreateTicketRequest request) {
    TicketRecord record = new TicketRecord();
    LocalDateTime now = LocalDateTime.now();

    record.setId(IdGenerator.ticketId());
    record.setTitle(request.getTitle());
    record.setDescription(request.getDescription());
    record.setStatus("pending");
    record.setPriority(StringUtils.hasText(request.getPriority()) ? request.getPriority() : "medium");
    if (StringUtils.hasText(request.getAssigneeId())) {
      record.setAssigneeId(authService.requireEnabledUser(request.getAssigneeId()).getId());
    }
    record.setCreatorId(session.getUserId());
    record.setDueDate(DateTimeUtils.parseIsoDateTime(request.getDueDate()));
    record.setCreatedAt(now);
    record.setUpdatedAt(now);

    ticketMapper.insertTicket(record);
    replaceAssetRelations(record.getId(), request.getAssetIds());
    ticketMapper.insertTimeline(buildTimeline(record.getId(), "create", session.getUserId(), "新建工单", null, "pending", now));

    return buildDetail(ticketMapper.findTicket(record.getId()));
  }

  @Transactional
  public TicketDetailResponse update(CurrentSession session, String ticketId, UpdateTicketRequest request) {
    TicketRecord record = requireTicket(ticketId);

    if (StringUtils.hasText(request.getTitle())) {
      record.setTitle(request.getTitle());
    }
    if (StringUtils.hasText(request.getDescription())) {
      record.setDescription(request.getDescription());
    }
    if (StringUtils.hasText(request.getPriority())) {
      record.setPriority(request.getPriority());
    }
    if (StringUtils.hasText(request.getDueDate())) {
      record.setDueDate(DateTimeUtils.parseIsoDateTime(request.getDueDate()));
    }

    record.setUpdatedAt(LocalDateTime.now());
    ticketMapper.updateTicket(record);
    if (request.getAssetIds() != null) {
      replaceAssetRelations(record.getId(), request.getAssetIds());
    }
    ticketMapper.insertTimeline(buildTimeline(record.getId(), "update", session.getUserId(), "更新工单信息", null, null, record.getUpdatedAt()));

    return buildDetail(ticketMapper.findTicket(record.getId()));
  }

  @Transactional
  public TicketDetailResponse action(CurrentSession session, String ticketId, TicketActionRequest request) {
    // 流转动作按动作细分权限，接口注解无法表达这种动态映射
    String action = request.getAction();
    String requiredPermission = switch (action) {
      case "assign" -> "ticket:assign";
      case "transfer" -> "ticket:transfer";
      case "close" -> "ticket:close";
      case "advance" -> "ticket:advance";
      case "reject" -> "ticket:reject";
      case "approve" -> "ticket:approve";
      case "claim" -> "ticket:claim";
      case "approve_claim" -> "ticket:claim:approve";
      case "reject_claim" -> "ticket:claim:approve";
      default -> throw new BusinessException(400, "不支持的工单动作");
    };
    TicketRecord record = requireTicket(ticketId);
    // 审批通过后负责人往往只有 ticket:claim，允许其提交自己名下工单的复核
    boolean assigneeMayAdvance = "advance".equals(action) && isAssignee(session, record);
    if (!assigneeMayAdvance) {
      authService.requirePermission(session, requiredPermission);
    }
    String previousStatus = record.getStatus();

    // 状态机前置校验：非法转移直接拒绝（409），防止跨状态倒退/重复关单/越权流转
    validateTransition(action, previousStatus);

    // 指派/转派必须指定目标人员，且目标必须存在并启用
    if (("assign".equals(action) || "transfer".equals(action)) && !StringUtils.hasText(request.getAssigneeId())) {
      throw new BusinessException(400, "请选择指派对象");
    }

    switch (action) {
      case "assign" -> {
        // 初始指派：仅 pending → processing
        record.setAssigneeId(authService.requireEnabledUser(request.getAssigneeId()).getId());
        record.setStatus("processing");
      }
      case "claim" -> {
        record.setClaimantId(session.getUserId());
        record.setStatus("claiming");
      }
      case "approve_claim" -> {
        if (session.getUserId().equals(record.getClaimantId())) {
          throw new BusinessException(403, "申请人不能审批自己的接单申请");
        }
        if (!StringUtils.hasText(record.getClaimantId())) {
          throw new BusinessException(400, "接单申请人不存在");
        }
        record.setAssigneeId(authService.requireEnabledUser(record.getClaimantId()).getId());
        record.setStatus("processing");
      }
      case "reject_claim" -> {
        record.setClaimantId(null);
        record.setStatus("pending");
      }
      case "transfer" -> {
        // 转派换人：processing/review 均可，review 换人后退回 processing 重新处理
        record.setAssigneeId(authService.requireEnabledUser(request.getAssigneeId()).getId());
        if ("review".equals(previousStatus)) {
          record.setStatus("processing");
        }
      }
      case "advance" -> record.setStatus("review");    // processing → review（提交复核）
      case "approve" -> record.setStatus("done");      // review → done（复核通过）
      case "reject" -> record.setStatus("processing"); // review → processing（驳回）
      case "close" -> record.setStatus("done");        // processing/review → done（关闭）
      default -> throw new BusinessException(400, "不支持的工单动作");
    }

    LocalDateTime now = LocalDateTime.now();
    record.setUpdatedAt(now);
    if ("done".equals(record.getStatus())) {
      record.setDoneAt(now);
    }
    if (ticketMapper.transitionTicket(record, previousStatus) == 0) {
      throw new BusinessException(409, "工单状态已变化，请刷新后重试");
    }
    ticketMapper.insertTimeline(buildTimeline(
        record.getId(),
        action,
        session.getUserId(),
        request.getRemark(),
        previousStatus,
        record.getStatus(),
        record.getUpdatedAt()
    ));

    return buildDetail(ticketMapper.findTicket(record.getId()));
  }

  /**
   * 工单状态机转移矩阵：只有命中合法转移才放行，否则抛 409。
   * 状态：pending(待处理) → claiming(接单待审) → processing(处理中) → review(待复核) → done(已完成)。
   * 待处理也可由 assign 直接进入处理中。claiming 期间不能指派，也不能直接关闭。
   */
  private void validateTransition(String action, String currentStatus) {
    switch (action) {
      case "assign" -> {
        if ("claiming".equals(currentStatus)) {
          throw new BusinessException(409, "接单待审的工单需先通过或驳回");
        }
        if (!"pending".equals(currentStatus)) {
          throw new BusinessException(409, "仅待处理的工单可指派");
        }
      }
      case "claim" -> {
        if (!"pending".equals(currentStatus)) {
          throw new BusinessException(409, "仅待处理的工单可申请接单");
        }
      }
      case "approve_claim" -> {
        if (!"claiming".equals(currentStatus)) {
          throw new BusinessException(409, "仅接单待审的工单可通过");
        }
      }
      case "reject_claim" -> {
        if (!"claiming".equals(currentStatus)) {
          throw new BusinessException(409, "仅接单待审的工单可驳回");
        }
      }
      case "transfer" -> {
        if (!"processing".equals(currentStatus) && !"review".equals(currentStatus)) {
          throw new BusinessException(409, "仅处理中或待复核的工单可转派");
        }
      }
      case "advance" -> {
        if (!"processing".equals(currentStatus)) {
          throw new BusinessException(409, "仅处理中的工单可提交复核");
        }
      }
      case "approve" -> {
        if (!"review".equals(currentStatus)) {
          throw new BusinessException(409, "仅待复核的工单可复核通过");
        }
      }
      case "reject" -> {
        if (!"review".equals(currentStatus)) {
          throw new BusinessException(409, "仅待复核的工单可驳回");
        }
      }
      case "close" -> {
        if ("pending".equals(currentStatus)) {
          throw new BusinessException(409, "待处理的工单不可直接关闭，请先指派");
        }
        if ("claiming".equals(currentStatus)) {
          throw new BusinessException(409, "接单待审的工单不可直接关闭");
        }
        if ("done".equals(currentStatus)) {
          throw new BusinessException(409, "工单已关闭，不可重复关闭");
        }
      }
      default -> throw new BusinessException(400, "不支持的工单动作");
    }
  }

  public List<TicketCommentResponse> comments(CurrentSession session, String ticketId) {
    TicketRecord record = requireTicket(ticketId);
    requireAssigneeOrPermission(session, record, "ticket:view");
    return ticketMapper.listComments(ticketId).stream().map(this::toComment).toList();
  }

  @Transactional
  public TicketCommentResponse createComment(CurrentSession session, String ticketId, CreateCommentRequest request) {
    TicketRecord record = requireTicket(ticketId);
    requireAssigneeOrPermission(session, record, "ticket:comment");
    LocalDateTime now = LocalDateTime.now();

    TicketCommentRecord comment = new TicketCommentRecord();
    comment.setId(IdGenerator.randomId("cm"));
    comment.setTicketId(ticketId);
    comment.setAuthorId(session.getUserId());
    comment.setContent(request.getContent().trim());
    comment.setCreatedAt(now);
    ticketMapper.insertComment(comment);
    ticketMapper.touchUpdatedAt(ticketId, now);
    return toComment(comment);
  }

  @Transactional
  public TicketAttachmentResponse uploadAttachment(CurrentSession session, String ticketId, MultipartFile file) {
    TicketRecord record = requireTicket(ticketId);
    requireAssigneeOrPermission(session, record, "ticket:comment");
    LocalDateTime now = LocalDateTime.now();
    String attachmentId = IdGenerator.randomId("att");
    TicketAttachmentStorage.StoredAttachment stored = attachmentStorage.save(ticketId, attachmentId, file);

    TicketAttachmentRecord attachment = new TicketAttachmentRecord();
    attachment.setId(attachmentId);
    attachment.setTicketId(ticketId);
    attachment.setName(stored.fileName());
    attachment.setSize(stored.size());
    attachment.setUrl("/api/tickets/" + ticketId + "/attachments/" + attachmentId + "/download");
    attachment.setCreatedAt(now);

    try {
      ticketMapper.insertAttachment(attachment);
      ticketMapper.touchUpdatedAt(ticketId, now);
    } catch (RuntimeException ex) {
      // 元数据没落库就不留文件实体，避免磁盘上多出永远访问不到的孤儿附件
      attachmentStorage.delete(stored.path());
      throw ex;
    }
    return toAttachment(attachment);
  }

  /** 下载前校验工单与附件归属，并确认文件实体仍在磁盘上。 */
  public AttachmentDownload loadAttachment(CurrentSession session, String ticketId, String attachmentId) {
    TicketRecord record = requireTicket(ticketId);
    requireAssigneeOrPermission(session, record, "ticket:view");
    TicketAttachmentRecord attachment = ticketMapper.findAttachment(ticketId, attachmentId);
    if (attachment == null) {
      throw new BusinessException(404, "附件不存在");
    }
    Path path = attachmentStorage.resolve(ticketId, attachmentId);
    if (!Files.exists(path)) {
      throw new BusinessException(404, "附件文件已丢失");
    }
    return new AttachmentDownload(attachment.getName(), attachment.getSize() == null ? 0L : attachment.getSize(), new FileSystemResource(path));
  }

  private boolean isAssignee(CurrentSession session, TicketRecord record) {
    return session.getUserId() != null && session.getUserId().equals(record.getAssigneeId());
  }

  /** 负责人可以看和处理自己的工单；其他人仍走原权限码。 */
  private void requireAssigneeOrPermission(CurrentSession session, TicketRecord record, String permission) {
    if (isAssignee(session, record)) {
      return;
    }
    authService.requirePermission(session, permission);
  }

  private TicketRecord requireTicket(String ticketId) {
    TicketRecord record = ticketMapper.findTicket(ticketId);
    if (record == null) {
      throw new BusinessException(404, "工单不存在");
    }
    return record;
  }

  private TicketDetailResponse buildDetail(TicketRecord record) {
    TicketDetailResponse response = new TicketDetailResponse();
    response.setId(record.getId());
    response.setTitle(record.getTitle());
    response.setDescription(record.getDescription());
    response.setStatus(record.getStatus());
    response.setPriority(record.getPriority());
    response.setAssigneeId(record.getAssigneeId());
    response.setAssigneeName(record.getAssigneeName());
    response.setClaimantId(record.getClaimantId());
    response.setClaimantName(record.getClaimantName());
    response.setCreatorId(record.getCreatorId());
    response.setCreatorName(record.getCreatorName());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    response.setUpdatedAt(DateTimeUtils.toIsoString(record.getUpdatedAt()));
    response.setDueDate(DateTimeUtils.toIsoString(record.getDueDate()));
    response.setAssetIds(ticketMapper.listAssetIds(record.getId()));
    response.setTimeline(ticketMapper.listTimeline(record.getId()).stream().map(this::toTimeline).toList());
    response.setComments(ticketMapper.listComments(record.getId()).stream().map(this::toComment).toList());
    response.setAttachments(ticketMapper.listAttachments(record.getId()).stream().map(this::toAttachment).toList());
    return response;
  }

  private TicketListItemResponse toListItem(TicketRecord record, List<String> assetIds) {
    TicketListItemResponse response = new TicketListItemResponse();
    response.setId(record.getId());
    response.setTitle(record.getTitle());
    response.setStatus(record.getStatus());
    response.setPriority(record.getPriority());
    response.setAssigneeId(record.getAssigneeId());
    response.setAssigneeName(record.getAssigneeName());
    response.setClaimantId(record.getClaimantId());
    response.setClaimantName(record.getClaimantName());
    response.setCreatorId(record.getCreatorId());
    response.setCreatorName(record.getCreatorName());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    response.setUpdatedAt(DateTimeUtils.toIsoString(record.getUpdatedAt()));
    response.setAssetIds(assetIds);
    return response;
  }

  private TicketTimelineItemResponse toTimeline(TicketTimelineRecord record) {
    TicketTimelineItemResponse response = new TicketTimelineItemResponse();
    response.setId(record.getId());
    response.setAction(record.getAction());
    response.setOperatorId(record.getOperatorId());
    response.setOperatorName(record.getOperatorName());
    response.setRemark(record.getRemark());
    response.setFromStatus(record.getFromStatus());
    response.setToStatus(record.getToStatus());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    return response;
  }

  private TicketCommentResponse toComment(TicketCommentRecord record) {
    TicketCommentResponse response = new TicketCommentResponse();
    response.setId(record.getId());
    response.setAuthorId(record.getAuthorId());
    response.setAuthorName(record.getAuthorName());
    response.setContent(record.getContent());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    return response;
  }

  private TicketAttachmentResponse toAttachment(TicketAttachmentRecord record) {
    TicketAttachmentResponse response = new TicketAttachmentResponse();
    response.setId(record.getId());
    response.setName(record.getName());
    response.setUrl(record.getUrl());
    response.setSize(record.getSize());
    response.setCreatedAt(DateTimeUtils.toIsoString(record.getCreatedAt()));
    return response;
  }

  private void replaceAssetRelations(String ticketId, List<String> assetIds) {
    ticketMapper.deleteAssetRelations(ticketId);
    if (assetIds == null || assetIds.isEmpty()) {
      return;
    }
    assetIds.stream()
        .filter(StringUtils::hasText)
        .map(String::trim)
        .distinct()
        .forEach(assetId -> ticketMapper.insertAssetRelation(ticketId, assetId));
  }

  private TicketTimelineRecord buildTimeline(
      String ticketId,
      String action,
      String operatorId,
      String remark,
      String fromStatus,
      String toStatus,
      LocalDateTime createdAt
  ) {
    TicketTimelineRecord record = new TicketTimelineRecord();
    record.setId(IdGenerator.randomId("tl"));
    record.setTicketId(ticketId);
    record.setAction(action);
    record.setOperatorId(operatorId);
    record.setRemark(remark);
    record.setFromStatus(fromStatus);
    record.setToStatus(toStatus);
    record.setCreatedAt(createdAt);
    return record;
  }

  private Map<String, List<String>> buildAssetMap(List<String> ticketIds) {
    if (ticketIds.isEmpty()) {
      return Collections.emptyMap();
    }

    List<TicketAssetRelationRecord> relations = ticketMapper.listAssetRelationsByTicketIds(ticketIds);
    Map<String, List<String>> assetMap = new LinkedHashMap<>();
    for (TicketAssetRelationRecord relation : relations) {
      assetMap.computeIfAbsent(relation.getTicketId(), key -> new ArrayList<>()).add(relation.getAssetId());
    }
    return assetMap;
  }
}
