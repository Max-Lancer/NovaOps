<script setup lang="ts">
import { onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyTicketsApi } from '@/api/ticket'
import type { TicketListItemDto, TicketPriority, TicketStatus } from '@/types/ticket'

defineOptions({ name: 'MyTickets' })

const router = useRouter()
const loading = ref(false)
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
    const page = await getMyTicketsApi({ page: 1, pageSize: 50 })
    tickets.value = page.list
  } catch {
    tickets.value = []
  } finally {
    loading.value = false
  }
}

const describe = (ticket: TicketListItemDto) =>
  `${statusText[ticket.status]} · ${priorityText[ticket.priority]} · ${ticket.creatorName || '未知创建人'}`

onMounted(() => {
  void load()
})

onActivated(() => {
  void load()
})
</script>

<template>
  <section>
    <a-card title="我的工单" :bordered="false" :loading="loading">
      <p class="hint">这里只显示指派给你的工单。接单通过后，可以从这里打开并提交复核。</p>
      <a-empty v-if="!tickets.length" description="还没有指派给你的工单" />
      <a-list v-else :data-source="tickets">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta :title="item.title" :description="describe(item)" />
            <template #actions>
              <a-button type="link" @click="router.push(`/ops/ticket/detail/${item.id}`)">查看</a-button>
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
