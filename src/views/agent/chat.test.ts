import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const send = vi.hoisted(() => vi.fn())

vi.mock('@/composables/useChat', () => ({
  useChat: () => ({ send }),
}))

vi.mock('vue-element-plus-x', () => ({
  Conversations: { name: 'Conversations', template: '<div />' },
}))

vi.mock('@/components/agent/ChatCore.vue', () => ({
  default: { name: 'ChatCore', template: '<div class="chat-core-stub" />' },
}))

import ChatPage from './chat.vue'

vi.mock('@/store/chat', () => ({
  useChatStore: () => ({
    conversations: [],
    conversationId: '',
    loadConversations: vi.fn().mockResolvedValue([]),
    newConversation: vi.fn(),
    openConversation: vi.fn(),
  }),
}))

const mountChat = async (query = '') => {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/agent/chat', component: ChatPage },
      { path: '/home', component: { template: '<div />' } },
    ],
  })
  await router.push(query ? `/agent/chat?q=${encodeURIComponent(query)}` : '/agent/chat')
  await router.isReady()
  const wrapper = mount(ChatPage, {
    global: {
      plugins: [pinia, router],
      stubs: {
        ChatCore: true,
        Conversations: true,
        PlusOutlined: true,
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('agent chat question query', () => {
  beforeEach(() => {
    send.mockReset()
  })

  it('sends the question from the route once and clears the query', async () => {
    const { router } = await mountChat('VPN 怎么排查')
    expect(send).toHaveBeenCalledTimes(1)
    expect(send).toHaveBeenCalledWith('VPN 怎么排查')
    expect(router.currentRoute.value.query.q).toBeUndefined()

    await router.push('/agent/chat?q=第二次提问')
    await flushPromises()

    expect(send).toHaveBeenCalledTimes(2)
    expect(send).toHaveBeenLastCalledWith('第二次提问')
    expect(router.currentRoute.value.query.q).toBeUndefined()
  })

  it('does not send when the page is opened without a question', async () => {
    await mountChat()
    expect(send).not.toHaveBeenCalled()
  })
})
