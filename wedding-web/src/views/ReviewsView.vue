<script setup>
import { onMounted, ref } from 'vue'
import { ArrowLeft, ChevronLeft, ChevronRight, Star } from '@lucide/vue'
import BrandLogo from '../components/BrandLogo.vue'
import { publicApi } from '../api/public'

const loading = ref(false)
const errorMessage = ref('')
const feedback = ref([])
const page = ref(0)
const totalPages = ref(0)

onMounted(loadFeedback)

async function loadFeedback() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await publicApi.feedback({ page: page.value, size: 12 })
    feedback.value = data.content
    totalPages.value = data.totalPages
  } catch {
    feedback.value = []
    totalPages.value = 0
    errorMessage.value = '客户评价暂时无法加载，请稍后再试。'
  } finally {
    loading.value = false
  }
}

function changePage(nextPage) {
  if (nextPage < 0 || nextPage >= totalPages.value || nextPage === page.value) return
  page.value = nextPage
  loadFeedback()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <div class="site-shell reviews-page">
    <header class="site-header detail-header">
      <RouterLink class="brand" to="/" aria-label="糖诗·美学首页">
        <BrandLogo />
      </RouterLink>
      <RouterLink class="detail-back-link" to="/">
        <ArrowLeft :size="17" />
        返回首页
      </RouterLink>
    </header>

    <main>
      <section class="reviews-heading">
        <p class="section-kicker">Client voices</p>
        <h1>被认真记住的感受</h1>
      </section>
      <section class="reviews-content">
        <div v-if="loading" class="public-loading">正在加载客户评价...</div>
        <p v-else-if="errorMessage" class="public-error">{{ errorMessage }}</p>
        <div v-else-if="feedback.length" class="public-feedback-list reviews-list">
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
        <div v-else class="public-empty">
          <h3>暂无公开评价</h3>
          <p>审核通过的客户评价会显示在这里。</p>
        </div>

        <nav v-if="totalPages > 1" class="public-pagination" aria-label="评价分页">
          <button type="button" aria-label="上一页" title="上一页" :disabled="page === 0" @click="changePage(page - 1)">
            <ChevronLeft :size="18" />
          </button>
          <span>{{ page + 1 }} / {{ totalPages }}</span>
          <button type="button" aria-label="下一页" title="下一页" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">
            <ChevronRight :size="18" />
          </button>
        </nav>
      </section>
    </main>
  </div>
</template>
