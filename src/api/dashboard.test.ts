import dayjs from 'dayjs'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/utils/request', () => ({
  default: { get },
}))

import { getDashboardMetricsApi } from './dashboard'

describe('getDashboardMetricsApi', () => {
  beforeEach(() => {
    get.mockReset()
  })

  it('normalizes a manually picked range to the start and end of each day', async () => {
    const start = dayjs('2026-04-20T15:30:00')
    const end = dayjs('2026-04-21T08:05:00')

    await getDashboardMetricsApi({
      startDate: start.toISOString(),
      endDate: end.toISOString(),
    })

    expect(get).toHaveBeenCalledWith('/ops/dashboard/metrics', {
      params: {
        startDate: start.startOf('day').toISOString(),
        endDate: end.endOf('day').toISOString(),
      },
    })
  })
})
