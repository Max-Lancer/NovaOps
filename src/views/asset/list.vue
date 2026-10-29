<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import type { TableProps } from 'ant-design-vue'
import { DownOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import ProTable from '@/components/pro-table/index.vue'
import { getUserOptionsApi } from '@/api/auth'
import { assetActionApi, createAssetApi, getAssetDetailApi, getAssetListApi, updateAssetApi } from '@/api/asset'
import type { UserOptionDto } from '@/types/auth'
import type {
  AssetListItemDto,
  AssetListQueryDto,
  AssetStatus,
  AssetType,
  CreateAssetDto,
  UpdateAssetDto,
} from '@/types/asset'

defineOptions({
  name: 'AssetList',
})

const router = useRouter()
const loading = ref(false)
const modalLoading = ref(false)
const list = ref<AssetListItemDto[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const createModalOpen = ref(false)
const editModalOpen = ref(false)
const claimModalOpen = ref(false)
const current = ref<AssetListItemDto | null>(null)
const userOptionsLoading = ref(false)
const claimUsers = ref<UserOptionDto[]>([])

const filters = reactive<{
  status?: AssetStatus
  type?: AssetType
  keyword: string
}>({
  status: undefined,
  type: undefined,
  keyword: '',
})

const createForm = reactive<CreateAssetDto>({
  name: '',
  type: 'server',
  location: '',
  spec: '',
  remark: '',
})

const editForm = reactive<UpdateAssetDto>({
  name: '',
  type: 'server',
  location: '',
  spec: '',
  remark: '',
})

const claimForm = reactive({
  ownerId: '',
  remark: '',
})

const statusTextMap: Record<AssetStatus, string> = {
  stock: '库存中',
  in_use: '已领用',
  scrapped: '已报废',
}

const statusColorMap: Record<AssetStatus, string> = {
  stock: 'green',
  in_use: 'blue',
  scrapped: 'default',
}

const typeTextMap: Record<AssetType, string> = {
  server: '服务器',
  laptop: '笔记本',
  network: '网络设备',
  license: '软件授权',
}

const columns: TableProps['columns'] = [
  { title: '资产编号', dataIndex: 'assetNo', key: 'assetNo', width: 140 },
  { title: '资产名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
  { title: '类型', dataIndex: 'type', key: 'type', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '领用人', dataIndex: 'ownerName', key: 'ownerName', width: 120 },
  { title: '位置', dataIndex: 'location', key: 'location', width: 160, ellipsis: true },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 160 },
  { title: '操作', key: 'actions', fixed: 'right', width: 160 },
]

const buildQuery = (): AssetListQueryDto => ({
  page: page.value,
  pageSize: pageSize.value,
  status: filters.status,
  type: filters.type,
  keyword: filters.keyword.trim() || undefined,
})

const fetchList = async () => {
  loading.value = true
  try {
    const data = await getAssetListApi(buildQuery())
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const search = async () => {
  page.value = 1
  await fetchList()
}

const reset = async () => {
  filters.status = undefined
  filters.type = undefined
  filters.keyword = ''
  page.value = 1
  await fetchList()
}

const openCreate = () => {
  createForm.name = ''
  createForm.type = 'server'
  createForm.location = ''
  createForm.spec = ''
  createForm.remark = ''
  createModalOpen.value = true
}

const submitCreate = async () => {
  if (!createForm.name || !createForm.spec || !createForm.location) {
    message.warning('请填写资产名称/规格/位置')
    return
  }
  modalLoading.value = true
  try {
    await createAssetApi(createForm)
    message.success('资产入库成功')
    createModalOpen.value = false
    await fetchList()
  } finally {
    modalLoading.value = false
  }
}

const openEdit = async (record: AssetListItemDto) => {
  modalLoading.value = true
  try {
    const detail = await getAssetDetailApi(record.id)
    current.value = detail
    editForm.name = detail.name
    editForm.type = detail.type
    editForm.location = detail.location || ''
    editForm.spec = detail.spec || ''
    editForm.remark = detail.remark || ''
    editModalOpen.value = true
  } finally {
    modalLoading.value = false
  }
}

const submitEdit = async () => {
  if (!current.value) {
    return
  }
  modalLoading.value = true
  try {
    await updateAssetApi(current.value.id, editForm)
    message.success('资产更新成功')
    editModalOpen.value = false
    await fetchList()
  } finally {
    modalLoading.value = false
  }
}

const loadClaimUsers = async () => {
  userOptionsLoading.value = true
  try {
    claimUsers.value = await getUserOptionsApi()
  } finally {
    userOptionsLoading.value = false
  }
}

const openClaim = async (record: AssetListItemDto) => {
  current.value = record
  claimForm.ownerId = record.ownerId || ''
  claimForm.remark = ''
  claimModalOpen.value = true
  if (!claimUsers.value.length) {
    await loadClaimUsers()
  }
}

const submitClaim = async () => {
  if (!current.value || !claimForm.ownerId) {
    message.warning('请选择领用人')
    return
  }
  modalLoading.value = true
  try {
    await assetActionApi(current.value.id, {
      action: 'claim',
      ownerId: claimForm.ownerId,
      remark: claimForm.remark,
    })
    message.success('领用成功')
    claimModalOpen.value = false
    await fetchList()
  } finally {
    modalLoading.value = false
  }
}

const receiveAsset = (record: AssetListItemDto) => {
  Modal.confirm({
    title: `确认将 ${record.assetNo} 回收入库？`,
    onOk: async () => {
      await assetActionApi(record.id, {
        action: 'receive',
      })
      message.success('已回收入库')
      await fetchList()
    },
  })
}

const scrapAsset = (record: AssetListItemDto) => {
  Modal.confirm({
    title: `确认报废 ${record.assetNo}？`,
    content: '报废后不可恢复为在用状态。',
    okButtonProps: { danger: true },
    onOk: async () => {
      await assetActionApi(record.id, {
        action: 'scrap',
      })
      message.success('资产已报废')
      await fetchList()
    },
  })
}

const toDetail = (id: string) => {
  void router.push(`/ops/asset/detail/${id}`)
}

const handlePageChange = async (value: number) => {
  page.value = value
  await fetchList()
}

const handlePageSizeChange = async (value: number) => {
  pageSize.value = value
  page.value = 1
  await fetchList()
}

const typeOptions = computed(() => [
  { label: '服务器', value: 'server' },
  { label: '笔记本', value: 'laptop' },
  { label: '网络设备', value: 'network' },
  { label: '软件授权', value: 'license' },
])

const statusOptions = computed(() => [
  { label: '库存中', value: 'stock' },
  { label: '已领用', value: 'in_use' },
  { label: '已报废', value: 'scrapped' },
])

onMounted(() => {
  void fetchList()
})
</script>

<template>
  <section class="asset-list-page">
    <ProTable
      header-title="资产列表"
      :columns="columns"
      :data-source="list"
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
            <a-col :xs="24" :sm="12" :md="8" :lg="8">
              <a-form-item label="资产状态" style="margin-bottom: 0;">
                <a-select
                  v-model:value="filters.status"
                  allow-clear
                  placeholder="请选择状态"
                  style="width: 100%;"
                  :options="statusOptions"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="8" :lg="8">
              <a-form-item label="资产类型" style="margin-bottom: 0;">
                <a-select
                  v-model:value="filters.type"
                  allow-clear
                  placeholder="请选择类型"
                  style="width: 100%;"
                  :options="typeOptions"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="8" :lg="8">
              <a-form-item label="关键字" style="margin-bottom: 0;">
                <a-input
                  v-model:value="filters.keyword"
                  allow-clear
                  style="width: 100%;"
                  placeholder="编号 / 名称 / 位置 / 领用人"
                  @press-enter="search"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <div class="filter-actions-bar">
            <a-space>
              <a-button type="primary" @click="search">查询</a-button>
              <a-button @click="reset">重置</a-button>
            </a-space>
          </div>
        </a-form>
      </template>

      <template #actions>
        <Permission code="asset:create">
          <a-button type="primary" @click="openCreate">资产入库</a-button>
        </Permission>
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <a @click="toDetail((record as AssetListItemDto).id)">
            {{ (record as AssetListItemDto).name }}
          </a>
        </template>
        <template v-else-if="column.key === 'type'">
          {{ typeTextMap[(record as AssetListItemDto).type] }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColorMap[(record as AssetListItemDto).status]">
            {{ statusTextMap[(record as AssetListItemDto).status] }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'ownerName'">
          {{ (record as AssetListItemDto).ownerName || '-' }}
        </template>
        <template v-else-if="column.key === 'updatedAt'">
          {{ dayjs((record as AssetListItemDto).updatedAt).format('YYYY-MM-DD HH:mm') }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="toDetail((record as AssetListItemDto).id)">详情</a-button>
            <Permission code="asset:edit">
              <a-button type="link" size="small" @click="openEdit(record as AssetListItemDto)">编辑</a-button>
            </Permission>
            <a-dropdown>
              <a-button type="link" size="small">
                更多 <DownOutlined style="font-size: 10px;" />
              </a-button>
              <template #overlay>
                <a-menu>
                  <Permission code="asset:claim">
                    <a-menu-item
                      :disabled="(record as AssetListItemDto).status !== 'stock'"
                      @click="openClaim(record as AssetListItemDto)"
                    >
                      领用
                    </a-menu-item>
                  </Permission>
                  <Permission code="asset:claim">
                    <a-menu-item
                      :disabled="(record as AssetListItemDto).status !== 'in_use'"
                      @click="receiveAsset(record as AssetListItemDto)"
                    >
                      回收
                    </a-menu-item>
                  </Permission>
                  <Permission code="asset:scrap">
                    <a-menu-item
                      danger
                      :disabled="(record as AssetListItemDto).status === 'scrapped'"
                      @click="scrapAsset(record as AssetListItemDto)"
                    >
                      报废
                    </a-menu-item>
                  </Permission>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </template>
      </template>
    </ProTable>

    <a-modal v-model:open="createModalOpen" title="资产入库" :confirm-loading="modalLoading" @ok="submitCreate">
      <a-form layout="vertical">
        <a-form-item label="资产名称" required>
          <a-input v-model:value="createForm.name" />
        </a-form-item>
        <a-form-item label="资产类型" required>
          <a-select v-model:value="createForm.type" :options="typeOptions" />
        </a-form-item>
        <a-form-item label="存放位置" required>
          <a-input v-model:value="createForm.location" />
        </a-form-item>
        <a-form-item label="规格说明" required>
          <a-textarea v-model:value="createForm.spec" :rows="3" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="createForm.remark" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="editModalOpen" title="编辑资产" :confirm-loading="modalLoading" @ok="submitEdit">
      <a-form layout="vertical">
        <a-form-item label="资产名称">
          <a-input v-model:value="editForm.name" />
        </a-form-item>
        <a-form-item label="资产类型">
          <a-select v-model:value="editForm.type" :options="typeOptions" />
        </a-form-item>
        <a-form-item label="存放位置">
          <a-input v-model:value="editForm.location" />
        </a-form-item>
        <a-form-item label="规格说明">
          <a-textarea v-model:value="editForm.spec" :rows="3" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="editForm.remark" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="claimModalOpen" title="资产领用" :confirm-loading="modalLoading" @ok="submitClaim">
      <a-form layout="vertical">
        <a-form-item label="领用人" required>
          <a-select
            v-model:value="claimForm.ownerId"
            show-search
            option-filter-prop="label"
            :loading="userOptionsLoading"
            :options="claimUsers.map(user => ({ label: `${user.displayName}（${user.username}）`, value: user.id }))"
            placeholder="请选择启用用户"
          />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="claimForm.remark" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<style scoped>
.asset-list-page {
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
</style>
