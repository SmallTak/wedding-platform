<script setup>
import { computed, onMounted, ref } from 'vue'
import { RefreshCw } from '@lucide/vue'
import { analyticsApi } from '../api/operations'
import { apiErrorMessage } from '../utils/content'

const loading = ref(false)
const errorMessage = ref('')
const days = ref(30)
const overview = ref({
  days: 30,
  startDate: null,
  endDate: null,
  summary: {
    pageViews: 0,
    uniqueVisitors: 0,
    collectionViews: 0,
    inquiryCount: 0,
    creatorUploadCount: 0,
    pendingContent: 0,
    rejectedContent: 0,
    publishedContent: 0,
    offlineContent: 0,
  },
  trend: [],
  topCollections: [],
})

const metrics = computed(() => [
  {
    label: '官网访问量',
    value: overview.value.summary.pageViews,
    note: `近 ${days.value} 天有效页面访问`,
    tone: 'ink',
  },
  {
    label: '独立访客',
    value: overview.value.summary.uniqueVisitors,
    note: `近 ${days.value} 天匿名访客`,
    tone: 'green',
  },
  {
    label: '作品浏览量',
    value: overview.value.summary.collectionViews,
    note: '作品集详情访问',
    tone: 'red',
  },
])

const businessMetrics = computed(() => [
  { label: '咨询线索', value: overview.value.summary.inquiryCount, note: `近 ${days.value} 天` },
  { label: '创作者上传', value: overview.value.summary.creatorUploadCount, note: `近 ${days.value} 天图片` },
  { label: '待审核内容', value: overview.value.summary.pendingContent, note: '当前作品集' },
  { label: '被驳回内容', value: overview.value.summary.rejectedContent, note: '当前部分驳回' },
  { label: '已发布内容', value: overview.value.summary.publishedContent, note: '当前作品集' },
  { label: '已下架内容', value: overview.value.summary.offlineContent, note: '当前作品集' },
])

const chartMaximum = computed(() => Math.max(
  1,
  ...overview.value.trend.flatMap((item) => [
    item.pageViews,
    item.collectionViews,
  ]),
))

onMounted(loadOverview)

async function loadOverview() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await analyticsApi.overview(days.value)
    overview.value = data
  } catch (error) {
    errorMessage.value = apiErrorMessage(error, '统计数据加载失败')
  } finally {
    loading.value = false
  }
}

function changeDays(value) {
  if (days.value === value) return
  days.value = value
  loadOverview()
}

function barHeight(value) {
  if (!value) return '0%'
  return `${Math.max(4, (value / chartMaximum.value) * 100)}%`
}

function contentViews(item) {
  return item.collectionViews
}

function formatNumber(value) {
  return new Intl.NumberFormat('zh-CN').format(value || 0)
}

function formatDay(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(`${value}T00:00:00`))
}
</script>

<template>
  <main class="dashboard-content analytics-content" v-loading="loading">
    <section class="metric-grid" aria-label="访问统计">
      <article v-for="metric in metrics" :key="metric.label" class="metric-item">
        <span :class="['metric-indicator', metric.tone]"></span>
        <p>{{ metric.label }}</p>
        <strong>{{ formatNumber(metric.value) }}</strong>
        <small>{{ metric.note }}</small>
      </article>
    </section>

    <p v-if="errorMessage" class="dashboard-error">{{ errorMessage }}</p>

    <section class="dashboard-section">
      <div class="section-title-row analytics-section-heading">
        <div>
          <p>Traffic trend</p>
          <h2>访问趋势</h2>
        </div>
        <div class="analytics-heading-actions">
          <div class="segmented-control" aria-label="统计周期">
            <button :class="{ active: days === 7 }" type="button" @click="changeDays(7)">近 7 天</button>
            <button :class="{ active: days === 30 }" type="button" @click="changeDays(30)">近 30 天</button>
          </div>
          <button class="icon-command" type="button" aria-label="刷新统计" title="刷新统计" @click="loadOverview">
            <RefreshCw :size="17" />
          </button>
        </div>
      </div>

      <div class="analytics-legend" aria-label="趋势图图例">
        <span><i class="page"></i>官网访问</span>
        <span><i class="content"></i>作品浏览</span>
      </div>
      <div class="analytics-chart-scroll">
        <div
          class="analytics-chart"
          :style="{ '--analytics-columns': overview.trend.length || 1 }"
        >
          <div v-for="item in overview.trend" :key="item.date" class="analytics-chart-column">
            <div class="analytics-chart-bars">
              <i
                class="page"
                :style="{ height: barHeight(item.pageViews) }"
                :title="`${item.date} 官网访问 ${item.pageViews}，独立访客 ${item.uniqueVisitors}`"
              ></i>
              <i
                class="content"
                :style="{ height: barHeight(contentViews(item)) }"
                :title="`${item.date} 作品 ${item.collectionViews}`"
              ></i>
            </div>
            <span>{{ formatDay(item.date) }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="dashboard-section">
      <div class="section-title-row">
        <div>
          <p>Business overview</p>
          <h2>业务概览</h2>
        </div>
      </div>
      <div class="analytics-business-grid">
        <div v-for="metric in businessMetrics" :key="metric.label">
          <span>{{ metric.label }}</span>
          <strong>{{ formatNumber(metric.value) }}</strong>
          <small>{{ metric.note }}</small>
        </div>
      </div>
    </section>

    <section class="dashboard-split analytics-popular-grid">
      <div class="dashboard-section compact-section">
        <div class="section-title-row">
          <div><p>Popular collections</p><h2>热门作品集</h2></div>
        </div>
        <div v-if="overview.topCollections.length" class="analytics-ranking">
          <article v-for="(item, index) in overview.topCollections" :key="item.targetId">
            <span>{{ index + 1 }}</span>
            <div><strong>{{ item.title }}</strong><small>{{ item.uniqueVisitors }} 位访客</small></div>
            <b>{{ formatNumber(item.views) }}</b>
          </article>
        </div>
        <div v-else class="dashboard-empty">当前周期暂无作品浏览数据</div>
      </div>
    </section>
  </main>
</template>
