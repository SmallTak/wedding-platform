<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, CalendarDays, MapPin, Star } from '@lucide/vue'
import { useRoute } from 'vue-router'
import ContentAccessGate from '../components/ContentAccessGate.vue'
import BrandLogo from '../components/BrandLogo.vue'
import heroImage from '../assets/wedding-hero.jpg'
import { publicApi } from '../api/public'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const accessRequired = ref(false)
const accessLoading = ref(false)
const accessError = ref('')
const detail = ref(null)

const project = computed(() => detail.value?.project)
const collections = computed(() => detail.value?.collections || [])
const feedback = computed(() => detail.value?.feedback || [])
const creatorLabel = computed(() => detail.value?.creators
  .map((creator) => {
    const roles = creator.professionalRoles.join('、')
    return roles ? `${creator.displayName} · ${roles}` : creator.displayName
  })
  .join(' / '))

onMounted(loadProject)

async function loadProject() {
  loading.value = true
  errorMessage.value = ''
  accessRequired.value = false
  try {
    const { data } = await publicApi.project(route.params.projectId)
    detail.value = data
  } catch (error) {
    if (error.response?.status === 401 && error.response?.data?.code === 'CONTENT_ACCESS_REQUIRED') {
      accessRequired.value = true
    } else {
      errorMessage.value = error.response?.status === 404
        ? '该婚礼项目尚未公开或已经下架。'
        : '婚礼项目暂时无法加载，请稍后再试。'
    }
  } finally {
    loading.value = false
  }
}

async function unlockProject(password) {
  accessLoading.value = true
  accessError.value = ''
  try {
    await publicApi.projectAccess(route.params.projectId, password)
    await loadProject()
  } catch (error) {
    accessError.value = error.response?.data?.code === 'CONTENT_ACCESS_RATE_LIMITED'
      ? '尝试次数过多，请稍后再试。'
      : '访问密码不正确。'
  } finally {
    accessLoading.value = false
  }
}

function formatDate(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}
</script>

<template>
  <div class="site-shell project-detail-page">
    <header class="site-header detail-header">
      <RouterLink class="brand" to="/" aria-label="糖诗·美学首页">
        <BrandLogo />
      </RouterLink>
      <RouterLink class="detail-back-link" :to="{ name: 'project-list' }">
        <ArrowLeft :size="17" />
        返回项目列表
      </RouterLink>
    </header>

    <main v-if="loading" class="detail-state">正在加载婚礼项目...</main>
    <main v-else-if="accessRequired" class="detail-state access-state">
      <ContentAccessGate
        :loading="accessLoading"
        :error-message="accessError"
        @submit="unlockProject"
      />
    </main>
    <main v-else-if="errorMessage" class="detail-state">
      <h1>无法查看项目</h1>
      <p>{{ errorMessage }}</p>
      <RouterLink class="hero-link" :to="{ name: 'project-list' }">返回项目列表</RouterLink>
    </main>
    <main v-else-if="project">
      <section
        class="project-detail-hero"
        :style="{ backgroundImage: `url(${project.coverPreviewUrl || heroImage})` }"
      >
        <div class="hero-overlay"></div>
        <div>
          <p>{{ project.coupleDisplayName || 'Wedding story' }}</p>
          <h1>{{ project.title }}</h1>
          <span><MapPin :size="15" />{{ project.locationText }}</span>
        </div>
      </section>

      <section class="project-detail-intro">
        <div>
          <p class="section-kicker">The wedding</p>
          <p>{{ project.description || '一场关于相聚、承诺与真实情绪的婚礼记录。' }}</p>
        </div>
        <dl>
          <div>
            <dt><CalendarDays :size="15" />日期</dt>
            <dd>{{ formatDate(project.eventDate) }}</dd>
          </div>
          <div>
            <dt><MapPin :size="15" />地点</dt>
            <dd>{{ project.locationText }}</dd>
          </div>
          <div v-if="creatorLabel">
            <dt>创作者</dt>
            <dd>{{ creatorLabel }}</dd>
          </div>
        </dl>
      </section>

      <section class="project-collections">
        <div class="section-heading">
          <div>
            <p class="section-kicker">Published collections</p>
            <h2>项目作品</h2>
          </div>
          <span>{{ collections.length }} 组</span>
        </div>
        <div v-if="collections.length" class="work-grid">
          <RouterLink
            v-for="collection in collections"
            :key="collection.id"
            :to="{ name: 'collection-detail', params: { collectionId: collection.id } }"
            class="work-item"
          >
            <div
              class="work-image"
              :style="{ backgroundImage: `url(${collection.coverThumbnailUrl || collection.coverPreviewUrl || heroImage})` }"
            ></div>
            <div class="work-meta">
              <div>
                <p>{{ collection.category?.name || '婚礼作品' }}</p>
                <h3>{{ collection.title }}</h3>
              </div>
              <span>{{ collection.creators.map((creator) => creator.displayName).join('、') }}</span>
            </div>
          </RouterLink>
        </div>
        <div v-else class="public-empty">
          <h3>暂无公开作品集</h3>
          <p>该项目下的作品集会在审核发布后显示。</p>
        </div>
      </section>

      <section v-if="feedback.length" class="project-feedback-section">
        <div class="section-heading">
          <div>
            <p class="section-kicker">Client voices</p>
            <h2>客户评价</h2>
          </div>
          <span>{{ feedback.length }} 条</span>
        </div>
        <div class="public-feedback-list">
          <blockquote v-for="item in feedback" :key="item.id" class="public-feedback-item">
            <div class="public-feedback-rating" :aria-label="`${item.rating} 星`">
              <Star v-for="index in 5" :key="index" :size="15" :class="{ filled: index <= item.rating }" />
            </div>
            <p>“{{ item.content }}”</p>
            <footer>
              <strong>{{ item.customerDisplayName }}</strong>
              <span>{{ item.creatorDisplayName }}</span>
            </footer>
            <div v-if="item.reply" class="public-feedback-reply">
              <span>创作者回复</span>
              <p>{{ item.reply.content }}</p>
            </div>
          </blockquote>
        </div>
      </section>
    </main>
  </div>
</template>
