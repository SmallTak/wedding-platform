<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  ImagePlus,
  Images,
  RefreshCw,
  Save,
  Send,
  Star,
  Trash2,
  Upload,
} from '@lucide/vue'
import { collectionApi, photoApi } from '../api/content'
import {
  apiErrorMessage,
  formatDateTime,
  formatFileSize,
  isVersionConflict,
  publishStatusLabels,
  reviewStatusLabels,
  statusTone,
} from '../utils/content'

const route = useRoute()
const router = useRouter()
const collectionId = Number(route.params.collectionId)
const loading = ref(false)
const uploading = ref(false)
const savingOrder = ref(false)
const mutationId = ref(null)
const submitting = ref(false)
const uploadProgress = ref(0)
const collection = ref(null)
const photos = ref([])
const collectionVersion = ref(null)
const coverPhotoId = ref(null)
const orderDirty = ref(false)
const fileInput = ref(null)

const publishedLocked = computed(() => collection.value?.publishStatus === 'PUBLISHED')
const previewList = computed(() => photos.value.map((photo) => photo.previewUrl))
const totalSize = computed(() => photos.value.reduce((sum, photo) => sum + photo.fileSize, 0))
const canSubmit = computed(() =>
  !publishedLocked.value
  && !orderDirty.value
  && photos.value.length > 0
  && Boolean(coverPhotoId.value)
  && !['PENDING', 'APPROVED'].includes(collection.value?.reviewStatus),
)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [collectionResponse, photoResponse] = await Promise.all([
      collectionApi.get(collectionId),
      photoApi.list(collectionId),
    ])
    collection.value = collectionResponse.data
    applyBatch(photoResponse.data)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '作品集图片加载失败'))
  } finally {
    loading.value = false
  }
}

function applyBatch(batch) {
  photos.value = batch.photos
  collectionVersion.value = batch.collectionVersion
  coverPhotoId.value = batch.coverPhotoId
  if (collection.value) {
    collection.value.version = batch.collectionVersion
    collection.value.coverPhotoId = batch.coverPhotoId
  }
  orderDirty.value = false
}

function openFilePicker() {
  if (publishedLocked.value) {
    ElMessage.warning('已发布作品集不能继续上传图片')
    return
  }
  if (orderDirty.value) {
    ElMessage.warning('请先保存或放弃当前图片排序')
    return
  }
  fileInput.value?.click()
}

async function handleFileSelection(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  if (files.length > 50) {
    ElMessage.warning('单次最多上传 50 张图片')
    return
  }
  if (files.some((file) => file.size > 30 * 1024 * 1024)) {
    ElMessage.warning('单张图片不能超过 30 MB')
    return
  }

  uploading.value = true
  uploadProgress.value = 0
  try {
    const { data } = await photoApi.upload(collectionId, files, (progressEvent) => {
      if (progressEvent.total) {
        uploadProgress.value = Math.min(99, Math.round((progressEvent.loaded / progressEvent.total) * 100))
      }
    })
    uploadProgress.value = 100
    applyBatch(data)
    ElMessage.success(`已上传 ${files.length} 张图片`)
  } catch (error) {
    await handleMutationError(error, '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function movePhoto(index, offset) {
  const target = index + offset
  if (target < 0 || target >= photos.value.length) return
  const next = [...photos.value]
  const [photo] = next.splice(index, 1)
  next.splice(target, 0, photo)
  photos.value = next
  orderDirty.value = true
}

async function savePhotoOrder() {
  savingOrder.value = true
  try {
    const { data } = await photoApi.reorder(collectionId, {
      version: collectionVersion.value,
      photoIds: photos.value.map((photo) => photo.id),
    })
    applyBatch(data)
    ElMessage.success('图片顺序已保存')
  } catch (error) {
    await handleMutationError(error, '图片排序保存失败')
  } finally {
    savingOrder.value = false
  }
}

async function discardOrder() {
  try {
    const { data } = await photoApi.list(collectionId)
    applyBatch(data)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '图片顺序重新加载失败'))
  }
}

async function setCover(photo) {
  if (orderDirty.value) {
    ElMessage.warning('请先保存或放弃当前图片排序')
    return
  }
  mutationId.value = photo.id
  try {
    const { data } = await photoApi.setCover(collectionId, {
      version: collectionVersion.value,
      photoId: photo.id,
    })
    applyBatch(data)
    ElMessage.success('作品集封面已更新')
  } catch (error) {
    await handleMutationError(error, '封面设置失败')
  } finally {
    mutationId.value = null
  }
}

async function deletePhoto(photo) {
  if (orderDirty.value) {
    ElMessage.warning('请先保存或放弃当前图片排序')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定移除“${photo.originalName}”吗？数据库将逻辑删除该图片，物理文件会保留。`,
      '移除作品图片',
      {
        confirmButtonText: '确认移除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  mutationId.value = photo.id
  try {
    const { data } = await photoApi.delete(collectionId, photo.id, collectionVersion.value)
    applyBatch(data)
    ElMessage.success('图片已从作品集中移除')
  } catch (error) {
    await handleMutationError(error, '图片移除失败')
  } finally {
    mutationId.value = null
  }
}

async function submitForReview() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const { data } = await collectionApi.submit(collectionId, collectionVersion.value)
    collection.value = data.collection
    applyBatch(data.photoBatch)
    ElMessage.success('作品集已提交审核')
  } catch (error) {
    await handleMutationError(error, '提交审核失败')
  } finally {
    submitting.value = false
  }
}

async function handleMutationError(error, fallback) {
  if (isVersionConflict(error)) {
    await loadData()
  }
  ElMessage.error(apiErrorMessage(error, fallback))
}

function goBack() {
  router.push({ name: 'collections' })
}
</script>

<template>
  <main class="dashboard-content photo-management-content" v-loading="loading">
    <section class="photo-page-heading">
      <button class="icon-command" type="button" aria-label="返回作品集" title="返回作品集" @click="goBack">
        <ArrowLeft :size="18" />
      </button>
      <div>
        <p>{{ collection?.project?.title || '独立作品集' }}</p>
        <h2>{{ collection?.title || '图片管理' }}</h2>
      </div>
      <div v-if="collection" class="photo-heading-states">
        <span :class="['state-chip', statusTone(collection.reviewStatus)]">
          {{ reviewStatusLabels[collection.reviewStatus] || collection.reviewStatus }}
        </span>
        <span :class="['state-chip', statusTone(collection.publishStatus)]">
          {{ publishStatusLabels[collection.publishStatus] || collection.publishStatus }}
        </span>
      </div>
    </section>

    <section class="photo-toolbar-band">
      <div class="photo-stats">
        <div><span>图片</span><strong>{{ photos.length }}</strong></div>
        <div><span>总大小</span><strong>{{ formatFileSize(totalSize) }}</strong></div>
        <div><span>作品集版本</span><strong>{{ collectionVersion ?? '-' }}</strong></div>
      </div>
      <div class="toolbar-commands">
        <button
          class="secondary-command"
          type="button"
          :disabled="!canSubmit || submitting"
          @click="submitForReview"
        >
          <Send :size="16" />{{ submitting ? '正在提交' : '提交审核' }}
        </button>
        <button
          v-if="orderDirty"
          class="secondary-command"
          type="button"
          :disabled="savingOrder"
          @click="discardOrder"
        >
          <RefreshCw :size="16" />放弃排序
        </button>
        <button
          v-if="orderDirty"
          class="primary-command compact-command"
          type="button"
          :disabled="savingOrder"
          @click="savePhotoOrder"
        >
          <Save :size="16" />保存排序
        </button>
        <button
          class="primary-command compact-command"
          type="button"
          :disabled="uploading || publishedLocked"
          @click="openFilePicker"
        >
          <Upload :size="16" />{{ uploading ? '正在上传' : '上传图片' }}
        </button>
        <input
          ref="fileInput"
          class="visually-hidden"
          type="file"
          accept="image/jpeg,image/png"
          multiple
          @change="handleFileSelection"
        />
      </div>
    </section>

    <el-progress
      v-if="uploading"
      class="upload-progress"
      :percentage="uploadProgress"
      :stroke-width="7"
      :show-text="true"
    />

    <div v-if="publishedLocked" class="locked-notice">
      当前作品集已发布，图片上传、排序、封面和删除操作已锁定。
    </div>

    <section v-if="photos.length" class="photo-grid" aria-label="作品集图片">
      <article
        v-for="(photo, index) in photos"
        :key="photo.id"
        class="photo-item"
        :class="{ cover: photo.id === coverPhotoId }"
      >
        <div class="photo-preview">
          <el-image
            :src="photo.thumbnailUrl"
            :preview-src-list="previewList"
            :initial-index="index"
            fit="cover"
            preview-teleported
            hide-on-click-modal
          >
            <template #error><Images :size="28" /></template>
          </el-image>
          <span v-if="photo.id === coverPhotoId" class="cover-label"><Star :size="12" fill="currentColor" />封面</span>
          <span class="photo-index">{{ index + 1 }}</span>
          <span :class="['photo-review-label', statusTone(photo.reviewStatus)]">
            {{ reviewStatusLabels[photo.reviewStatus] || photo.reviewStatus }}
          </span>
        </div>
        <div class="photo-meta">
          <strong :title="photo.originalName">{{ photo.originalName }}</strong>
          <span>{{ photo.width }} × {{ photo.height }} · {{ formatFileSize(photo.fileSize) }}</span>
          <small>{{ formatDateTime(photo.createdAt) }}</small>
          <small v-if="photo.rejectionReason" class="photo-rejection">{{ photo.rejectionReason }}</small>
        </div>
        <div class="photo-commands">
          <button
            type="button"
            aria-label="上移"
            title="上移"
            :disabled="index === 0 || publishedLocked || uploading"
            @click="movePhoto(index, -1)"
          >
            <ArrowUp :size="16" />
          </button>
          <button
            type="button"
            aria-label="下移"
            title="下移"
            :disabled="index === photos.length - 1 || publishedLocked || uploading"
            @click="movePhoto(index, 1)"
          >
            <ArrowDown :size="16" />
          </button>
          <button
            type="button"
            aria-label="设为封面"
            title="设为封面"
            :disabled="photo.id === coverPhotoId || publishedLocked || uploading || mutationId === photo.id"
            @click="setCover(photo)"
          >
            <Star :size="16" />
          </button>
          <button
            class="danger-command"
            type="button"
            aria-label="移除图片"
            title="移除图片"
            :disabled="publishedLocked || uploading || mutationId === photo.id"
            @click="deletePhoto(photo)"
          >
            <Trash2 :size="16" />
          </button>
        </div>
      </article>
    </section>

    <section v-else-if="!loading" class="photo-empty">
      <ImagePlus :size="34" />
      <h3>尚未上传作品图片</h3>
      <button
        class="primary-command compact-command"
        type="button"
        :disabled="publishedLocked"
        @click="openFilePicker"
      >
        <Upload :size="16" />上传第一批图片
      </button>
    </section>
  </main>
</template>
