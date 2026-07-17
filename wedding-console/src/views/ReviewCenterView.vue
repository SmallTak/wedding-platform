<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check,
  Eye,
  RefreshCw,
  Rocket,
  Search,
  ShieldCheck,
  Undo2,
  X,
} from '@lucide/vue'
import { reviewApi } from '../api/content'
import {
  apiErrorMessage,
  formatDateTime,
  publishStatusLabels,
  reviewStatusLabels,
  statusTone,
} from '../utils/content'

const loading = ref(false)
const detailLoading = ref(false)
const mutating = ref(false)
const queue = ref([])
const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const detailVisible = ref(false)
const detail = ref(null)
const selectedPhotoIds = ref([])

const filters = reactive({
  keyword: '',
  reviewStatus: null,
  publishStatus: null,
})

const pendingPhotos = computed(() => detail.value?.photoBatch.photos.filter(
  (photo) => photo.reviewStatus === 'PENDING',
) || [])
const selectedPendingCount = computed(() => selectedPhotoIds.value.filter(
  (id) => pendingPhotos.value.some((photo) => photo.id === id),
).length)

onMounted(loadQueue)

async function loadQueue() {
  loading.value = true
  try {
    const { data } = await reviewApi.list({
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim() || undefined,
      reviewStatus: filters.reviewStatus || undefined,
      publishStatus: filters.publishStatus || undefined,
    })
    queue.value = data.content
    totalElements.value = data.totalElements
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '审核队列加载失败'))
  } finally {
    loading.value = false
  }
}

function runSearch() {
  page.value = 0
  loadQueue()
}

function resetFilters() {
  Object.assign(filters, { keyword: '', reviewStatus: null, publishStatus: null })
  page.value = 0
  loadQueue()
}

function changePage(value) {
  page.value = value - 1
  loadQueue()
}

async function openDetail(item) {
  detailVisible.value = true
  detailLoading.value = true
  selectedPhotoIds.value = []
  try {
    const { data } = await reviewApi.get(item.id)
    detail.value = data
  } catch (error) {
    detailVisible.value = false
    ElMessage.error(apiErrorMessage(error, '审核详情加载失败'))
  } finally {
    detailLoading.value = false
  }
}

function applyDetail(data) {
  detail.value = data
  selectedPhotoIds.value = []
}

function togglePendingPhotos() {
  selectedPhotoIds.value = selectedPendingCount.value === pendingPhotos.value.length
    ? []
    : pendingPhotos.value.map((photo) => photo.id)
}

async function reviewSelected(decision) {
  if (!selectedPendingCount.value || !detail.value) return
  let reason = null
  if (decision === 'REJECT') {
    try {
      const result = await ElMessageBox.prompt('填写本批图片的驳回原因', '批量驳回', {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: (value) => Boolean(value?.trim()) || '请输入驳回原因',
      })
      reason = result.value.trim()
    } catch {
      return
    }
  }
  mutating.value = true
  try {
    const { data } = await reviewApi.reviewPhotos(detail.value.collection.id, {
      version: detail.value.collection.version,
      photoIds: selectedPhotoIds.value,
      decision,
      reason,
    })
    applyDetail(data)
    ElMessage.success(decision === 'APPROVE' ? '所选图片已通过' : '所选图片已驳回')
    await loadQueue()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '图片审核失败'))
    await reloadDetail()
  } finally {
    mutating.value = false
  }
}

async function approveCollection() {
  if (!detail.value) return
  mutating.value = true
  try {
    const { data } = await reviewApi.approve(
      detail.value.collection.id,
      detail.value.collection.version,
    )
    applyDetail(data)
    ElMessage.success('作品集已通过，当前可发布')
    await loadQueue()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '作品集通过失败'))
    await reloadDetail()
  } finally {
    mutating.value = false
  }
}

async function rejectCollection() {
  if (!detail.value) return
  let reason
  try {
    const result = await ElMessageBox.prompt('填写作品集资料的驳回原因', '驳回作品集', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (value) => Boolean(value?.trim()) || '请输入驳回原因',
    })
    reason = result.value.trim()
  } catch {
    return
  }
  await runCollectionMutation(
    () => reviewApi.reject(detail.value.collection.id, {
      version: detail.value.collection.version,
      reason,
    }),
    '作品集已驳回',
    '作品集驳回失败',
  )
}

async function publishCollection() {
  if (!detail.value) return
  let form
  try {
    const result = await ElMessageBox.confirm(
      '发布后官网将立即展示该作品集，创作者内容编辑会被锁定。',
      '发布作品集',
      { confirmButtonText: '公开发布', cancelButtonText: '取消', type: 'warning' },
    )
    form = result
  } catch {
    return
  }
  if (!form) return
  await runCollectionMutation(
    () => reviewApi.publish(detail.value.collection.id, {
      version: detail.value.collection.version,
      visibility: 'PUBLIC',
      featured: detail.value.collection.featured || false,
      pinned: detail.value.collection.pinned || false,
      sortOrder: detail.value.collection.sortOrder || 0,
    }),
    '作品集已公开发布',
    '作品集发布失败',
  )
}

async function offlineCollection() {
  if (!detail.value) return
  let reason
  try {
    const result = await ElMessageBox.prompt('填写下架原因', '下架作品集', {
      confirmButtonText: '确认下架',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (value) => Boolean(value?.trim()) || '请输入下架原因',
    })
    reason = result.value.trim()
  } catch {
    return
  }
  await runCollectionMutation(
    () => reviewApi.offline(detail.value.collection.id, {
      version: detail.value.collection.version,
      reason,
    }),
    '作品集已下架',
    '作品集下架失败',
  )
}

async function runCollectionMutation(request, successMessage, fallback) {
  mutating.value = true
  try {
    const { data } = await request()
    applyDetail(data)
    ElMessage.success(successMessage)
    await loadQueue()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, fallback))
    await reloadDetail()
  } finally {
    mutating.value = false
  }
}

async function reloadDetail() {
  if (!detail.value) return
  try {
    const { data } = await reviewApi.get(detail.value.collection.id)
    applyDetail(data)
  } catch {
    detailVisible.value = false
  }
}
</script>

<template>
  <main class="dashboard-content review-center-content" v-loading="loading">
    <section class="management-summary" aria-label="审核概览">
      <div><span>队列内容</span><strong>{{ totalElements }}</strong></div>
      <div><span>本页待审核</span><strong>{{ queue.filter((item) => item.reviewStatus === 'PENDING').length }}</strong></div>
      <div><span>本页可发布</span><strong>{{ queue.filter((item) => item.publishStatus === 'READY').length }}</strong></div>
    </section>

    <section class="dashboard-section management-panel">
      <form class="management-toolbar review-toolbar" role="search" @submit.prevent="runSearch">
        <div class="table-search">
          <Search :size="17" />
          <input v-model="filters.keyword" type="search" placeholder="搜索作品集" />
        </div>
        <el-select v-model="filters.reviewStatus" clearable placeholder="全部审核状态">
          <el-option label="待审核" value="PENDING" />
          <el-option label="部分驳回" value="PARTIALLY_REJECTED" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="草稿" value="DRAFT" />
        </el-select>
        <el-select v-model="filters.publishStatus" clearable placeholder="全部发布状态">
          <el-option label="未发布" value="UNPUBLISHED" />
          <el-option label="可发布" value="READY" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下架" value="OFFLINE" />
        </el-select>
        <button class="icon-command" type="button" aria-label="重置筛选" title="重置筛选" @click="resetFilters">
          <RefreshCw :size="17" />
        </button>
      </form>

      <div class="management-table review-management-table" role="table" aria-label="作品审核队列">
        <div class="management-row management-table-head" role="row">
          <span>作品集</span><span>图片状态</span><span>提交时间</span><span>审核</span><span>发布</span><span>操作</span>
        </div>
        <article v-for="item in queue" :key="item.id" class="management-row" role="row">
          <div class="primary-cell review-title-cell" data-label="作品集">
            <img v-if="item.coverThumbnailUrl" :src="item.coverThumbnailUrl" alt="" />
            <span v-else class="collection-cover-placeholder"><ShieldCheck :size="18" /></span>
            <div><strong>{{ item.title }}</strong><small>{{ item.categoryName }}</small></div>
          </div>
          <div class="review-photo-counts" data-label="图片状态">
            <span>{{ item.totalPhotos }} 张</span>
            <small>{{ item.pendingPhotos }} 待审 · {{ item.rejectedPhotos }} 驳回 · {{ item.approvedPhotos }} 通过</small>
          </div>
          <span data-label="提交时间">{{ formatDateTime(item.submittedAt || item.updatedAt) }}</span>
          <span data-label="审核" :class="['state-chip', statusTone(item.reviewStatus)]">
            {{ reviewStatusLabels[item.reviewStatus] || item.reviewStatus }}
          </span>
          <span data-label="发布" :class="['state-chip', statusTone(item.publishStatus)]">
            {{ publishStatusLabels[item.publishStatus] || item.publishStatus }}
          </span>
          <div class="row-commands" data-label="操作">
            <button type="button" aria-label="打开审核" title="打开审核" @click="openDetail(item)">
              <Eye :size="16" />
            </button>
          </div>
        </article>
        <div v-if="!loading && queue.length === 0" class="empty-table">暂无符合条件的审核内容</div>
      </div>

      <el-pagination
        v-if="totalElements > size"
        class="management-pagination"
        background
        layout="prev, pager, next"
        :current-page="page + 1"
        :page-size="size"
        :total="totalElements"
        @current-change="changePage"
      />
    </section>

    <el-drawer v-model="detailVisible" size="min(960px, 92vw)" class="review-drawer" destroy-on-close>
      <template #header>
        <div class="review-drawer-heading">
          <div><small>{{ detail?.collection.category?.name }}</small><h2>{{ detail?.collection.title || '审核详情' }}</h2></div>
          <div v-if="detail" class="photo-heading-states">
            <span :class="['state-chip', statusTone(detail.collection.reviewStatus)]">
              {{ reviewStatusLabels[detail.collection.reviewStatus] }}
            </span>
            <span :class="['state-chip', statusTone(detail.collection.publishStatus)]">
              {{ publishStatusLabels[detail.collection.publishStatus] }}
            </span>
          </div>
        </div>
      </template>

      <div v-loading="detailLoading" class="review-detail">
        <template v-if="detail">
          <section class="review-metadata-band">
            <div><span>图片</span><strong>{{ detail.photoBatch.photos.length }}</strong></div>
            <div><span>待审核</span><strong>{{ pendingPhotos.length }}</strong></div>
            <div><span>版本</span><strong>{{ detail.collection.version }}</strong></div>
            <p v-if="detail.collection.rejectionReason">{{ detail.collection.rejectionReason }}</p>
          </section>

          <section class="review-action-bar">
            <div>
              <button class="secondary-command" type="button" :disabled="!pendingPhotos.length" @click="togglePendingPhotos">
                {{ selectedPendingCount === pendingPhotos.length && pendingPhotos.length ? '取消全选' : '全选待审图片' }}
              </button>
              <span>已选 {{ selectedPendingCount }} 张</span>
            </div>
            <div>
              <button class="secondary-command" type="button" :disabled="!selectedPendingCount || mutating" @click="reviewSelected('REJECT')">
                <X :size="16" />驳回图片
              </button>
              <button class="primary-command compact-command" type="button" :disabled="!selectedPendingCount || mutating" @click="reviewSelected('APPROVE')">
                <Check :size="16" />通过图片
              </button>
            </div>
          </section>

          <section class="review-photo-grid">
            <label
              v-for="photo in detail.photoBatch.photos"
              :key="photo.id"
              :class="['review-photo-item', `review-${photo.reviewStatus.toLowerCase()}`]"
            >
              <input
                v-if="photo.reviewStatus === 'PENDING'"
                v-model="selectedPhotoIds"
                type="checkbox"
                :value="photo.id"
              />
              <img :src="photo.thumbnailUrl" :alt="photo.originalName" />
              <span>{{ reviewStatusLabels[photo.reviewStatus] || photo.reviewStatus }}</span>
              <small v-if="photo.rejectionReason">{{ photo.rejectionReason }}</small>
            </label>
          </section>

          <section class="review-publication-bar">
            <div>
              <strong>作品集处理</strong>
              <span>{{ detail.collection.description || '未填写作品集介绍' }}</span>
            </div>
            <div>
              <button
                v-if="detail.collection.publishStatus === 'PUBLISHED'"
                class="secondary-command"
                type="button"
                :disabled="mutating"
                @click="offlineCollection"
              ><Undo2 :size="16" />下架</button>
              <template v-else>
                <button
                  v-if="detail.collection.reviewStatus === 'PENDING'"
                  class="secondary-command"
                  type="button"
                  :disabled="mutating"
                  @click="rejectCollection"
                ><X :size="16" />驳回资料</button>
                <button
                  v-if="detail.collection.reviewStatus === 'PENDING'"
                  class="secondary-command"
                  type="button"
                  :disabled="pendingPhotos.length || mutating"
                  @click="approveCollection"
                ><Check :size="16" />通过作品集</button>
                <button
                  v-if="detail.collection.publishStatus === 'READY'"
                  class="primary-command compact-command"
                  type="button"
                  :disabled="mutating"
                  @click="publishCollection"
                ><Rocket :size="16" />公开发布</button>
              </template>
            </div>
          </section>
        </template>
      </div>
    </el-drawer>
  </main>
</template>
