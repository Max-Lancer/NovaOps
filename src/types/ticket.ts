export type TicketStatus = 'pending' | 'processing' | 'review' | 'done'

export type TicketPriority = 'low' | 'medium' | 'high' | 'urgent'

export type TicketActionType = 'assign' | 'close' | 'reject' | 'transfer' | 'advance' | 'approve'

export interface TicketAttachmentDto {
  id: string
  name: string
  url: string
  size: number
  createdAt: string
}

export interface TicketCommentDto {
  id: string
  authorId: string
  authorName: string
  content: string
  createdAt: string
}

export interface TicketTimelineItemDto {
  id: string
  action: TicketActionType | 'create' | 'update'
  operatorId: string
  operatorName: string
  remark?: string
  fromStatus?: TicketStatus
  toStatus?: TicketStatus
  createdAt: string
}

export interface TicketListItemDto {
  id: string
  title: string
  status: TicketStatus
  priority: TicketPriority
  assigneeId?: string
  assigneeName?: string
  creatorId: string
  creatorName: string
  createdAt: string
  updatedAt: string
  assetIds: string[]
}

export interface TicketDetailDto extends TicketListItemDto {
  description: string
  dueDate?: string
  timeline: TicketTimelineItemDto[]
  comments: TicketCommentDto[]
  attachments: TicketAttachmentDto[]
}

export interface TicketListQueryDto {
  page?: number
  pageSize?: number
  status?: TicketStatus
  priority?: TicketPriority
  keyword?: string
  startDate?: string
  endDate?: string
}

export interface CreateTicketDto {
  title: string
  description: string
  priority: TicketPriority
  assigneeId?: string
  dueDate?: string
  assetIds?: string[]
}

export interface UpdateTicketDto {
  title?: string
  description?: string
  priority?: TicketPriority
  dueDate?: string
  assetIds?: string[]
}

export interface TicketActionDto {
  action: TicketActionType
  assigneeId?: string
  remark?: string
}

export interface CreateCommentDto {
  content: string
}
