import dayjs from 'dayjs'
import request from '@/utils/request'
import type { DashboardMetricsDto, DashboardMetricsQueryDto } from '@/types/dashboard'

export const getDashboardMetricsApi = (params: DashboardMetricsQueryDto) => {
  return request.get<DashboardMetricsDto>('/ops/dashboard/metrics', {
    params: {
      ...params,
      startDate: params.startDate ? dayjs(params.startDate).startOf('day').toISOString() : params.startDate,
      endDate: params.endDate ? dayjs(params.endDate).endOf('day').toISOString() : params.endDate,
    },
  })
}
