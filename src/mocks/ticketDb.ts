import dayjs from 'dayjs'
import type { PageResult } from '@/types/api'
import type {
  CreateCommentDto,
  CreateTicketDto,
  TicketActionDto,
  TicketActionType,
  TicketAttachmentDto,
  TicketCommentDto,
  TicketDetailDto,
  TicketListItemDto,
  TicketListQueryDto,
  TicketPriority,
  TicketStatus,
  TicketTimelineItemDto,
  UpdateTicketDto,
} from '@/types/ticket'
import type { RelatedTicketDto } from '@/types/asset'
import { getUser, listMockUsers } from './db'

const statusFlow: TicketStatus[] = ['pending', 'processing', 'review', 'done']
const priorities: TicketPriority[] = ['low', 'medium', 'high', 'urgent']
// 种子工单的负责人候选：真实用户表中的运维人员/管理员
const seedAssigneeIds = ['u-tom', 'u-jerry', 'u-alice', 'u-staff', 'u-admin']

const tickets: TicketDetailDto[] = []

const uuid = (prefix: string) => {
  return `${prefix}_${Math.random().toString(36).slice(2, 8)}_${Date.now()}`
}

const formatTicketId = (index: number) => {
  return `A-TICKET-${String(index).padStart(4, '0')}`
}

const resolveUserId = (userId: string | undefined | null) => {
  if (!userId) {
    return null
  }
  return listMockUsers().find((item) => item.id === userId) || null
}

const resolveUsername = (username: string) => {
  const user = getUser(username)
  if (!user) {
    return { id: username, displayName: username }
  }
  return { id: user.id, displayName: user.displayName }
}

const createTimelineItem = (payload: Partial<TicketTimelineItemDto>): TicketTimelineItemDto => {
  return {
    id: payload.id || uuid('tl'),
    action: payload.action || 'update',
    operatorId: payload.operatorId || 'system',
    operatorName: payload.operatorName || 'system',
    remark: payload.remark,
    fromStatus: payload.fromStatus,
    toStatus: payload.toStatus,
    createdAt: payload.createdAt || dayjs().toISOString(),
  }
}

const seedTickets = () => {
  if (tickets.length > 0) {
    return
  }

  const admin = resolveUserId('u-admin') || { id: 'u-admin', displayName: 'System Admin' }

  for (let index = 0; index < 24; index += 1) {
    const status = statusFlow[index % statusFlow.length] || 'pending'
    const priority = priorities[index % priorities.length] || 'medium'
    const createdAt = dayjs().subtract(index, 'day')
    const updatedAt = createdAt.add((index % 6) + 1, 'hour')
    const ticketId = formatTicketId(index + 1)
    const assignee = resolveUserId(seedAssigneeIds[index % seedAssigneeIds.length] || '')
    const timeline: TicketTimelineItemDto[] = [
      createTimelineItem({
        action: 'create',
        operatorId: admin.id,
        operatorName: admin.displayName,
        toStatus: 'pending',
        remark: '创建工单',
        createdAt: createdAt.toISOString(),
      }),
    ]
    if (status !== 'pending') {
      timeline.push(
        createTimelineItem({
          action: 'assign',
          operatorId: admin.id,
          operatorName: admin.displayName,
          fromStatus: 'pending',
          toStatus: 'processing',
          remark: '初始指派',
          createdAt: createdAt.add(1, 'hour').toISOString(),
        })
      )
    }
    if (status === 'review' || status === 'done') {
      timeline.push(
        createTimelineItem({
          action: 'advance',
          operatorId: assignee?.id,
          operatorName: assignee?.displayName,
          fromStatus: 'processing',
          toStatus: 'review',
          remark: '提交复核',
          createdAt: createdAt.add(2, 'hour').toISOString(),
        })
      )
    }
    if (status === 'done') {
      timeline.push(
        createTimelineItem({
          action: 'approve',
          operatorId: admin.id,
          operatorName: admin.displayName,
          fromStatus: 'review',
          toStatus: 'done',
          remark: '复核通过',
          createdAt: createdAt.add(3, 'hour').toISOString(),
        })
      )
    }
    const tom = resolveUserId('u-tom')
    const alice = resolveUserId('u-alice')
    tickets.push({
      id: ticketId,
      title: `A 网络与终端巡检异常 #${index + 1}`,
      description: `巡检发现异常指标，需排查交换机/终端策略。工单编号 ${ticketId}。`,
      status,
      priority,
      assigneeId: assignee?.id,
      assigneeName: assignee?.displayName,
      creatorId: admin.id,
      creatorName: admin.displayName,
      createdAt: createdAt.toISOString(),
      updatedAt: updatedAt.toISOString(),
      dueDate: createdAt.add(3, 'day').toISOString(),
      assetIds: [`ASSET-${index + 1}`, `ASSET-${index + 101}`],
      timeline,
      comments: [
        {
          id: uuid('cm'),
          authorId: tom?.id || '',
          authorName: tom?.displayName || '',
          content: '收到，正在排查日志。',
          createdAt: createdAt.add(3, 'hour').toISOString(),
        },
        {
          id: uuid('cm'),
          authorId: alice?.id || '',
          authorName: alice?.displayName || '',
          content: '已补充现场截图。',
          createdAt: createdAt.add(6, 'hour').toISOString(),
        },
      ],
      attachments: [],
    })
  }
}

seedTickets()

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

const findTicket = (ticketId: string) => {
  return tickets.find((item) => item.id === ticketId) || null
}

const toListItem = (ticket: TicketDetailDto): TicketListItemDto => {
  return {
    id: ticket.id,
    title: ticket.title,
    status: ticket.status,
    priority: ticket.priority,
    assigneeId: ticket.assigneeId,
    assigneeName: ticket.assigneeName,
    creatorId: ticket.creatorId,
    creatorName: ticket.creatorName,
    createdAt: ticket.createdAt,
    updatedAt: ticket.updatedAt,
    assetIds: ticket.assetIds,
  }
}

export const queryTickets = (
  query: TicketListQueryDto
): PageResult<TicketListItemDto> => {
  const page = Math.max(Number(query.page || 1), 1)
  const pageSize = Math.max(Number(query.pageSize || 10), 1)

  let filtered = [...tickets]

  if (query.status) {
    filtered = filtered.filter((ticket) => ticket.status === query.status)
  }
  if (query.priority) {
    filtered = filtered.filter((ticket) => ticket.priority === query.priority)
  }
  if (query.keyword) {
    const keyword = query.keyword.toLowerCase()
    filtered = filtered.filter((ticket) => {
      return (
        ticket.title.toLowerCase().includes(keyword) ||
        ticket.description.toLowerCase().includes(keyword) ||
        ticket.id.toLowerCase().includes(keyword)
      )
    })
  }
  if (query.startDate && query.endDate) {
    const start = dayjs(query.startDate)
    const end = dayjs(query.endDate)
    filtered = filtered.filter((ticket) => {
      const createdAt = dayjs(ticket.createdAt)
      return (createdAt.isAfter(start) || createdAt.isSame(start)) && (createdAt.isBefore(end) || createdAt.isSame(end))
    })
  }

  const sorted = filtered.sort((a, b) => dayjs(b.updatedAt).valueOf() - dayjs(a.updatedAt).valueOf())
  const total = sorted.length
  const startIndex = (page - 1) * pageSize
  const list = sorted.slice(startIndex, startIndex + pageSize).map(toListItem)

  return {
    list: clone(list),
    page,
    pageSize,
    total,
  }
}

export const getTicketDetail = (ticketId: string) => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  return clone(ticket)
}

export const createTicket = (
  username: string,
  payload: CreateTicketDto
): TicketDetailDto => {
  const now = dayjs().toISOString()
  const nextId = formatTicketId(tickets.length + 1)
  const creator = resolveUsername(username)
  const assignee = resolveUserId(payload.assigneeId || '')
  if (payload.assigneeId && !assignee) {
    throw new Error('指派对象不存在')
  }
  const ticket: TicketDetailDto = {
    id: nextId,
    title: payload.title,
    description: payload.description,
    status: 'pending',
    priority: payload.priority,
    assigneeId: assignee?.id,
    assigneeName: assignee?.displayName,
    creatorId: creator.id,
    creatorName: creator.displayName,
    createdAt: now,
    updatedAt: now,
    dueDate: payload.dueDate,
    assetIds: payload.assetIds || [],
    attachments: [],
    comments: [],
    timeline: [
      createTimelineItem({
        action: 'create',
        operatorId: creator.id,
        operatorName: creator.displayName,
        toStatus: 'pending',
        remark: '新建工单',
        createdAt: now,
      }),
    ],
  }
  tickets.unshift(ticket)
  return clone(ticket)
}

export const updateTicket = (
  ticketId: string,
  username: string,
  payload: UpdateTicketDto
) => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  ticket.title = payload.title || ticket.title
  ticket.description = payload.description || ticket.description
  ticket.priority = payload.priority || ticket.priority
  ticket.dueDate = payload.dueDate || ticket.dueDate
  if (payload.assetIds) {
    ticket.assetIds = payload.assetIds
  }
  ticket.updatedAt = dayjs().toISOString()
  const operator = resolveUsername(username)
  ticket.timeline.unshift(
    createTimelineItem({
      action: 'update',
      operatorId: operator.id,
      operatorName: operator.displayName,
      remark: '更新工单信息',
      createdAt: ticket.updatedAt,
    })
  )
  return clone(ticket)
}

const ILLEGAL_TRANSITION_MSG: Record<string, string> = {
  assign: '仅待处理的工单可指派',
  transfer: '仅处理中或待复核的工单可转派',
  advance: '仅处理中的工单可提交复核',
  approve: '仅待复核的工单可复核通过',
  reject: '仅待复核的工单可驳回',
  close: '待处理的工单不可直接关闭，或工单已关闭',
}

export const actionTicket = (
  ticketId: string,
  username: string,
  payload: TicketActionDto
) => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  const previousStatus = ticket.status
  const now = dayjs().toISOString()

  // 状态机前置校验（与后端 TicketService.validateTransition 保持一致）
  const validate = (action: TicketActionType, status: TicketStatus): boolean => {
    switch (action) {
      case 'assign':
        return status === 'pending'
      case 'transfer':
        return status === 'processing' || status === 'review'
      case 'advance':
        return status === 'processing'
      case 'approve':
        return status === 'review'
      case 'reject':
        return status === 'review'
      case 'close':
        return status === 'processing' || status === 'review'
      default:
        return false
    }
  }
  if (!validate(payload.action, ticket.status)) {
    throw new Error(ILLEGAL_TRANSITION_MSG[payload.action] || '非法状态流转')
  }

  switch (payload.action) {
    case 'assign':
    case 'transfer': {
      if (!payload.assigneeId) {
        throw new Error('请选择指派对象')
      }
      const assignee = resolveUserId(payload.assigneeId)
      if (!assignee) {
        throw new Error('指派对象不存在')
      }
      ticket.assigneeId = assignee.id
      ticket.assigneeName = assignee.displayName
      if (payload.action === 'assign') {
        ticket.status = 'processing' // pending → processing（初始指派）
      } else if (ticket.status === 'review') {
        ticket.status = 'processing' // review 换人后退回重新处理
      }
      break
    }
    case 'advance':
      ticket.status = 'review' // processing → review（提交复核）
      break
    case 'approve':
      ticket.status = 'done' // review → done（复核通过）
      break
    case 'reject':
      ticket.status = 'processing' // review → processing（驳回）
      break
    case 'close':
      ticket.status = 'done' // processing/review → done（关闭）
      break
    default:
      break
  }

  ticket.updatedAt = now
  const operator = resolveUsername(username)
  ticket.timeline.unshift(
    createTimelineItem({
      action: payload.action,
      operatorId: operator.id,
      operatorName: operator.displayName,
      remark: payload.remark || undefined,
      fromStatus: previousStatus,
      toStatus: ticket.status,
      createdAt: now,
    })
  )
  return clone(ticket)
}

export const listTicketComments = (ticketId: string): TicketCommentDto[] | null => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  return clone(ticket.comments.sort((a, b) => dayjs(b.createdAt).valueOf() - dayjs(a.createdAt).valueOf()))
}

export const createTicketComment = (
  ticketId: string,
  username: string,
  payload: CreateCommentDto
) => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  const author = resolveUsername(username)
  const comment: TicketCommentDto = {
    id: uuid('cm'),
    authorId: author.id,
    authorName: author.displayName,
    content: payload.content,
    createdAt: dayjs().toISOString(),
  }
  ticket.comments.unshift(comment)
  ticket.updatedAt = dayjs().toISOString()
  return clone(comment)
}

export const uploadTicketAttachment = (
  ticketId: string,
  name: string,
  size: number
): TicketAttachmentDto | null => {
  const ticket = findTicket(ticketId)
  if (!ticket) {
    return null
  }
  const attachmentId = uuid('att')
  const attachment: TicketAttachmentDto = {
    id: attachmentId,
    name,
    size,
    url: `/api/tickets/${ticketId}/attachments/${attachmentId}/download`,
    createdAt: dayjs().toISOString(),
  }
  ticket.attachments.unshift(attachment)
  ticket.updatedAt = dayjs().toISOString()
  return clone(attachment)
}

export const listRelatedTicketsByAsset = (assetId: string): RelatedTicketDto[] => {
  const related = tickets
    .filter((ticket) => ticket.assetIds.includes(assetId))
    .sort((a, b) => dayjs(b.updatedAt).valueOf() - dayjs(a.updatedAt).valueOf())
    .slice(0, 8)
    .map((ticket) => ({
      id: ticket.id,
      title: ticket.title,
      status: ticket.status,
      priority: ticket.priority,
      assigneeId: ticket.assigneeId,
      assigneeName: ticket.assigneeName,
    }))
  return clone(related)
}
