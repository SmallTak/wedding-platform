<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, X } from '@lucide/vue'
import { publicApi } from '../api/public'
import ContentAccessGate from '../components/ContentAccessGate.vue'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const accessRequired = ref(false)
const accessLoading = ref(false)
const accessError = ref('')
const detail = ref(null)
const activePhotoIndex = ref(null)

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

function openPreview(index) {
  activePhotoIndex.value = index
  document.body.style.overflow = 'hidden'
}

function closePreview() {
  activePhotoIndex.value = null
  document.body.style.overflow = ''
}
</script>

<template>
  <div class="site-shell collection-detail-page">
    <header class="site-header detail-header">
      <RouterLink class="brand" to="/" aria-label="Wedding Archive 首页">
        <span class="brand-mark">WA</span>
        <span>Wedding Archive</span>
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
        :style="{ backgroundImage: `url(${collection.coverPreviewUrl})` }"
      >
        <div class="hero-overlay"></div>
        <div class="collection-hero-content">
          <p>{{ collection.category?.name || 'Wedding collection' }}</p>
          <h1>{{ collection.title }}</h1>
        </div>
      </section>

      <section class="collection-intro">
        <div class="collection-description">
          <p class="section-kicker">The story</p>
          <p>{{ collection.description || '一组关于仪式、相聚与真实情绪的婚礼影像。' }}</p>
        </div>
        <dl>
          <div v-if="collection.project">
            <dt>项目</dt>
            <dd>
              <RouterLink :to="{ name: 'project-detail', params: { projectId: collection.project.id } }">
                {{ collection.project.title }} · {{ collection.project.locationText }}
              </RouterLink>
            </dd>
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

      <section class="collection-gallery" aria-label="作品图片">
        <button
          v-for="(photo, index) in photos"
          :key="photo.id"
          type="button"
          :style="{ aspectRatio: `${photo.width} / ${photo.height}` }"
          @click="openPreview(index)"
        >
          <img :src="photo.thumbnailUrl" :alt="`${collection.title} 图片 ${index + 1}`" loading="lazy" />
        </button>
      </section>

      <section class="collection-end">
        <p>{{ photos.length }} 张公开预览</p>
        <RouterLink class="contact-link" to="/">浏览更多作品</RouterLink>
      </section>
    </main>

    <div v-if="activePhoto" class="image-lightbox" role="dialog" aria-modal="true" @click.self="closePreview">
      <button type="button" aria-label="关闭预览" title="关闭预览" @click="closePreview">
        <X :size="22" />
      </button>
      <img :src="activePhoto.previewUrl" :alt="collection.title" />
      <span>{{ activePhotoIndex + 1 }} / {{ photos.length }}</span>
    </div>
  </div>
</template>
