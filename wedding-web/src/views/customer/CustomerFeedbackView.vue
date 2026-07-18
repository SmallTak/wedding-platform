<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ChevronLeft,
  ChevronRight,
  Pencil,
  Plus,
  RefreshCw,
  Star,
  Trash2,
  X,
} from '@lucide/vue'
import { customerFeedbackApi } from '../../api/customer'

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const feedback = ref([])
const options = reactive({ projects: [], creators: [] })
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const reviewStatus = ref('')
const editorOpen = ref(false)
const editingId = ref(null)
const form = reactive({
  version: null,
  projectId: null,
  creatorUserId: null,
  rating: 5,
  content: '',
})

const reviewLabels = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
const publishLabels = { UNPUBLISHED: '未公开', PUBLISHED: '已公开', OFFLINE: '已下架' }
const selectedProject = computed(() => options.projects.find((item) => item.id === form.projectId))
const availableCreators = computed(() => {
  const creatorIds = selectedProject.value?.creatorUserIds || []
  return options.creators.filter((creator) => creatorIds.includes(creator.id))
})

watch(
  () => form.projectId,
  () => {
    if (!availableCreators.value.some((creator) => creator.id === form.creatorUserId)) {
      form.creatorUserId = availableCreators.value[0]?.id || null
    }
  },
)

onMounted(async () => {
  await Promise.all([loadOptions(), loadFeedback()])
})

async function loadOptions() {
  try {
    const { data } = await customerFeedbackApi.options()
    options.projects = data.projects
    options.creators = data.creators
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '评价选项加载失败'
  }
}

async function loadFeedback() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await customerFeedbackApi.list({
      page: page.value,
      size: 12,
      reviewStatus: reviewStatus.value || undefined,
    })
    feedback.value = data.content
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '评价记录加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    version: null,
    projectId: options.projects[0]?.id || null,
    creatorUserId: null,
    rating: 5,
    content: '',
  })
  editorOpen.value = true
}

function openEdit(item) {
  editingId.value = item.id
  Object.assign(form, {
    version: item.version,
    projectId: item.project?.id || null,
    creatorUserId: item.creator?.id || null,
    rating: item.rating,
    content: item.content,
  })
  editorOpen.value = true
}

async function save() {
  if (!form.projectId || !form.creatorUserId || !form.content.trim()) {
    errorMessage.value = '请选择项目和创作者，并填写评价内容'
    return
  }
  saving.value = true
  errorMessage.value = ''
  const payload = {
    projectId: form.projectId,
    creatorUserId: form.creatorUserId,
    rating: form.rating,
    content: form.content.trim(),
  }
  if (editingId.value) payload.version = form.version
  try {
    if (editingId.value) await customerFeedbackApi.update(editingId.value, payload)
    else await customerFeedbackApi.create(payload)
    editorOpen.value = false
    await loadFeedback()
  } catch (error) {
    const code = error.response?.data?.code
    if (code === 'CUSTOMER_PROJECT_LINK_REQUIRED') errorMessage.value = '项目关联已失效，暂时不能提交评价'
    else if (code === 'FEEDBACK_CREATOR_NOT_IN_PROJECT') errorMessage.value = '该创作者已不在所选项目中'
    else errorMessage.value = error.response?.data?.message || '评价保存失败'
  } finally {
    saving.value = false
  }
}

async function withdraw(item) {
  if (!window.confirm('确认撤回这条评价？撤回后记录将从列表移除。')) return
  try {
    await customerFeedbackApi.withdraw(item.id, item.version)
    await loadFeedback()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '评价撤回失败'
  }
}

function canEdit(item) {
  return item.publishStatus === 'UNPUBLISHED'
    && ['PENDING', 'REJECTED'].includes(item.reviewStatus)
}

function changePage(nextPage) {
  if (nextPage < 0 || nextPage >= totalPages.value) return
  page.value = nextPage
  loadFeedback()
}

function runFilter() {
  page.value = 0
  loadFeedback()
}

function formatDate(value) {
  return value ? new Intl.DateTimeFormat('zh-CN').format(new Date(value)) : ''
}
</script>

<template>
  <div class="customer-page">
    <section class="customer-page-heading">
      <div>
        <p>Client feedback</p>
        <h1>我的评价</h1>
      </div>
      <div class="customer-heading-actions">
        <button class="customer-icon-button" type="button" aria-label="刷新评价" title="刷新" @click="loadFeedback">
          <RefreshCw :size="18" />
        </button>
        <button class="customer-primary-command" type="button" :disabled="options.projects.length === 0" @click="openCreate">
          <Plus :size="17" />
          写评价
        </button>
      </div>
    </section>

    <section class="customer-summary-strip customer-feedback-summary" aria-label="评价概览">
      <div><span>评价总数</span><strong>{{ totalElements }}</strong></div>
      <div>
        <span>关联项目</span>
        <strong>{{ options.projects.length }}</strong>
      </div>
    </section>

    <section v-if="options.projects.length === 0" class="customer-context-notice">
      关联申请审核通过后，才能选择项目参与创作者并提交评价。
      <RouterLink :to="{ name: 'customer-projects' }">前往项目关联</RouterLink>
    </section>

    <section v-if="editorOpen" class="customer-section customer-feedback-editor">
      <div class="customer-section-heading">
        <div>
          <h2>{{ editingId ? '修改评价' : '提交评价' }}</h2>
          <p>评价通过审核并公开前可以修改或撤回；公开后内容将锁定。</p>
        </div>
        <button class="customer-icon-button" type="button" aria-label="关闭编辑" title="关闭" @click="editorOpen = false">
          <X :size="18" />
        </button>
      </div>
      <form class="customer-feedback-form" @submit.prevent="save">
        <label>
          婚礼项目
          <select v-model="form.projectId" required>
            <option v-for="project in options.projects" :key="project.id" :value="project.id">
              {{ project.projectCode }} · {{ project.title || '项目内容暂不展示' }}
            </option>
          </select>
        </label>
        <label>
          被评价创作者
          <select v-model="form.creatorUserId" required>
            <option v-for="creator in availableCreators" :key="creator.id" :value="creator.id">
              {{ creator.displayName || '创作者' }}
              {{ creator.professionalRoles.length ? `· ${creator.professionalRoles.join(' / ')}` : '' }}
            </option>
          </select>
        </label>
        <fieldset class="customer-rating-field">
          <legend>星级</legend>
          <div>
            <button
              v-for="rating in 5"
              :key="rating"
              type="button"
              :class="{ active: rating <= form.rating }"
              :aria-label="`${rating} 星`"
              @click="form.rating = rating"
            >
              <Star :size="21" />
            </button>
          </div>
        </fieldset>
        <label class="customer-wide-field">
          评价内容
          <textarea v-model.trim="form.content" rows="7" maxlength="2000" required></textarea>
          <small>{{ form.content.length }} / 2000</small>
        </label>
        <button class="customer-primary-command" type="submit" :disabled="saving">
          {{ saving ? '正在提交' : '提交审核' }}
        </button>
      </form>
    </section>

    <section class="customer-section">
      <div class="customer-section-heading customer-feedback-toolbar">
        <div>
          <h2>评价记录</h2>
          <p>驳回后修改会重新进入审核流程。</p>
        </div>
        <select v-model="reviewStatus" aria-label="筛选审核状态" @change="runFilter">
          <option value="">全部审核状态</option>
          <option value="PENDING">待审核</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option>
        </select>
      </div>

      <p v-if="errorMessage" class="customer-form-message error" role="alert">{{ errorMessage }}</p>
      <div v-if="loading" class="customer-empty-state">正在加载评价记录...</div>
      <div v-else-if="feedback.length" class="customer-record-list">
        <article v-for="item in feedback" :key="item.id" class="customer-feedback-record">
          <div class="customer-feedback-record-head">
            <div>
              <span>{{ item.project?.projectCode }}</span>
              <h3>{{ item.project?.title || '项目内容暂不展示' }} · {{ item.creator?.displayName || '创作者' }}</h3>
            </div>
            <div class="customer-feedback-rating" :aria-label="`${item.rating} 星`">
              <Star v-for="index in 5" :key="index" :size="15" :class="{ filled: index <= item.rating }" />
            </div>
          </div>
          <p>{{ item.content }}</p>
          <div class="customer-feedback-status">
            <span :class="['customer-state-chip', item.reviewStatus.toLowerCase()]">
              {{ reviewLabels[item.reviewStatus] }}
            </span>
            <span :class="['customer-state-chip', item.publishStatus.toLowerCase()]">
              {{ publishLabels[item.publishStatus] }}
            </span>
            <time>{{ formatDate(item.createdAt) }}</time>
          </div>
          <p v-if="item.rejectionReason" class="customer-rejection-reason">驳回原因：{{ item.rejectionReason }}</p>
          <p v-if="item.offlineReason" class="customer-rejection-reason">下架原因：{{ item.offlineReason }}</p>
          <div v-if="canEdit(item)" class="customer-record-actions">
            <button type="button" @click="openEdit(item)"><Pencil :size="16" />修改</button>
            <button class="danger" type="button" @click="withdraw(item)"><Trash2 :size="16" />撤回</button>
          </div>
        </article>
      </div>
      <div v-else class="customer-empty-state">暂无符合条件的评价记录</div>

      <nav v-if="totalPages > 1" class="customer-pagination" aria-label="评价分页">
        <button type="button" aria-label="上一页" :disabled="page === 0" @click="changePage(page - 1)">
          <ChevronLeft :size="18" />
        </button>
        <span>{{ page + 1 }} / {{ totalPages }}</span>
        <button type="button" aria-label="下一页" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">
          <ChevronRight :size="18" />
        </button>
      </nav>
    </section>
  </div>
</template>
