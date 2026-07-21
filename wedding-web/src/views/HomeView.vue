<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  ArrowDown,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Menu,
  Pause,
  Play,
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
let touchStartX = 0
let touchStartY = 0
const workPaused = ref(false)
const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const workSlides = computed(() => carousel.value.slice(0, 5))
const activeSlide = computed(() => workSlides.value[currentWorkIndex.value] || null)
const isFilteringWorks = computed(() =>
  Boolean(appliedKeyword.value) || selectedCategoryId.value !== null,
)
const resultTitle = computed(() => {
  if (appliedKeyword.value) return `“${appliedKeyword.value}”的作品`
  const category = categories.value.find((item) => item.id === selectedCategoryId.value)
  return category ? category.name : '近作选章'
})

onMounted(async () => {
  await Promise.all([loadStatus(), loadCategories(), loadHomepage()])
})
onBeforeUnmount(stopWorkRotation)

watch(
  () => workSlides.value.length,
  (slideCount) => {
    if (currentWorkIndex.value >= slideCount) currentWorkIndex.value = 0
    startWorkRotation()
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
    collections.value = data.collections || []
    feedback.value = data.feedback || []
    carousel.value = data.carousel || []
    startWorkRotation()
  } catch {
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
  if (workPaused.value || reduceMotion || workSlides.value.length < 2) return
  workTimer = window.setInterval(() => {
    currentWorkIndex.value = (currentWorkIndex.value + 1) % workSlides.value.length
  }, 7600)
}

function stopWorkRotation() {
  if (workTimer !== null) {
    window.clearInterval(workTimer)
    workTimer = null
  }
}

function pauseWorkRotation() {
  workPaused.value = true
  stopWorkRotation()
}

function resumeWorkRotation() {
  workPaused.value = false
  startWorkRotation()
}

function toggleWorkRotation() {
  if (workPaused.value) resumeWorkRotation()
  else pauseWorkRotation()
}

function showWorkSlide(index) {
  if (!workSlides.value.length) return
  currentWorkIndex.value = (index + workSlides.value.length) % workSlides.value.length
  startWorkRotation()
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
    showWorkSlide(currentWorkIndex.value + (dx < 0 ? 1 : -1))
  }
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
  if (selectedCategoryId.value === null) await loadHomepage()
  else await loadCollections()
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
  <div class="site-shell home-page">
    <header class="site-header">
      <RouterLink class="brand" to="/" aria-label="糖诗·美学首页">
        <BrandLogo />
      </RouterLink>

      <nav :class="['site-nav', { 'is-open': menuOpen }]" aria-label="主导航">
        <a href="#story" @click="menuOpen = false">关于糖诗</a>
        <a href="#works" @click="menuOpen = false">婚礼作品</a>
        <a href="#categories" @click="menuOpen = false">作品分类</a>
        <RouterLink :to="{ name: 'reviews' }" @click="menuOpen = false">客户评价</RouterLink>
        <a href="#contact" @click="menuOpen = false">预约咨询</a>
      </nav>

      <div class="header-actions">
        <RouterLink class="icon-button" :to="{ name: 'customer-feedback' }" aria-label="客户中心" title="客户中心">
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
          <X v-if="menuOpen" :size="19" />
          <Menu v-else :size="19" />
        </button>
      </div>
    </header>

    <form v-if="searchOpen" class="search-bar" role="search" @submit.prevent="searchCollections">
      <label for="site-search">在作品中寻找</label>
      <div class="search-input-row">
        <input id="site-search" v-model="keyword" type="search" placeholder="婚礼、地点或风格" autofocus />
        <button type="submit" aria-label="提交搜索" title="搜索"><Search :size="18" /></button>
      </div>
    </form>

    <main>
      <section
        class="hero-section"
        aria-label="精选婚礼作品"
        @mouseenter="pauseWorkRotation"
        @mouseleave="resumeWorkRotation"
        @focusin="pauseWorkRotation"
        @focusout="resumeWorkRotation"
        @touchstart.passive="handleTouchStart"
        @touchend.passive="handleTouchEnd"
      >
        <div class="hero-media" aria-hidden="true">
          <div
            v-if="!workSlides.length"
            class="hero-slide active"
            :style="{ backgroundImage: `url(${heroImage})` }"
          ></div>
          <div
            v-for="(slide, index) in workSlides"
            :key="slide.photoId"
            :class="['hero-slide', { active: index === currentWorkIndex }]"
            :style="{
              backgroundImage: `url(${slide.originalUrl || slide.previewUrl || slide.thumbnailUrl || heroImage})`,
              backgroundPosition: `${Number(slide.focalX ?? 50)}% ${Number(slide.focalY ?? 50)}%`,
            }"
          ></div>
        </div>
        <div class="hero-overlay"></div>

        <div class="hero-content">
          <p class="eyebrow">TANGSHI AESTHETICS · HANGZHOU</p>
          <h1>一场婚礼，<br />一卷光阴。</h1>
          <p class="hero-copy">以东方的克制与温度，收藏仪式发生时无法重来的光线、表情与拥抱。</p>
          <RouterLink
            v-if="activeSlide"
            class="hero-link"
            :to="{ name: 'collection-detail', params: { collectionId: activeSlide.collectionId } }"
          >
            查看此卷作品
            <ArrowUpRight :size="17" />
          </RouterLink>
          <a v-else class="hero-link" href="#works">
            浏览公开作品
            <ArrowDown :size="17" />
          </a>
        </div>

        <div v-if="workSlides.length > 1" class="hero-controls" aria-label="首屏轮播控制">
          <span>{{ String(currentWorkIndex + 1).padStart(2, '0') }}</span>
          <div class="hero-progress" aria-hidden="true">
            <i :style="{ width: `${((currentWorkIndex + 1) / workSlides.length) * 100}%` }"></i>
          </div>
          <span>{{ String(workSlides.length).padStart(2, '0') }}</span>
          <button type="button" aria-label="上一张" title="上一张" @click="showWorkSlide(currentWorkIndex - 1)">
            <ChevronLeft :size="19" />
          </button>
          <button
            type="button"
            :aria-label="workPaused ? '继续轮播' : '暂停轮播'"
            :title="workPaused ? '继续轮播' : '暂停轮播'"
            @click="toggleWorkRotation"
          >
            <Play v-if="workPaused" :size="16" />
            <Pause v-else :size="16" />
          </button>
          <button type="button" aria-label="下一张" title="下一张" @click="showWorkSlide(currentWorkIndex + 1)">
            <ChevronRight :size="19" />
          </button>
        </div>

        <p v-if="activeSlide" class="hero-caption">
          <span>{{ activeSlide.collectionTitle }}</span>
          <span>{{ [activeSlide.locationText, formatDate(activeSlide.eventDate)].filter(Boolean).join(' · ') }}</span>
        </p>
      </section>

      <section id="story" class="brand-story-section">
        <div class="brand-story-mark">糖诗</div>
        <div class="brand-story-heading">
          <p class="section-kicker">Our point of view</p>
          <h2>不追逐喧哗，<br />只让真情自然发生。</h2>
        </div>
        <div class="brand-story-copy">
          <p>我们相信，好的婚礼影像不是一场表演，而是替时间留下证词。</p>
          <p>从晨起梳妆到夜色散场，糖诗以克制的观察、细腻的光影和对人的理解，写下每一场婚礼独有的气韵。</p>
        </div>
      </section>

      <section id="works" class="content-section featured-section">
        <div class="section-heading">
          <div>
            <p class="section-kicker">Selected stories</p>
            <h2>{{ resultTitle }}</h2>
          </div>
          <button v-if="isFilteringWorks" class="text-button" type="button" @click="clearFilters">回到近作</button>
          <p v-else>从相遇、礼成，到夜色深处。<br />每一卷，都是独一无二的叙事。</p>
        </div>

        <div v-if="loading" class="public-loading">正在展开作品...</div>
        <p v-else-if="loadError" class="public-error">{{ loadError }}</p>
        <div v-else-if="collections.length" class="work-grid">
          <RouterLink
            v-for="(work, index) in collections"
            :key="work.id"
            :to="{ name: 'collection-detail', params: { collectionId: work.id } }"
            class="work-item"
          >
            <div class="work-number">{{ String(index + 1).padStart(2, '0') }}</div>
            <div class="work-image-wrap">
              <img
                :src="work.coverOriginalUrl || work.coverPreviewUrl || work.coverThumbnailUrl || heroImage"
                :alt="work.title"
                loading="lazy"
              />
            </div>
            <div class="work-meta">
              <div>
                <p>{{ work.category?.name || '婚礼作品' }}</p>
                <h3>{{ work.title }}</h3>
              </div>
              <span>{{ work.locationText || work.creators.map((creator) => creator.displayName).join('、') }}</span>
            </div>
          </RouterLink>
        </div>
        <div v-else class="public-empty">
          <h3>暂无已发布作品</h3>
          <p>新的婚礼故事将在审核并发布后出现在这里。</p>
        </div>
      </section>

      <section id="categories" class="category-band">
        <div class="category-intro">
          <p class="section-kicker">Browse the archive</p>
          <h2>循风格，<br />寻一场心仪婚礼。</h2>
          <p>从仪式气质进入作品档案，找到与你们相近的表达。</p>
        </div>
        <div class="category-list">
          <button
            v-for="(category, index) in categories"
            :key="category.id"
            type="button"
            :class="{ active: selectedCategoryId === category.id }"
            @click="selectCategory(category.id)"
          >
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
            <strong>{{ category.name }}</strong>
            <ArrowUpRight :size="18" />
          </button>
          <p v-if="!categories.length" class="category-empty">分类正在整理中</p>
        </div>
      </section>

      <section v-if="feedback.length" class="homepage-feedback-section">
        <div class="section-heading">
          <div>
            <p class="section-kicker">Client voices</p>
            <h2>他们记住的，<br />不只是照片。</h2>
          </div>
          <RouterLink class="text-link" :to="{ name: 'reviews' }">
            读更多来信
            <ArrowUpRight :size="17" />
          </RouterLink>
        </div>
        <div class="public-feedback-list">
          <blockquote v-for="(item, index) in feedback" :key="item.id" class="public-feedback-item">
            <span class="feedback-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="public-feedback-rating" :aria-label="`${item.rating} 星`">
              <Star v-for="rating in 5" :key="rating" :size="14" :class="{ filled: rating <= item.rating }" />
            </div>
            <p>“{{ item.content }}”</p>
            <footer>
              <strong>{{ item.customerDisplayName }}</strong>
              <span>{{ item.collectionTitle }} · {{ item.creatorDisplayName }}</span>
            </footer>
            <div v-if="item.reply" class="public-feedback-reply">
              <span>创作者回复</span>
              <p>{{ item.reply.content }}</p>
            </div>
          </blockquote>
        </div>
      </section>

      <section id="contact" class="contact-section">
        <div class="contact-heading">
          <p class="section-kicker">Begin your story</p>
          <h2>愿我们在恰好的<br />光阴里相见。</h2>
          <p class="contact-copy">留下婚期、地点与期待。团队会在确认档期后，与你认真聊聊这一天。</p>
          <span class="contact-seal">预约</span>
        </div>
        <form class="inquiry-form" @submit.prevent="submitInquiry">
          <div class="inquiry-form-grid">
            <label>
              <span>你的称呼</span>
              <input v-model="inquiryForm.name" maxlength="100" autocomplete="name" placeholder="如何称呼你" required />
            </label>
            <label>
              <span>联系方式</span>
              <input v-model="inquiryForm.contact" minlength="5" maxlength="120" autocomplete="tel" placeholder="手机或微信" required />
            </label>
            <label>
              <span>婚期</span>
              <input v-model="inquiryForm.weddingDate" type="date" />
            </label>
            <label>
              <span>地点</span>
              <input v-model="inquiryForm.region" maxlength="200" placeholder="城市或场地" />
            </label>
            <label class="inquiry-wide-field">
              <span>服务期待</span>
              <input v-model="inquiryForm.serviceNeeds" maxlength="1000" placeholder="婚礼摄影、跟拍、妆造或策划" required />
            </label>
            <label class="inquiry-wide-field">
              <span>想对我们说</span>
              <textarea v-model="inquiryForm.remark" rows="4" maxlength="2000" placeholder="关于你们、婚礼，或喜欢的影像气质"></textarea>
            </label>
            <label class="inquiry-honeypot" aria-hidden="true">
              网站
              <input v-model="inquiryForm.website" tabindex="-1" autocomplete="off" />
            </label>
          </div>
          <p v-if="inquiryError" class="inquiry-message error">{{ inquiryError }}</p>
          <p v-else-if="inquiryReceipt" class="inquiry-message success">已收到咨询，编号 {{ inquiryReceipt }}</p>
          <button type="submit" :disabled="inquirySubmitting">
            <Send :size="17" />
            {{ inquirySubmitting ? '正在送出' : '送出预约' }}
          </button>
        </form>
      </section>
    </main>

    <footer class="site-footer">
      <BrandLogo />
      <p>东方婚礼影像与美学记录</p>
      <span>© 2026 糖诗·美学</span>
      <span class="service-status">
        <i :class="{ online: serviceOnline }"></i>
        {{ serviceOnline ? '服务在线' : '服务维护中' }}
      </span>
    </footer>
  </div>
</template>
