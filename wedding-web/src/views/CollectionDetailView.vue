<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ChevronLeft, ChevronRight, X } from '@lucide/vue'
import { publicApi } from '../api/public'
import BrandLogo from '../components/BrandLogo.vue'
import ContentAccessGate from '../components/ContentAccessGate.vue'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const accessRequired = ref(false)
const accessLoading = ref(false)
const accessError = ref('')
const detail = ref(null)
const activePhotoIndex = ref(null)
const activePhotoUrl = ref('')
let touchStartX = 0
let touchStartY = 0

const collection = computed(() => detail.value?.collection)
const photos = computed(() => detail.value?.photos || [])
const activePhoto = computed(() => (
  activePhotoIndex.value === null ? null : photos.value[activePhotoIndex.value]
))
const creatorLabel = computed(() => collection.value?.creators
  .map((creator) => {
    const roles = creator.professionalRoles.join('、')
    return roles ? `${creator.displayName} · ${roles}` : creator.displayName
  })
  .join(' / '))
const detailMeta = computed(() => [
  collection.value?.eventDate,
  collection.value?.locationText,
].filter(Boolean).join(' · '))

onMounted(loadCollection)
onUnmounted(() => {
  document.body.style.overflow = ''
})

async function loadCollection() {
  loading.value = true
  errorMessage.value = ''
  accessRequired.value = false
  try {
    const { data } = await publicApi.collection(route.params.collectionId)
    detail.value = data
    publicApi.trackVisit('COLLECTION', data.collection.id).catch(() => {})
  } catch (error) {
    if (error.response?.status === 401 && error.response?.data?.code === 'CONTENT_ACCESS_REQUIRED') {
      accessRequired.value = true
    } else {
      errorMessage.value = error.response?.status === 404
        ? '该作品集尚未公开或已经下架。'
        : '作品集暂时无法加载，请稍后再试。'
    }
  } finally {
    loading.value = false
  }
}

async function unlockCollection(password) {
  accessLoading.value = true
  accessError.value = ''
  try {
    await publicApi.collectionAccess(route.params.collectionId, password)
    await loadCollection()
  } catch (error) {
    accessError.value = error.response?.data?.code === 'CONTENT_ACCESS_RATE_LIMITED'
      ? '尝试次数过多，请稍后再试。'
      : '访问密码不正确。'
  } finally {
    accessLoading.value = false
  }
}

function showPhoto(index) {
  if (!photos.value.length) return
  openPreview((index + photos.value.length) % photos.value.length)
}

function showNextPhoto() {
  if (activePhotoIndex.value !== null) showPhoto(activePhotoIndex.value + 1)
}

function showPrevPhoto() {
  if (activePhotoIndex.value !== null) showPhoto(activePhotoIndex.value - 1)
}

function handleLightboxKeydown(event) {
  if (event.key === 'Escape') closePreview()
  if (event.key === 'ArrowRight') showNextPhoto()
  if (event.key === 'ArrowLeft') showPrevPhoto()
}

function handleTouchStart(event) {
  const touch = event.changedTouches?.[0]
  if (!touch) return
  touchStartX = touch.clientX
  touchStartY = touch.clientY
}

function handleTouchEnd(event) {
  const touch = event.changedTouches?.[0]
  if (!touch) return
  const dx = touch.clientX - touchStartX
  const dy = touch.clientY - touchStartY
  if (Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy) * 1.5) {
    dx < 0 ? showNextPhoto() : showPrevPhoto()
  }
}

function openPreview(index) {
  const photo = photos.value[index]
  if (!photo) return
  activePhotoIndex.value = index
  activePhotoUrl.value = photo.originalUrl || photo.previewUrl || photo.thumbnailUrl
  document.body.style.overflow = 'hidden'
}

function closePreview() {
  activePhotoIndex.value = null
  activePhotoUrl.value = ''
  document.body.style.overflow = ''
}
</script>

<template>
  <div class="site-shell collection-detail-page">
    <header class="site-header detail-header">
      <RouterLink class="brand" to="/" aria-label="糖诗·美学首页">
        <BrandLogo />
      </RouterLink>
      <RouterLink class="detail-back-link" to="/">
        <ArrowLeft :size="17" />
        返回作品首页
      </RouterLink>
    </header>

    <main v-if="loading" class="detail-state">正在加载作品...</main>
    <main v-else-if="accessRequired" class="detail-state access-state">
      <ContentAccessGate
        :loading="accessLoading"
        :error-message="accessError"
        @submit="unlockCollection"
      />
    </main>
    <main v-else-if="errorMessage" class="detail-state">
      <h1>无法查看作品</h1>
      <p>{{ errorMessage }}</p>
      <RouterLink class="hero-link" to="/">返回首页</RouterLink>
    </main>
    <main v-else-if="collection">
      <section
        class="collection-hero"
        :style="{
          backgroundImage: `url(${collection.coverOriginalUrl || collection.coverPreviewUrl || collection.coverThumbnailUrl})`,
        }"
      >
        <div class="hero-overlay"></div>
        <div class="collection-hero-content">
          <p>{{ collection.category?.name || 'Wedding collection' }} · 糖诗影像档案</p>
          <h1>{{ collection.title }}</h1>
          <span v-if="detailMeta">{{ detailMeta }}</span>
        </div>
        <span class="collection-hero-index">ARCHIVE / {{ String(collection.id).padStart(3, '0') }}</span>
      </section>

      <section class="collection-intro">
        <div class="collection-description">
          <p class="section-kicker">About this story</p>
          <h2>关于这一卷</h2>
          <p>{{ collection.description || '一组关于仪式、相聚与真实情绪的婚礼影像。' }}</p>
        </div>
        <dl>
          <div v-if="collection.eventDate || collection.locationText">
            <dt>日期地点</dt>
            <dd>{{ [collection.eventDate, collection.locationText].filter(Boolean).join(' · ') }}</dd>
          </div>
          <div v-if="creatorLabel">
            <dt>创作者</dt>
            <dd>{{ creatorLabel }}</dd>
          </div>
          <div v-if="collection.tags.length">
            <dt>标签</dt>
            <dd>{{ collection.tags.map((tag) => tag.name).join('、') }}</dd>
          </div>
        </dl>
      </section>

      <div class="collection-gallery-heading">
        <p class="section-kicker">Selected frames</p>
        <h2>影像选帧</h2>
        <span>{{ String(photos.length).padStart(2, '0') }} 幅</span>
      </div>

      <section class="collection-gallery" aria-label="作品图片">
        <button
          v-for="(photo, index) in photos"
          :key="photo.id"
          type="button"
          :style="{ aspectRatio: `${photo.width} / ${photo.height}` }"
          @click="openPreview(index)"
        >
          <img
            :src="photo.previewUrl || photo.thumbnailUrl || photo.originalUrl"
            :alt="`${collection.title} 图片 ${index + 1}`"
            loading="lazy"
          />
        </button>
      </section>

      <section class="collection-end">
        <p>此卷至此，余韵未尽。</p>
        <RouterLink class="contact-link" to="/">回到作品首页 <ArrowLeft :size="16" /></RouterLink>
      </section>
    </main>

    <div v-if="activePhoto" class="image-lightbox" role="dialog" aria-modal="true" tabindex="-1" @click.self="closePreview" @keydown="handleLightboxKeydown" @touchstart.passive="handleTouchStart" @touchend.passive="handleTouchEnd">
      <button class="lightbox-close" type="button" aria-label="关闭预览" title="关闭预览" @click="closePreview">
        <X :size="22" />
      </button>
      <button v-if="photos.length > 1" class="lightbox-nav prev" type="button" aria-label="上一张" @click.stop="showPrevPhoto">
        <ChevronLeft :size="30" />
      </button>
      <img :src="activePhotoUrl" :alt="collection.title" />
      <button v-if="photos.length > 1" class="lightbox-nav next" type="button" aria-label="下一张" @click.stop="showNextPhoto">
        <ChevronRight :size="30" />
      </button>
      <span>{{ activePhotoIndex + 1 }} / {{ photos.length }}</span>
    </div>
  </div>
</template>
