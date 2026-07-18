<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check,
  Eye,
  FolderHeart,
  Images,
  RefreshCw,
  Rocket,
  Search,
  ShieldCheck,
  Undo2,
  X,
} from '@lucide/vue'
import { reviewApi } from '../api/content'
import PublicationDialog from '../components/PublicationDialog.vue'
import {
  apiErrorMessage,
  formatDateTime,
  publishStatusLabels,
  reviewItemStatusLabels,
  reviewStatusLabels,
  reviewTaskStatusLabels,
  statusTone,
} from '../utils/content'

const targetType = ref('COLLECTION')
const loading = ref(false)
const detailLoading = ref(false)
const mutating = ref(false)
const queue = ref([])
const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const detailVisible = ref(false)
const publicationDialogVisible = ref(false)
const detail = ref(null)
const selectedFieldIds = ref([])
const selectedPhotoIds = ref([])
const DEFAULT_REVIEW_STATUS = 'PENDING'

const filters = reactive({
  keyword: '',
  reviewStatus: DEFAULT_REVIEW_STATUS,
  publishStatus: null,
})

const isProject = computed(() => targetType.value === 'PROJECT')
const currentTarget = computed(() => (
  isProject.value ? detail.value?.project : detail.value?.collection
))
const currentHistory = computed(() => detail.value?.reviewHistory)
const fieldItems = computed(() => currentHistory.value?.currentItems.filter(
  (item) => item.itemType === 'FIELD',
) || [])
const pendingFields = computed(() => fieldItems.value.filter((item) => item.status === 'PENDING'))
const selectedFieldCount = computed(() => selectedFieldIds.value.filter(
  (id) => pendingFields.value.some((item) => item.id === id),
).length)
const pendingPhotos = computed(() => isProject.value
  ? []
  : detail.value?.photoBatch.photos.filter((photo) => photo.reviewStatus === 'PENDING') || [])
const selectedPhotoCount = computed(() => selectedPhotoIds.value.filter(
  (id) => pendingPhotos.value.some((photo) => photo.id === id),
).length)
const photosFullyApproved = computed(() => isProject.value
  || detail.value?.photoBatch.photos.every((photo) => photo.reviewStatus === 'APPROVED'))
const emptyQueueMessage = computed(() => (
  filters.reviewStatus === DEFAULT_REVIEW_STATUS
    && !filters.keyword.trim()
    && !filters.publishStatus
    ? '暂无待审核内容'
    : '暂无符合条件的审核内容'
))
const canPublishTarget = computed(() => (
  currentTarget.value?.publishStatus === 'READY'
    || (
      currentTarget.value?.publishStatus === 'OFFLINE'
      && currentTarget.value?.reviewStatus === 'APPROVED'
    )
))

onMounted(loadQueue)

watch(targetType, () => {
  page.value = 0
  detailVisible.value = false
  detail.value = null
  loadQueue()
})

async function loadQueue() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim() || undefined,
      reviewStatus: filters.reviewStatus || undefined,
      publishStatus: filters.publishStatus || undefined,
    }
    const { data } = isProject.value
      ? await reviewApi.listProjects(params)
      : await reviewApi.list(params)
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
  Object.assign(filters, {
    keyword: '',
    reviewStatus: DEFAULT_REVIEW_STATUS,
    publishStatus: null,
  })
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
  selectedFieldIds.value = []
  selectedPhotoIds.value = []
  try {
    const { data } = isProject.value
      ? await reviewApi.getProject(item.id)
      : await reviewApi.get(item.id)
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
  selectedFieldIds.value = []
  selectedPhotoIds.value = []
}

function togglePendingFields() {
  selectedFieldIds.value = selectedFieldCount.value === pendingFields.value.length
    ? []
    : pendingFields.value.map((item) => item.id)
}

function togglePendingPhotos() {
  selectedPhotoIds.value = selectedPhotoCount.value === pendingPhotos.value.length
    ? []
    : pendingPhotos.value.map((photo) => photo.id)
}

async function askReason(title, prompt) {
  try {
    const result = await ElMessageBox.prompt(prompt, title, {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (value) => Boolean(value?.trim()) || '请输入驳回原因',
    })
    return result.value.trim()
  } catch {
    return null
  }
}

async function reviewSelectedFields(decision) {
  if (!selectedFieldCount.value || !currentTarget.value) return
  const reason = decision === 'REJECT'
    ? await askReason('驳回字段', '填写所选字段的驳回原因')
    : null
  if (decision === 'REJECT' && !reason) return

  mutating.value = true
  try {
    const payload = {
      version: currentTarget.value.version,
      reviewItemIds: selectedFieldIds.value,
      decision,
      reason,
    }
    const { data } = isProject.value
      ? await reviewApi.reviewProjectFields(currentTarget.value.id, payload)
      : await reviewApi.reviewFields(currentTarget.value.id, payload)
    applyDetail(data)
    ElMessage.success(decision === 'APPROVE' ? '所选字段已通过' : '所选字段已驳回')
    await loadQueue()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '字段审核失败'))
    await reloadDetail()
  } finally {
    mutating.value = false
  }
}

async function reviewSelectedPhotos(decision) {
  if (!selectedPhotoCount.value || !detail.value?.collection) return
  const reason = decision === 'REJECT'
    ? await askReason('批量驳回', '填写本批图片的驳回原因')
    : null
  if (decision === 'REJECT' && !reason) return

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

async function approveRemainingFields() {
  if (!currentTarget.value) return
  await runTargetMutation(
    () => isProject.value
      ? reviewApi.approveProject(currentTarget.value.id, currentTarget.value.version)
      : reviewApi.approve(currentTarget.value.id, currentTarget.value.version),
    isProject.value ? '项目字段已全部通过' : '作品集已通过，当前可发布',
    '内容通过失败',
  )
}

async function rejectRemainingFields() {
  if (!currentTarget.value) return
  const reason = await askReason(
    isProject.value ? '驳回项目字段' : '驳回作品集字段',
    '填写全部剩余待审核字段的驳回原因',
  )
  if (!reason) return
  await runTargetMutation(
    () => isProject.value
      ? reviewApi.rejectProject(currentTarget.value.id, {
          version: currentTarget.value.version,
          reason,
        })
      : reviewApi.reject(currentTarget.value.id, {
          version: currentTarget.value.version,
          reason,
        }),
    '剩余待审核字段已驳回',
    '字段驳回失败',
  )
}

function publishTarget() {
  if (!currentTarget.value) return
  publicationDialogVisible.value = true
}

async function confirmPublication(settings) {
  if (!currentTarget.value) return
  const republishing = currentTarget.value.publishStatus === 'OFFLINE'
  await runTargetMutation(
    () => isProject.value
      ? reviewApi.publishProject(currentTarget.value.id, {
          version: currentTarget.value.version,
          ...settings,
        })
      : reviewApi.publish(currentTarget.value.id, {
          version: currentTarget.value.version,
          ...settings,
          featured: currentTarget.value.featured || false,
          pinned: currentTarget.value.pinned || false,
          sortOrder: currentTarget.value.sortOrder || 0,
        }),
    isProject.value
      ? (republishing ? '婚礼项目已重新上架' : '婚礼项目已发布')
      : (republishing ? '作品集已重新上架' : '作品集已发布'),
    isProject.value ? '婚礼项目发布失败' : '作品集发布失败',
  )
  if (currentTarget.value?.publishStatus === 'PUBLISHED') {
    publicationDialogVisible.value = false
  }
}

async function offlineTarget() {
  if (!currentTarget.value) return
  let reason
  try {
    const result = await ElMessageBox.prompt(
      '填写下架原因',
      isProject.value ? '下架婚礼项目' : '下架作品集',
      {
        confirmButtonText: '确认下架',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: (value) => Boolean(value?.trim()) || '请输入下架原因',
      },
    )
    reason = result.value.trim()
  } catch {
    return
  }
  await runTargetMutation(
    () => isProject.value
      ? reviewApi.offlineProject(currentTarget.value.id, {
          version: currentTarget.value.version,
          reason,
        })
      : reviewApi.offline(currentTarget.value.id, {
          version: currentTarget.value.version,
          reason,
        }),
    isProject.value ? '婚礼项目已下架' : '作品集已下架',
    isProject.value ? '婚礼项目下架失败' : '作品集下架失败',
  )
}

async function runTargetMutation(request, successMessage, fallback) {
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
  if (!currentTarget.value) return
  try {
    const { data } = isProject.value
      ? await reviewApi.getProject(currentTarget.value.id)
      : await reviewApi.get(currentTarget.value.id)
    applyDetail(data)
  } catch {
    detailVisible.value = false
  }
}

function canPublish(item) {
  return item.publishStatus === 'READY'
    || (item.publishStatus === 'OFFLINE' && item.reviewStatus === 'APPROVED')
}
</script>

<template>
  <main class="dashboard-content review-center-content" v-loading="loading">
    <section class="management-summary" aria-label="审核概览">
      <div><span>队列内容</span><strong>{{ totalElements }}</strong></div>
      <div><span>本页待审核</span><strong>{{ queue.filter((item) => item.reviewStatus === 'PENDING').length }}</strong></div>
      <div><span>本页可发布</span><strong>{{ queue.filter(canPublish).length }}</strong></div>
    </section>

    <section class="dashboard-section management-panel">
      <div class="review-target-switch" role="tablist" aria-label="审核内容类型">
        <button
          type="button"
          :class="{ active: targetType === 'COLLECTION' }"
          @click="targetType = 'COLLECTION'"
        >
          <Images :size="16" />作品集
        </button>
        <button
          type="button"
          :class="{ active: targetType === 'PROJECT' }"
          @click="targetType = 'PROJECT'"
        >
          <FolderHeart :size="16" />婚礼项目
        </button>
      </div>

      <form class="management-toolbar review-toolbar" role="search" @submit.prevent="runSearch">
        <div class="table-search">
          <Search :size="17" />
          <input v-model="filters.keyword" type="search" :placeholder="isProject ? '搜索婚礼项目' : '搜索作品集'" />
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

      <div class="management-table review-management-table" role="table" aria-label="内容审核队列">
        <div class="management-row management-table-head" role="row">
          <span>{{ isProject ? '婚礼项目' : '作品集' }}</span>
          <span>字段状态</span>
          <span>{{ isProject ? '地点' : '图片状态' }}</span>
          <span>提交时间</span>
          <span>审核</span>
          <span>发布</span>
          <span>操作</span>
        </div>
        <article v-for="item in queue" :key="item.id" class="management-row" role="row">
          <div class="primary-cell review-title-cell" :data-label="isProject ? '婚礼项目' : '作品集'">
            <template v-if="!isProject">
              <img v-if="item.coverThumbnailUrl" :src="item.coverThumbnailUrl" alt="" />
              <span v-else class="collection-cover-placeholder"><ShieldCheck :size="18" /></span>
            </template>
            <span v-else class="collection-cover-placeholder"><FolderHeart :size="18" /></span>
            <div>
              <strong>{{ item.title }}</strong>
              <small>{{ isProject ? item.projectCode : item.categoryName }}</small>
            </div>
          </div>
          <div class="review-photo-counts" data-label="字段状态">
            <span>{{ item.pendingFields }} 待审</span>
            <small>{{ item.rejectedFields }} 驳回 · {{ item.approvedFields }} 通过</small>
          </div>
          <div class="review-photo-counts" :data-label="isProject ? '地点' : '图片状态'">
            <template v-if="isProject">
              <span>{{ item.locationText }}</span>
            </template>
            <template v-else>
              <span>{{ item.totalPhotos }} 张</span>
              <small>{{ item.pendingPhotos }} 待审 · {{ item.rejectedPhotos }} 驳回 · {{ item.approvedPhotos }} 通过</small>
            </template>
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
        <div v-if="!loading && queue.length === 0" class="empty-table">{{ emptyQueueMessage }}</div>
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

    <el-drawer v-model="detailVisible" size="min(1040px, 94vw)" class="review-drawer" destroy-on-close>
      <template #header>
        <div class="review-drawer-heading">
          <div>
            <small>{{ isProject ? detail?.project.projectCode : detail?.collection.category?.name }}</small>
            <h2>{{ currentTarget?.title || '审核详情' }}</h2>
          </div>
          <div v-if="currentTarget" class="photo-heading-states">
            <span :class="['state-chip', statusTone(currentTarget.reviewStatus)]">
              {{ reviewStatusLabels[currentTarget.reviewStatus] }}
            </span>
            <span :class="['state-chip', statusTone(currentTarget.publishStatus)]">
              {{ publishStatusLabels[currentTarget.publishStatus] }}
            </span>
          </div>
        </div>
      </template>

      <div v-loading="detailLoading" class="review-detail">
        <template v-if="currentTarget">
          <section class="review-metadata-band">
            <div><span>字段项</span><strong>{{ fieldItems.length }}</strong></div>
            <div><span>待审核</span><strong>{{ pendingFields.length + pendingPhotos.length }}</strong></div>
            <div><span>内容版本</span><strong>{{ currentTarget.version }}</strong></div>
            <p v-if="currentTarget.rejectionReason">{{ currentTarget.rejectionReason }}</p>
          </section>

          <section class="review-section-heading">
            <div><strong>字段审核</strong><span>当前生效版本</span></div>
            <div>
              <button class="secondary-command" type="button" :disabled="!pendingFields.length" @click="togglePendingFields">
                {{ selectedFieldCount === pendingFields.length && pendingFields.length ? '取消全选' : '全选待审字段' }}
              </button>
              <button class="secondary-command" type="button" :disabled="!selectedFieldCount || mutating" @click="reviewSelectedFields('REJECT')">
                <X :size="16" />驳回字段
              </button>
              <button class="primary-command compact-command" type="button" :disabled="!selectedFieldCount || mutating" @click="reviewSelectedFields('APPROVE')">
                <Check :size="16" />通过字段
              </button>
            </div>
          </section>

          <section class="field-review-list admin-field-review-list">
            <label
              v-for="item in fieldItems"
              :key="item.id"
              :class="['field-review-item', { 'has-field-checkbox': item.status === 'PENDING' }]"
            >
              <input
                v-if="item.status === 'PENDING'"
                v-model="selectedFieldIds"
                type="checkbox"
                :value="item.id"
              />
              <div>
                <strong>{{ item.fieldLabel }}</strong>
                <span>{{ item.displayValue }}</span>
                <small v-if="item.rejectionReason">{{ item.rejectionReason }}</small>
              </div>
              <span :class="['state-chip', statusTone(item.status)]">
                {{ reviewItemStatusLabels[item.status] || item.status }}
              </span>
            </label>
          </section>

          <template v-if="!isProject">
            <section class="review-section-heading">
              <div><strong>图片审核</strong><span>已选 {{ selectedPhotoCount }} 张</span></div>
              <div>
                <button class="secondary-command" type="button" :disabled="!pendingPhotos.length" @click="togglePendingPhotos">
                  {{ selectedPhotoCount === pendingPhotos.length && pendingPhotos.length ? '取消全选' : '全选待审图片' }}
                </button>
                <button class="secondary-command" type="button" :disabled="!selectedPhotoCount || mutating" @click="reviewSelectedPhotos('REJECT')">
                  <X :size="16" />驳回图片
                </button>
                <button class="primary-command compact-command" type="button" :disabled="!selectedPhotoCount || mutating" @click="reviewSelectedPhotos('APPROVE')">
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
          </template>

          <section v-if="currentHistory?.revisions.length" class="review-history-section">
            <h3>修订记录</h3>
            <el-collapse class="review-revision-list">
              <el-collapse-item
                v-for="revision in currentHistory.revisions"
                :key="revision.id"
                :name="revision.id"
              >
                <template #title>
                  <div class="review-revision-title">
                    <strong>修订 {{ revision.revisionNo }}</strong>
                    <span>{{ formatDateTime(revision.submittedAt) }}</span>
                    <i :class="['state-chip', statusTone(revision.status)]">
                      {{ reviewTaskStatusLabels[revision.status] || revision.status }}
                    </i>
                  </div>
                </template>
                <div class="revision-item-list">
                  <div v-for="item in revision.items" :key="item.id">
                    <span>{{ item.fieldLabel }}</span>
                    <strong>{{ item.displayValue }}</strong>
                    <small>{{ reviewItemStatusLabels[item.status] || item.status }}</small>
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </section>

          <section class="review-publication-bar">
            <div>
              <strong>{{ isProject ? '项目处理' : '作品集处理' }}</strong>
              <span>{{ currentTarget.description || '未填写内容介绍' }}</span>
            </div>
            <div>
              <button
                v-if="currentTarget.publishStatus === 'PUBLISHED'"
                class="secondary-command"
                type="button"
                :disabled="mutating"
                @click="offlineTarget"
              ><Undo2 :size="16" />下架</button>
              <template v-else>
                <button
                  v-if="pendingFields.length"
                  class="secondary-command"
                  type="button"
                  :disabled="mutating"
                  @click="rejectRemainingFields"
                ><X :size="16" />驳回剩余字段</button>
                <button
                  v-if="pendingFields.length"
                  class="secondary-command"
                  type="button"
                  :disabled="!photosFullyApproved || mutating"
                  @click="approveRemainingFields"
                ><Check :size="16" />通过剩余字段</button>
                <button
                  v-if="canPublishTarget"
                  class="primary-command compact-command"
                  type="button"
                  :disabled="mutating"
                  @click="publishTarget"
                ><Rocket :size="16" />{{ currentTarget.publishStatus === 'OFFLINE' ? '重新上架' : '公开发布' }}</button>
              </template>
            </div>
          </section>
        </template>
      </div>
    </el-drawer>

    <PublicationDialog
      v-model="publicationDialogVisible"
      :target-label="isProject ? '婚礼项目' : '作品集'"
      :action-label="currentTarget?.publishStatus === 'OFFLINE' ? '重新上架' : '发布'"
      :loading="mutating"
      @submit="confirmPublication"
    />
  </main>
</template>
