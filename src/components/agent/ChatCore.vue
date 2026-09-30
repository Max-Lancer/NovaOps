<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue'
import { RobotOutlined, UserOutlined } from '@ant-design/icons-vue'
import { BubbleList, Welcome, XSender } from 'vue-element-plus-x'
import type { BubbleListItemProps } from 'vue-element-plus-x/types/BubbleList'
import type { AgentPlanStepDto, CitationDto, QueryRoute, ValidationStatus } from '@/types/agent'
import { useChat } from '@/composables/useChat'

type ChatBubble = BubbleListItemProps & {
  key: string
  role: 'user' | 'assistant'
  content: string
  citations?: CitationDto[]
  evidence?: CitationDto[]
  route?: QueryRoute
  routeReason?: string
  retrievalExecuted?: boolean
  retrievedCount?: number
  validatedCount?: number
  validationStatus?: ValidationStatus
  validationReason?: string
  validationPassed?: boolean
  steps?: AgentPlanStepDto[]
  reasoningExpanded?: boolean
}

const { store, send, stop } = useChat()
const senderRef = ref<InstanceType<typeof XSender>>()
const Preview = defineAsyncComponent(() => import('@/components/markdown/MdPreviewAsync.vue'))

const routeLabel: Record<QueryRoute, string> = {
  METADATA: '元数据',
  RAG: '知识检索',
  CHAT: '通用对话',
  CLARIFY: '需要澄清',
  REJECT: '已拒绝',
}

const routeColor: Record<QueryRoute, string> = {
  METADATA: 'purple',
  RAG: 'blue',
  CHAT: 'cyan',
  CLARIFY: 'orange',
  REJECT: 'red',
}

const validationLabel: Partial<Record<ValidationStatus, string>> = {
  PASSED: '依据校验通过',
  NO_EVIDENCE: '无可靠证据',
  FAILED: '依据校验失败',
  SERVICE_UNAVAILABLE: '服务暂不可用',
}

const bubbleItems = computed<ChatBubble[]>(() =>
  store.messages.map((item, index) => ({
    ...item,
    key: item.id,
    placement: item.role === 'user' ? 'end' : 'start',
    shape: 'corner',
    variant: 'filled',
    maxWidth: item.role === 'user' ? '72%' : '86%',
    avatarSize: '34px',
    avatarGap: '12px',
    loading: item.role === 'assistant' && store.loading && index === store.messages.length - 1 && !item.content,
  })),
)

const submit = async () => {
  const value = senderRef.value?.getModelValue().text.trim() || ''
  if (!value || store.loading) return
  senderRef.value?.clear()
  await send(value)
  senderRef.value?.focus('end')
}

const askSuggestion = async (question: string) => {
  if (store.loading) return
  await send(question)
}

const stepDescription = (step: AgentPlanStepDto) => {
  const payload = step.payload || {}
  const query = step.action === 'search_kb' && step.query ? `检索词：${step.query}` : ''
  let result = ''
  if (step.action === 'search_kb' && typeof payload.count === 'number') result = `检索到 ${payload.count} 条资料`
  if (step.action === 'answer' && typeof payload.characterCount === 'number') result = `已生成 ${payload.characterCount} 字`
  if (step.action === 'validate' && typeof payload.passed === 'boolean') {
    result = payload.passed ? '引用校验通过' : '引用校验未通过'
  }
  if (step.status === 'failed' && typeof payload.message === 'string') result = payload.message
  return [step.reason, query, result].filter(Boolean).join(' · ')
}

const stepItems = (steps: AgentPlanStepDto[]) => steps.map((step) => ({
  key: step.action,
  title: step.label,
  description: stepDescription(step),
  status: step.status === 'running' ? 'process' : step.status === 'done' ? 'finish' : step.status === 'failed' ? 'error' : 'wait',
}))

const progressLabel = (steps: AgentPlanStepDto[]) => {
  const completed = steps.filter((step) => step.status === 'done').length
  return `执行过程 · ${completed}/${steps.length}`
}

const toggleReasoning = (key: string, activeKey: string|string[]) => {
  const message = store.messages.find((item) => item.id === key)
  if (message) message.reasoningExpanded = Array.isArray(activeKey) ? activeKey.includes('reasoning') : activeKey === 'reasoning'
}
</script>

<template>
  <section class="chat-core">
    <div class="message-stage">
      <div v-if="!bubbleItems.length" class="welcome-wrap">
        <Welcome
          variant="borderless"
          title="你好，我是 Nova AI"
          description="检索企业知识库，并为企业问题提供可追溯依据。"
        />
        <div class="suggestions" aria-label="推荐问题">
          <button type="button" @click="askSuggestion('如何使用当前知识库？')">如何使用当前知识库？</button>
          <button type="button" @click="askSuggestion('如何处理服务器告警？')">如何处理服务器告警？</button>
          <button type="button" @click="askSuggestion('总结当前知识库的主要内容')">知识库有哪些内容？</button>
        </div>
      </div>

      <BubbleList
        v-else
        class="bubble-list"
        :list="bubbleItems"
        max-height="100%"
        show-back-button
        btn-color="var(--nova-primary)"
      >
        <template #avatar="{ item }">
          <span class="message-avatar" :class="item.role">
            <UserOutlined v-if="item.role === 'user'" />
            <RobotOutlined v-else />
          </span>
        </template>
        <template #content="{ item }">
          <div v-if="item.role === 'user'" class="user-message">{{ item.content }}</div>
          <div v-else class="assistant-message">
            <a-collapse
              v-if="item.steps?.length"
              class="reasoning-panel"
              ghost
              :active-key="item.reasoningExpanded ? ['reasoning'] : []"
              @change="toggleReasoning(String(item.key), $event)"
            >
              <a-collapse-panel key="reasoning">
                <template #header>
                  <span class="reasoning-title">{{ progressLabel(item.steps) }}</span>
                </template>
                <a-steps direction="vertical" size="small" :items="stepItems(item.steps)" />
              </a-collapse-panel>
            </a-collapse>
            <Preview v-if="item.content" :editor-id="String(item.key)" :model-value="item.content" />
          </div>
        </template>
        <template #footer="{ item }">
          <div v-if="item.role === 'assistant'" class="answer-footer">
            <div v-if="item.route" class="execution-summary">
              <a-tag :color="routeColor[item.route]" :title="item.routeReason">
                {{ routeLabel[item.route] }}
              </a-tag>
              <a-tag v-if="item.retrievalExecuted">
                检索 {{ item.retrievedCount || 0 }} 条 · 有效 {{ item.validatedCount || 0 }} 条
              </a-tag>
              <a-tag
                v-if="item.validationStatus && validationLabel[item.validationStatus]"
                :color="item.validationStatus === 'PASSED' ? 'success' : item.validationStatus === 'NO_EVIDENCE' ? 'default' : 'warning'"
                :title="item.validationReason"
              >
                {{ validationLabel[item.validationStatus] }}
              </a-tag>
            </div>
            <div v-if="item.citations?.length" class="citations">
              <span class="footer-label">回答引用</span>
              <a-tag v-for="citation in item.citations" :key="citation.chunkId" color="blue">
                [{{ citation.index }}] {{ citation.documentName }}
              </a-tag>
            </div>
            <a-collapse v-if="item.evidence?.length" class="evidence-list" ghost size="small">
              <a-collapse-panel key="evidence" :header="`查看 ${item.evidence.length} 条原始检索证据`">
                <article v-for="evidence in item.evidence" :key="evidence.chunkId" class="evidence-card">
                  <div class="evidence-heading">
                    <strong>[{{ evidence.index }}] {{ evidence.documentName }}</strong>
                    <span>score {{ evidence.score.toFixed(3) }}</span>
                  </div>
                  <code>{{ evidence.chunkId }}</code>
                  <pre>{{ evidence.content }}</pre>
                </article>
              </a-collapse-panel>
            </a-collapse>
            <a-alert
              v-if="item.validationStatus === 'FAILED' || item.validationPassed === false"
              type="warning"
              show-icon
              message="回答未通过完整依据校验，系统已阻止未验证内容输出"
            />
          </div>
        </template>
      </BubbleList>
    </div>

    <div class="sender-dock">
      <a-alert v-if="store.lastError" type="error" show-icon :message="store.lastError" closable />
      <XSender
        ref="senderRef"
        class="nova-sender"
        placeholder="向 Nova AI 提问，Enter 发送，Shift + Enter 换行"
        submit-type="enter"
        :max-length="4000"
        :loading="store.loading"
        clearable
        @submit="submit"
        @cancel="stop"
      />
      <p class="sender-note">Nova AI 可能会出错，企业结论请结合引用资料核对。</p>
    </div>
  </section>
</template>

<style scoped>
.chat-core{display:grid;grid-template-rows:minmax(0,1fr) auto;height:100%;min-height:0;background:var(--nova-surface)}
.message-stage{min-height:0;overflow:hidden;background:linear-gradient(180deg,color-mix(in srgb,var(--nova-primary) 4%,var(--nova-surface)) 0,var(--nova-surface) 180px)}
.bubble-list{height:100%;padding:24px clamp(16px,4vw,56px)}
.welcome-wrap{display:grid;place-content:center;justify-items:center;height:100%;padding:32px;text-align:center}
.welcome-wrap :deep(.el-welcome){max-width:620px;background:transparent}
.suggestions{display:flex;flex-wrap:wrap;justify-content:center;gap:10px;max-width:700px;margin-top:18px}
.suggestions button{padding:9px 14px;border:1px solid var(--nova-border);border-radius:999px;background:var(--nova-surface-elevated);color:var(--nova-text);cursor:pointer;transition:border-color .2s,transform .2s}
.suggestions button:hover{border-color:var(--nova-primary);transform:translateY(-1px)}
.message-avatar{display:grid;width:34px;height:34px;place-items:center;border:1px solid var(--nova-border);border-radius:10px;background:var(--nova-surface-elevated);color:var(--nova-primary);box-shadow:0 4px 12px rgba(15,23,42,.08)}
.message-avatar.user{border-color:transparent;background:var(--nova-primary);color:#fff}
.user-message{color:inherit;white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word}
.assistant-message{display:grid;gap:10px;min-width:0}
.reasoning-panel{border:1px solid color-mix(in srgb,var(--nova-primary) 18%,var(--nova-border));border-radius:10px;background:color-mix(in srgb,var(--nova-primary) 4%,var(--nova-surface-elevated))}
.reasoning-panel :deep(.ant-collapse-header){min-height:44px;align-items:center!important;padding:8px 12px!important}
.reasoning-panel :deep(.ant-collapse-content-box){padding:0 14px 8px 34px!important}
.reasoning-panel :deep(.ant-steps-item-description){max-width:none!important;color:var(--nova-text-secondary)!important;font-size:12px}
.reasoning-title{color:var(--nova-text-secondary);font-size:13px;font-weight:600;letter-spacing:.01em}
.bubble-list :deep(.elx-bubble--end){--elx-bubble-bg:var(--nova-primary);--elx-bubble-text-color:#fff}
.bubble-list :deep(.elx-bubble--start){--elx-bubble-bg:var(--nova-surface-elevated);--elx-bubble-text-color:var(--nova-text);--elx-bubble-border-color:var(--nova-border)}
.bubble-list :deep(.elx-bubble--start .elx-bubble__content){border:1px solid var(--nova-border);box-shadow:0 4px 14px rgba(15,23,42,.04)}
.bubble-list :deep(.markdown-preview),
.bubble-list :deep(.markdown-preview .md-editor),
.bubble-list :deep(.markdown-preview .md-editor-preview-wrapper),
.bubble-list :deep(.markdown-preview .md-editor-preview){background:transparent!important}
.bubble-list :deep(.markdown-preview .md-editor){--md-bk-color:transparent;--md-color:var(--nova-text);border:0!important;color:var(--nova-text)}
.bubble-list :deep(.markdown-preview .md-editor-preview){padding:0!important;color:var(--nova-text)}
.answer-footer{display:grid;gap:8px;margin-top:8px}
.execution-summary,.citations{display:flex;flex-wrap:wrap;align-items:center;gap:6px}
.footer-label{color:var(--nova-text-secondary);font-size:12px}
.evidence-list{border-top:1px solid var(--nova-border)}
.evidence-list :deep(.ant-collapse-header){padding:8px 0!important;color:var(--nova-text-secondary)!important;font-size:13px}
.evidence-list :deep(.ant-collapse-content-box){display:grid;gap:10px;padding:0!important}
.evidence-card{display:grid;gap:6px;padding:10px;border:1px solid var(--nova-border);border-radius:8px;background:color-mix(in srgb,var(--nova-surface) 70%,transparent)}
.evidence-heading{display:flex;justify-content:space-between;gap:12px;color:var(--nova-text);font-size:12px}
.evidence-heading span,.evidence-card code{color:var(--nova-text-secondary);font-size:11px}
.evidence-card pre{max-height:220px;margin:0;padding:10px;overflow:auto;border-radius:6px;background:var(--nova-surface);color:var(--nova-text);font:12px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
.sender-dock{display:grid;gap:8px;padding:14px clamp(16px,4vw,56px) 12px;border-top:1px solid var(--nova-border);background:color-mix(in srgb,var(--nova-surface) 94%,transparent);backdrop-filter:blur(12px)}
.nova-sender{width:100%}
.nova-sender :deep(.el-sender-wrap){border-color:var(--nova-border);background:var(--nova-surface-elevated);box-shadow:0 10px 30px rgba(15,23,42,.08)}
.sender-note{margin:0;color:var(--nova-text-secondary);font-size:12px;text-align:center}
@media(max-width:760px){.bubble-list{padding:16px 10px}.sender-dock{padding:10px}.welcome-wrap{padding:20px}.suggestions{display:grid;width:100%}.suggestions button{text-align:left}}
</style>
