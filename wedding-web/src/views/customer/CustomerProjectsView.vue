<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ArrowUpRight,
  CalendarDays,
  CheckCircle2,
  Clock3,
  MapPin,
  RefreshCw,
  Send,
  XCircle,
} from '@lucide/vue'
import { customerProjectApi } from '../../api/customer'

const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const applications = ref([])
const linkedProjects = ref([])
const form = reactive({ projectCode: '', applyNote: '' })

const pendingCount = computed(() => applications.value.filter((item) => item.status === 'PENDING').length)
const rejectedCount = computed(() => applications.value.filter((item) => item.status === 'REJECTED').length)
const statusLabels = {
  PENDING: '待审核',
  APPROVED: '已关联',
  REJECTED: '已驳回',
}
const statusIcons = {
  PENDING: Clock3,
  APPROVED: CheckCircle2,
  REJECTED: XCircle,
}

onMounted(loadData)

async function loadData() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [applicationResponse, linkedResponse] = await Promise.all([
      customerProjectApi.applications(),
      customerProjectApi.linked(),
    ])
    applications.value = applicationResponse.data
    linkedProjects.value = linkedResponse.data
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '项目关联记录加载失败'
  } finally {
    loading.value = false
  }
}

async function apply() {
  if (!form.projectCode.trim() || !form.applyNote.trim()) {
    errorMessage.value = '请填写项目编号和申请说明'
    return
  }
  submitting.value = true
  errorMessage.value = ''
  successMessage.value = ''
  const existing = applications.value.find((item) =>
    item.project?.projectCode?.toLowerCase() === form.projectCode.trim().toLowerCase(),
  )
  try {
    await customerProjectApi.apply({
      projectCode: form.projectCode.trim(),
      applyNote: form.applyNote.trim(),
      version: existing?.status === 'REJECTED' ? existing.version : null,
    })
    form.projectCode = ''
    form.applyNote = ''
    successMessage.value = existing?.status === 'REJECTED'
      ? '申请已重新提交'
      : '关联申请已提交'
    await loadData()
  } catch (error) {
    const code = error.response?.data?.code
    if (code === 'PROJECT_CODE_NOT_FOUND') errorMessage.value = '未找到该项目编号，请向运营人员确认'
    else if (code === 'CUSTOMER_PROJECT_APPLICATION_PENDING') errorMessage.value = '该项目的关联申请正在审核中'
    else if (code === 'CUSTOMER_PROJECT_ALREADY_LINKED') errorMessage.value = '该项目已经关联'
    else if (code === 'CUSTOMER_PROJECT_VERSION_CONFLICT') {
      errorMessage.value = '申请状态已变化，请刷新后重新提交'
      await loadData()
    } else errorMessage.value = error.response?.data?.message || '关联申请提交失败'
  } finally {
    submitting.value = false
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
  <div class="customer-page">
    <section class="customer-page-heading">
      <div>
        <p>Project access</p>
        <h1>项目关联</h1>
      </div>
      <button class="customer-icon-button" type="button" aria-label="刷新关联记录" title="刷新" @click="loadData">
        <RefreshCw :size="18" />
      </button>
    </section>

    <section class="customer-summary-strip" aria-label="项目关联概览">
      <div><span>已关联项目</span><strong>{{ linkedProjects.length }}</strong></div>
      <div><span>待审核申请</span><strong>{{ pendingCount }}</strong></div>
      <div><span>已驳回申请</span><strong>{{ rejectedCount }}</strong></div>
    </section>

    <section class="customer-section customer-application-section">
      <div class="customer-section-heading">
        <div>
          <h2>申请关联项目</h2>
          <p>项目编号由运营人员提供。审核通过后，才可以评价该项目中的参与创作者。</p>
        </div>
      </div>
      <form class="customer-application-form" @submit.prevent="apply">
        <label>
          项目编号
          <input
            v-model.trim="form.projectCode"
            type="text"
            maxlength="32"
            placeholder="例如：TS20260718"
            required
          />
          <small>请完整填写编号中的字母、数字、短横线或下划线。</small>
        </label>
        <label class="customer-wide-field">
          申请说明
          <textarea
            v-model.trim="form.applyNote"
            rows="4"
            maxlength="1000"
            placeholder="说明您与该婚礼项目的关系，便于运营人员核验。"
            required
          ></textarea>
          <small>建议填写新人称呼、婚礼日期或其他便于确认的信息。</small>
        </label>
        <p v-if="errorMessage" class="customer-form-message error" role="alert">{{ errorMessage }}</p>
        <p v-if="successMessage" class="customer-form-message success">{{ successMessage }}</p>
        <button class="customer-primary-command" type="submit" :disabled="submitting">
          <Send :size="17" />
          {{ submitting ? '正在提交' : '提交申请' }}
        </button>
      </form>
    </section>

    <section class="customer-section">
      <div class="customer-section-heading">
        <div>
          <h2>关联记录</h2>
          <p>已驳回的申请修正说明后，可使用同一项目编号重新提交。</p>
        </div>
      </div>

      <div v-if="loading" class="customer-empty-state">正在加载关联记录...</div>
      <div v-else-if="applications.length" class="customer-record-list">
        <article v-for="item in applications" :key="item.id" class="customer-project-record">
          <div class="customer-record-title">
            <component :is="statusIcons[item.status]" :size="20" />
            <div>
              <span>{{ item.project?.projectCode || '项目已不可用' }}</span>
              <h3>{{ item.project?.detailsVisible ? item.project.title : '项目内容暂不展示' }}</h3>
            </div>
            <strong :class="['customer-state-chip', item.status.toLowerCase()]">
              {{ statusLabels[item.status] }}
            </strong>
          </div>
          <div v-if="item.project?.detailsVisible" class="customer-project-meta">
            <span v-if="item.project.eventDate"><CalendarDays :size="15" />{{ formatDate(item.project.eventDate) }}</span>
            <span v-if="item.project.locationText"><MapPin :size="15" />{{ item.project.locationText }}</span>
          </div>
          <p class="customer-apply-note">{{ item.applyNote }}</p>
          <p v-if="item.rejectionReason" class="customer-rejection-reason">驳回原因：{{ item.rejectionReason }}</p>
          <RouterLink
            v-if="item.status === 'APPROVED' && item.project?.publicDetailAvailable"
            class="customer-text-link"
            :to="{ name: 'project-detail', params: { projectId: item.project.id } }"
          >
            查看公开项目
            <ArrowUpRight :size="16" />
          </RouterLink>
        </article>
      </div>
      <div v-else class="customer-empty-state">还没有项目关联记录</div>
    </section>
  </div>
</template>
