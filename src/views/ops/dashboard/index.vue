<script setup lang="ts">
import { computed, h, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  type GridComponentOption,
  type LegendComponentOption,
  type TitleComponentOption,
  type TooltipComponentOption,
} from 'echarts/components'
import { init, use, type ComposeOption, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { BarSeriesOption, LineSeriesOption, PieSeriesOption } from 'echarts/charts'
import { getDashboardMetricsApi } from '@/api/dashboard'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import type { DashboardMetricsDto } from '@/types/dashboard'

defineOptions({
  name: 'Dashboard',
})

use([
  CanvasRenderer,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  LineChart,
  PieChart,
  BarChart,
])

type ECOption = ComposeOption<
  | TitleComponentOption
  | TooltipComponentOption
  | GridComponentOption
  | LegendComponentOption
  | LineSeriesOption
  | PieSeriesOption
  | BarSeriesOption
>

const authStore = useAuthStore()
const themeStore = useThemeStore()
const loading = ref(false)
const metrics = ref<DashboardMetricsDto | null>(null)
const rangeValue = ref<[Dayjs, Dayjs]>([dayjs().subtract(6, 'day').startOf('day'), dayjs().endOf('day')])

const trendRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()
const barRef = ref<HTMLDivElement>()

const trendChart = ref<ECharts>()
const pieChart = ref<ECharts>()
const barChart = ref<ECharts>()
let chartResizeObserver: ResizeObserver | undefined

const greeting = computed(() => {
  const name = authStore.user?.displayName || authStore.user?.username || 'User'
  return `Hi, ${name}`
})
const canViewTickets = computed(() => authStore.permissions.includes('ticket:view'))

const applyQuickRange = (days: number) => {
  rangeValue.value = [dayjs().subtract(days - 1, 'day').startOf('day'), dayjs().endOf('day')]
}

const chartColors = () => ({ text: getComputedStyle(document.documentElement).getPropertyValue('--nova-text').trim(), secondary: getComputedStyle(document.documentElement).getPropertyValue('--nova-text-secondary').trim(), border: getComputedStyle(document.documentElement).getPropertyValue('--nova-border').trim(), primary: themeStore.primaryColor })
const createTrendOption = (data: DashboardMetricsDto): ECOption => ({
  textStyle: { color: chartColors().text },
  tooltip: { trigger: 'axis' },
  legend: {
    top: 0,
    right: 20,
    data: ['新增工单', '完成工单'],
    textStyle: { color: chartColors().text },
  },
  grid: {
    left: 20,
    right: 20,
    top: 40,
    bottom: 20,
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    data: data.trend.dates,
    boundaryGap: false,
    axisLine: { lineStyle: { color: chartColors().border } },
    axisLabel: { color: chartColors().secondary },
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: chartColors().secondary },
    splitLine: { lineStyle: { type: 'dashed', color: chartColors().border } },
  },
  series: [
    {
      name: '新增工单',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      lineStyle: { width: 3 },
      areaStyle: { opacity: 0.1 },
      data: data.trend.created,
      color: chartColors().primary,
    },
    {
      name: '完成工单',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      lineStyle: { width: 3 },
      areaStyle: { opacity: 0.08 },
      data: data.trend.closed,
      color: '#52c41a',
    },
  ],
})

const createPieOption = (data: DashboardMetricsDto): ECOption => {
  const total = data.categories.reduce((sum, item) => sum + item.value, 0)

  return {
    textStyle: { color: chartColors().text },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        return `<div style="font-size: 13px; line-height: 1.6;">
          <div style="font-weight: 600; margin-bottom: 2px;">${params.marker} ${params.name}</div>
          <div>工单数量：<b>${params.value} 条</b></div>
          <div>占比比例：<b>${params.percent}%</b></div>
        </div>`
      },
    },
    legend: {
      orient: 'horizontal',
      bottom: 0,
      left: 'center',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: chartColors().text, fontSize: 12 },
      formatter: (name: string) => {
        const item = data.categories.find((c) => c.name === name)
        const val = item ? item.value : 0
        const percent = total > 0 ? Math.round((val / total) * 100) : 0
        return `${name} ${val} (${percent}%)`
      },
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: false,
        label: {
          show: false,
        },
        labelLine: {
          show: false,
        },
        emphasis: {
          scale: true,
          scaleSize: 6,
          label: {
            show: false,
          },
          labelLine: {
            show: false,
          },
        },
        color: [chartColors().primary, '#22c55e', '#f59e0b', '#8b5cf6'],
        data: data.categories,
      },
    ],
  }
}

const createBarOption = (data: DashboardMetricsDto): ECOption => ({
  textStyle: { color: chartColors().text },
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 20, top: 24, bottom: 24 },
  xAxis: {
    type: 'value',
    splitLine: { lineStyle: { type: 'dashed', color: chartColors().border } },
  },
  yAxis: {
    type: 'category',
    data: data.durations.map((item) => item.name),
    axisLabel: { color: chartColors().secondary },
  },
  series: [
    {
      type: 'bar',
      data: data.durations.map((item) => item.hours),
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: '#fa8c16',
      },
      label: {
        show: true,
        position: 'right',
        formatter: '{c}h',
      },
    },
  ],
})

const safeResize = (chart?: ECharts, el?: HTMLElement) => {
  if (chart && el && el.offsetWidth > 0 && el.offsetHeight > 0) {
    chart.resize()
  }
}

const resizeAllCharts = () => {
  safeResize(trendChart.value, trendRef.value)
  safeResize(pieChart.value, pieRef.value)
  safeResize(barChart.value, barRef.value)
}

const renderCharts = () => {
  if (!metrics.value) {
    return
  }
  trendChart.value?.setOption(createTrendOption(metrics.value), true)
  pieChart.value?.setOption(createPieOption(metrics.value), true)
  barChart.value?.setOption(createBarOption(metrics.value), true)
  nextTick(resizeAllCharts)
}

const initCharts = async () => {
  await nextTick()
  if (!trendChart.value && trendRef.value) {
    trendChart.value = init(trendRef.value)
  }
  if (!pieChart.value && pieRef.value) {
    pieChart.value = init(pieRef.value)
  }
  if (!barChart.value && barRef.value) {
    barChart.value = init(barRef.value)
  }
  if (!chartResizeObserver) {
    chartResizeObserver = new ResizeObserver((entries) => {
      const hasVisibleEntry = entries.some((entry) => entry.contentRect.width > 0 && entry.contentRect.height > 0)
      if (hasVisibleEntry) {
        resizeAllCharts()
      }
    })
    ;[trendRef.value, pieRef.value, barRef.value].forEach((element) => {
      if (element) chartResizeObserver?.observe(element)
    })
  }
  renderCharts()
}

const fetchMetrics = async () => {
  if (!canViewTickets.value) {
    metrics.value = null
    return
  }
  const [start, end] = rangeValue.value
  loading.value = true
  try {
    metrics.value = await getDashboardMetricsApi({
      startDate: start.toISOString(),
      endDate: end.toISOString(),
    })
  } finally {
    loading.value = false
    await initCharts()
  }
}

watch(
  rangeValue,
  () => {
    void fetchMetrics()
  },
  { deep: true }
)

watch(() => [themeStore.resolvedMode, themeStore.primaryColor], () => { void nextTick(renderCharts) })

onMounted(() => {
  void fetchMetrics()
  window.addEventListener('resize', resizeAllCharts)
})

onActivated(() => {
  // 切回 Dashboard 时自动拉取最新工单统计
  void fetchMetrics()
  void nextTick(() => {
    resizeAllCharts()
    // 防御性延时：确保 keep-alive 激活和过渡动画结束后准确获取实际容器宽度
    setTimeout(resizeAllCharts, 60)
    setTimeout(resizeAllCharts, 250)
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeAllCharts)
  chartResizeObserver?.disconnect()
  trendChart.value?.dispose()
  pieChart.value?.dispose()
  barChart.value?.dispose()
})
</script>

<template>
  <main class="dashboard-page">
    <a-alert
      v-if="!canViewTickets"
      type="info"
      show-icon
      message="当前身份没有工单查看权限，暂时无法生成工单统计。"
    />
    <a-card :bordered="false" class="welcome-card">
      <div class="welcome-head">
        <div>
          <a-typography-title :level="3">{{ greeting }}</a-typography-title>
          <a-typography-paragraph class="sub-text">
            {{ greeting }} · NovaOps 运维协作平台
          </a-typography-paragraph>
        </div>
        <a-space>
          <a-range-picker v-model:value="rangeValue" />
          <a-button @click="applyQuickRange(7)">近7天</a-button>
          <a-button @click="applyQuickRange(30)">近30天</a-button>
          <a-button :icon="h(ReloadOutlined)" :loading="loading" @click="fetchMetrics">刷新</a-button>
        </a-space>
      </div>
    </a-card>

    <a-row :gutter="[12, 12]" class="overview-row">
      <a-col :xs="24" :sm="12" :xl="6">
        <a-card :loading="loading" :bordered="false" class="metric-card metric-blue">
          <a-statistic title="工单总量" :value="metrics?.overview.ticketTotal || 0" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :xl="6">
        <a-card :loading="loading" :bordered="false" class="metric-card metric-green">
          <a-statistic
            title="完成率"
            :value="metrics?.overview.doneRate || 0"
            :precision="1"
            suffix="%"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :xl="6">
        <a-card :loading="loading" :bordered="false" class="metric-card metric-orange">
          <a-statistic
            title="平均处理时长"
            :value="metrics?.overview.avgHandleHours || 0"
            :precision="1"
            suffix="h"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :xl="6">
        <a-card :loading="loading" :bordered="false" class="metric-card metric-violet">
          <a-statistic title="紧急工单占比" :value="metrics?.overview.urgentRate || 0" :precision="1" suffix="%" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[12, 12]">
      <a-col :xs="24" :xl="16">
        <a-card title="工单趋势" :bordered="false" class="chart-card">
          <a-spin :spinning="loading" class="chart-spin"><div ref="trendRef" class="chart-box chart-line"></div></a-spin>
        </a-card>
      </a-col>
      <a-col :xs="24" :xl="8">
        <a-card title="工单状态分布" :bordered="false" class="chart-card">
          <a-spin :spinning="loading" class="chart-spin"><div ref="pieRef" class="chart-box chart-pie"></div></a-spin>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="平均处理时长（按优先级）" :bordered="false" class="chart-card">
      <a-spin :spinning="loading" class="chart-spin"><div ref="barRef" class="chart-box chart-bar"></div></a-spin>
    </a-card>
  </main>
</template>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 12px;
}

.welcome-card {
  overflow: hidden;
  border-radius: 12px;
  background: linear-gradient(120deg, color-mix(in srgb, var(--nova-primary) 9%, var(--nova-surface)), var(--nova-surface) 55%);
}

.welcome-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.sub-text {
  margin: 0;
  color: var(--nova-text-secondary);
}

.overview-row {
  margin-bottom: 0;
}

.metric-card,.chart-card{height:100%;overflow:hidden;border:1px solid color-mix(in srgb,var(--nova-border) 78%,transparent);border-radius:12px;box-shadow:0 8px 24px rgba(15,23,42,.05)}
.metric-card{position:relative}.metric-card::before{position:absolute;top:0;right:0;left:0;height:3px;content:''}.metric-blue::before{background:var(--nova-primary)}.metric-green::before{background:#22c55e}.metric-orange::before{background:#f59e0b}.metric-violet::before{background:#8b5cf6}
.metric-card :deep(.ant-statistic-content){font-weight:650;letter-spacing:-.03em}.metric-card :deep(.ant-statistic-title){color:var(--nova-text-secondary)}
.chart-spin,.chart-spin :deep(.ant-spin-container){display:block;height:100%}

.chart-box {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.chart-line {
  height: 340px;
}

.chart-pie {
  height: 340px;
}

.chart-bar {
  height: 300px;
}

@media(max-width:760px){.welcome-head{align-items:flex-start;flex-direction:column}.welcome-head :deep(.ant-space){width:100%;flex-wrap:wrap}.chart-line,.chart-pie{height:280px}.chart-bar{height:260px}}
</style>
