import request from '@/utils/request'
import type { DashboardMetricsDto, DashboardMetricsQueryDto } from '@/types/dashboard'

export const getDashboardMetricsApi = (params: DashboardMetricsQueryDto) => {
  return request.get<DashboardMetricsDto>('/ops/dashboard/metrics', { params })
}
