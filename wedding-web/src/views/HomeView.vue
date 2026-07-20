<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  ArrowUpRight,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Menu,
  Search,
  Send,
  Star,
  UserRound,
  X,
} from '@lucide/vue'
import heroImage from '../assets/wedding-hero.png'
import { publicApi } from '../api/public'
import BrandLogo from '../components/BrandLogo.vue'

const menuOpen = ref(false)
const searchOpen = ref(false)
const serviceOnline = ref(false)
const loading = ref(false)
const loadError = ref('')
const categories = ref([])
const collections = ref([])
const projects = ref([])
const feedback = ref([])
const carousel = ref([])
const currentWorkIndex = ref(0)
const keyword = ref('')
const appliedKeyword = ref('')
const selectedCategoryId = ref(null)
const inquirySubmitting = ref(false)
const inquiryError = ref('')
const inquiryReceipt = ref('')
const inquiryForm = reactive({
  name: '',
  contact: '',
  weddingDate: '',
  region: '',
  serviceNeeds: '',
  remark: '',
  website: '',
})

let workTimer = null
let workPaused = false
const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const workSlides = computed(() => carousel.value)
const isFilteringWorks = computed(() =>
  Boolean(appliedKeyword.value) || selectedCategoryId.value !== null,
)
const resultTitle = computed(() => {
  if (appliedKeyword.value) return `“${appliedKeyword.value}”的作品`
  const category = categories.value.find((item) => item.id === selectedCategoryId.value)
  return category ? category.name : '最新作品'
})

onMounted(async () => {
  await Promise.all([loadStatus(), loadCategories(), loadHomepage()])
})
onBeforeUnmount(stopWorkRotation)

watch(
  [() => workSlides.value.length, isFilteringWorks],
  ([slideCount, filtering]) => {
    if (currentWorkIndex.value >= slideCount) currentWorkIndex.value = 0
    if (filtering) {
      stopWorkRotation()
    } else {
      startWorkRotation()
    }
  },
)

async function loadStatus() {
  try {
    const response = await publicApi.status()
    serviceOnline.value = response.data?.status === 'UP'
  } catch {
    serviceOnline.value = false
  }
}

async function loadCategories() {
  try {
    const { data } = await publicApi.categories()
    categories.value = data
  } catch {
    categories.value = []
  }
}

async function loadHomepage() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await publicApi.home()
    projects.value = data.projects
    collections.value = data.collections
    feedback.value = data.feedback
    carousel.value = data.carousel || []
    startWorkRotation()
  } catch {
    projects.value = []
    collections.value = []
    feedback.value = []
    carousel.value = []
    loadError.value = '首页内容暂时无法加载，请稍后再试。'
  } finally {
    loading.value = false
  }
}

function startWorkRotation() {
  stopWorkRotation()
  if (workPaused || reduceMotion || isFilteringWorks.value || workSlides.value.length < 2) return
  workTimer = window.setInterval(() => {
    currentWorkIndex.value = (currentWorkIndex.value + 1) % workSlides.value.length
  }, 6500)
}

function stopWorkRotation() {
  if (workTimer !== null) {
    window.clearInterval(workTimer)
    workTimer = null
  }
}

function pauseWorkRotation() {
  workPaused = true
  stopWorkRotation()
}

function resumeWorkRotation() {
  workPaused = false
  startWorkRotation()
}

function showWorkSlide(index) {
  currentWorkIndex.value = (index + workSlides.value.length) % workSlides.value.length
  startWorkRotation()
}

async function loadCollections() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await publicApi.collections({
      page: 0,
      size: 12,
      keyword: appliedKeyword.value || undefined,
      categoryId: selectedCategoryId.value || undefined,
    })
    collections.value = data.content
    projects.value = []
    feedback.value = []
  } catch {
    collections.value = []
    loadError.value = '作品暂时无法加载，请稍后再试。'
  } finally {
    loading.value = false
  }
}

async function searchCollections() {
  searchOpen.value = false
  selectedCategoryId.value = null
  if (!keyword.value.trim()) {
    await clearFilters()
    return
  }
  appliedKeyword.value = keyword.value.trim()
  await loadCollections()
  await nextTick()
  document.querySelector('#works')?.scrollIntoView({ behavior: 'smooth' })
}

async function selectCategory(categoryId) {
  keyword.value = ''
  appliedKeyword.value = ''
  selectedCategoryId.value = selectedCategoryId.value === categoryId ? null : categoryId
  if (selectedCategoryId.value === null) {
    await loadHomepage()
  } else {
    await loadCollections()
  }
  await nextTick()
  document.querySelector('#works')?.scrollIntoView({ behavior: 'smooth' })
}

async function clearFilters() {
  keyword.value = ''
  appliedKeyword.value = ''
  selectedCategoryId.value = null
  await loadHomepage()
}

async function submitInquiry() {
  if (!inquiryForm.name.trim() || !inquiryForm.contact.trim() || !inquiryForm.serviceNeeds.trim()) {
    inquiryError.value = '请填写姓名、联系方式和服务需求。'
    return
  }
  inquirySubmitting.value = true
  inquiryError.value = ''
  inquiryReceipt.value = ''
  try {
    const { data } = await publicApi.submitInquiry({
      name: inquiryForm.name.trim(),
      contact: inquiryForm.contact.trim(),
      weddingDate: inquiryForm.weddingDate || null,
      region: inquiryForm.region.trim() || null,
      serviceNeeds: inquiryForm.serviceNeeds.trim(),
      remark: inquiryForm.remark.trim() || null,
      website: inquiryForm.website,
    })
    inquiryReceipt.value = data.referenceCode
    Object.assign(inquiryForm, {
      name: '',
      contact: '',
      weddingDate: '',
      region: '',
      serviceNeeds: '',
      remark: '',
      website: '',
    })
  } catch (error) {
    inquiryError.value = error.response?.data?.code === 'INQUIRY_RATE_LIMITED'
      ? '提交次数较多，请稍后再试。'
      : '咨询提交失败，请稍后再试。'
  } finally {
    inquirySubmitting.value = false
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
  <div class="site-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/" aria-label="糖诗·美学首页">
        <BrandLogo />
      </RouterLink>

      <nav :class="['site-nav', { 'is-open': menuOpen }]" aria-label="主导航">
        <RouterLink :to="{ name: 'project-list' }" @click="menuOpen = false">婚礼项目</RouterLink>
        <a href="#works" @click="menuOpen = false">婚礼作品</a>
        <a href="#categories" @click="menuOpen = false">作品分类</a>
        <RouterLink :to="{ name: 'reviews' }" @click="menuOpen = false">客户评价</RouterLink>
        <a href="#contact" @click="menuOpen = false">预约咨询</a>
      </nav>

      <div class="header-actions">
        <RouterLink
          class="icon-button"
          :to="{ name: 'customer-projects' }"
          aria-label="客户中心"
          title="客户中心"
        >
          <UserRound :size="18" />
        </RouterLink>
        <button
          class="icon-button"
          type="button"
          aria-label="搜索作品"
          :aria-expanded="searchOpen"
          @click="searchOpen = !searchOpen"
        >
          <Search v-if="!searchOpen" :size="18" />
          <X v-else :size="18" />
        </button>
        <button
          class="icon-button menu-button"
          type="button"
          :aria-label="menuOpen ? '关闭导航' : '打开导航'"
          :aria-expanded="menuOpen"
          @click="menuOpen = !menuOpen"
        >
          <Menu :size="19" />
        </button>
      </div>
    </header>

    <form v-if="searchOpen" class="search-bar" role="search" @submit.prevent="searchCollections">
      <label for="site-search">搜索作品</label>
      <div class="search-input-row">
        <input id="site-search" v-model="keyword" type="search" placeholder="婚礼、地点或风格" autofocus />
        <button type="submit" aria-label="提交搜索" title="搜索"><Search :size="18" /></button>
      </div>
    </form>

    <main>
      <section
        class="hero-section"
        :style="{ backgroundImage: `url(${heroImage})` }"
      >
        <div class="hero-overlay"></div>
        <div class="hero-content">
          <p class="eyebrow">TANGSHI AESTHETICS · 2026</p>
          <h1>糖诗·美学</h1>
          <p class="hero-copy">收藏仪式发生时，那些无法重来的光线、表情与拥抱。</p>
          <a class="hero-link" href="#works">
            浏览公开作品
            <ArrowUpRight :size="17" />
          </a>
        </div>
      </section>

      <section v-if="projects.length" class="content-section homepage-projects">
        <div class="section-heading">
          <div>
            <p class="section-kicker">Featured weddings</p>
            <h2>推荐婚礼项目</h2>
          </div>
          <RouterLink class="text-link" :to="{ name: 'project-list' }">
            查看全部
            <ArrowUpRight :size="17" />
          </RouterLink>
        </div>
        <div class="homepage-project-grid">
          <RouterLink
            v-for="project in projects"
            :key="project.id"
            :to="{ name: 'project-detail', params: { projectId: project.id } }"
            class="homepage-project-item"
          >
            <img
              :src="project.coverThumbnailUrl || project.coverPreviewUrl || project.coverOriginalUrl || heroImage"
              :alt="project.title"
              loading="lazy"
            />
            <div>
              <span><CalendarDays :size="14" />{{ formatDate(project.eventDate) }}</span>
              <h3>{{ project.title }}</h3>
              <p>{{ project.locationText }}</p>
            </div>
          </RouterLink>
        </div>
      </section>

      <section
        v-if="loading || loadError || isFilteringWorks || workSlides.length"
        id="works"
        class="content-section featured-section"
      >
        <div class="section-heading">
          <div>
            <p class="section-kicker">Published work</p>
            <h2>{{ resultTitle }}</h2>
          </div>
          <button
            v-if="isFilteringWorks"
            class="text-button"
            type="button"
            @click="clearFilters"
          >查看全部</button>
        </div>

        <div v-if="loading" class="public-loading">正在加载作品...</div>
        <p v-else-if="loadError" class="public-error">{{ loadError }}</p>
        <div v-else-if="isFilteringWorks && collections.length" class="work-grid">
          <RouterLink
            v-for="work in collections"
            :key="work.id"
            :to="{ name: 'collection-detail', params: { collectionId: work.id } }"
            class="work-item"
          >
            <div
              class="work-image"
              :style="{
                backgroundImage: `url(${work.coverThumbnailUrl || work.coverPreviewUrl || work.coverOriginalUrl || heroImage})`,
              }"
            ></div>
            <div class="work-meta">
              <div>
                <p>{{ work.category?.name || '婚礼作品' }}</p>
                <h3>{{ work.title }}</h3>
              </div>
              <span>{{ work.project?.locationText || work.creators.map((creator) => creator.displayName).join('、') }}</span>
            </div>
          </RouterLink>
        </div>
        <div v-else-if="isFilteringWorks" class="public-empty">
          <h3>暂无已发布作品</h3>
          <p>新的婚礼故事将在审核并发布后出现在这里。</p>
        </div>
        <div
          v-else-if="workSlides.length"
          class="work-carousel"
          @mouseenter="pauseWorkRotation"
          @mouseleave="resumeWorkRotation"
          @focusin="pauseWorkRotation"
          @focusout="resumeWorkRotation"
        >
          <RouterLink
            v-for="(slide, index) in workSlides"
            :key="slide.collectionId"
            :to="{ name: 'collection-detail', params: { collectionId: slide.collectionId } }"
            :class="['work-carousel-slide', { active: index === currentWorkIndex }]"
            :aria-hidden="index === currentWorkIndex ? undefined : 'true'"
            :tabindex="index === currentWorkIndex ? undefined : -1"
            :style="{
              backgroundImage: `url(${slide.previewUrl || slide.thumbnailUrl || slide.originalUrl})`,
              backgroundPosition: `${Number(slide.focalX ?? 50)}% ${Number(slide.focalY ?? 50)}%`,
            }"
          >
            <div class="work-carousel-overlay"></div>
            <div class="work-carousel-copy">
              <p v-if="slide.locationText || slide.eventDate">
                <span v-if="slide.locationText">{{ slide.locationText }}</span>
                <span v-if="slide.eventDate">{{ formatDate(slide.eventDate) }}</span>
              </p>
              <h3>{{ slide.collectionTitle }}</h3>
              <div v-if="slide.description">{{ slide.description }}</div>
              <strong>查看完整作品 <ArrowUpRight :size="17" /></strong>
            </div>
          </RouterLink>
          <div v-if="workSlides.length > 1" class="work-carousel-controls" aria-label="作品轮播控制">
            <button type="button" aria-label="上一个作品" title="上一个作品" @click="showWorkSlide(currentWorkIndex - 1)">
              <ChevronLeft :size="19" />
            </button>
            <div class="work-carousel-dots">
              <button
                v-for="(slide, index) in workSlides"
                :key="slide.collectionId"
                type="button"
                :class="{ active: index === currentWorkIndex }"
                :aria-label="`显示第 ${index + 1} 个作品`"
                :aria-current="index === currentWorkIndex ? 'true' : undefined"
                @click="showWorkSlide(index)"
              ></button>
            </div>
            <button type="button" aria-label="下一个作品" title="下一个作品" @click="showWorkSlide(currentWorkIndex + 1)">
              <ChevronRight :size="19" />
            </button>
          </div>
        </div>
      </section>

      <section id="categories" class="category-band">
        <p class="section-kicker">Collections</p>
        <div class="category-list">
          <button
            v-for="(category, index) in categories"
            :key="category.id"
            type="button"
            :class="{ active: selectedCategoryId === category.id }"
            @click="selectCategory(category.id)"
          >
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
            {{ category.name }}
            <ArrowUpRight :size="18" />
          </button>
          <p v-if="!categories.length" class="category-empty">分类正在整理中</p>
        </div>
      </section>

      <section v-if="feedback.length" class="homepage-feedback-section">
        <div class="section-heading">
          <div>
            <p class="section-kicker">Client voices</p>
            <h2>他们记住的时刻</h2>
          </div>
          <RouterLink class="text-link" :to="{ name: 'reviews' }">
            查看全部评价
            <ArrowUpRight :size="17" />
          </RouterLink>
        </div>
        <div class="public-feedback-list">
          <blockquote v-for="item in feedback" :key="item.id" class="public-feedback-item">
            <div class="public-feedback-rating" :aria-label="`${item.rating} 星`">
              <Star v-for="index in 5" :key="index" :size="15" :class="{ filled: index <= item.rating }" />
            </div>
            <p>“{{ item.content }}”</p>
            <footer>
              <strong>{{ item.customerDisplayName }}</strong>
              <span>{{ item.projectTitle }} · {{ item.creatorDisplayName }}</span>
            </footer>
            <div v-if="item.reply" class="public-feedback-reply">
              <span>创作者回复</span>
              <p>{{ item.reply.content }}</p>
            </div>
          </blockquote>
        </div>
      </section>

      <section id="contact" class="contact-section">
        <div>
          <p class="section-kicker">Enquiry</p>
          <h2>告诉我们你的婚期</h2>
          <p class="contact-copy">留下联系方式与服务需求，团队会根据婚期和地区尽快回复。</p>
        </div>
        <form class="inquiry-form" @submit.prevent="submitInquiry">
          <div class="inquiry-form-grid">
            <label>
              姓名
              <input v-model="inquiryForm.name" maxlength="100" autocomplete="name" required />
            </label>
            <label>
              联系方式
              <input v-model="inquiryForm.contact" minlength="5" maxlength="120" autocomplete="tel" placeholder="手机或微信" required />
            </label>
            <label>
              婚期
              <input v-model="inquiryForm.weddingDate" type="date" />
            </label>
            <label>
              地区
              <input v-model="inquiryForm.region" maxlength="200" placeholder="城市或场地" />
            </label>
            <label class="inquiry-wide-field">
              服务需求
              <input v-model="inquiryForm.serviceNeeds" maxlength="1000" placeholder="婚礼摄影、跟拍、妆造或策划" required />
            </label>
            <label class="inquiry-wide-field">
              补充说明
              <textarea v-model="inquiryForm.remark" rows="4" maxlength="2000"></textarea>
            </label>
            <label class="inquiry-honeypot" aria-hidden="true">
              网站
              <input v-model="inquiryForm.website" tabindex="-1" autocomplete="off" />
            </label>
          </div>
          <p v-if="inquiryError" class="inquiry-message error">{{ inquiryError }}</p>
          <p v-else-if="inquiryReceipt" class="inquiry-message success">
            已收到咨询，编号 {{ inquiryReceipt }}
          </p>
          <button type="submit" :disabled="inquirySubmitting">
            <Send :size="17" />
            {{ inquirySubmitting ? '正在提交' : '提交咨询' }}
          </button>
        </form>
      </section>
    </main>

    <footer class="site-footer">
      <span>© 2026 糖诗·美学</span>
      <span class="service-status">
        <i :class="{ online: serviceOnline }"></i>
        {{ serviceOnline ? '服务在线' : '服务维护中' }}
      </span>
    </footer>
  </div>
</template>
