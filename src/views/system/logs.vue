<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import type { TablePaginationConfig, TableProps } from 'ant-design-vue'
import { getSystemLogsApi } from '@/api/system'
import type { AccessLogDto } from '@/types/system'

defineOptions({ name: 'SystemLogs' })

const loading = ref(false)
const logs = ref<AccessLogDto[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const detail = ref<AccessLogDto>()
const filters = reactive({
  keyword: '',
  method: undefined as string | undefined,
  httpStatus: undefined as number | undefined,
})

const columns: TableProps['columns'] = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '方法', dataIndex: 'method', key: 'method', width: 90 },
  { title: '路径', dataIndex: 'path', key: 'path' },
  { title: 'HTTP', dataIndex: 'httpStatus', key: 'httpStatus', width: 80 },
  { title: '结果码', dataIndex: 'resultCode', key: 'resultCode', width: 90 },
  { title: '耗时', dataIndex: 'durationMs', key: 'durationMs', width: 90 },
  { title: '用户', dataIndex: 'username', key: 'username', width: 120 },
  { title: '说明', dataIndex: 'resultMessage', key: 'resultMessage' },
]

const fetchLogs = async () => {
  loading.value = true
  try {
    const data = await getSystemLogsApi({
      page: page.value,
      pageSize: pageSize.value,
      keyword: filters.keyword || undefined,
      method: filters.method,
      httpStatus: filters.httpStatus,
    })
    logs.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  void fetchLogs()
}

const changePage = (pagination: TablePaginationConfig) => {
  page.value = pagination.current || 1
  pageSize.value = pagination.pageSize || 10
  void fetchLogs()
}

onMounted(() => {
  void fetchLogs()
})
</script>

<template>
  <a-card title="系统运行日志" :bordered="false">
    <a-form layout="inline" class="filters">
      <a-form-item>
        <a-input v-model:value="filters.keyword" allow-clear placeholder="路径、用户、请求号或结果" @press-enter="search" />
      </a-form-item>
      <a-form-item label="方法">
        <a-select v-model:value="filters.method" allow-clear style="width: 120px" :options="['GET', 'POST', 'PUT', 'DELETE'].map((item) => ({ label: item, value: item }))" />
      </a-form-item>
      <a-form-item label="HTTP">
        <a-select v-model:value="filters.httpStatus" allow-clear style="width: 110px" :options="[200, 400, 401, 403, 404, 500].map((item) => ({ label: String(item), value: item }))" />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="search">查询</a-button>
      </a-form-item>
    </a-form>
    <a-table
      :columns="columns"
      :data-source="logs"
      :loading="loading"
      row-key="id"
      :pagination="{ current: page, pageSize, total, showSizeChanger: true }"
      :custom-row="(record: AccessLogDto) => ({ onClick: () => { detail = record } })"
      @change="changePage"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'createdAt'">{{ dayjs(record.createdAt).format('YYYY-MM-DD HH:mm:ss') }}</template>
        <template v-else-if="column.key === 'durationMs'">{{ record.durationMs }} ms</template>
        <template v-else-if="column.key === 'username'">{{ record.username || '-' }}</template>
        <template v-else-if="column.key === 'resultMessage'">{{ record.resultMessage || '-' }}</template>
      </template>
    </a-table>
    <a-drawer :open="Boolean(detail)" title="请求详情" width="460" @close="detail = undefined">
      <a-descriptions v-if="detail" :column="1" bordered size="small">
        <a-descriptions-item label="请求号">{{ detail.requestId }}</a-descriptions-item>
        <a-descriptions-item label="时间">{{ dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm:ss') }}</a-descriptions-item>
        <a-descriptions-item label="请求">{{ detail.method }} {{ detail.path }}</a-descriptions-item>
        <a-descriptions-item label="HTTP 状态">{{ detail.httpStatus }}</a-descriptions-item>
        <a-descriptions-item label="业务结果">{{ detail.resultCode ?? '-' }} {{ detail.resultMessage || '' }}</a-descriptions-item>
        <a-descriptions-item label="异常类型">{{ detail.exceptionType || '-' }}</a-descriptions-item>
        <a-descriptions-item label="耗时">{{ detail.durationMs }} ms</a-descriptions-item>
        <a-descriptions-item label="用户">{{ detail.username || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-drawer>
  </a-card>
</template>

<style scoped>
.filters { margin-bottom: 16px; }
</style>
