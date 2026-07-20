<script setup>
import { onMounted, ref } from 'vue'
import {
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  Star,
} from '@lucide/vue'
import { customerFeedbackApi } from '../../api/customer'

const loading = ref(false)
const errorMessage = ref('')
const feedback = ref([])
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const reviewStatus = ref('')

const reviewLabels = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
const publishLabels = { UNPUBLISHED: '未公开', PUBLISHED: '已公开', OFFLINE: '已下架' }

onMounted(loadFeedback)

async function loadFeedback() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await customerFeedbackApi.list({
      page: page.value,
      size: 12,
      reviewStatus: reviewStatus.value || undefined,
    })
    feedback.value = data.content || []
    totalPages.value = data.totalPages || 0
    totalElements.value = data.totalElements || 0
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '评价记录加载失败'
  } finally {
    loading.value = false
  }
}

function changePage(nextPage) {
  if (nextPage < 0 || nextPage >= totalPages.value) return
  page.value = nextPage
  loadFeedback()
}

function runFilter() {
  page.value = 0
  loadFeedback()
}

function formatDate(value) {
  return value ? new Intl.DateTimeFormat('zh-CN').format(new Date(value)) : ''
}
</script>

<template>
  <div class="customer-page">
    <section class="customer-page-heading">
      <div>
        <p>Client feedback</p>
        <h1>我的评价</h1>
      </div>
      <div class="customer-heading-actions">
        <button class="customer-icon-button" type="button" aria-label="刷新评价" title="刷新" @click="loadFeedback">
          <RefreshCw :size="18" />
        </button>
      </div>
    </section>

    <section class="customer-summary-strip customer-feedback-summary" aria-label="评价概览">
      <div><span>评价总数</span><strong>{{ totalElements }}</strong></div>
      <div>
        <span>提交方式</span>
        <strong>团队代录</strong>
      </div>
    </section>

    <section class="customer-context-notice">
      项目关联和客户自助评价已下线。如需补充评价，请联系团队，由管理员按作品集代为提交。
    </section>

    <section class="customer-section">
      <div class="customer-section-heading customer-feedback-toolbar">
        <div>
          <h2>评价记录</h2>
          <p>这里展示与你账号关联的评价审核和公开状态。</p>
        </div>
        <select v-model="reviewStatus" aria-label="筛选审核状态" @change="runFilter">
          <option value="">全部审核状态</option>
          <option value="PENDING">待审核</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option>
        </select>
      </div>

      <p v-if="errorMessage" class="customer-form-message error" role="alert">{{ errorMessage }}</p>
      <div v-if="loading" class="customer-empty-state">正在加载评价记录...</div>
      <div v-else-if="feedback.length" class="customer-record-list">
        <article v-for="item in feedback" :key="item.id" class="customer-feedback-record">
          <div class="customer-feedback-record-head">
            <div>
              <span>{{ item.collection?.eventDate || '' }}</span>
              <h3>{{ item.collection?.title || '作品集内容暂不展示' }} · {{ item.creator?.displayName || '创作者' }}</h3>
            </div>
            <div class="customer-feedback-rating" :aria-label="`${item.rating} 星`">
              <Star v-for="index in 5" :key="index" :size="15" :class="{ filled: index <= item.rating }" />
            </div>
          </div>
          <p>{{ item.content }}</p>
          <div class="customer-feedback-status">
            <span :class="['customer-state-chip', item.reviewStatus.toLowerCase()]">
              {{ reviewLabels[item.reviewStatus] }}
            </span>
            <span :class="['customer-state-chip', item.publishStatus.toLowerCase()]">
              {{ publishLabels[item.publishStatus] }}
            </span>
            <time>{{ formatDate(item.createdAt) }}</time>
          </div>
          <p v-if="item.rejectionReason" class="customer-rejection-reason">驳回原因：{{ item.rejectionReason }}</p>
          <p v-if="item.offlineReason" class="customer-rejection-reason">下架原因：{{ item.offlineReason }}</p>
        </article>
      </div>
      <div v-else class="customer-empty-state">暂无符合条件的评价记录</div>

      <nav v-if="totalPages > 1" class="customer-pagination" aria-label="评价分页">
        <button type="button" aria-label="上一页" :disabled="page === 0" @click="changePage(page - 1)">
          <ChevronLeft :size="18" />
        </button>
        <span>{{ page + 1 }} / {{ totalPages }}</span>
        <button type="button" aria-label="下一页" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">
          <ChevronRight :size="18" />
        </button>
      </nav>
    </section>
  </div>
</template>
