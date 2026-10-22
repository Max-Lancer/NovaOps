<script setup lang="ts">
import { computed, h, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { checkSystemHealthApi, getSystemHealthApi } from '@/api/system'
import { useThemeStore } from '@/store/theme'
import type { ModuleHealthDto, ModuleStatus } from '@/types/system'

defineOptions({ name: 'SystemOverview' })

use([CanvasRenderer, GridComponent, LegendComponent, TooltipComponent, PieChart, BarChart, LineChart])

const themeStore = useThemeStore()
const loading = ref(false)
const checking = ref(false)
const health = ref<ModuleHealthDto | null>(null)
const pieRef = ref<HTMLDivElement>()
const barRef = ref<HTMLDivElement>()
const lineRef = ref<HTMLDivElement>()
const pieChart = ref<ECharts>()
const barChart = ref<ECharts>()
const lineChart = ref<ECharts>()

const statusText: Record<ModuleStatus, string> = {
  up: '正常',
  degraded: '降级',
  down: '不可用',
}
const statusColor: Record<ModuleStatus, string> = {
  up: 'success',
  degraded: 'warning',
  down: 'error',
}

const latest = computed(() => health.value?.latest || [])
const checkedAt = computed(() => latest.value[0]?.checkedAt)

const chartColors = () => ({
  text: getComputedStyle(document.documentElement).getPropertyValue('--nova-text').trim() || '#0f172a',
  secondary: getComputedStyle(document.documentElement).getPropertyValue('--nova-text-secondary').trim() || '#64748b',
  border: getComputedStyle(document.documentElement).getPropertyValue('--nova-border').trim() || '#e2e8f0',
  primary: themeStore.primaryColor,
})

const renderCharts = () => {
  const data = health.value
  if (!data) {
    return
  }
  const colors = chartColors()
  const counts = { up: 0, degraded: 0, down: 0 }
  data.latest.forEach((item) => {
    counts[item.status] += 1
  })
  pieChart.value?.setOption({
    textStyle: { color: colors.text },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: colors.secondary } },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      data: [
        { name: '正常', value: counts.up, itemStyle: { color: '#22c55e' } },
        { name: '降级', value: counts.degraded, itemStyle: { color: '#f59e0b' } },
        { name: '不可用', value: counts.down, itemStyle: { color: '#ef4444' } },
      ],
    }],
  })
  barChart.value?.setOption({
    textStyle: { color: colors.text },
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 16, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: data.latest.map((item) => item.moduleName), axisLabel: { color: colors.secondary } },
    yAxis: { type: 'value', name: 'ms', axisLabel: { color: colors.secondary }, splitLine: { lineStyle: { color: colors.border } } },
    series: [{ type: 'bar', data: data.latest.map((item) => item.latencyMs), itemStyle: { color: colors.primary }, barMaxWidth: 42 }],
  })
  const modules = [...new Set(data.history.map((item) => item.moduleName))]
  const times = [...new Set(data.history.map((item) => item.checkedAt))]
  lineChart.value?.setOption({
    textStyle: { color: colors.text },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: colors.secondary } },
    grid: { left: 48, right: 16, top: 36, bottom: 32 },
    xAxis: { type: 'category', data: times.map((time) => dayjs(time).format('MM-DD HH:mm')), axisLabel: { color: colors.secondary } },
    yAxis: { type: 'value', name: 'ms', axisLabel: { color: colors.secondary }, splitLine: { lineStyle: { color: colors.border } } },
    series: modules.map((name) => ({
      name,
      type: 'line',
      smooth: true,
      data: times.map((time) => data.history.find((item) => item.moduleName === name && item.checkedAt === time)?.latencyMs ?? null),
    })),
  })
}

const resizeCharts = () => {
  pieChart.value?.resize()
  barChart.value?.resize()
  lineChart.value?.resize()
}

const ensureCharts = async () => {
  await nextTick()
  if (pieRef.value && !pieChart.value) pieChart.value = init(pieRef.value)
  if (barRef.value && !barChart.value) barChart.value = init(barRef.value)
  if (lineRef.value && !lineChart.value) lineChart.value = init(lineRef.value)
  renderCharts()
  resizeCharts()
}

const loadHealth = async (refresh = false) => {
  if (refresh) checking.value = true
  else loading.value = true
  try {
    health.value = refresh ? await checkSystemHealthApi() : await getSystemHealthApi()
    if (refresh) message.success('检查完成')
    await ensureCharts()
  } finally {
    loading.value = false
    checking.value = false
  }
}

watch(() => [themeStore.resolvedMode, themeStore.primaryColor], () => { void nextTick(renderCharts) })
onMounted(() => { void loadHealth(); window.addEventListener('resize', resizeCharts) })
onActivated(() => {
  void nextTick(() => {
    resizeCharts()
    setTimeout(resizeCharts, 60)
    setTimeout(resizeCharts, 250)
  })
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  pieChart.value?.dispose()
  barChart.value?.dispose()
  lineChart.value?.dispose()
})
</script>

<template>
  <section class="system-overview">
    <a-card :bordered="false">
      <div class="head">
        <div>
          <h2>系统运行概览</h2>
          <p>最近检查：{{ checkedAt ? dayjs(checkedAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}</p>
        </div>
        <a-button type="primary" :icon="h(ReloadOutlined)" :loading="checking" @click="loadHealth(true)">重新检查</a-button>
      </div>
    </a-card>

    <a-row :gutter="12">
      <a-col v-for="item in latest" :key="item.moduleCode" :xs="24" :md="12" :xl="6">
        <a-card :bordered="false" :loading="loading">
          <div class="module-title">{{ item.moduleName }}</div>
          <a-tag :color="statusColor[item.status]">{{ statusText[item.status] }}</a-tag>
          <div class="latency">{{ item.latencyMs }} ms</div>
          <p>{{ item.message }}</p>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="12">
      <a-col :xs="24" :lg="10"><a-card title="模块状态" :bordered="false"><div ref="pieRef" class="chart"></div></a-card></a-col>
      <a-col :xs="24" :lg="14"><a-card title="最近一次耗时" :bordered="false"><div ref="barRef" class="chart"></div></a-card></a-col>
    </a-row>
    <a-card title="检查耗时趋势" :bordered="false"><div ref="lineRef" class="chart chart-line"></div></a-card>
  </section>
</template>

<style scoped>
.system-overview { display: grid; gap: 12px; }
.head, .module-title { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.head h2, .module-title, p { margin: 0; }
.head p, .system-overview :deep(.ant-card p) { color: var(--nova-text-secondary); }
.module-title { margin-bottom: 8px; font-weight: 600; }
.latency { margin: 8px 0; font-size: 20px; font-weight: 650; }
.chart { width: 100%; height: 280px; }
.chart-line { height: 320px; }
</style>
