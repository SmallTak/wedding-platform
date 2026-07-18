<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check,
  MessageSquareReply,
  Pencil,
  Plus,
  RefreshCw,
  Star,
  Trash2,
  Undo2,
  X,
} from '@lucide/vue'
import { feedbackApi } from '../api/operations'
import { useAuthStore } from '../stores/auth'
import { apiErrorMessage, formatDateTime, statusTone } from '../utils/content'

const auth = useAuthStore()
const isAdmin = computed(() => auth.user?.accountType === 'ADMIN')
const loading = ref(false)
const saving = ref(false)
const feedback = ref([])
const options = reactive({ projects: [], creators: [] })
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const filters = reactive({
  reviewStatus: '',
  publishStatus: '',
  projectId: null,
})
const dialogVisible = ref(false)
const replyDialogVisible = ref(false)
const editingId = ref(null)
const replyingFeedback = ref(null)
const form = reactive({
  version: null,
  projectId: null,
  creatorUserId: null,
  customerDisplayName: '',
  rating: 5,
  content: '',
})
const replyForm = reactive({
  version: null,
  content: '',
})

const reviewLabels = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}
const publishLabels = {
  UNPUBLISHED: '未公开',
  PUBLISHED: '已公开',
  OFFLINE: '已下架',
}
const pendingCount = computed(() => feedback.value.filter((item) => item.reviewStatus === 'PENDING').length)
const publishedCount = computed(() => feedback.value.filter((item) => item.publishStatus === 'PUBLISHED').length)
const pendingReplyCount = computed(() =>
  feedback.value.filter((item) => item.reply?.reviewStatus === 'PENDING').length,
)
const selectedProject = computed(() =>
  options.projects.find((project) => project.id === form.projectId),
)
const availableCreators = computed(() => {
  const creatorIds = selectedProject.value?.creatorUserIds || []
  return options.creators.filter((creator) => creatorIds.includes(creator.id))
})

watch(
  () => form.projectId,
  () => {
    if (!isAdmin.value) {
      form.creatorUserId = auth.user?.id || null
      return
    }
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
    const { data } = await feedbackApi.options()
    options.projects = data.projects
    options.creators = data.creators
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '评价选项加载失败'))
  }
}

async function loadFeedback() {
  loading.value = true
  try {
    const { data } = await feedbackApi.list({
      page: page.value,
      size: 20,
      reviewStatus: filters.reviewStatus || undefined,
      publishStatus: filters.publishStatus || undefined,
      projectId: filters.projectId || undefined,
    })
    feedback.value = data.content
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '客户评价加载失败'))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    version: null,
    projectId: options.projects[0]?.id || null,
    creatorUserId: isAdmin.value ? null : auth.user?.id || null,
    customerDisplayName: '',
    rating: 5,
    content: '',
  })
}

function openCreateDialog() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(item) {
  editingId.value = item.id
  Object.assign(form, {
    version: item.version,
    projectId: item.project?.id || null,
    creatorUserId: item.creator?.id || null,
    customerDisplayName: item.customerDisplayName,
    rating: item.rating,
    content: item.content,
  })
  dialogVisible.value = true
}

async function saveFeedback() {
  if (!form.projectId || !form.creatorUserId || !form.customerDisplayName.trim() || !form.content.trim()) {
    ElMessage.warning('请完整填写项目、创作者、客户称呼和评价内容')
    return
  }
  saving.value = true
  const payload = {
    projectId: form.projectId,
    creatorUserId: form.creatorUserId,
    customerDisplayName: form.customerDisplayName.trim(),
    rating: form.rating,
    content: form.content.trim(),
  }
  if (editingId.value) payload.version = form.version
  try {
    if (editingId.value) await feedbackApi.update(editingId.value, payload)
    else await feedbackApi.create(payload)
    dialogVisible.value = false
    ElMessage.success(editingId.value ? '评价已更新并重新提交审核' : '评价已提交审核')
    await loadFeedback()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '评价保存失败'))
  } finally {
    saving.value = false
  }
}

async function approve(item) {
  try {
    await feedbackApi.approve(item.id, item.version)
    ElMessage.success('评价已通过并公开')
    await loadFeedback()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '评价审核失败'))
  }
}

async function reject(item) {
  try {
    const { value } = await ElMessageBox.prompt('填写驳回原因', '驳回客户评价', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (input) => Boolean(input?.trim()) || '请输入驳回原因',
    })
    await feedbackApi.reject(item.id, { version: item.version, reason: value.trim() })
    ElMessage.success('评价已驳回')
    await loadFeedback()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '评价驳回失败'))
  }
}

async function offline(item) {
  try {
    const { value } = await ElMessageBox.prompt('填写下架原因', '下架客户评价', {
      confirmButtonText: '确认下架',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (input) => Boolean(input?.trim()) || '请输入下架原因',
    })
    await feedbackApi.offline(item.id, { version: item.version, reason: value.trim() })
    ElMessage.success('评价已下架')
    await loadFeedback()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '评价下架失败'))
  }
}

async function withdraw(item) {
  try {
    await ElMessageBox.confirm(
      '撤回后该评价将从列表移除，且不会在官网公开。',
      '撤回客户评价',
      {
        confirmButtonText: '确认撤回',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await feedbackApi.withdraw(item.id, item.version)
    ElMessage.success('评价已撤回')
    await loadFeedback()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '评价撤回失败'))
  }
}

function openReplyDialog(item) {
  replyingFeedback.value = item
  Object.assign(replyForm, {
    version: item.reply?.version ?? null,
    content: item.reply?.content || '',
  })
  replyDialogVisible.value = true
}

async function saveReply() {
  if (!replyForm.content.trim()) {
    ElMessage.warning('请输入公开回复')
    return
  }
  saving.value = true
  try {
    await feedbackApi.reply(replyingFeedback.value.id, {
      version: replyForm.version,
      content: replyForm.content.trim(),
    })
    replyDialogVisible.value = false
    ElMessage.success('回复已提交审核')
    await loadFeedback()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '回复提交失败'))
  } finally {
    saving.value = false
  }
}

async function approveReply(item) {
  try {
    await feedbackApi.approveReply(item.id, item.reply.version)
    ElMessage.success('回复已通过并公开')
    await loadFeedback()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '回复审核失败'))
  }
}

async function rejectReply(item) {
  try {
    const { value } = await ElMessageBox.prompt('填写驳回原因', '驳回公开回复', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (input) => Boolean(input?.trim()) || '请输入驳回原因',
    })
    await feedbackApi.rejectReply(item.id, {
      version: item.reply.version,
      reason: value.trim(),
    })
    ElMessage.success('回复已驳回')
    await loadFeedback()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '回复驳回失败'))
  }
}

function runFilter() {
  page.value = 0
  loadFeedback()
}

function changePage(nextPage) {
  page.value = nextPage
  loadFeedback()
}

function canEdit(item) {
  const canChange = isAdmin.value || item.submittedBy === auth.user?.id
  return canChange
    && item.publishStatus === 'UNPUBLISHED'
    && ['PENDING', 'REJECTED'].includes(item.reviewStatus)
}

function canReply(item) {
  return !isAdmin.value
    && item.creator?.id === auth.user?.id
    && item.publishStatus === 'PUBLISHED'
    && item.reply?.reviewStatus !== 'APPROVED'
}
</script>

<template>
  <main class="dashboard-content management-content" v-loading="loading">
    <section class="management-summary operations-summary" aria-label="评价概览">
      <div><span>评价总数</span><strong>{{ totalElements }}</strong></div>
      <div><span>本页待审核</span><strong>{{ pendingCount }}</strong></div>
      <div><span>本页已公开</span><strong>{{ publishedCount }}</strong></div>
      <div><span>待审回复</span><strong>{{ pendingReplyCount }}</strong></div>
    </section>

    <section class="dashboard-section management-panel">
      <div class="management-toolbar operations-toolbar">
        <el-select v-model="filters.projectId" clearable filterable placeholder="全部项目" @change="runFilter">
          <el-option
            v-for="project in options.projects"
            :key="project.id"
            :label="`${project.projectCode} · ${project.title}`"
            :value="project.id"
          />
        </el-select>
        <el-select v-model="filters.reviewStatus" placeholder="全部审核状态" @change="runFilter">
          <el-option label="全部审核状态" value="" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-select v-model="filters.publishStatus" placeholder="全部公开状态" @change="runFilter">
          <el-option label="全部公开状态" value="" />
          <el-option label="未公开" value="UNPUBLISHED" />
          <el-option label="已公开" value="PUBLISHED" />
          <el-option label="已下架" value="OFFLINE" />
        </el-select>
        <div class="toolbar-commands">
          <button class="icon-command" type="button" aria-label="刷新" title="刷新" @click="loadFeedback">
            <RefreshCw :size="17" />
          </button>
          <button class="primary-command compact-command" type="button" @click="openCreateDialog">
            <Plus :size="17" />代提交评价
          </button>
        </div>
      </div>

      <div class="management-table feedback-management-table" role="table" aria-label="客户评价">
        <div class="management-row management-table-head" role="row">
          <span>评价</span><span>项目与创作者</span><span>星级</span><span>审核</span><span>公开</span><span>回复</span><span>操作</span>
        </div>
        <article v-for="item in feedback" :key="item.id" class="management-row" role="row">
          <div class="feedback-copy primary-cell" data-label="评价">
            <strong>{{ item.customerDisplayName }}</strong>
            <p>{{ item.content }}</p>
            <small>{{ formatDateTime(item.createdAt) }}</small>
            <small v-if="item.rejectionReason" class="negative-text">驳回：{{ item.rejectionReason }}</small>
          </div>
          <div class="feedback-target" data-label="项目与创作者">
            <strong>{{ item.project?.title || '项目已不可用' }}</strong>
            <span>{{ item.creator?.displayName || '创作者已不可用' }}</span>
          </div>
          <div class="feedback-rating" data-label="星级" :aria-label="`${item.rating} 星`">
            <Star v-for="index in 5" :key="index" :size="14" :class="{ filled: index <= item.rating }" />
          </div>
          <span data-label="审核" :class="['state-chip', statusTone(item.reviewStatus)]">
            {{ reviewLabels[item.reviewStatus] }}
          </span>
          <span data-label="公开" :class="['state-chip', statusTone(item.publishStatus)]">
            {{ publishLabels[item.publishStatus] }}
          </span>
          <div class="feedback-reply" data-label="回复">
            <template v-if="item.reply">
              <span :class="['state-chip', statusTone(item.reply.reviewStatus)]">
                {{ reviewLabels[item.reply.reviewStatus] }}
              </span>
              <small>{{ item.reply.content }}</small>
              <small v-if="item.reply.rejectionReason" class="negative-text">
                驳回：{{ item.reply.rejectionReason }}
              </small>
            </template>
            <span v-else>尚未回复</span>
          </div>
          <div class="row-commands" data-label="操作">
            <button v-if="canEdit(item)" type="button" aria-label="编辑评价" title="编辑评价" @click="openEditDialog(item)">
              <Pencil :size="16" />
            </button>
            <button v-if="canEdit(item)" class="danger-command" type="button" aria-label="撤回评价" title="撤回评价" @click="withdraw(item)">
              <Trash2 :size="16" />
            </button>
            <button v-if="isAdmin && item.reviewStatus === 'PENDING'" type="button" aria-label="通过评价" title="通过评价" @click="approve(item)">
              <Check :size="16" />
            </button>
            <button v-if="isAdmin && item.reviewStatus === 'PENDING'" class="danger-command" type="button" aria-label="驳回评价" title="驳回评价" @click="reject(item)">
              <X :size="16" />
            </button>
            <button v-if="isAdmin && item.publishStatus === 'PUBLISHED'" type="button" aria-label="下架评价" title="下架评价" @click="offline(item)">
              <Undo2 :size="16" />
            </button>
            <button v-if="canReply(item)" type="button" aria-label="提交公开回复" title="提交公开回复" @click="openReplyDialog(item)">
              <MessageSquareReply :size="16" />
            </button>
            <button v-if="isAdmin && item.reply?.reviewStatus === 'PENDING'" type="button" aria-label="通过回复" title="通过回复" @click="approveReply(item)">
              <Check :size="16" />
            </button>
            <button v-if="isAdmin && item.reply?.reviewStatus === 'PENDING'" class="danger-command" type="button" aria-label="驳回回复" title="驳回回复" @click="rejectReply(item)">
              <X :size="16" />
            </button>
          </div>
        </article>
        <div v-if="!loading && feedback.length === 0" class="empty-table">暂无符合条件的客户评价</div>
      </div>

      <div v-if="totalPages > 1" class="management-pagination">
        <el-button :disabled="page === 0" @click="changePage(page - 1)">上一页</el-button>
        <span>{{ page + 1 }} / {{ totalPages }}</span>
        <el-button :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">下一页</el-button>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑客户评价' : '代提交客户评价'" width="620px" class="management-dialog">
      <form id="feedback-form" class="dialog-form" @submit.prevent="saveFeedback">
        <label>
          婚礼项目
          <el-select v-model="form.projectId" filterable placeholder="选择婚礼项目">
            <el-option
              v-for="project in options.projects"
              :key="project.id"
              :label="`${project.projectCode} · ${project.title}`"
              :value="project.id"
            />
          </el-select>
        </label>
        <label>
          被评价创作者
          <el-select v-model="form.creatorUserId" :disabled="!isAdmin" placeholder="选择项目参与创作者">
            <el-option
              v-for="creator in availableCreators"
              :key="creator.id"
              :label="creator.displayName"
              :value="creator.id"
            />
          </el-select>
        </label>
        <label>
          客户称呼
          <el-input v-model="form.customerDisplayName" maxlength="100" show-word-limit placeholder="例如：林女士" />
        </label>
        <label>
          星级
          <el-rate v-model="form.rating" />
        </label>
        <label>
          评价内容
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </label>
      </form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" native-type="submit" form="feedback-form" :loading="saving">提交审核</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="replyDialogVisible" title="公开回复" width="560px" class="management-dialog">
      <form id="feedback-reply-form" class="dialog-form" @submit.prevent="saveReply">
        <label>
          回复内容
          <el-input v-model="replyForm.content" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </label>
      </form>
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" native-type="submit" form="feedback-reply-form" :loading="saving">提交审核</el-button>
      </template>
    </el-dialog>
  </main>
</template>
