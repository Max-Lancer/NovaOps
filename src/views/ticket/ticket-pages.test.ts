import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setupAntd } from '@/plugins/antd'
import { getUserOptionsApi } from '@/api/auth'
import { getAssetBatchApi } from '@/api/asset'
import {
  getTicketCommentsApi,
  getTicketDetailApi,
  getTicketListApi,
  ticketActionApi,
} from '@/api/ticket'
import type { TicketDetailDto, TicketListItemDto } from '@/types/ticket'
import { useAuthStore } from '@/store/auth'
import { usePermissionStore } from '@/store/permission'
import Permission from '@/components/Permission.vue'
import TicketDetail from './detail.vue'
import TicketList from './list.vue'

vi.mock('@/api/auth', () => ({
  getUserOptionsApi: vi.fn(),
}))

vi.mock('@/api/asset', () => ({
  getAssetBatchApi: vi.fn(),
}))

vi.mock('@/api/ticket', () => ({
  getTicketListApi: vi.fn(),
  getTicketDetailApi: vi.fn(),
  createTicketApi: vi.fn(),
  updateTicketApi: vi.fn(),
  ticketActionApi: vi.fn(),
  getTicketCommentsApi: vi.fn(),
  createTicketCommentApi: vi.fn(),
  uploadTicketAttachmentApi: vi.fn(),
}))

const ticket: TicketDetailDto = {
  id: 'A-TICKET-0001',
  title: '网络故障',
  description: '交换机端口异常',
  status: 'processing',
  priority: 'high',
  assigneeId: 'u-staff',
  assigneeName: 'Support Staff',
  creatorId: 'u-admin',
  creatorName: 'System Admin',
  createdAt: '2026-08-28T08:00:00Z',
  updatedAt: '2026-08-28T09:00:00Z',
  assetIds: [],
  timeline: [
    {
      id: 'tl-1',
      action: 'assign',
      operatorId: 'u-admin',
      operatorName: 'System Admin',
      fromStatus: 'pending',
      toStatus: 'processing',
      createdAt: '2026-08-28T09:00:00Z',
    },
  ],
  comments: [],
  attachments: [],
}

const antdPlugin = { install: setupAntd }
const globalOptions = {
  plugins: [antdPlugin],
  stubs: { Permission: { template: '<div><slot /></div>' } },
}

type StubOverrides = Record<string, { template: string }>

const mountPage = async (
  path: string,
  component: typeof TicketList | typeof TicketDetail,
  stubs: StubOverrides = {},
  realPermission = false,
  prepare?: () => void,
) => {
  const pinia = createPinia()
  setActivePinia(pinia)
  prepare?.()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path, component }],
  })
  const target = path.replace(':id', ticket.id)
  await router.push(target)
  await router.isReady()
  const pageStubs: Record<string, { template: string }> = { ...globalOptions.stubs, ...stubs }
  if (realPermission) {
    delete pageStubs.Permission
  }
  return mount(component, {
    global: {
      ...globalOptions,
      plugins: [antdPlugin, pinia, router],
      stubs: pageStubs,
      components: realPermission ? { Permission } : {},
    },
  })
}

describe('ticket pages person contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getUserOptionsApi).mockResolvedValue([
      { id: 'u-staff', username: 'staff', displayName: 'Support Staff' },
      { id: 'u-jerry', username: 'jerry', displayName: 'Jerry' },
    ])
    vi.mocked(getTicketListApi).mockResolvedValue({
      list: [ticket as TicketListItemDto],
      page: 1,
      pageSize: 10,
      total: 1,
    })
    vi.mocked(getTicketDetailApi).mockResolvedValue(ticket)
    vi.mocked(getTicketCommentsApi).mockResolvedValue([])
    vi.mocked(getAssetBatchApi).mockResolvedValue([])
    vi.mocked(ticketActionApi).mockResolvedValue(ticket)
  })

  it('renders person names and submits assigneeId from the list', async () => {
    const wrapper = await mountPage('/ticket/list', TicketList)
    await flushPromises()

    expect(wrapper.text()).toContain('Support Staff')
    expect(wrapper.text()).toContain('System Admin')

    const vm = wrapper.vm as unknown as {
      openAssignModal: (record: TicketListItemDto) => void
      assignForm: { assigneeId: string; remark: string }
      submitAssign: () => Promise<void>
    }
    vm.openAssignModal({ ...ticket, status: 'pending' })
    vm.assignForm.assigneeId = 'u-jerry'
    await vm.submitAssign()

    expect(ticketActionApi).toHaveBeenCalledWith(ticket.id, {
      action: 'assign',
      assigneeId: 'u-jerry',
      remark: '列表页指派',
    })
  })

  it('renders person names and submits assigneeId for transfer in detail', async () => {
    const wrapper = await mountPage('/ticket/detail/:id', TicketDetail)
    await flushPromises()

    expect(wrapper.text()).toContain('Support Staff')
    expect(wrapper.text()).toContain('System Admin')

    const vm = wrapper.vm as unknown as {
      actionForm: { assigneeId: string; remark: string }
      doAction: (action: 'transfer') => Promise<void>
    }
    vm.actionForm.assigneeId = 'u-jerry'
    vm.actionForm.remark = '轮班转派'
    await vm.doAction('transfer')

    expect(ticketActionApi).toHaveBeenCalledWith(ticket.id, {
      action: 'transfer',
      assigneeId: 'u-jerry',
      remark: '轮班转派',
    })
  })

  it('shows review actions and submits approve instead of advance for a ticket under review', async () => {
    vi.mocked(getTicketDetailApi).mockResolvedValue({ ...ticket, status: 'review' })
    const wrapper = await mountPage('/ticket/detail/:id', TicketDetail)
    await flushPromises()

    const buttonLabels = wrapper
      .findAll('button')
      .map((button) => button.text().replace(/\s/g, ''))
    expect(buttonLabels).toContain('复核通过')
    expect(buttonLabels).toContain('驳回')
    expect(buttonLabels).not.toContain('提交复核')
    expect(buttonLabels).not.toContain('指派')

    const approveButton = wrapper.findAll('button').find((button) => button.text() === '复核通过')
    expect(approveButton).toBeDefined()
    await approveButton!.trigger('click')
    await flushPromises()

    expect(ticketActionApi).toHaveBeenCalledWith(ticket.id, {
      action: 'approve',
      assigneeId: undefined,
      remark: undefined,
    })
  })

  it('shows only the state-machine actions allowed for a processing ticket', async () => {
    const wrapper = await mountPage('/ticket/detail/:id', TicketDetail)
    await flushPromises()

    const buttonLabels = wrapper
      .findAll('button')
      .map((button) => button.text().replace(/\s/g, ''))
    expect(buttonLabels).toContain('提交复核')
    expect(buttonLabels).toContain('转派')
    expect(buttonLabels).toContain('关闭')
    expect(buttonLabels).not.toContain('指派')
    expect(buttonLabels).not.toContain('复核通过')
    expect(buttonLabels).not.toContain('驳回')
  })

  it('does not submit an action that is illegal for the current ticket status', async () => {
    vi.mocked(getTicketDetailApi).mockResolvedValue({ ...ticket, status: 'review' })
    const wrapper = await mountPage('/ticket/detail/:id', TicketDetail)
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      doAction: (action: 'advance') => Promise<void>
    }
    await vm.doAction('advance')

    expect(ticketActionApi).not.toHaveBeenCalled()
  })

  it('lets the assignee submit review and comment without ticket permissions', async () => {
    const wrapper = await mountPage('/ticket/detail/:id', TicketDetail, {}, true, () => {
      const authStore = useAuthStore()
      authStore.user = {
        id: 'u-staff',
        username: 'staff',
        displayName: 'Support Staff',
        roles: ['member'],
        permissions: [],
      }
      usePermissionStore().codes = []
    })
    await flushPromises()

    const buttonLabels = wrapper.findAll('button').map((button) => button.text().replace(/\s/g, ''))
    expect(buttonLabels).toContain('提交复核')
    expect(buttonLabels).not.toContain('关闭')
    expect(buttonLabels).not.toContain('转派')
    expect(wrapper.text()).toContain('发表评论')
  })

  it('shows list actions only for statuses accepted by the backend state machine', async () => {
    const statuses = ['pending', 'processing', 'review', 'done'] as const
    vi.mocked(getTicketListApi).mockResolvedValue({
      list: statuses.map((status, index) => ({
        ...ticket,
        id: `A-TICKET-000${index + 1}`,
        status,
      })),
      page: 1,
      pageSize: 10,
      total: statuses.length,
    })
    // 指派/关闭收在"更多"下拉里，且 dropdown 的 overlay 默认懒渲染；
    // 让 dropdown/menu 在挂载时直接渲染出来，才能按状态断言行内允许的操作
    const wrapper = await mountPage('/ticket/list', TicketList, {
      'a-dropdown': { template: '<div><slot /><slot name="overlay" /></div>' },
      'a-menu': { template: '<ul><slot /></ul>' },
      'a-menu-item': { template: '<li><slot /></li>' },
    })
    await flushPromises()

    const rowActions = (id: string) => {
      const row = wrapper.findAll('tbody tr').find((item) => item.text().includes(id))
      expect(row).toBeDefined()
      return [
        ...row!.findAll('button').map((button) => button.text().replace(/\s/g, '')),
        ...row!.findAll('li').map((item) => item.text().replace(/\s/g, '')),
      ]
    }

    expect(rowActions('A-TICKET-0001')).toEqual(expect.arrayContaining(['指派']))
    expect(rowActions('A-TICKET-0001')).not.toEqual(expect.arrayContaining(['关闭']))
    expect(rowActions('A-TICKET-0002')).not.toEqual(expect.arrayContaining(['指派']))
    expect(rowActions('A-TICKET-0002')).toEqual(expect.arrayContaining(['关闭']))
    expect(rowActions('A-TICKET-0003')).not.toEqual(expect.arrayContaining(['指派']))
    expect(rowActions('A-TICKET-0003')).toEqual(expect.arrayContaining(['关闭']))
    expect(rowActions('A-TICKET-0004')).not.toEqual(expect.arrayContaining(['指派']))
    expect(rowActions('A-TICKET-0004')).not.toEqual(expect.arrayContaining(['关闭']))
  })
})
