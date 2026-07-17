<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChevronRight, Clock3, RefreshCw, Rocket, ShieldCheck } from '@lucide/vue'
import { collectionApi, projectApi, reviewApi } from '../api/content'
import { useAuthStore } from '../stores/auth'
import {
  apiErrorMessage,
  formatDateTime,
  publishStatusLabels,
  reviewStatusLabels,
  statusTone,
} from '../utils/content'

const auth = useAuthStore()
const loading = ref(false)
const overview = ref({
  pendingReviews: 0,
  rejectedCollections: 0,
  readyToPublish: 0,
  publishedCollections: 0,
  recentReviews: [],
})
const errorMessage = ref('')

const isAdmin = computed(() => auth.user?.accountType === 'ADMIN')
const metrics = computed(() => [
  {
    label: isAdmin.value ? '待审核作品集' : '待处理作品集',
    value: overview.value.pendingReviews,
    note: isAdmin.value ? '需要管理员处理' : '继续完善后可提交',
    tone: 'red',
  },
  {
    label: '部分驳回',
    value: overview.value.rejectedCollections,
    note: '需要修改或补充图片',
    tone: 'amber',
  },
  {
    label: '可发布作品',
    value: overview.value.readyToPublish,
    note: '已通过审核，等待发布',
    tone: 'green',
  },
  {
    label: '已发布作品',
    value: overview.value.publishedCollections,
    note: '当前官网公开内容',
    tone: 'ink',
  },
])

onMounted(loadOverview)

async function loadOverview() {
  loading.value = true
  errorMessage.value = ''
  try {
    if (isAdmin.value) {
      const { data } = await reviewApi.dashboard()
      overview.value = data
    } else {
      const [collectionsResponse, projectsResponse] = await Promise.all([
        collectionApi.list({ page: 0, size: 100 }),
        projectApi.list({ page: 0, size: 1 }),
      ])
      const collections = collectionsResponse.data.content
      overview.value = {
        pendingReviews: collections.filter((item) => item.reviewStatus === 'PENDING').length,
        rejectedCollections: collections.filter((item) => item.reviewStatus === 'PARTIALLY_REJECTED').length,
        readyToPublish: collections.filter((item) => item.publishStatus === 'READY').length,
        publishedCollections: collections.filter((item) => item.publishStatus === 'PUBLISHED').length,
        recentReviews: collections.slice(0, 5).map((item) => ({
          id: item.id,
          title: item.title,
          categoryName: item.category?.name || '未分类',
          reviewStatus: item.reviewStatus,
          publishStatus: item.publishStatus,
          updatedAt: item.updatedAt,
          totalPhotos: 0,
          pendingPhotos: 0,
          rejectedPhotos: 0,
          approvedPhotos: 0,
        })),
        projectCount: projectsResponse.data.totalElements,
      }
    }
  } catch (error) {
    errorMessage.value = apiErrorMessage(error, '运营数据加载失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="dashboard-content" v-loading="loading">
    <section class="metric-grid" aria-label="运营概览">
      <article v-for="metric in metrics" :key="metric.label" class="metric-item">
        <span :class="['metric-indicator', metric.tone]"></span>
        <p>{{ metric.label }}</p>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.note }}</small>
      </article>
    </section>

    <p v-if="errorMessage" class="dashboard-error">{{ errorMessage }}</p>

    <section class="dashboard-section">
      <div class="section-title-row">
        <div><p>{{ isAdmin ? 'Review queue' : 'My collections' }}</p><h2>{{ isAdmin ? '待处理内容' : '我的作品集状态' }}</h2></div>
        <RouterLink v-if="isAdmin" to="/reviews" class="section-action">进入审核中心<ChevronRight :size="17" /></RouterLink>
        <RouterLink v-else to="/collections" class="section-action">管理作品集<ChevronRight :size="17" /></RouterLink>
      </div>
      <div class="review-table">
        <div class="review-row review-head">
          <span>作品集</span><span>图片</span><span>更新时间</span><span>审核</span><span>发布</span><span></span>
        </div>
        <article v-for="item in overview.recentReviews" :key="item.id" class="review-row">
          <div>
            <strong>{{ item.title }}</strong>
            <small>{{ item.categoryName }}</small>
          </div>
          <span v-if="item.totalPhotos">{{ item.totalPhotos }} 张</span>
          <span v-else>打开查看</span>
          <span>{{ formatDateTime(item.submittedAt || item.updatedAt) }}</span>
          <span :class="['status-tag', statusTone(item.reviewStatus)]">
            <Clock3 v-if="item.reviewStatus === 'PENDING'" :size="14" />
            <ShieldCheck v-else :size="14" />
            {{ reviewStatusLabels[item.reviewStatus] || item.reviewStatus }}
          </span>
          <span :class="['status-tag', statusTone(item.publishStatus)]">
            <Rocket v-if="item.publishStatus === 'READY'" :size="14" />
            {{ publishStatusLabels[item.publishStatus] || item.publishStatus }}
          </span>
          <RouterLink
            v-if="isAdmin"
            :to="{ name: 'reviews' }"
            class="row-action"
            aria-label="查看审核详情"
            title="查看审核详情"
          ><ChevronRight :size="18" /></RouterLink>
          <RouterLink
            v-else
            :to="{ name: 'collections' }"
            class="row-action"
            aria-label="查看作品集"
            title="查看作品集"
          ><ChevronRight :size="18" /></RouterLink>
        </article>
        <div v-if="!loading && !overview.recentReviews.length" class="dashboard-empty">
          <ShieldCheck :size="22" />
          <span>暂无需要处理的内容</span>
        </div>
      </div>
    </section>

    <section class="dashboard-split">
      <div class="dashboard-section compact-section">
        <div class="section-title-row">
          <div><p>Workflow</p><h2>内容状态</h2></div>
          <button class="icon-command" type="button" aria-label="刷新运营数据" title="刷新运营数据" @click="loadOverview">
            <RefreshCw :size="17" />
          </button>
        </div>
        <div class="creator-summary">
          <div><span>待审核</span><strong>{{ overview.pendingReviews }}</strong></div>
          <div><span>可发布</span><strong>{{ overview.readyToPublish }}</strong></div>
          <div><span>已发布</span><strong>{{ overview.publishedCollections }}</strong></div>
        </div>
      </div>
      <div class="dashboard-section compact-section">
        <div class="section-title-row"><div><p>Next action</p><h2>下一步</h2></div></div>
        <div class="dashboard-next-action">
          <ShieldCheck :size="20" />
          <p>{{ isAdmin ? '处理待审核作品，审核通过后即可公开发布。' : '完成图片整理并提交审核，审核通过后会出现在官网。' }}</p>
        </div>
      </div>
    </section>
  </main>
</template>
