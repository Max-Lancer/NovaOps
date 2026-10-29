import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Router, RouteLocationNormalized } from 'vue-router'

const { auth, permissions } = vi.hoisted(() => ({
  auth: { isAuthenticated: true, user: { id: 'user' }, me: vi.fn(), logout: vi.fn() },
  permissions: {
    isRouteReady: true,
    menus: [] as { component: string; path: string; children?: unknown[] }[],
    codes: [] as string[],
    hasPermission(code: string) { return this.codes.includes(code) },
    generateRoutes: vi.fn(),
    resetDynamicRoutes: vi.fn(),
  },
}))
vi.mock('@/store', () => ({ pinia: {} }))
vi.mock('@/store/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/store/permission', () => ({ usePermissionStore: () => permissions }))

import { setupRouterGuard } from './guard'

describe('default route permission fallback', () => {
  let guard: (to: RouteLocationNormalized) => unknown
  beforeEach(() => {
    permissions.codes = []
    permissions.menus = []
    setupRouterGuard({ beforeEach: (fn: typeof guard) => { guard = fn } } as unknown as Router)
  })
  const home = { path: '/home', fullPath: '/home', meta: {} } as RouteLocationNormalized

  it('allows the home page when chat is authorized', async () => {
    permissions.codes = ['agent:chat']
    expect(await guard(home)).toBe(true)
  })

  it('uses an available menu page when home is unavailable', async () => {
    permissions.codes = ['ticket:view']
    permissions.menus = [{ component: 'TicketListView', path: '/ops/ticket/list' }]
    expect(await guard(home)).toBe('/ops/ticket/list')
  })

  it('shows forbidden when neither menus nor chat are permitted', async () => {
    expect(await guard(home)).toBe('/403')
  })
})
