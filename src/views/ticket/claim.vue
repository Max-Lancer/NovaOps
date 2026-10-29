<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getClaimQueueApi, ticketActionApi } from '@/api/ticket'
import type { TicketListItemDto, TicketPriority, TicketStatus } from '@/types/ticket'

defineOptions({ name: 'ClaimQueue' })

const loading = ref(false)
const actingId = ref('')
const tickets = ref<TicketListItemDto[]>([])

const statusText: Record<TicketStatus, string> = {
  pending: '待处理',
  claiming: '接单待审',
  processing: '处理中',
  review: '待复核',
  done: '已完成',
}
const priorityText: Record<TicketPriority, string> = {
  low: '低',
  medium: '中',
  high: '高',
  urgent: '紧急',
}

const load = async () => {
  loading.value = true
  try {
    const page = await getClaimQueueApi({ page: 1, pageSize: 50 })
    tickets.value = page.list
  } finally {
    loading.value = false
  }
}

const describe = (ticket: TicketListItemDto) => `${statusText[ticket.status]} · ${priorityText[ticket.priority]}`

const claim = async (ticket: TicketListItemDto) => {
  actingId.value = ticket.id
  try {
    await ticketActionApi(ticket.id, { action: 'claim', remark: '申请接单' })
    message.success('已提交接单申请')
    await load()
  } finally {
    actingId.value = ''
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section>
    <a-card title="待接工单" :bordered="false" :loading="loading">
      <p class="hint">对待处理工单发起接单申请。审批通过后，你才会成为负责人。</p>
      <a-empty v-if="!tickets.length" description="没有可申请的工单" />
      <a-list v-else :data-source="tickets">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta :title="item.title" :description="describe(item)" />
            <template #actions>
              <a-button
                v-if="item.status === 'pending'"
                type="link"
                :loading="actingId === item.id"
                @click="claim(item)"
              >
                申请接单
              </a-button>
              <span v-else>等待审批</span>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-card>
  </section>
</template>

<style scoped>
.hint { margin-top: 0; color: var(--nova-text-secondary); }
</style>
