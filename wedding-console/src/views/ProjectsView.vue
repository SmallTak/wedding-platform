<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  CalendarDays,
  MapPin,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  UsersRound,
} from '@lucide/vue'
import { projectApi } from '../api/content'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'
import {
  apiErrorMessage,
  formatDate,
  isVersionConflict,
  publishStatusLabels,
  reviewStatusLabels,
  statusTone,
} from '../utils/content'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const assigning = ref(false)
const creatorsLoading = ref(false)
const projects = ref([])
const creators = ref([])
const keyword = ref('')
const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const formDialogVisible = ref(false)
const creatorDialogVisible = ref(false)
const editingId = ref(null)
const creatorProject = ref(null)
const creatorUserIds = ref([])

const form = reactive({
  version: null,
  title: '',
  coupleDisplayName: '',
  eventDate: '',
  regionCode: '',
  locationText: '',
  description: '',
})

const isAdmin = computed(() => auth.user?.accountType === 'ADMIN')
const currentPagePublished = computed(
  () => projects.value.filter((project) => project.publishStatus === 'PUBLISHED').length,
)
const currentPageDrafts = computed(
  () => projects.value.filter((project) => project.reviewStatus === 'DRAFT').length,
)
const activeCreators = computed(() =>
  creators.value.filter((creator) => creator.accountStatus === 'ACTIVE'),
)

onMounted(loadProjects)

async function loadProjects() {
  loading.value = true
  try {
    const { data } = await projectApi.list({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
    })
    projects.value = data.content
    totalElements.value = data.totalElements
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '婚礼项目加载失败'))
  } finally {
    loading.value = false
  }
}

function runSearch() {
  page.value = 0
  loadProjects()
}

function resetSearch() {
  keyword.value = ''
  page.value = 0
  loadProjects()
}

function changePage(value) {
  page.value = value - 1
  loadProjects()
}

function resetForm() {
  Object.assign(form, {
    version: null,
    title: '',
    coupleDisplayName: '',
    eventDate: '',
    regionCode: '',
    locationText: '',
    description: '',
  })
}

function openCreateDialog() {
  editingId.value = null
  resetForm()
  formDialogVisible.value = true
}

function openEditDialog(project) {
  editingId.value = project.id
  Object.assign(form, {
    version: project.version,
    title: project.title,
    coupleDisplayName: project.coupleDisplayName || '',
    eventDate: project.eventDate,
    regionCode: project.regionCode,
    locationText: project.locationText,
    description: project.description || '',
  })
  formDialogVisible.value = true
}

function validateForm() {
  if (!form.title.trim() || !form.eventDate || !form.regionCode.trim() || !form.locationText.trim()) {
    ElMessage.warning('请完整填写项目标题、日期、地区编码和地点')
    return false
  }
  return true
}

async function saveProject() {
  if (!validateForm()) return
  saving.value = true
  const payload = {
    title: form.title.trim(),
    coupleDisplayName: form.coupleDisplayName.trim() || null,
    eventDate: form.eventDate,
    regionCode: form.regionCode.trim(),
    locationText: form.locationText.trim(),
    description: form.description.trim() || null,
  }
  try {
    if (editingId.value) {
      await projectApi.update(editingId.value, { version: form.version, ...payload })
      ElMessage.success('项目资料已更新')
    } else {
      await projectApi.create(payload)
      ElMessage.success('婚礼项目已创建')
    }
    formDialogVisible.value = false
    await loadProjects()
  } catch (error) {
    if (isVersionConflict(error)) {
      formDialogVisible.value = false
      await loadProjects()
    }
    ElMessage.error(apiErrorMessage(error, editingId.value ? '项目更新失败' : '项目创建失败'))
  } finally {
    saving.value = false
  }
}

async function loadCreators() {
  if (creators.value.length) return
  creatorsLoading.value = true
  try {
    const { data } = await http.get('/admin/creators')
    creators.value = data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '创作者列表加载失败'))
    throw error
  } finally {
    creatorsLoading.value = false
  }
}

async function openCreatorDialog(project) {
  creatorProject.value = project
  creatorUserIds.value = project.creators.map((creator) => creator.userId)
  creatorDialogVisible.value = true
  try {
    await loadCreators()
  } catch {
    creatorDialogVisible.value = false
  }
}

async function assignCreators() {
  if (!creatorProject.value) return
  assigning.value = true
  try {
    await projectApi.assignCreators(creatorProject.value.id, {
      version: creatorProject.value.version,
      creatorUserIds: creatorUserIds.value,
    })
    creatorDialogVisible.value = false
    ElMessage.success('项目参与创作者已更新')
    await loadProjects()
  } catch (error) {
    if (isVersionConflict(error)) {
      creatorDialogVisible.value = false
      await loadProjects()
    }
    ElMessage.error(apiErrorMessage(error, '参与创作者更新失败'))
  } finally {
    assigning.value = false
  }
}

function creatorNames(project) {
  if (!project.creators.length) return '暂无参与者'
  return project.creators.map((creator) => creator.displayName || `用户 ${creator.userId}`).join('、')
}
</script>

<template>
  <main class="dashboard-content management-content" v-loading="loading">
    <section class="management-summary" aria-label="项目概览">
      <div><span>可访问项目</span><strong>{{ totalElements }}</strong></div>
      <div><span>本页草稿</span><strong>{{ currentPageDrafts }}</strong></div>
      <div><span>本页已发布</span><strong>{{ currentPagePublished }}</strong></div>
    </section>

    <section class="dashboard-section management-panel">
      <form class="management-toolbar" role="search" @submit.prevent="runSearch">
        <div class="table-search">
          <Search :size="17" />
          <input v-model="keyword" type="search" placeholder="搜索项目编号、标题、新人或地点" />
        </div>
        <div class="toolbar-commands">
          <button
            class="icon-command"
            type="button"
            aria-label="重置搜索"
            title="重置搜索"
            @click="resetSearch"
          >
            <RefreshCw :size="17" />
          </button>
          <button class="primary-command compact-command" type="button" @click="openCreateDialog">
            <Plus :size="17" />新建项目
          </button>
        </div>
      </form>

      <div class="management-table project-management-table" role="table" aria-label="婚礼项目">
        <div class="management-row management-table-head" role="row">
          <span>项目</span><span>日期与地点</span><span>参与创作者</span><span>审核</span><span>发布</span><span>操作</span>
        </div>
        <article v-for="project in projects" :key="project.id" class="management-row" role="row">
          <div class="primary-cell" data-label="项目">
            <strong>{{ project.title }}</strong>
            <small>{{ project.projectCode }}<template v-if="project.coupleDisplayName"> · {{ project.coupleDisplayName }}</template></small>
          </div>
          <div class="project-place" data-label="日期与地点">
            <span><CalendarDays :size="14" />{{ formatDate(project.eventDate) }}</span>
            <span><MapPin :size="14" />{{ project.locationText }}</span>
          </div>
          <div class="creator-cell" data-label="参与创作者" :title="creatorNames(project)">
            <UsersRound :size="15" />
            <span>{{ creatorNames(project) }}</span>
          </div>
          <span data-label="审核" :class="['state-chip', statusTone(project.reviewStatus)]">
            {{ reviewStatusLabels[project.reviewStatus] || project.reviewStatus }}
          </span>
          <span data-label="发布" :class="['state-chip', statusTone(project.publishStatus)]">
            {{ publishStatusLabels[project.publishStatus] || project.publishStatus }}
          </span>
          <div class="row-commands" data-label="操作">
            <button
              type="button"
              aria-label="编辑项目"
              title="编辑项目"
              :disabled="project.publishStatus === 'PUBLISHED'"
              @click="openEditDialog(project)"
            >
              <Pencil :size="16" />
            </button>
            <button
              v-if="isAdmin"
              type="button"
              aria-label="分配参与创作者"
              title="分配参与创作者"
              :disabled="project.publishStatus === 'PUBLISHED'"
              @click="openCreatorDialog(project)"
            >
              <UsersRound :size="16" />
            </button>
          </div>
        </article>
        <div v-if="!loading && projects.length === 0" class="empty-table">暂无符合条件的婚礼项目</div>
      </div>

      <el-pagination
        v-if="totalElements > size"
        class="management-pagination"
        background
        layout="prev, pager, next"
        :current-page="page + 1"
        :page-size="size"
        :total="totalElements"
        @current-change="changePage"
      />
    </section>

    <el-dialog
      v-model="formDialogVisible"
      :title="editingId ? '编辑婚礼项目' : '新建婚礼项目'"
      width="680px"
      class="management-dialog"
    >
      <form id="project-form" class="dialog-form management-form" @submit.prevent="saveProject">
        <label class="wide-field">项目标题<el-input v-model="form.title" maxlength="200" show-word-limit /></label>
        <label>新人展示名称<el-input v-model="form.coupleDisplayName" maxlength="100" placeholder="可留空" /></label>
        <label>婚礼日期<el-date-picker v-model="form.eventDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" /></label>
        <label>地区编码<el-input v-model="form.regionCode" maxlength="64" placeholder="例如 330100" /></label>
        <label>婚礼地点<el-input v-model="form.locationText" maxlength="300" /></label>
        <label class="wide-field">项目介绍<el-input v-model="form.description" type="textarea" :rows="5" maxlength="5000" show-word-limit /></label>
      </form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" native-type="submit" form="project-form" :loading="saving">
          {{ editingId ? '保存修改' : '创建项目' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="creatorDialogVisible"
      title="分配项目参与创作者"
      width="600px"
      class="management-dialog"
    >
      <div v-loading="creatorsLoading" class="assignment-dialog">
        <p>{{ creatorProject?.title }}</p>
        <el-checkbox-group v-model="creatorUserIds" class="creator-option-list">
          <el-checkbox v-for="creator in activeCreators" :key="creator.id" :value="creator.id">
            <span>{{ creator.displayName || creator.mobile }}</span>
            <small>{{ creator.professionalRoles.map((role) => role.name).join('、') }}</small>
          </el-checkbox>
        </el-checkbox-group>
        <div v-if="!creatorsLoading && activeCreators.length === 0" class="empty-inline">暂无启用的创作者账号</div>
      </div>
      <template #footer>
        <el-button @click="creatorDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="assignCreators">保存参与者</el-button>
      </template>
    </el-dialog>
  </main>
</template>
