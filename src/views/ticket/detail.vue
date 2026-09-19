<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { getAssetBatchApi } from '@/api/asset'
import { getUserOptionsApi } from '@/api/auth'
import {
  createTicketCommentApi,
  fetchTicketAttachmentApi,
  getTicketCommentsApi,
  getTicketDetailApi,
  ticketActionApi,
  uploadTicketAttachmentApi,
} from '@/api/ticket'
import type { AssetSimpleDto } from '@/types/asset'
import type { UserOptionDto } from '@/types/auth'
import type { TicketActionType, TicketAttachmentDto, TicketCommentDto, TicketDetailDto, TicketStatus } from '@/types/ticket'

defineOptions({
  name: 'TicketDetail',
})

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const actionLoading = ref(false)
const commentLoading = ref(false)
const detail = ref<TicketDetailDto | null>(null)
const comments = ref<TicketCommentDto[]>([])
const relatedAssets = ref<AssetSimpleDto[]>([])
const assigneeUsers = ref<UserOptionDto[]>([])
const newComment = ref('')

const actionForm = reactive({
  assigneeId: '',
  remark: '',
})

const assigneeOptions = computed(() =>
  assigneeUsers.value.map((user) => ({
    label: user.displayName,
    value: user.id,
  }))
)

const statusTextMap: Record<TicketStatus, string> = {
  pending: '待处理',
  processing: '处理中',
  review: '待复核',
  done: '已完成',
}

const flowSteps: TicketStatus[] = ['pending', 'processing', 'review', 'done']

const allowedActionsByStatus: Record<TicketStatus, readonly TicketActionType[]> = {
  pending: ['assign'],
  processing: ['transfer', 'advance', 'close'],
  review: ['transfer', 'approve', 'reject', 'close'],
  done: [],
}

const isActionAvailable = (action: TicketActionType) => {
  if (!detail.value) {
    return false
  }
  return allowedActionsByStatus[detail.value.status].includes(action)
}

const currentStep = computed(() => {
  if (!detail.value) {
    return 0
  }
  const index = flowSteps.indexOf(detail.value.status)
  return index >= 0 ? index : 0
})

const ticketId = computed(() => String(route.params.id || ''))

const loadTicket = async () => {
  if (!ticketId.value) {
    return
  }
  loading.value = true
  try {
    const data = await getTicketDetailApi(ticketId.value)
    detail.value = data
    if (data.assetIds.length) {
      relatedAssets.value = await getAssetBatchApi(data.assetIds)
    } else {
      relatedAssets.value = []
    }
  } finally {
    loading.value = false
  }
}

const loadComments = async () => {
  if (!ticketId.value) {
    return
  }
  const data = await getTicketCommentsApi(ticketId.value)
  comments.value = data
}

const refreshDetail = async () => {
  await Promise.all([loadTicket(), loadComments()])
}

const doAction = async (action: TicketActionType) => {
  if (!detail.value) {
    return
  }
  if (!isActionAvailable(action)) {
    message.warning('当前工单状态不可执行此操作')
    return
  }
  if ((action === 'assign' || action === 'transfer') && !actionForm.assigneeId) {
    message.warning('请选择指派对象')
    return
  }
  actionLoading.value = true
  try {
    await ticketActionApi(detail.value.id, {
      action,
      assigneeId:
        action === 'assign' || action === 'transfer' ? actionForm.assigneeId : undefined,
      remark: actionForm.remark || undefined,
    })
    message.success('状态流转成功')
    actionForm.remark = ''
    await refreshDetail()
  } finally {
    actionLoading.value = false
  }
}

const submitComment = async () => {
  if (!newComment.value.trim() || !ticketId.value) {
    return
  }
  commentLoading.value = true
  try {
    await createTicketCommentApi(ticketId.value, {
      content: newComment.value.trim(),
    })
    newComment.value = ''
    await loadComments()
    message.success('评论已发布')
  } finally {
    commentLoading.value = false
  }
}

const handleUpload = async (options: any) => {
  try {
    const file = options.file as File
    await uploadTicketAttachmentApi(ticketId.value, file)
    options.onSuccess?.({}, file)
    message.success('附件上传成功')
    await loadTicket()
  } catch (error) {
    options.onError?.(error)
  }
}

// 下载要带 Authorization 头，直接给 href 会 401；因此取回 blob 后用临时链接落盘
const downloadAttachment = async (item: TicketAttachmentDto) => {
  const blob = await fetchTicketAttachmentApi(ticketId.value, item.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = item.name
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

watch(
  () => route.params.id,
  () => {
    void refreshDetail()
  }
)

onMounted(() => {
  void Promise.all([
    refreshDetail(),
    getUserOptionsApi().then((users) => {
      assigneeUsers.value = users
    }),
  ])
})
</script>

<template>
  <section class="ticket-detail-page">
    <a-card :loading="loading" :bordered="false">
      <template #title>
        <a-space>
          <a-button size="small" @click="router.back()">返回</a-button>
          <span>{{ detail?.title || '工单详情' }}</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <Permission code="ticket:assign">
            <a-button
              v-if="isActionAvailable('assign')"
              size="small"
              :loading="actionLoading"
              @click="doAction('assign')"
            >
              指派
            </a-button>
          </Permission>
          <Permission code="ticket:transfer">
            <a-button
              v-if="isActionAvailable('transfer')"
              size="small"
              :loading="actionLoading"
              @click="doAction('transfer')"
            >
              转派
            </a-button>
          </Permission>
          <Permission code="ticket:close">
            <a-button
              v-if="isActionAvailable('close')"
              size="small"
              danger
              :loading="actionLoading"
              @click="doAction('close')"
            >
              关闭
            </a-button>
          </Permission>
          <Permission code="ticket:advance">
            <a-button
              v-if="isActionAvailable('advance')"
              size="small"
              :loading="actionLoading"
              @click="doAction('advance')"
            >
              提交复核
            </a-button>
          </Permission>
          <Permission code="ticket:approve">
            <a-button
              v-if="isActionAvailable('approve')"
              size="small"
              type="primary"
              :loading="actionLoading"
              @click="doAction('approve')"
            >
              复核通过
            </a-button>
          </Permission>
          <Permission code="ticket:reject">
            <a-button
              v-if="isActionAvailable('reject')"
              size="small"
              :loading="actionLoading"
              @click="doAction('reject')"
            >
              驳回
            </a-button>
          </Permission>
        </a-space>
      </template>

      <a-steps :current="currentStep" size="small" class="mb16">
        <a-step title="待处理" />
        <a-step title="处理中" />
        <a-step title="待复核" />
        <a-step title="已完成" />
      </a-steps>

      <a-descriptions :column="2" bordered size="small">
        <a-descriptions-item label="工单号">{{ detail?.id }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag color="geekblue">{{ detail ? statusTextMap[detail.status] : '-' }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="优先级">{{ detail?.priority }}</a-descriptions-item>
        <a-descriptions-item label="负责人">{{ detail?.assigneeName || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建人">{{ detail?.creatorName }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">
          {{ detail?.updatedAt ? dayjs(detail.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="关联资产">
          <a-space wrap>
            <a-tag
              v-for="asset in relatedAssets"
              :key="asset.id"
              color="blue"
              class="asset-tag"
              @click="router.push(`/asset/detail/${asset.id}`)"
            >
              {{ asset.id }} / {{ asset.name }}
            </a-tag>
            <span v-if="!relatedAssets.length">-</span>
          </a-space>
        </a-descriptions-item>
        <a-descriptions-item label="描述" :span="2">{{ detail?.description }}</a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-row :gutter="12">
      <a-col :span="14">
        <a-card title="流转记录" :bordered="false">
          <a-timeline>
            <a-timeline-item v-for="item in detail?.timeline || []" :key="item.id">
              <div class="timeline-title">
                {{ item.action }}
                <span class="timeline-time">{{ dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss') }}</span>
              </div>
              <div>执行人：{{ item.operatorName || '-' }}</div>
              <div v-if="item.fromStatus && item.toStatus">
                状态：{{ statusTextMap[item.fromStatus] }} -> {{ statusTextMap[item.toStatus] }}
              </div>
              <div v-if="item.remark">备注：{{ item.remark }}</div>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </a-col>
      <a-col :span="10">
        <a-card title="状态操作" :bordered="false">
          <a-form layout="vertical">
            <a-form-item label="指派 / 转派给">
              <a-select
                v-model:value="actionForm.assigneeId"
                allow-clear
                :options="assigneeOptions"
              />
            </a-form-item>
            <a-form-item label="备注">
              <a-textarea v-model:value="actionForm.remark" :rows="3" placeholder="填写 reject/transfer 说明" />
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="12">
      <a-col :span="12">
        <a-card title="评论" :bordered="false">
          <a-list :data-source="comments" size="small">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-comment
                  :author="item.authorName"
                  :content="item.content"
                  :datetime="dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss')"
                />
              </a-list-item>
            </template>
          </a-list>
          <Permission code="ticket:comment">
            <a-space direction="vertical" style="width: 100%; margin-top: 12px">
              <a-textarea v-model:value="newComment" :rows="3" placeholder="输入评论..." />
              <a-button type="primary" :loading="commentLoading" @click="submitComment">发表评论</a-button>
            </a-space>
          </Permission>
        </a-card>
      </a-col>

      <a-col :span="12">
        <a-card title="附件" :bordered="false">
          <Permission code="ticket:comment">
            <a-upload :show-upload-list="false" :custom-request="handleUpload">
              <a-button type="dashed">上传附件</a-button>
            </a-upload>
          </Permission>
          <a-list :data-source="detail?.attachments || []" size="small" class="mt12">
            <template #renderItem="{ item }">
              <a-list-item>
                <a @click="downloadAttachment(item as TicketAttachmentDto)">{{ item.name }}</a>
                <span>{{ (item.size / 1024).toFixed(1) }} KB</span>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </a-col>
    </a-row>
  </section>
</template>

<style scoped>
.ticket-detail-page {
  display: grid;
  gap: 12px;
}

.mb16 {
  margin-bottom: 16px;
}

.mt12 {
  margin-top: 12px;
}

.timeline-title {
  font-weight: 600;
  display: flex;
  gap: 8px;
  align-items: center;
}

.timeline-time {
  color: #64748b;
  font-size: 12px;
}

.asset-tag {
  cursor: pointer;
}
</style>
