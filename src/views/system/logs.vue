<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { Dayjs } from 'dayjs'
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
  resultCode: undefined as number | undefined,
  dateRange: null as [Dayjs, Dayjs] | null,
})

const resultCodeOptions = [
  { label: '0 成功', value: 0 },
  { label: '400 参数错误', value: 400 },
  { label: '401 未登录', value: 401 },
  { label: '403 无权限', value: 403 },
  { label: '404 不存在', value: 404 },
  { label: '500 服务异常', value: 500 },
]

const resultCodeText = (code?: number) => {
  if (code == null) return '-'
  const matched = resultCodeOptions.find((item) => item.value === code)
  return matched ? matched.label : String(code)
}

const resultCodeColor = (code?: number) => {
  if (code == null) return 'default'
  if (code === 0) return 'success'
  if (code >= 500) return 'error'
  return 'warning'
}

const columns: TableProps['columns'] = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '方法', dataIndex: 'method', key: 'method', width: 90 },
  { title: '路径', dataIndex: 'path', key: 'path' },
  { title: '业务结果码', dataIndex: 'resultCode', key: 'resultCode', width: 140 },
  { title: 'HTTP', dataIndex: 'httpStatus', key: 'httpStatus', width: 80 },
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
      // 0 是成功，不能用 || 丢掉
      resultCode: filters.resultCode,
      startDate: filters.dateRange?.[0]?.startOf('day').toISOString(),
      endDate: filters.dateRange?.[1]?.endOf('day').toISOString(),
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
    <a-alert
      type="info"
      show-icon
      class="hint"
      message="业务成败看结果码。本项目接口多数固定返回 HTTP 200，401/403 等错误写在响应体 code 里，按 HTTP 状态筛选对不上真实失败。"
    />
    <a-form layout="inline" class="filters">
      <a-form-item>
        <a-input v-model:value="filters.keyword" allow-clear placeholder="路径、用户、请求号或结果" @press-enter="search" />
      </a-form-item>
      <a-form-item label="方法">
        <a-select v-model:value="filters.method" allow-clear style="width: 120px" :options="['GET', 'POST', 'PUT', 'DELETE'].map((item) => ({ label: item, value: item }))" />
      </a-form-item>
      <a-form-item label="业务结果码">
        <a-select v-model:value="filters.resultCode" allow-clear style="width: 150px" :options="resultCodeOptions" />
      </a-form-item>
      <a-form-item label="时间">
        <a-range-picker v-model:value="filters.dateRange" />
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
        <template v-else-if="column.key === 'resultCode'">
          <a-tag :color="resultCodeColor(record.resultCode)">{{ resultCodeText(record.resultCode) }}</a-tag>
        </template>
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
        <a-descriptions-item label="业务结果码">
          <a-tag :color="resultCodeColor(detail.resultCode)">{{ resultCodeText(detail.resultCode) }}</a-tag>
          <span v-if="detail.resultMessage"> {{ detail.resultMessage }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="HTTP 状态">
          {{ detail.httpStatus }}
          <span class="http-note">（传输状态，通常为 200）</span>
        </a-descriptions-item>
        <a-descriptions-item label="异常类型">{{ detail.exceptionType || '-' }}</a-descriptions-item>
        <a-descriptions-item label="耗时">{{ detail.durationMs }} ms</a-descriptions-item>
        <a-descriptions-item label="用户">{{ detail.username || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-drawer>
  </a-card>
</template>

<style scoped>
.hint { margin-bottom: 16px; }
.filters { margin-bottom: 16px; }
.http-note { color: var(--nova-text-secondary); }
</style>
