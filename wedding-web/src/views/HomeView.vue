<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ArrowUpRight, Menu, Search, X } from '@lucide/vue'
import heroImage from '../assets/wedding-hero.jpg'
import { publicApi } from '../api/public'

const menuOpen = ref(false)
const searchOpen = ref(false)
const serviceOnline = ref(false)
const loading = ref(false)
const loadError = ref('')
const categories = ref([])
const collections = ref([])
const keyword = ref('')
const selectedCategoryId = ref(null)

const heroBackground = computed(() => collections.value[0]?.coverPreviewUrl || heroImage)
const resultTitle = computed(() => {
  if (keyword.value.trim()) return `“${keyword.value.trim()}”的作品`
  const category = categories.value.find((item) => item.id === selectedCategoryId.value)
  return category ? category.name : '最新作品'
})

onMounted(async () => {
  await Promise.all([loadStatus(), loadCategories(), loadCollections()])
})

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

async function loadCollections() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await publicApi.collections({
      page: 0,
      size: 12,
      keyword: keyword.value.trim() || undefined,
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
  await loadCollections()
  await nextTick()
  document.querySelector('#works')?.scrollIntoView({ behavior: 'smooth' })
}

async function selectCategory(categoryId) {
  keyword.value = ''
  selectedCategoryId.value = selectedCategoryId.value === categoryId ? null : categoryId
  await loadCollections()
  await nextTick()
  document.querySelector('#works')?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
  <div class="site-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/" aria-label="Wedding Archive 首页">
        <span class="brand-mark">WA</span>
        <span>Wedding Archive</span>
      </RouterLink>

      <nav :class="['site-nav', { 'is-open': menuOpen }]" aria-label="主导航">
        <a href="#works" @click="menuOpen = false">婚礼作品</a>
        <a href="#categories" @click="menuOpen = false">作品分类</a>
        <a href="#contact" @click="menuOpen = false">预约咨询</a>
      </nav>

      <div class="header-actions">
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
          aria-label="打开导航"
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
      <section class="hero-section" :style="{ backgroundImage: `url(${heroBackground})` }">
        <div class="hero-overlay"></div>
        <div class="hero-content">
          <p class="eyebrow">Wedding stories · 2026</p>
          <h1>Wedding Archive</h1>
          <p class="hero-copy">收藏仪式发生时，那些无法重来的光线、表情与拥抱。</p>
          <a class="hero-link" href="#works">
            浏览公开作品
            <ArrowUpRight :size="17" />
          </a>
        </div>
      </section>

      <section id="works" class="content-section featured-section">
        <div class="section-heading">
          <div>
            <p class="section-kicker">Published work</p>
            <h2>{{ resultTitle }}</h2>
          </div>
          <button
            v-if="selectedCategoryId || keyword"
            class="text-button"
            type="button"
            @click="keyword = ''; selectedCategoryId = null; loadCollections()"
          >查看全部</button>
        </div>

        <div v-if="loading" class="public-loading">正在加载作品...</div>
        <p v-else-if="loadError" class="public-error">{{ loadError }}</p>
        <div v-else-if="collections.length" class="work-grid">
          <RouterLink
            v-for="work in collections"
            :key="work.id"
            :to="{ name: 'collection-detail', params: { collectionId: work.id } }"
            class="work-item"
          >
            <div
              class="work-image"
              :style="{ backgroundImage: `url(${work.coverThumbnailUrl || work.coverPreviewUrl || heroImage})` }"
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
        <div v-else class="public-empty">
          <h3>暂无已发布作品</h3>
          <p>新的婚礼故事将在审核并发布后出现在这里。</p>
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

      <section id="contact" class="contact-section">
        <div>
          <p class="section-kicker">Enquiry</p>
          <h2>告诉我们你的婚期</h2>
        </div>
        <a class="contact-link" href="mailto:hello@example.com">
          预约咨询
          <ArrowUpRight :size="18" />
        </a>
      </section>
    </main>

    <footer class="site-footer">
      <span>© 2026 Wedding Archive</span>
      <span class="service-status">
        <i :class="{ online: serviceOnline }"></i>
        {{ serviceOnline ? '服务在线' : '服务维护中' }}
      </span>
    </footer>
  </div>
</template>
