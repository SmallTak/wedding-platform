<script setup>
import { onMounted, ref } from 'vue'
import { ArrowLeft, ChevronLeft, ChevronRight, Search } from '@lucide/vue'
import heroImage from '../assets/wedding-hero.jpg'
import { publicApi } from '../api/public'

const loading = ref(false)
const errorMessage = ref('')
const projects = ref([])
const keyword = ref('')
const page = ref(0)
const totalPages = ref(0)

onMounted(loadProjects)

async function loadProjects() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await publicApi.projects({
      page: page.value,
      size: 12,
      keyword: keyword.value.trim() || undefined,
    })
    projects.value = data.content
    totalPages.value = data.totalPages
  } catch {
    projects.value = []
    totalPages.value = 0
    errorMessage.value = '婚礼项目暂时无法加载，请稍后再试。'
  } finally {
    loading.value = false
  }
}

function searchProjects() {
  page.value = 0
  loadProjects()
}

function changePage(nextPage) {
  if (nextPage < 0 || nextPage >= totalPages.value || nextPage === page.value) return
  page.value = nextPage
  loadProjects()
  window.scrollTo({ top: 0, behavior: 'smooth' })
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
  <div class="site-shell project-list-page">
    <header class="site-header detail-header">
      <RouterLink class="brand" to="/" aria-label="Wedding Archive 首页">
        <span class="brand-mark">WA</span>
        <span>Wedding Archive</span>
      </RouterLink>
      <RouterLink class="detail-back-link" to="/">
        <ArrowLeft :size="17" />
        返回首页
      </RouterLink>
    </header>

    <main>
      <section class="project-list-heading">
        <div>
          <p class="section-kicker">Wedding projects</p>
          <h1>婚礼项目</h1>
        </div>
        <form role="search" @submit.prevent="searchProjects">
          <input v-model="keyword" type="search" placeholder="搜索标题、新人或地点" />
          <button type="submit" aria-label="搜索项目" title="搜索项目">
            <Search :size="18" />
          </button>
        </form>
      </section>

      <section class="project-list-content">
        <div v-if="loading" class="public-loading">正在加载婚礼项目...</div>
        <p v-else-if="errorMessage" class="public-error">{{ errorMessage }}</p>
        <div v-else-if="projects.length" class="project-grid">
          <RouterLink
            v-for="project in projects"
            :key="project.id"
            :to="{ name: 'project-detail', params: { projectId: project.id } }"
            class="project-list-item"
          >
            <img
              :src="project.coverThumbnailUrl || project.coverPreviewUrl || heroImage"
              :alt="project.title"
              loading="lazy"
            />
            <div>
              <span>{{ formatDate(project.eventDate) }}</span>
              <h2>{{ project.title }}</h2>
              <p>{{ project.locationText }}</p>
            </div>
          </RouterLink>
        </div>
        <div v-else class="public-empty">
          <h3>暂无公开项目</h3>
          <p>已公开发布的婚礼项目会显示在这里。</p>
        </div>

        <nav v-if="totalPages > 1" class="public-pagination" aria-label="项目分页">
          <button
            type="button"
            aria-label="上一页"
            title="上一页"
            :disabled="page === 0"
            @click="changePage(page - 1)"
          >
            <ChevronLeft :size="18" />
          </button>
          <span>{{ page + 1 }} / {{ totalPages }}</span>
          <button
            type="button"
            aria-label="下一页"
            title="下一页"
            :disabled="page + 1 >= totalPages"
            @click="changePage(page + 1)"
          >
            <ChevronRight :size="18" />
          </button>
        </nav>
      </section>
    </main>
  </div>
</template>
