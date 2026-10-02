<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { assetActionApi, getAssetDetailApi } from '@/api/asset'
import type { AssetDetailDto, AssetStatus } from '@/types/asset'

defineOptions({
  name: 'AssetDetail',
})

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const actionLoading = ref(false)
const detail = ref<AssetDetailDto | null>(null)

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

const assetId = computed(() => String(route.params.id || ''))

const loadDetail = async () => {
  if (!assetId.value) {
    return
  }
  loading.value = true
  try {
    detail.value = await getAssetDetailApi(assetId.value)
  } finally {
    loading.value = false
  }
}

const doAction = async (action: 'receive' | 'scrap') => {
  if (!detail.value) {
    return
  }
  actionLoading.value = true
  try {
    await assetActionApi(detail.value.id, { action })
    message.success(action === 'receive' ? '已回收入库' : '资产已报废')
    await loadDetail()
  } finally {
    actionLoading.value = false
  }
}

watch(
  () => route.params.id,
  () => {
    void loadDetail()
  }
)

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <section class="asset-detail-page">
    <a-card :loading="loading" :bordered="false">
      <template #title>
        <a-space>
          <a-button size="small" @click="router.back()">返回</a-button>
          <span>{{ detail?.name || '资产详情' }}</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <Permission code="asset:claim">
            <a-button
              size="small"
              :loading="actionLoading"
              :disabled="detail?.status !== 'in_use'"
              @click="doAction('receive')"
            >
              回收入库
            </a-button>
          </Permission>
          <Permission code="asset:scrap">
            <a-button
              size="small"
              danger
              :loading="actionLoading"
              :disabled="detail?.status === 'scrapped'"
              @click="doAction('scrap')"
            >
              报废
            </a-button>
          </Permission>
        </a-space>
      </template>

      <a-descriptions :column="2" bordered size="small">
        <a-descriptions-item label="资产编号">{{ detail?.assetNo || '-' }}</a-descriptions-item>
        <a-descriptions-item label="资产状态">
          <a-tag v-if="detail" :color="statusColorMap[detail.status]">
            {{ statusTextMap[detail.status] }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="资产类型">{{ detail?.type }}</a-descriptions-item>
        <a-descriptions-item label="领用人">{{ detail?.ownerName || '-' }}</a-descriptions-item>
        <a-descriptions-item label="存放位置">{{ detail?.location }}</a-descriptions-item>
        <a-descriptions-item label="采购日期">{{ detail?.purchaseDate }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">
          {{ detail?.updatedAt ? dayjs(detail.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="规格">{{ detail?.spec }}</a-descriptions-item>
        <a-descriptions-item label="备注" :span="2">{{ detail?.remark || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-card title="关联工单" :bordered="false">
      <a-table
        row-key="id"
        size="small"
        :pagination="false"
        :data-source="detail?.relatedTickets || []"
        :columns="[
          { title: '工单号', dataIndex: 'id', key: 'id', width: 160 },
          { title: '标题', dataIndex: 'title', key: 'title' },
          { title: '状态', dataIndex: 'status', key: 'status', width: 120 },
          { title: '优先级', dataIndex: 'priority', key: 'priority', width: 120 },
          { title: '负责人', dataIndex: 'assigneeName', key: 'assigneeName', width: 120 },
          { title: '操作', key: 'actions', width: 100 },
        ]"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'actions'">
            <a-button type="link" size="small" @click="router.push(`/ops/ticket/detail/${record.id}`)">查看</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </section>
</template>

<style scoped>
.asset-detail-page {
  display: grid;
  gap: 12px;
}
</style>
