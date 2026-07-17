<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  FolderHeart,
  ImagePlus,
  Images,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  UsersRound,
} from '@lucide/vue'
import { collectionApi, projectApi } from '../api/content'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'
import {
  apiErrorMessage,
  isVersionConflict,
  publishStatusLabels,
  reviewStatusLabels,
  statusTone,
} from '../utils/content'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const supportLoading = ref(false)
const saving = ref(false)
const assigning = ref(false)
const creatorsLoading = ref(false)
const collections = ref([])
const projectOptions = ref([])
const categoryOptions = ref([])
const tagOptions = ref([])
const allCreators = ref([])
const creatorChoices = ref([])
const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const formDialogVisible = ref(false)
const creatorDialogVisible = ref(false)
const editingId = ref(null)
const editingCollection = ref(null)
const creatorCollection = ref(null)
const creatorUserIds = ref([])

const filters = reactive({
  keyword: '',
  projectId: null,
  categoryId: null,
})

const form = reactive({
  version: null,
  projectId: null,
  title: '',
  description: '',
  categoryId: null,
  tagIds: [],
})

const isAdmin = computed(() => auth.user?.accountType === 'ADMIN')
const currentPagePublished = computed(
  () => collections.value.filter((collection) => collection.publishStatus === 'PUBLISHED').length,
)
const currentPageIndependent = computed(
  () => collections.value.filter((collection) => !collection.project).length,
)
const formCategoryOptions = computed(() => {
  const options = categoryOptions.value.map((item) => ({ ...item, retained: false }))
  const current = editingCollection.value?.category
  if (current && !options.some((item) => item.id === current.id)) {
    options.push({ ...current, retained: true })
  }
  return options
})
const formTagOptions = computed(() => {
  const options = tagOptions.value.map((item) => ({ ...item, retained: false }))
  for (const tag of editingCollection.value?.tags || []) {
    if (!options.some((item) => item.id === tag.id)) options.push({ ...tag, retained: true })
  }
  return options
})
const formProjectOptions = computed(() => {
  const options = projectOptions.value.map((item) => ({ ...item }))
  const current = editingCollection.value?.project
  if (current && !options.some((item) => item.id === current.id)) options.push(current)
  return options
})

onMounted(async () => {
  await Promise.all([loadSupportData(), loadCollections()])
})

async function loadSupportData() {
  supportLoading.value = true
  try {
    const [optionResponse, projects] = await Promise.all([
      collectionApi.options(),
      loadAllProjects(),
    ])
    categoryOptions.value = optionResponse.data.categories
    tagOptions.value = optionResponse.data.tags
    projectOptions.value = projects
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '作品集表单选项加载失败'))
  } finally {
    supportLoading.value = false
  }
}

async function loadAllProjects() {
  const result = []
  let currentPage = 0
  let totalPages = 1
  while (currentPage < totalPages) {
    const { data } = await projectApi.list({ page: currentPage, size: 100 })
    result.push(...data.content)
    totalPages = data.totalPages
    currentPage += 1
  }
  return result
}

async function loadCollections() {
  loading.value = true
  try {
    const { data } = await collectionApi.list({
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim() || undefined,
      projectId: filters.projectId || undefined,
      categoryId: filters.categoryId || undefined,
    })
    collections.value = data.content
    totalElements.value = data.totalElements
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '作品集加载失败'))
  } finally {
    loading.value = false
  }
}

function runSearch() {
  page.value = 0
  loadCollections()
}

function resetFilters() {
  Object.assign(filters, { keyword: '', projectId: null, categoryId: null })
  page.value = 0
  loadCollections()
}

function changePage(value) {
  page.value = value - 1
  loadCollections()
}

function resetForm() {
  Object.assign(form, {
    version: null,
    projectId: null,
    title: '',
    description: '',
    categoryId: null,
    tagIds: [],
  })
}

function openCreateDialog() {
  if (!categoryOptions.value.length) {
    ElMessage.warning('请先由管理员创建并启用至少一个作品分类')
    return
  }
  editingId.value = null
  editingCollection.value = null
  resetForm()
  formDialogVisible.value = true
}

function openEditDialog(collection) {
  editingId.value = collection.id
  editingCollection.value = collection
  Object.assign(form, {
    version: collection.version,
    projectId: collection.project?.id || null,
    title: collection.title,
    description: collection.description || '',
    categoryId: collection.category.id,
    tagIds: collection.tags.map((tag) => tag.id),
  })
  formDialogVisible.value = true
}

function validateForm() {
  if (!form.title.trim() || !form.categoryId) {
    ElMessage.warning('请填写作品集标题并选择主分类')
    return false
  }
  if (form.tagIds.length > 20) {
    ElMessage.warning('每个作品集最多选择 20 个标签')
    return false
  }
  return true
}

async function saveCollection() {
  if (!validateForm()) return
  saving.value = true
  const payload = {
    projectId: form.projectId || null,
    title: form.title.trim(),
    description: form.description.trim() || null,
    categoryId: form.categoryId,
    tagIds: form.tagIds,
  }
  try {
    if (editingId.value) {
      await collectionApi.update(editingId.value, { version: form.version, ...payload })
      ElMessage.success('作品集资料已更新')
    } else {
      await collectionApi.create(payload)
      ElMessage.success('作品集已创建')
    }
    formDialogVisible.value = false
    await loadCollections()
  } catch (error) {
    if (isVersionConflict(error)) {
      formDialogVisible.value = false
      await loadCollections()
    }
    ElMessage.error(apiErrorMessage(error, editingId.value ? '作品集更新失败' : '作品集创建失败'))
  } finally {
    saving.value = false
  }
}

async function loadCreators() {
  if (allCreators.value.length) return
  const { data } = await http.get('/admin/creators')
  allCreators.value = data
}

async function openCreatorDialog(collection) {
  creatorCollection.value = collection
  creatorUserIds.value = collection.creators.map((creator) => creator.userId)
  creatorChoices.value = []
  creatorDialogVisible.value = true
  creatorsLoading.value = true
  try {
    await loadCreators()
    const activeCreators = allCreators.value.filter((creator) => creator.accountStatus === 'ACTIVE')
    if (collection.project) {
      const { data: project } = await projectApi.get(collection.project.id)
      const participantIds = new Set(project.creators.map((creator) => creator.userId))
      creatorChoices.value = activeCreators.filter((creator) => participantIds.has(creator.id))
    } else {
      creatorChoices.value = activeCreators
    }
  } catch (error) {
    creatorDialogVisible.value = false
    ElMessage.error(apiErrorMessage(error, '共同创作者选项加载失败'))
  } finally {
    creatorsLoading.value = false
  }
}

async function assignCreators() {
  if (!creatorCollection.value) return
  assigning.value = true
  const selectableIds = new Set(creatorChoices.value.map((creator) => creator.id))
  try {
    await collectionApi.assignCreators(creatorCollection.value.id, {
      version: creatorCollection.value.version,
      creatorUserIds: creatorUserIds.value.filter((id) => selectableIds.has(id)),
    })
    creatorDialogVisible.value = false
    ElMessage.success('共同创作者已更新')
    await loadCollections()
  } catch (error) {
    if (isVersionConflict(error)) {
      creatorDialogVisible.value = false
      await loadCollections()
    }
    ElMessage.error(apiErrorMessage(error, '共同创作者更新失败'))
  } finally {
    assigning.value = false
  }
}

function creatorNames(collection) {
  if (!collection.creators.length) return '暂无共同创作者'
  return collection.creators
    .map((creator) => creator.displayName || `用户 ${creator.userId}`)
    .join('、')
}

function tagNames(collection) {
  return collection.tags.length ? collection.tags.map((tag) => tag.name).join('、') : '未设置标签'
}

function openPhotos(collection) {
  router.push({ name: 'collection-photos', params: { collectionId: collection.id } })
}
</script>

<template>
  <main class="dashboard-content management-content" v-loading="loading || supportLoading">
    <section class="management-summary" aria-label="作品集概览">
      <div><span>可访问作品集</span><strong>{{ totalElements }}</strong></div>
      <div><span>本页独立作品集</span><strong>{{ currentPageIndependent }}</strong></div>
      <div><span>本页已发布</span><strong>{{ currentPagePublished }}</strong></div>
    </section>

    <section class="dashboard-section management-panel">
      <form class="management-toolbar collection-toolbar" role="search" @submit.prevent="runSearch">
        <div class="table-search">
          <Search :size="17" />
          <input v-model="filters.keyword" type="search" placeholder="搜索作品集标题或介绍" />
        </div>
        <el-select v-model="filters.projectId" clearable filterable placeholder="全部项目">
          <el-option
            v-for="project in projectOptions"
            :key="project.id"
            :label="`${project.projectCode} · ${project.title}`"
            :value="project.id"
          />
        </el-select>
        <el-select v-model="filters.categoryId" clearable placeholder="全部分类">
          <el-option
            v-for="category in categoryOptions"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </el-select>
        <div class="toolbar-commands">
          <button class="icon-command" type="button" aria-label="重置筛选" title="重置筛选" @click="resetFilters">
            <RefreshCw :size="17" />
          </button>
          <button class="primary-command compact-command" type="button" @click="openCreateDialog">
            <Plus :size="17" />新建作品集
          </button>
        </div>
      </form>

      <div class="management-table collection-management-table" role="table" aria-label="作品集">
        <div class="management-row management-table-head" role="row">
          <span>作品集</span><span>关联项目</span><span>分类标签</span><span>共同创作者</span><span>审核</span><span>发布</span><span>操作</span>
        </div>
        <article v-for="collection in collections" :key="collection.id" class="management-row" role="row">
          <div class="primary-cell collection-title-cell" data-label="作品集">
            <span class="collection-cover-placeholder"><Images :size="19" /></span>
            <div>
              <strong>{{ collection.title }}</strong>
              <small>{{ collection.coverPhotoId ? `封面图片 #${collection.coverPhotoId}` : '尚未设置封面' }}</small>
            </div>
          </div>
          <div class="association-cell" data-label="关联项目">
            <FolderHeart :size="15" />
            <span v-if="collection.project">{{ collection.project.title }}<small>{{ collection.project.projectCode }}</small></span>
            <span v-else>独立作品集<small>未关联婚礼项目</small></span>
          </div>
          <div class="taxonomy-cell" data-label="分类标签" :title="tagNames(collection)">
            <strong>{{ collection.category.name }}</strong>
            <small>{{ tagNames(collection) }}</small>
          </div>
          <div class="creator-cell" data-label="共同创作者" :title="creatorNames(collection)">
            <UsersRound :size="15" />
            <span>{{ creatorNames(collection) }}</span>
          </div>
          <span data-label="审核" :class="['state-chip', statusTone(collection.reviewStatus)]">
            {{ reviewStatusLabels[collection.reviewStatus] || collection.reviewStatus }}
          </span>
          <span data-label="发布" :class="['state-chip', statusTone(collection.publishStatus)]">
            {{ publishStatusLabels[collection.publishStatus] || collection.publishStatus }}
          </span>
          <div class="row-commands" data-label="操作">
            <button type="button" aria-label="管理图片" title="管理图片" @click="openPhotos(collection)">
              <ImagePlus :size="16" />
            </button>
            <button
              type="button"
              aria-label="编辑作品集"
              title="编辑作品集"
              :disabled="collection.publishStatus === 'PUBLISHED'"
              @click="openEditDialog(collection)"
            >
              <Pencil :size="16" />
            </button>
            <button
              v-if="isAdmin"
              type="button"
              aria-label="分配共同创作者"
              title="分配共同创作者"
              :disabled="collection.publishStatus === 'PUBLISHED'"
              @click="openCreatorDialog(collection)"
            >
              <UsersRound :size="16" />
            </button>
          </div>
        </article>
        <div v-if="!loading && collections.length === 0" class="empty-table">暂无符合条件的作品集</div>
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
      :title="editingId ? '编辑作品集' : '新建作品集'"
      width="680px"
      class="management-dialog"
    >
      <form id="collection-form" class="dialog-form management-form" @submit.prevent="saveCollection">
        <label class="wide-field">作品集标题<el-input v-model="form.title" maxlength="200" show-word-limit /></label>
        <label class="wide-field">
          关联婚礼项目
          <el-select v-model="form.projectId" clearable filterable placeholder="不选择则创建独立作品集">
            <el-option
              v-for="project in formProjectOptions"
              :key="project.id"
              :label="`${project.projectCode} · ${project.title}`"
              :value="project.id"
            />
          </el-select>
        </label>
        <label>
          主分类
          <el-select v-model="form.categoryId" placeholder="选择分类">
            <el-option
              v-for="category in formCategoryOptions"
              :key="category.id"
              :label="category.retained ? `${category.name}（已停用，仅保留）` : category.name"
              :value="category.id"
              :disabled="category.retained"
            />
          </el-select>
        </label>
        <label>
          内容标签
          <el-select v-model="form.tagIds" multiple collapse-tags collapse-tags-tooltip placeholder="可选择多个标签">
            <el-option
              v-for="tag in formTagOptions"
              :key="tag.id"
              :label="tag.retained ? `${tag.name}（已停用，仅保留）` : tag.name"
              :value="tag.id"
              :disabled="tag.retained"
            />
          </el-select>
        </label>
        <label class="wide-field">作品集介绍<el-input v-model="form.description" type="textarea" :rows="5" maxlength="5000" show-word-limit /></label>
      </form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" native-type="submit" form="collection-form" :loading="saving">
          {{ editingId ? '保存修改' : '创建作品集' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="creatorDialogVisible"
      title="分配共同创作者"
      width="600px"
      class="management-dialog"
    >
      <div v-loading="creatorsLoading" class="assignment-dialog">
        <p>{{ creatorCollection?.title }}</p>
        <small v-if="creatorCollection?.project" class="assignment-note">
          仅显示“{{ creatorCollection.project.title }}”的启用参与者
        </small>
        <el-checkbox-group v-model="creatorUserIds" class="creator-option-list">
          <el-checkbox v-for="creator in creatorChoices" :key="creator.id" :value="creator.id">
            <span>{{ creator.displayName || creator.mobile }}</span>
            <small>{{ creator.professionalRoles.map((role) => role.name).join('、') }}</small>
          </el-checkbox>
        </el-checkbox-group>
        <div v-if="!creatorsLoading && creatorChoices.length === 0" class="empty-inline">
          暂无可分配的启用创作者
        </div>
      </div>
      <template #footer>
        <el-button @click="creatorDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="assignCreators">保存共同创作者</el-button>
      </template>
    </el-dialog>
  </main>
</template>
