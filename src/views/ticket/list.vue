<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import type { CheckboxOptionType, TableProps } from 'ant-design-vue'
import { DownOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import ProTable from '@/components/pro-table/index.vue'
import { getUserOptionsApi } from '@/api/auth'
import {
  createTicketApi,
  getTicketDetailApi,
  getTicketListApi,
  ticketActionApi,
  updateTicketApi,
} from '@/api/ticket'
import type {
  CreateTicketDto,
  TicketListItemDto,
  TicketListQueryDto,
  TicketPriority,
  TicketStatus,
  UpdateTicketDto,
} from '@/types/ticket'
import type { UserOptionDto } from '@/types/auth'

defineOptions({
  name: 'TicketList',
})

const router = useRouter()
const COLUMN_STORAGE_KEY = 'novaops_ticket_columns'
const DEFAULT_VISIBLE_COLUMNS = ['id', 'title', 'status', 'priority', 'assigneeName', 'creatorName', 'updatedAt']

const loading = ref(false)
const tableData = ref<TicketListItemDto[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const searchForm = reactive<{
  status?: TicketStatus
  priority?: TicketPriority
  keyword: string
  dateRange: [Dayjs, Dayjs] | null
}>({
  status: undefined,
  priority: undefined,
  keyword: '',
  dateRange: null,
})

const createModalVisible = ref(false)
const editModalVisible = ref(false)
const assignModalVisible = ref(false)
const modalLoading = ref(false)
const currentRecord = ref<TicketListItemDto | null>(null)
const assigneeUsers = ref<UserOptionDto[]>([])

const createForm = reactive<CreateTicketDto>({
  title: '',
  description: '',
  priority: 'medium',
  assigneeId: '',
  dueDate: '',
  assetIds: [],
})

const editForm = reactive<UpdateTicketDto>({
  title: '',
  description: '',
  priority: 'medium',
  dueDate: '',
  assetIds: [],
})

const assignForm = reactive({
  assigneeId: '',
  remark: '',
})

const statusTextMap: Record<TicketStatus, string> = {
  pending: '待处理',
  claiming: '接单待审',
  processing: '处理中',
  review: '待复核',
  done: '已完成',
}

const priorityTextMap: Record<TicketPriority, string> = {
  low: '低',
  medium: '中',
  high: '高',
  urgent: '紧急',
}

const priorityColorMap: Record<TicketPriority, string> = {
  low: 'green',
  medium: 'blue',
  high: 'orange',
  urgent: 'red',
}

const assigneeOptions = computed(() =>
  assigneeUsers.value.map((user) => ({
    label: user.displayName,
    value: user.id,
  }))
)

const restoreVisibleColumns = () => {
  const cache = localStorage.getItem(COLUMN_STORAGE_KEY)
  if (!cache) {
    return DEFAULT_VISIBLE_COLUMNS
  }
  try {
    const parsed = JSON.parse(cache) as string[]
    if (!Array.isArray(parsed)) {
      return DEFAULT_VISIBLE_COLUMNS
    }
    return parsed.map((key) => {
      if (key === 'assignee') return 'assigneeName'
      if (key === 'creator') return 'creatorName'
      return key
    })
  } catch {
    return DEFAULT_VISIBLE_COLUMNS
  }
}

const visibleColumnKeys = ref<string[]>(restoreVisibleColumns())

watch(
  visibleColumnKeys,
  (value) => {
    localStorage.setItem(COLUMN_STORAGE_KEY, JSON.stringify(value))
  },
  { deep: true }
)

const allColumns = [
  { title: '工单号', dataIndex: 'id', key: 'id', width: 140 },
  { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true, width: 240 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
  { title: '负责人', dataIndex: 'assigneeName', key: 'assigneeName', width: 110 },
  { title: '创建人', dataIndex: 'creatorName', key: 'creatorName', width: 110 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 160 },
  { title: '关联资产', dataIndex: 'assetIds', key: 'assetIds', width: 160 },
  { title: '操作', key: 'actions', fixed: 'right' as const, width: 160 },
] as NonNullable<TableProps['columns']>

const columnOptions = computed<CheckboxOptionType[]>(() =>
  allColumns
    .filter((column) => column.key !== 'actions')
    .map((column) => ({
      label: String(column.title),
      value: String(column.key),
    }))
)

const tableColumns = computed<TableProps['columns']>(() =>
  allColumns.filter((column) => {
    const key = String(column.key || '')
    return key === 'actions' || visibleColumnKeys.value.includes(key)
  })
)

const buildQuery = (): TicketListQueryDto => {
  const params: TicketListQueryDto = {
    page: page.value,
    pageSize: pageSize.value,
    status: searchForm.status,
    priority: searchForm.priority,
    keyword: searchForm.keyword.trim() || undefined,
  }
  if (searchForm.dateRange) {
    params.startDate = searchForm.dateRange[0].startOf('day').toISOString()
    params.endDate = searchForm.dateRange[1].endOf('day').toISOString()
  }
  return params
}

const fetchTickets = async () => {
  loading.value = true
  try {
    const data = await getTicketListApi(buildQuery())
    tableData.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const searchTickets = async () => {
  page.value = 1
  await fetchTickets()
}

const resetFilters = async () => {
  searchForm.status = undefined
  searchForm.priority = undefined
  searchForm.keyword = ''
  searchForm.dateRange = null
  page.value = 1
  await fetchTickets()
}

const goDetail = (id: string) => {
  void router.push(`/ops/ticket/detail/${id}`)
}

const handlePageChange = async (value: number) => {
  page.value = value
  await fetchTickets()
}

const handlePageSizeChange = async (value: number) => {
  pageSize.value = value
  page.value = 1
  await fetchTickets()
}

const exportCsv = () => {
  const headers = ['id', 'title', 'status', 'priority', 'assignee', 'creator', 'updatedAt']
  const rows = tableData.value.map((row) => [
    row.id,
    row.title,
    statusTextMap[row.status],
    priorityTextMap[row.priority],
    row.assigneeName || '',
    row.creatorName,
    dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm:ss'),
  ])
  const csv = [
    headers.join(','),
    ...rows.map((row) => row.map((v) => `"${String(v).replace(/"/g, '""')}"`).join(',')),
  ].join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `tickets-${dayjs().format('YYYYMMDD-HHmmss')}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  message.success('CSV 导出完成')
}

const openCreateModal = () => {
  createForm.title = ''
  createForm.description = ''
  createForm.priority = 'medium'
  createForm.assigneeId = ''
  createForm.dueDate = ''
  createForm.assetIds = []
  createModalVisible.value = true
}

const submitCreate = async () => {
  modalLoading.value = true
  try {
    await createTicketApi({
      ...createForm,
      assetIds: createForm.assetIds || [],
    })
    message.success('工单创建成功')
    createModalVisible.value = false
    await fetchTickets()
  } finally {
    modalLoading.value = false
  }
}

const openEditModal = async (record: TicketListItemDto) => {
  currentRecord.value = record
  modalLoading.value = true
  try {
    const detail = await getTicketDetailApi(record.id)
    editForm.title = detail.title
    editForm.description = detail.description
    editForm.priority = detail.priority
    editForm.assetIds = [...detail.assetIds]
    editForm.dueDate = detail.dueDate || ''
  } finally {
    modalLoading.value = false
  }
  editModalVisible.value = true
}

const submitEdit = async () => {
  if (!currentRecord.value) {
    return
  }
  modalLoading.value = true
  try {
    await updateTicketApi(currentRecord.value.id, {
      ...editForm,
      assetIds: editForm.assetIds || [],
    })
    message.success('工单更新成功')
    editModalVisible.value = false
    await fetchTickets()
  } finally {
    modalLoading.value = false
  }
}

const openAssignModal = (record: TicketListItemDto) => {
  if (record.status !== 'pending') {
    message.warning('仅待处理的工单可指派')
    return
  }
  currentRecord.value = record
  assignForm.assigneeId = record.assigneeId || ''
  assignForm.remark = ''
  assignModalVisible.value = true
}

const canAssignTicket = (record: TicketListItemDto) => record.status === 'pending'

const canCloseTicket = (record: TicketListItemDto) =>
  record.status === 'processing' || record.status === 'review'

const submitAssign = async () => {
  if (!currentRecord.value || !assignForm.assigneeId) {
    message.warning('请选择指派对象')
    return
  }
  if (!canAssignTicket(currentRecord.value)) {
    message.warning('仅待处理的工单可指派')
    return
  }
  modalLoading.value = true
  try {
    await ticketActionApi(currentRecord.value.id, {
      action: 'assign',
      assigneeId: assignForm.assigneeId,
      remark: assignForm.remark || '列表页指派',
    })
    message.success('指派成功')
    assignModalVisible.value = false
    await fetchTickets()
  } finally {
    modalLoading.value = false
  }
}

const closeTicket = async (record: TicketListItemDto) => {
  if (!canCloseTicket(record)) {
    message.warning('仅处理中或待复核的工单可关闭')
    return
  }
  Modal.confirm({
    title: `确认关闭工单 ${record.id}？`,
    content: '关闭后工单状态将变为已完成。',
    onOk: async () => {
      await ticketActionApi(record.id, {
        action: 'close',
        remark: '列表页关闭',
      })
      message.success('工单已关闭')
      await fetchTickets()
    },
  })
}

const handleCreateAssetInput = (event: Event) => {
  const target = event.target as HTMLInputElement
  const rawValue = target.value || ''
  createForm.assetIds = rawValue
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
}

onMounted(() => {
  void Promise.all([
    fetchTickets(),
    getUserOptionsApi().then((users) => {
      assigneeUsers.value = users
    }),
  ])
})
</script>

<template>
  <section class="ticket-list-page">
    <ProTable
      header-title="工单列表"
      :columns="tableColumns"
      :data-source="tableData"
      :loading="loading"
      :page="page"
      :page-size="pageSize"
      :total="total"
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    >
      <template #filters>
        <a-form layout="horizontal" :label-col="{ style: { width: '70px' } }">
          <a-row :gutter="[24, 16]">
            <a-col :xs="24" :sm="12" :md="12" :lg="6">
              <a-form-item label="状态" style="margin-bottom: 0;">
                <a-select
                  v-model:value="searchForm.status"
                  allow-clear
                  placeholder="请选择状态"
                  style="width: 100%;"
                  :options="[
                    { label: '待处理', value: 'pending' },
                    { label: '接单待审', value: 'claiming' },
                    { label: '处理中', value: 'processing' },
                    { label: '待复核', value: 'review' },
                    { label: '已完成', value: 'done' },
                  ]"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="12" :lg="6">
              <a-form-item label="优先级" style="margin-bottom: 0;">
                <a-select
                  v-model:value="searchForm.priority"
                  allow-clear
                  placeholder="请选择优先级"
                  style="width: 100%;"
                  :options="[
                    { label: '低', value: 'low' },
                    { label: '中', value: 'medium' },
                    { label: '高', value: 'high' },
                    { label: '紧急', value: 'urgent' },
                  ]"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="12" :lg="6">
              <a-form-item label="关键字" style="margin-bottom: 0;">
                <a-input
                  v-model:value="searchForm.keyword"
                  placeholder="搜索标题 / 描述关键字"
                  allow-clear
                  style="width: 100%;"
                  @press-enter="searchTickets"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="12" :lg="6">
              <a-form-item label="日期范围" style="margin-bottom: 0;">
                <a-range-picker v-model:value="searchForm.dateRange" style="width: 100%;" />
              </a-form-item>
            </a-col>
          </a-row>

          <div class="filter-actions-bar">
            <a-space>
              <a-button type="primary" @click="searchTickets">查询</a-button>
              <a-button @click="resetFilters">重置</a-button>
            </a-space>
          </div>
        </a-form>
      </template>

      <template #actions>
        <Permission code="ticket:create">
          <a-button type="primary" @click="openCreateModal">新建工单</a-button>
        </Permission>
        <a-button @click="exportCsv">导出 CSV</a-button>
        <a-popover trigger="click" placement="bottomRight">
          <template #content>
            <a-checkbox-group v-model:value="visibleColumnKeys" :options="columnOptions" />
          </template>
          <a-button :icon="h(SettingOutlined)">列配置</a-button>
        </a-popover>
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'">
          <a class="ticket-link" @click="goDetail((record as TicketListItemDto).id)">
            {{ (record as TicketListItemDto).title }}
          </a>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag color="geekblue">
            {{ statusTextMap[(record as TicketListItemDto).status] }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'priority'">
          <a-tag :color="priorityColorMap[(record as TicketListItemDto).priority]">
            {{ priorityTextMap[(record as TicketListItemDto).priority] }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'updatedAt'">
          {{ dayjs((record as TicketListItemDto).updatedAt).format('YYYY-MM-DD HH:mm') }}
        </template>
        <template v-else-if="column.key === 'assetIds'">
          <span>
            {{
              (record as TicketListItemDto).assetIds?.length
                ? (record as TicketListItemDto).assetIds.join(', ')
                : '-'
            }}
          </span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="goDetail((record as TicketListItemDto).id)">
              详情
            </a-button>
            <Permission code="ticket:edit">
              <a-button type="link" size="small" @click="openEditModal(record as TicketListItemDto)">
                编辑
              </a-button>
            </Permission>
            <a-dropdown
              v-if="canAssignTicket(record as TicketListItemDto) || canCloseTicket(record as TicketListItemDto)"
            >
              <a-button type="link" size="small">
                更多 <DownOutlined style="font-size: 10px;" />
              </a-button>
              <template #overlay>
                <a-menu>
                  <Permission code="ticket:assign">
                    <a-menu-item
                      v-if="canAssignTicket(record as TicketListItemDto)"
                      @click="openAssignModal(record as TicketListItemDto)"
                    >
                      指派
                    </a-menu-item>
                  </Permission>
                  <Permission code="ticket:close">
                    <a-menu-item
                      v-if="canCloseTicket(record as TicketListItemDto)"
                      danger
                      @click="closeTicket(record as TicketListItemDto)"
                    >
                      关闭
                    </a-menu-item>
                  </Permission>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </template>
      </template>
    </ProTable>

    <a-modal
      v-model:open="createModalVisible"
      title="新建工单"
      :confirm-loading="modalLoading"
      @ok="submitCreate"
    >
      <a-form layout="vertical">
        <a-form-item label="标题" required>
          <a-input v-model:value="createForm.title" />
        </a-form-item>
        <a-form-item label="描述" required>
          <a-textarea v-model:value="createForm.description" :rows="4" />
        </a-form-item>
        <a-form-item label="优先级">
          <a-select
            v-model:value="createForm.priority"
            :options="[
              { label: '低', value: 'low' },
              { label: '中', value: 'medium' },
              { label: '高', value: 'high' },
              { label: '紧急', value: 'urgent' },
            ]"
          />
        </a-form-item>
        <a-form-item label="负责人">
          <a-select v-model:value="createForm.assigneeId" allow-clear :options="assigneeOptions" />
        </a-form-item>
        <a-form-item label="关联资产 (逗号分隔)">
          <a-input :value="(createForm.assetIds || []).join(',')" @change="handleCreateAssetInput" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="editModalVisible"
      title="编辑工单"
      :confirm-loading="modalLoading"
      @ok="submitEdit"
    >
      <a-form layout="vertical">
        <a-form-item label="标题">
          <a-input v-model:value="editForm.title" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="editForm.description" :rows="4" />
        </a-form-item>
        <a-form-item label="优先级">
          <a-select
            v-model:value="editForm.priority"
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

    <a-modal
      v-model:open="assignModalVisible"
      title="指派工单"
      :confirm-loading="modalLoading"
      @ok="submitAssign"
    >
      <a-form layout="vertical">
        <a-form-item label="指派给" required>
          <a-select v-model:value="assignForm.assigneeId" :options="assigneeOptions" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="assignForm.remark" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<style scoped>
.ticket-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-actions-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #f0f0f0;
}

.ticket-link {
  color: #1677ff;
}
</style>
