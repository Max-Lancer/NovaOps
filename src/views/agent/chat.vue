<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PlusOutlined } from '@ant-design/icons-vue'
import { Conversations } from 'vue-element-plus-x'
import type { ConversationItem } from 'vue-element-plus-x/types/Conversations'
import ChatCore from '@/components/agent/ChatCore.vue'
import { useChat } from '@/composables/useChat'
import { useChatStore } from '@/store/chat'
import type { ConversationDto } from '@/types/agent'

defineOptions({ name: 'AgentChat' })

const route = useRoute()
const router = useRouter()
const store = useChatStore()
const { send } = useChat()

const questionFromRoute = () => {
  const value = route.query.q
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return value[0] || ''
  return ''
}

watch(
  questionFromRoute,
  (question) => {
    const text = question.trim()
    if (!text) return
    const nextQuery = { ...route.query }
    delete nextQuery.q
    void router.replace({ path: route.path, query: nextQuery })
    void send(text)
  },
  { immediate: true },
)
const conversationItems = computed<Array<ConversationItem<ConversationDto>>>(() =>
  store.conversations.map((item) => ({ ...item, label: item.title })),
)

const openConversation = (item: ConversationItem<ConversationDto>) => {
  void store.openConversation(item.id)
}

onMounted(() => {
  store.loadConversations().catch(() => {})
})
</script>

<template>
  <section class="agent-workspace">
    <aside class="conversation-rail">
      <div class="rail-head">
        <div><span class="eyebrow">NOVA AI</span><h2>对话记录</h2></div>
        <a-button type="primary" shape="circle" aria-label="新建会话" @click="store.newConversation()"><PlusOutlined /></a-button>
      </div>
      <Conversations
        v-model:active="store.conversationId"
        class="conversation-list"
        :items="conversationItems"
        row-key="id"
        label-key="title"
        :show-built-in-menu="false"
        :items-active-style="{ background: 'color-mix(in srgb, var(--nova-primary) 12%, transparent)', color: 'var(--nova-primary)' }"
        @change="openConversation"
      />
      <div v-if="!conversationItems.length" class="rail-empty">尚无历史会话</div>
    </aside>
    <main class="chat-panel">
      <header class="chat-head">
        <div><strong>NovaOps 企业知识助手</strong><span>回答均基于企业知识库并附来源引用</span></div>
        <span class="online-badge"><i></i> 服务在线</span>
      </header>
      <ChatCore />
    </main>
  </section>
</template>

<style scoped>
.agent-workspace{display:grid;grid-template-columns:280px minmax(0,1fr);height:calc(100vh - 176px);min-height:560px;overflow:hidden;border:1px solid var(--nova-border);border-radius:14px;background:var(--nova-surface);box-shadow:0 10px 30px rgba(15,23,42,.06)}
.conversation-rail{display:grid;grid-template-rows:auto minmax(0,1fr) auto;min-width:0;border-right:1px solid var(--nova-border);background:var(--nova-surface-elevated)}
.rail-head{display:flex;align-items:center;justify-content:space-between;padding:18px;border-bottom:1px solid var(--nova-border)}
.rail-head h2{margin:3px 0 0;color:var(--nova-text);font-size:18px}.eyebrow{color:var(--nova-primary);font-size:11px;font-weight:700;letter-spacing:.18em}
.conversation-list{min-height:0;padding:10px 8px;overflow:auto}.conversation-list :deep(.el-conversations-item){border-radius:9px;color:var(--nova-text)}
.rail-empty{padding:18px;color:var(--nova-text-secondary);font-size:13px;text-align:center}
.chat-panel{display:grid;grid-template-rows:auto minmax(0,1fr);min-width:0;min-height:0}
.chat-head{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 20px;border-bottom:1px solid var(--nova-border);background:var(--nova-surface)}
.chat-head>div{display:grid;gap:3px}.chat-head strong{color:var(--nova-text);font-size:15px}.chat-head span{color:var(--nova-text-secondary);font-size:12px}
.online-badge{display:flex!important;align-items:center;gap:6px;white-space:nowrap}.online-badge i{width:7px;height:7px;border-radius:50%;background:#22c55e;box-shadow:0 0 0 4px rgba(34,197,94,.12)}
@media(max-width:800px){.agent-workspace{grid-template-columns:1fr;height:calc(100vh - 150px)}.conversation-rail{display:none}.chat-head{padding:12px 14px}.chat-head>div span{display:none}}
</style>
