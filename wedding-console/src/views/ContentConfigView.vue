<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Folder, Pencil, Plus, RefreshCw, Tags, Trash2 } from '@lucide/vue'
import { contentConfigApi } from '../api/content'
import { apiErrorMessage, formatDateTime, isVersionConflict, statusTone } from '../utils/content'

const loading = ref(false)
const saving = ref(false)
const deletingId = ref(null)
const activeTab = ref('categories')
const categories = ref([])
const tags = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)

const form = reactive({
  version: null,
  name: '',
  description: '',
  sortOrder: 0,
  status: 'ACTIVE',
})

const items = computed(() => (activeTab.value === 'categories' ? categories.value : tags.value))
const activeCount = computed(() => items.value.filter((item) => item.status === 'ACTIVE').length)
const dialogTitle = computed(() => {
  const subject = activeTab.value === 'categories' ? '分类' : '标签'
  return `${editingId.value ? '编辑' : '新建'}${subject}`
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [categoryResponse, tagResponse] = await Promise.all([
      contentConfigApi.categories(),
      contentConfigApi.tags(),
    ])
    categories.value = categoryResponse.data
    tags.value = tagResponse.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '分类标签加载失败'))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    version: null,
    name: '',
    description: '',
    sortOrder: 0,
    status: 'ACTIVE',
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
    name: item.name,
    description: item.description || '',
    sortOrder: item.sortOrder,
    status: item.status,
  })
  dialogVisible.value = true
}

async function saveItem() {
  if (!form.name.trim()) {
    ElMessage.warning(`请输入${activeTab.value === 'categories' ? '分类' : '标签'}名称`)
    return
  }
  saving.value = true
  const isCategory = activeTab.value === 'categories'
  const payload = {
    name: form.name.trim(),
    sortOrder: form.sortOrder,
  }
  if (isCategory) payload.description = form.description.trim() || null
  if (editingId.value) {
    payload.version = form.version
    payload.status = form.status
  }

  try {
    if (isCategory) {
      if (editingId.value) await contentConfigApi.updateCategory(editingId.value, payload)
      else await contentConfigApi.createCategory(payload)
    } else if (editingId.value) {
      await contentConfigApi.updateTag(editingId.value, payload)
    } else {
      await contentConfigApi.createTag(payload)
    }
    dialogVisible.value = false
    ElMessage.success(`${isCategory ? '分类' : '标签'}已${editingId.value ? '更新' : '创建'}`)
    await loadData()
  } catch (error) {
    if (isVersionConflict(error)) {
      dialogVisible.value = false
      await loadData()
    }
    ElMessage.error(apiErrorMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function deleteItem(item) {
  const isCategory = activeTab.value === 'categories'
  const subject = isCategory ? '分类' : '标签'
  try {
    await ElMessageBox.confirm(
      `确认删除${subject}“${item.name}”？删除后不再出现在配置和作品集选项中。`,
      `删除${subject}`,
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  deletingId.value = item.id
  try {
    if (isCategory) await contentConfigApi.deleteCategory(item.id, item.version)
    else await contentConfigApi.deleteTag(item.id, item.version)
    ElMessage.success(`${subject}已删除`)
    await loadData()
  } catch (error) {
    if (isVersionConflict(error)) await loadData()
    ElMessage.error(apiErrorMessage(error, `${subject}删除失败`))
  } finally {
    deletingId.value = null
  }
}
</script>

<template>
  <main class="dashboard-content management-content" v-loading="loading">
    <section class="management-summary" aria-label="内容配置概览">
      <div><span>分类总数</span><strong>{{ categories.length }}</strong></div>
      <div><span>标签总数</span><strong>{{ tags.length }}</strong></div>
      <div><span>当前启用</span><strong>{{ activeCount }}</strong></div>
    </section>

    <section class="dashboard-section management-panel">
      <div class="management-toolbar">
        <div class="segmented-control" aria-label="配置类型">
          <button
            type="button"
            :class="{ active: activeTab === 'categories' }"
            @click="activeTab = 'categories'"
          >
            <Folder :size="16" />作品分类
          </button>
          <button
            type="button"
            :class="{ active: activeTab === 'tags' }"
            @click="activeTab = 'tags'"
          >
            <Tags :size="16" />内容标签
          </button>
        </div>
        <div class="toolbar-commands">
          <button class="icon-command" type="button" aria-label="刷新" title="刷新" @click="loadData">
            <RefreshCw :size="17" />
          </button>
          <button class="primary-command compact-command" type="button" @click="openCreateDialog">
            <Plus :size="17" />新建{{ activeTab === 'categories' ? '分类' : '标签' }}
          </button>
        </div>
      </div>

      <div
        class="management-table config-management-table"
        :class="{ 'tag-table': activeTab === 'tags' }"
        role="table"
        :aria-label="activeTab === 'categories' ? '作品分类' : '内容标签'"
      >
        <div class="management-row management-table-head" role="row">
          <span>名称</span><span v-if="activeTab === 'categories'">说明</span><span>排序</span><span>状态</span><span>最后更新</span><span>操作</span>
        </div>
        <article v-for="item in items" :key="item.id" class="management-row" role="row">
          <div class="primary-cell" data-label="名称">
            <strong>{{ item.name }}</strong>
            <small>ID {{ item.id }}</small>
          </div>
          <span v-if="activeTab === 'categories'" class="description-cell" data-label="说明">
            {{ item.description || '未填写说明' }}
          </span>
          <span data-label="排序">{{ item.sortOrder }}</span>
          <span data-label="状态" :class="['state-chip', statusTone(item.status)]">
            {{ item.status === 'ACTIVE' ? '启用' : '停用' }}
          </span>
          <time data-label="最后更新">{{ formatDateTime(item.updatedAt) }}</time>
          <div class="row-commands" data-label="操作">
            <button type="button" aria-label="编辑" title="编辑" @click="openEditDialog(item)">
              <Pencil :size="16" />
            </button>
            <button
              class="danger-command"
              type="button"
              :aria-label="`删除${activeTab === 'categories' ? '分类' : '标签'}`"
              :title="`删除${activeTab === 'categories' ? '分类' : '标签'}`"
              :disabled="deletingId === item.id"
              @click="deleteItem(item)"
            >
              <Trash2 :size="16" />
            </button>
          </div>
        </article>
        <div v-if="!loading && items.length === 0" class="empty-table">
          暂无{{ activeTab === 'categories' ? '作品分类' : '内容标签' }}
        </div>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="540px" class="management-dialog">
      <form id="config-form" class="dialog-form" @submit.prevent="saveItem">
        <label>名称<el-input v-model="form.name" maxlength="100" show-word-limit /></label>
        <label v-if="activeTab === 'categories'">
          分类说明
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </label>
        <label>
          排序值
          <el-input-number v-model="form.sortOrder" :min="0" :max="999999" controls-position="right" />
        </label>
        <fieldset v-if="editingId">
          <legend>使用状态</legend>
          <el-radio-group v-model="form.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </fieldset>
      </form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" native-type="submit" form="config-form" :loading="saving">
          {{ editingId ? '保存修改' : '确认创建' }}
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>
