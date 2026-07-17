<script setup>
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { ArrowUpRight, Menu, Search, X } from '@lucide/vue'
import heroImage from '../assets/wedding-hero.jpg'

const menuOpen = ref(false)
const searchOpen = ref(false)
const serviceOnline = ref(false)

const categories = ['婚礼跟拍', '订婚返图', '婚纱摄影', '化妆造型']

const featuredWorks = [
  {
    title: '花园里的誓言',
    category: '婚礼跟拍',
    meta: '杭州 · 2026',
    position: 'center 38%',
  },
  {
    title: '午后订婚宴',
    category: '订婚返图',
    meta: '湖州 · 2026',
    position: 'center 54%',
  },
  {
    title: '仪式前一刻',
    category: '化妆造型',
    meta: '绍兴 · 2026',
    position: 'center 72%',
  },
]

onMounted(async () => {
  try {
    const response = await axios.get('/api/public/status')
    serviceOnline.value = response.data?.status === 'UP'
  } catch {
    serviceOnline.value = false
  }
})
</script>

<template>
  <div class="site-shell">
    <header class="site-header">
      <a class="brand" href="/" aria-label="Wedding Archive 首页">
        <span class="brand-mark">WA</span>
        <span>Wedding Archive</span>
      </a>

      <nav :class="['site-nav', { 'is-open': menuOpen }]" aria-label="主导航">
        <a href="#works" @click="menuOpen = false">婚礼作品</a>
        <a href="#categories" @click="menuOpen = false">作品分类</a>
        <a href="#stories" @click="menuOpen = false">客户评价</a>
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

    <div v-if="searchOpen" class="search-bar">
      <label for="site-search">搜索作品</label>
      <input id="site-search" type="search" placeholder="婚礼、地点或风格" autofocus />
    </div>

    <main>
      <section class="hero-section" :style="{ backgroundImage: `url(${heroImage})` }">
        <div class="hero-overlay"></div>
        <div class="hero-content">
          <p class="eyebrow">Wedding stories · 2026</p>
          <h1>婚礼作品集</h1>
          <p class="hero-copy">收藏仪式发生时，那些无法重来的光线、表情与拥抱。</p>
          <a class="hero-link" href="#works">
            浏览最新作品
            <ArrowUpRight :size="17" />
          </a>
        </div>
      </section>

      <section id="works" class="content-section featured-section">
        <div class="section-heading">
          <div>
            <p class="section-kicker">Selected work</p>
            <h2>最新作品</h2>
          </div>
          <a href="#categories" class="text-link">
            查看全部
            <ArrowUpRight :size="16" />
          </a>
        </div>

        <div class="work-grid">
          <article v-for="work in featuredWorks" :key="work.title" class="work-item">
            <div
              class="work-image"
              :style="{
                backgroundImage: `url(${heroImage})`,
                backgroundPosition: work.position,
              }"
            ></div>
            <div class="work-meta">
              <div>
                <p>{{ work.category }}</p>
                <h3>{{ work.title }}</h3>
              </div>
              <span>{{ work.meta }}</span>
            </div>
          </article>
        </div>
      </section>

      <section id="categories" class="category-band">
        <p class="section-kicker">Collections</p>
        <div class="category-list">
          <a v-for="(category, index) in categories" :key="category" href="#works">
            <span>0{{ index + 1 }}</span>
            {{ category }}
            <ArrowUpRight :size="18" />
          </a>
        </div>
      </section>

      <section id="stories" class="story-section">
        <p class="section-kicker">Client story</p>
        <blockquote>
          “照片把当天的声音和情绪都带回来了。每一次翻看，都像重新走进那场仪式。”
        </blockquote>
        <p class="story-author">林女士 · 婚礼跟拍评价</p>
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
        {{ serviceOnline ? '服务在线' : '本地预览' }}
      </span>
    </footer>
  </div>
</template>

