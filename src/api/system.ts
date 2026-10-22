import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { AccessLogDto, AccessLogQueryDto, ModuleHealthDto } from '@/types/system'

export const getSystemHealthApi = () => {
  return request.get<ModuleHealthDto>('/system/health')
}

export const checkSystemHealthApi = () => {
  return request.post<ModuleHealthDto>('/system/health/check')
}

export const getSystemLogsApi = (params: AccessLogQueryDto) => {
  return request.get<PageResult<AccessLogDto>>('/system/logs', { params })
}
