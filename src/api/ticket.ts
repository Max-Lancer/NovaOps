import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type {
  CreateCommentDto,
  CreateTicketDto,
  TicketActionDto,
  TicketAttachmentDto,
  TicketCommentDto,
  TicketDetailDto,
  TicketListItemDto,
  TicketListQueryDto,
  UpdateTicketDto,
} from '@/types/ticket'

export const getTicketListApi = (params: TicketListQueryDto) => {
  return request.get<PageResult<TicketListItemDto>>('/tickets', { params })
}

export const getClaimQueueApi = (params: TicketListQueryDto) => {
  return request.get<PageResult<TicketListItemDto>>('/tickets/claim-queue', { params })
}

export const getMyTicketsApi = (params: TicketListQueryDto) => {
  return request.get<PageResult<TicketListItemDto>>('/tickets/mine', { params })
}

export const getTicketDetailApi = (id: string) => {
  return request.get<TicketDetailDto>(`/tickets/${id}`)
}

export const createTicketApi = (payload: CreateTicketDto) => {
  return request.post<TicketDetailDto, CreateTicketDto>('/tickets', payload)
}

export const updateTicketApi = (id: string, payload: UpdateTicketDto) => {
  return request.put<TicketDetailDto, UpdateTicketDto>(`/tickets/${id}`, payload)
}

export const ticketActionApi = (id: string, payload: TicketActionDto) => {
  return request.post<TicketDetailDto, TicketActionDto>(`/tickets/${id}/actions`, payload)
}

export const getTicketCommentsApi = (id: string) => {
  return request.get<TicketCommentDto[]>(`/tickets/${id}/comments`)
}

export const createTicketCommentApi = (id: string, payload: CreateCommentDto) => {
  return request.post<TicketCommentDto, CreateCommentDto>(`/tickets/${id}/comments`, payload)
}

export const uploadTicketAttachmentApi = (id: string, file: File) => {
  const data = new FormData()
  data.append('file', file, file.name)
  return request.post<TicketAttachmentDto, FormData>(`/tickets/${id}/attachments`, data, { timeout: 60000 })
}

/** 附件实体是文件流而不是 JSON，取回 Blob 后由调用方触发浏览器下载。 */
export const fetchTicketAttachmentApi = (id: string, attachmentId: string) => {
  return request.get<Blob>(`/tickets/${id}/attachments/${attachmentId}/download`, { responseType: 'blob' })
}
