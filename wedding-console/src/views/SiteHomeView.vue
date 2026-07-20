<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  ArrowUp,
  GripVertical,
  HelpCircle,
  RefreshCw,
  Save,
  Search,
  Star,
  Trash2,
} from '@lucide/vue'
import { homepageApi } from '../api/operations'
import { apiErrorMessage, formatDate } from '../utils/content'

const loading = ref(false)
const saving = ref(false)
const carouselSaving = ref(false)
const activeTab = ref('FEEDBACK')
const feedback = ref([])
const configured = ref([])
const carouselCandidates = ref([])
const carouselItems = ref([])
const carouselSearch = ref('')
const draggingPhotoId = ref(null)
const guidanceOpen = ref(false)
const limits = {
  FEEDBACK: 6,
}

const filteredCarouselCandidates = computed(() => {
  const keyword = carouselSearch.value.trim().toLocaleLowerCase()
  if (!keyword) return carouselCandidates.value
  return carouselCandidates.value.filter((candidate) =>
    [
      candidate.collectionTitle,
      candidate.locationText,
      candidate.description,
    ].some((value) => value?.toLocaleLowerCase().includes(keyword)),
  )
})
const carouselPhotoIds = computed(() =>
  new Set(carouselItems.value.map((item) => item.photoId)),
)
const orderedCarouselItems = computed(() =>
  [...carouselItems.value].sort((left, right) => (
    left.sortOrder - right.sortOrder || left.photoId - right.photoId
  )),
)
const candidates = computed(() => {
  return feedback.value
})
const selectedCount = computed(() =>
  configured.value.filter((item) => item.targetType === activeTab.value).length,
)
const activeLimit = computed(() => limits[activeTab.value])

onMounted(loadSettings)

async function loadSettings() {
  loading.value = true
  try {
    const [featuresResponse, carouselResponse] = await Promise.all([
      homepageApi.options(),
      homepageApi.carouselOptions(),
    ])
    feedback.value = featuresResponse.data.feedback
    configured.value = featuresResponse.data.features
      .filter((item) => item.targetType === 'FEEDBACK')
      .map((item) => ({ ...item }))
    carouselCandidates.value = carouselResponse.data.candidates
    carouselItems.value = carouselResponse.data.items.map((item) => ({ ...item }))
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '首页运营配置加载失败'))
  } finally {
    loading.value = false
  }
}

function carouselItemFor(photoId) {
  return carouselItems.value.find((item) => item.photoId === photoId)
}

function toggleCarouselCandidate(candidate, selected) {
  const existing = carouselItemFor(candidate.photoId)
  if (!selected && existing) {
    carouselItems.value = carouselItems.value.filter((item) => item !== existing)
    return
  }
  if (selected && !existing) {
    if (carouselItems.value.length >= 5) {
      ElMessage.warning('首页轮播最多选择 5 张照片')
      return
    }
    carouselItems.value.push({
      photoId: candidate.photoId,
      collectionTitle: candidate.collectionTitle,
      collectionId: candidate.collectionId,
      originalUrl: candidate.originalUrl,
      description: candidate.description,
      eventDate: candidate.eventDate,
      locationText: candidate.locationText,
      previewUrl: candidate.previewUrl,
      thumbnailUrl: candidate.thumbnailUrl,
      width: candidate.width,
      height: candidate.height,
      sortOrder: (carouselItems.value.length + 1) * 10,
      focalX: 50,
      focalY: 50,
      valid: true,
      invalidReason: null,
    })
  }
}

function removeCarouselItem(item) {
  carouselItems.value = carouselItems.value.filter((candidate) => candidate !== item)
}

function carouselCandidateDisabled(candidate) {
  return !carouselPhotoIds.value.has(candidate.photoId) && carouselItems.value.length >= 5
}

function carouselPreviewStyle(item) {
  const imageUrl = item.previewUrl || item.thumbnailUrl
  return {
    backgroundImage: imageUrl ? `url(${imageUrl})` : 'none',
    backgroundPosition: `${Number(item.focalX)}% ${Number(item.focalY)}%`,
  }
}

function carouselFocusPointStyle(item) {
  return {
    left: `${Number(item.focalX)}%`,
    top: `${Number(item.focalY)}%`,
  }
}

function carouselInvalidText(reason) {
  return {
    COLLECTION_NOT_AVAILABLE: '作品已删除或不存在',
    COLLECTION_NOT_PUBLIC: '作品已下架、隐藏或审核状态发生变化',
    COLLECTION_COVER_REQUIRED: '作品集未设置封面',
    COLLECTION_COVER_NOT_AVAILABLE: '作品照片已删除或未通过审核',
    ASSET_NOT_AVAILABLE: '图片处理失败或不可用',
  }[reason] || '作品当前不可用于首页轮播'
}

function carouselDateLocation(item) {
  return [
    item.locationText,
    item.eventDate ? formatDate(item.eventDate) : null,
  ].filter(Boolean).join(' · ') || '未填写作品集地点和日期'
}

function normalizeCarouselOrder(items) {
  return items.map((item, index) => ({
    ...item,
    sortOrder: (index + 1) * 10,
  }))
}

function moveCarouselItem(item, direction) {
  const items = [...orderedCarouselItems.value]
  const index = items.findIndex((candidate) => candidate.photoId === item.photoId)
  const nextIndex = index + direction
  if (index < 0 || nextIndex < 0 || nextIndex >= items.length) return
  const [moved] = items.splice(index, 1)
  items.splice(nextIndex, 0, moved)
  carouselItems.value = normalizeCarouselOrder(items)
}

function startCarouselDrag(item, event) {
  draggingPhotoId.value = item.photoId
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(item.photoId))
}

function dropCarouselItem(targetItem, event) {
  event.preventDefault()
  const sourceId = Number(event.dataTransfer.getData('text/plain'))
  if (!sourceId || sourceId === targetItem.photoId) return
  const items = [...orderedCarouselItems.value]
  const sourceIndex = items.findIndex((item) => item.photoId === sourceId)
  const targetIndex = items.findIndex((item) => item.photoId === targetItem.photoId)
  if (sourceIndex < 0 || targetIndex < 0) return
  const [moved] = items.splice(sourceIndex, 1)
  items.splice(targetIndex, 0, moved)
  carouselItems.value = normalizeCarouselOrder(items)
  draggingPhotoId.value = null
}

function finishCarouselDrag() {
  draggingPhotoId.value = null
}

function featureFor(targetId) {
  return configured.value.find(
    (item) => item.targetType === activeTab.value && item.targetId === targetId,
  )
}

function toggleCandidate(candidate, selected) {
  const existing = featureFor(candidate.id)
  if (!selected && existing) {
    configured.value = configured.value.filter((item) => item !== existing)
    return
  }
  if (selected && !existing) {
    const current = configured.value.filter((item) => item.targetType === activeTab.value)
    if (current.length >= activeLimit.value) {
      ElMessage.warning(`当前类型最多推荐 ${activeLimit.value} 条内容`)
      return
    }
    configured.value.push({
      targetType: activeTab.value,
      targetId: candidate.id,
      sortOrder: (current.length + 1) * 10,
      pinned: false,
    })
  }
}

function candidateDisabled(candidate) {
  return !featureFor(candidate.id) && selectedCount.value >= activeLimit.value
}

function candidateTitle(candidate) {
  if (activeTab.value === 'FEEDBACK') {
    return `${candidate.customerDisplayName} · ${candidate.creatorDisplayName || '创作者'}`
  }
  return candidate.title
}

function candidateMeta(candidate) {
  return candidate.content
}

async function saveCarousel() {
  carouselSaving.value = true
  try {
    const items = normalizeCarouselOrder(orderedCarouselItems.value).map(({
      photoId,
      sortOrder,
      focalX,
      focalY,
    }) => ({
      photoId,
      sortOrder,
      focalX,
      focalY,
    }))
    const { data } = await homepageApi.replaceCarousel(items)
    carouselCandidates.value = data.candidates
    carouselItems.value = data.items.map((item) => ({ ...item }))
    ElMessage.success('首页轮播配置已发布')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '首页轮播配置保存失败'))
  } finally {
    carouselSaving.value = false
  }
}

async function saveFeatures() {
  saving.value = true
  try {
    const items = configured.value.map(({ targetType, targetId, sortOrder, pinned }) => ({
      targetType,
      targetId,
      sortOrder,
      pinned,
    }))
    const { data } = await homepageApi.replace(items)
    configured.value = data.features
      .filter((item) => item.targetType === 'FEEDBACK')
      .map((item) => ({ ...item }))
    ElMessage.success('首页推荐配置已发布')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '首页推荐配置保存失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="dashboard-content management-content" v-loading="loading">
    <section class="management-summary operations-summary homepage-summary" aria-label="首页推荐概览">
      <div><span>轮播作品</span><strong>{{ carouselItems.length }}</strong></div>
      <div><span>已选评价</span><strong>{{ configured.filter((item) => item.targetType === 'FEEDBACK').length }}</strong></div>
    </section>

    <section class="dashboard-section management-panel homepage-carousel-panel">
      <div class="management-toolbar homepage-toolbar">
        <div class="homepage-panel-title">
          <strong>作品轮播</strong>
          <span>{{ carouselItems.length }} / 5</span>
        </div>
        <div class="toolbar-commands">
          <button
            class="icon-command"
            type="button"
            aria-label="查看填写说明"
            title="查看填写说明"
            :aria-expanded="guidanceOpen"
            @click="guidanceOpen = !guidanceOpen"
          >
            <HelpCircle :size="17" />
          </button>
          <button class="icon-command" type="button" aria-label="重新加载" title="重新加载" @click="loadSettings">
            <RefreshCw :size="17" />
          </button>
          <button
            class="primary-command compact-command"
            type="button"
            :disabled="carouselSaving"
            @click="saveCarousel"
          >
            <Save :size="17" />发布轮播
          </button>
        </div>
      </div>

      <div v-if="guidanceOpen" class="homepage-guidance">
        <strong>填写说明</strong>
        <span>只能选择已审核、已发布、公开且图片通过审核的作品照片。</span>
        <span>地点、日期和摘要来自作品集；需要修改时请回到作品集编辑页。</span>
        <span>焦点会同时作用于桌面和手机预览，发布后立即影响官网作品轮播。</span>
      </div>

      <div v-if="orderedCarouselItems.length" class="carousel-selection-list">
        <article
          v-for="item in orderedCarouselItems"
          :key="item.photoId"
          :class="[
            'carousel-selection-item',
            {
              invalid: !item.valid,
              dragging: draggingPhotoId === item.photoId,
            },
          ]"
          @dragover.prevent
          @drop="dropCarouselItem(item, $event)"
          @dragend="finishCarouselDrag"
        >
          <div class="carousel-item-actions">
            <button
              class="icon-command drag-handle"
              type="button"
              draggable="true"
              aria-label="拖动排序"
              title="拖动排序"
              @dragstart="startCarouselDrag(item, $event)"
            >
              <GripVertical :size="17" />
            </button>
            <button
              class="icon-command"
              type="button"
              :disabled="orderedCarouselItems[0] === item"
              aria-label="上移"
              title="上移"
              @click="moveCarouselItem(item, -1)"
            >
              <ArrowUp :size="15" />
            </button>
            <button
              class="icon-command"
              type="button"
              :disabled="orderedCarouselItems[orderedCarouselItems.length - 1] === item"
              aria-label="下移"
              title="下移"
              @click="moveCarouselItem(item, 1)"
            >
              <ArrowDown :size="15" />
            </button>
          </div>
          <div class="carousel-selection-copy">
            <strong>{{ item.collectionTitle || `作品集 #${item.photoId}` }}</strong>
            <span>{{ carouselDateLocation(item) }}</span>
            <span v-if="item.description" class="carousel-description">{{ item.description }}</span>
            <span v-if="!item.valid" class="negative-text">{{ carouselInvalidText(item.invalidReason) }}，请移除后发布</span>
          </div>
          <div class="carousel-preview-pair" aria-label="桌面和手机裁剪预览">
            <div class="carousel-preview-box">
              <span>桌面</span>
              <div class="carousel-selection-preview desktop" :style="carouselPreviewStyle(item)">
                <i :style="carouselFocusPointStyle(item)"></i>
              </div>
            </div>
            <div class="carousel-preview-box">
              <span>手机</span>
              <div class="carousel-selection-preview mobile" :style="carouselPreviewStyle(item)">
                <i :style="carouselFocusPointStyle(item)"></i>
              </div>
            </div>
          </div>
          <label class="carousel-focus-control">
            <span>水平焦点 <strong>{{ Number(item.focalX) }}%</strong></span>
            <el-slider v-model="item.focalX" :min="0" :max="100" />
          </label>
          <label class="carousel-focus-control">
            <span>垂直焦点 <strong>{{ Number(item.focalY) }}%</strong></span>
            <el-slider v-model="item.focalY" :min="0" :max="100" />
          </label>
          <button
            class="danger-command"
            type="button"
            aria-label="移除轮播作品"
            title="移除轮播作品"
            @click="removeCarouselItem(item)"
          >
            <Trash2 :size="17" />
          </button>
        </article>
      </div>
      <div v-else class="empty-table">未配置作品轮播，官网将隐藏该区域</div>

      <div class="carousel-candidate-toolbar">
        <div class="table-search">
          <Search :size="17" />
          <input v-model="carouselSearch" type="search" placeholder="搜索作品集" />
        </div>
        <span class="section-count">可选作品 {{ filteredCarouselCandidates.length }}</span>
      </div>
      <div class="carousel-candidate-grid">
        <article
          v-for="candidate in filteredCarouselCandidates"
          :key="candidate.photoId"
          :class="[
            'carousel-candidate',
            {
              selected: carouselPhotoIds.has(candidate.photoId),
              disabled: carouselCandidateDisabled(candidate),
            },
          ]"
        >
          <div class="carousel-candidate-image">
            <img
              :src="candidate.thumbnailUrl || candidate.previewUrl"
              :alt="candidate.collectionTitle"
              loading="lazy"
            />
            <el-checkbox
              :model-value="carouselPhotoIds.has(candidate.photoId)"
              :disabled="carouselCandidateDisabled(candidate)"
              :aria-label="`选择${candidate.collectionTitle}作品`"
              @change="toggleCarouselCandidate(candidate, $event)"
            />
          </div>
          <strong>{{ candidate.collectionTitle }}</strong>
          <span>{{ candidate.locationText || '未填写作品集地点' }}</span>
          <small v-if="candidate.eventDate">{{ formatDate(candidate.eventDate) }}</small>
        </article>
        <div v-if="!loading && filteredCarouselCandidates.length === 0" class="empty-table carousel-empty">
          当前没有可选的公开作品
        </div>
      </div>
    </section>

    <section class="dashboard-section management-panel">
      <div class="management-toolbar homepage-toolbar">
        <div class="segmented-control" aria-label="首页内容类型">
          <button type="button" :class="{ active: activeTab === 'FEEDBACK' }" @click="activeTab = 'FEEDBACK'">
            客户评价
          </button>
        </div>
        <div class="toolbar-commands">
          <span class="section-count">当前选择 {{ selectedCount }} / {{ activeLimit }}</span>
          <button class="icon-command" type="button" aria-label="重新加载" title="重新加载" @click="loadSettings">
            <RefreshCw :size="17" />
          </button>
          <button class="primary-command compact-command" type="button" :disabled="saving" @click="saveFeatures">
            <Save :size="17" />发布配置
          </button>
        </div>
      </div>

      <p class="homepage-inline-help">
        勾选要展示的内容后再发布；排序数字越小越靠前，开启置顶的内容会排在未置顶内容之前。
      </p>

      <div class="homepage-candidate-list">
        <article v-for="candidate in candidates" :key="candidate.id" class="homepage-candidate">
          <el-checkbox
            :model-value="Boolean(featureFor(candidate.id))"
            :disabled="candidateDisabled(candidate)"
            :aria-label="`推荐${candidateTitle(candidate)}`"
            @change="toggleCandidate(candidate, $event)"
          />
          <img
            v-if="activeTab !== 'FEEDBACK' && (candidate.coverThumbnailUrl || candidate.coverPreviewUrl)"
            :src="candidate.coverThumbnailUrl || candidate.coverPreviewUrl"
            alt=""
          />
          <div v-else class="homepage-candidate-icon">
            <Star :size="18" />
          </div>
          <div class="homepage-candidate-copy">
            <strong>{{ candidateTitle(candidate) }}</strong>
            <span>{{ candidateMeta(candidate) }}</span>
          </div>
          <template v-if="featureFor(candidate.id)">
            <label class="homepage-order">
              排序
              <el-input-number
                v-model="featureFor(candidate.id).sortOrder"
                :min="0"
                :max="999999"
                controls-position="right"
              />
            </label>
            <label class="homepage-pin">
              置顶
              <el-switch v-model="featureFor(candidate.id).pinned" />
            </label>
          </template>
        </article>
        <div v-if="!loading && candidates.length === 0" class="empty-table">
          当前没有可推荐的公开内容
        </div>
      </div>
    </section>
  </main>
</template>
