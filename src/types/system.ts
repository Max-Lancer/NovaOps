export type ModuleStatus = 'up' | 'degraded' | 'down'

export interface ModuleCheckItemDto {
  batchId: string
  moduleCode: string
  moduleName: string
  status: ModuleStatus
  latencyMs: number
  message: string
  checkedAt: string
}

export interface ModuleHealthDto {
  latest: ModuleCheckItemDto[]
  history: ModuleCheckItemDto[]
}

export interface AccessLogDto {
  id: string
  requestId: string
  method: string
  path: string
  httpStatus: number
  resultCode?: number
  resultMessage?: string
  exceptionType?: string
  durationMs: number
  username?: string
  createdAt: string
}

export interface AccessLogQueryDto {
  page: number
  pageSize: number
  keyword?: string
  method?: string
  httpStatus?: number
  resultCode?: number
  startDate?: string
  endDate?: string
}
