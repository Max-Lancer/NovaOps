<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { createTicketApi } from '@/api/ticket'
import { listTasksApi, type AgentTaskDto } from '@/api/agentTask'
import { useAuthStore } from '@/store/auth'
import type { TicketPriority } from '@/types/ticket'

defineOptions({ name: 'Home' })

const router = useRouter()
const authStore = useAuthStore()
const question = ref('')
const confirmations = ref<AgentTaskDto[]>([])
const loadingConfirmations = ref(false)
const reportOpen = ref(false)
const submitting = ref(false)
const reportForm = reactive({
  title: '',
  description: '',
  priority: 'medium' as TicketPriority,
})

const canAsk = computed(() => authStore.permissions.includes('agent:chat'))
const canConfirm = computed(() => authStore.permissions.includes('agent:task'))
const canReport = computed(() => authStore.permissions.includes('ticket:create'))
const canClaim = computed(() => authStore.permissions.includes('ticket:claim'))
const canViewTickets = computed(() => authStore.permissions.includes('ticket:view'))
const canDashboard = computed(() => authStore.permissions.includes('dashboard:view'))

const ask = () => {
  const text = question.value.trim()
  if (!text) {
    return
  }
  void router.push({ path: '/agent/chat', query: { q: text } })
}

const loadConfirmations = async () => {
  if (!canConfirm.value) {
    confirmations.value = []
    return
  }
  loadingConfirmations.value = true
  try {
    const tasks = await listTasksApi()
    confirmations.value = tasks.filter((task) => task.status === 'AWAITING_CONFIRM')
  } catch {
    confirmations.value = []
  } finally {
    loadingConfirmations.value = false
  }
}

const submitReport = async () => {
  if (!reportForm.title.trim() || !reportForm.description.trim()) {
    message.warning('请填写标题和描述')
    return
  }
  submitting.value = true
  try {
    const ticket = await createTicketApi({
      title: reportForm.title.trim(),
      description: reportForm.description.trim(),
      priority: reportForm.priority,
    })
    message.success('故障已提交')
    reportOpen.value = false
    reportForm.title = ''
    reportForm.description = ''
    reportForm.priority = 'medium'
    if (authStore.permissions.includes('ticket:view')) {
      void router.push(`/ops/ticket/detail/${ticket.id}`)
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadConfirmations()
})

onActivated(() => {
  void loadConfirmations()
})
</script>

<template>
  <section class="home-page">
    <header class="home-head">
      <h1>工作台</h1>
      <p>依据企业文档提问。要改业务数据时，先确认再写入。运维是当前开通的业务。</p>
    </header>
    <div class="home-grid">
      <a-card title="提问" :bordered="false">
        <a-textarea v-model:value="question" :rows="4" placeholder="例如：VPN 掉线时先检查什么？" :disabled="!canAsk" />
        <a-button class="ask-button" type="primary" :disabled="!canAsk || !question.trim()" @click="ask">去问答</a-button>
      </a-card>
      <a-card title="待我确认" :bordered="false" :loading="loadingConfirmations">
        <p v-if="!canConfirm" class="empty">当前身份不会收到 Agent 写操作确认。</p>
        <p v-else-if="!confirmations.length" class="empty">没有等待确认的操作。</p>
        <ul v-else class="confirm-list">
          <li v-for="task in confirmations" :key="task.id">
            <span>{{ task.goal }}</span>
            <a-button size="small" type="link" @click="router.push('/agent/console')">去确认</a-button>
          </li>
        </ul>
      </a-card>
      <a-card title="已开通业务" :bordered="false">
        <div class="biz-card">
          <strong>运维</strong>
          <p>报故障、申请接单，或查看工单与资产。</p>
          <a-space wrap>
            <a-button v-if="canReport" @click="reportOpen = true">报故障</a-button>
            <a-button v-if="canClaim" @click="router.push('/ops/ticket/claim')">待接工单</a-button>
            <a-button v-if="canClaim && !canViewTickets" @click="router.push('/ops/ticket/mine')">我的工单</a-button>
            <a-button v-if="canDashboard" type="primary" @click="router.push('/ops/dashboard')">运维看板</a-button>
          </a-space>
        </div>
      </a-card>
    </div>
    <a-modal v-model:open="reportOpen" title="报故障" :confirm-loading="submitting" @ok="submitReport">
      <a-form layout="vertical">
        <a-form-item label="标题" required>
          <a-input v-model:value="reportForm.title" />
        </a-form-item>
        <a-form-item label="描述" required>
          <a-textarea v-model:value="reportForm.description" :rows="4" />
        </a-form-item>
        <a-form-item label="优先级">
          <a-select
            v-model:value="reportForm.priority"
            :options="[
              { label: '低', value: 'low' },
              { label: '中', value: 'medium' },
              { label: '高', value: 'high' },
              { label: '紧急', value: 'urgent' },
            ]"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<style scoped>
.home-page { display: grid; gap: 20px; }
.home-head h1 { margin: 0 0 8px; color: var(--nova-text); font-size: 28px; }
.home-head p { margin: 0; color: var(--nova-text-secondary); }
.home-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.ask-button { margin-top: 12px; }
.empty { margin: 0; color: var(--nova-text-secondary); }
.confirm-list { margin: 0; padding: 0; list-style: none; display: grid; gap: 8px; }
.confirm-list li { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
.biz-card { display: grid; gap: 8px; }
.biz-card p { margin: 0; color: var(--nova-text-secondary); }
@media (max-width: 960px) { .home-grid { grid-template-columns: 1fr; } }
</style>
