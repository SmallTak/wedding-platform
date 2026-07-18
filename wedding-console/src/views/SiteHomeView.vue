<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw, Save, Star } from '@lucide/vue'
import { homepageApi } from '../api/operations'
import { apiErrorMessage } from '../utils/content'

const loading = ref(false)
const saving = ref(false)
const activeTab = ref('PROJECT')
const projects = ref([])
const collections = ref([])
const feedback = ref([])
const configured = ref([])
const limits = {
  PROJECT: 6,
  COLLECTION: 12,
  FEEDBACK: 6,
}

const candidates = computed(() => {
  if (activeTab.value === 'PROJECT') return projects.value
  if (activeTab.value === 'COLLECTION') return collections.value
  return feedback.value
})
const selectedCount = computed(() =>
  configured.value.filter((item) => item.targetType === activeTab.value).length,
)
const totalSelected = computed(() => configured.value.length)
const activeLimit = computed(() => limits[activeTab.value])

onMounted(loadOptions)

async function loadOptions() {
  loading.value = true
  try {
    const { data } = await homepageApi.options()
    projects.value = data.projects
    collections.value = data.collections
    feedback.value = data.feedback
    configured.value = data.features.map((item) => ({ ...item }))
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '首页运营配置加载失败'))
  } finally {
    loading.value = false
  }
}

function featureFor(targetId) {
  return configured.value.find(
    (item) => item.targetType === activeTab.value && item.targetId === targetId,
  )
}

function toggleCandidate(candidate, selected) {
  const existing = featureFor(candidate.id)
  if (!selected && existing) {
    configured.value = configured.value.filter((item) => item !== existing)
    return
  }
  if (selected && !existing) {
    const current = configured.value.filter((item) => item.targetType === activeTab.value)
    if (current.length >= activeLimit.value) {
      ElMessage.warning(`当前类型最多推荐 ${activeLimit.value} 条内容`)
      return
    }
    configured.value.push({
      targetType: activeTab.value,
      targetId: candidate.id,
      sortOrder: (current.length + 1) * 10,
      pinned: false,
    })
  }
}

function candidateDisabled(candidate) {
  return !featureFor(candidate.id) && selectedCount.value >= activeLimit.value
}

function candidateTitle(candidate) {
  if (activeTab.value === 'FEEDBACK') {
    return `${candidate.customerDisplayName} · ${candidate.creatorDisplayName || '创作者'}`
  }
  return candidate.title
}

function candidateMeta(candidate) {
  if (activeTab.value === 'PROJECT') return candidate.locationText || '未填写地点'
  if (activeTab.value === 'COLLECTION') return candidate.category?.name || '婚礼作品'
  return candidate.content
}

async function saveFeatures() {
  saving.value = true
  try {
    const items = configured.value.map(({ targetType, targetId, sortOrder, pinned }) => ({
      targetType,
      targetId,
      sortOrder,
      pinned,
    }))
    const { data } = await homepageApi.replace(items)
    configured.value = data.features.map((item) => ({ ...item }))
    ElMessage.success('首页推荐配置已发布')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '首页推荐配置保存失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="dashboard-content management-content" v-loading="loading">
    <section class="management-summary operations-summary" aria-label="首页推荐概览">
      <div><span>已选项目</span><strong>{{ configured.filter((item) => item.targetType === 'PROJECT').length }}</strong></div>
      <div><span>已选作品集</span><strong>{{ configured.filter((item) => item.targetType === 'COLLECTION').length }}</strong></div>
      <div><span>已选评价</span><strong>{{ configured.filter((item) => item.targetType === 'FEEDBACK').length }}</strong></div>
      <div><span>推荐总数</span><strong>{{ totalSelected }}</strong></div>
    </section>

    <section class="dashboard-section management-panel">
      <div class="management-toolbar homepage-toolbar">
        <div class="segmented-control" aria-label="首页内容类型">
          <button type="button" :class="{ active: activeTab === 'PROJECT' }" @click="activeTab = 'PROJECT'">
            婚礼项目
          </button>
          <button type="button" :class="{ active: activeTab === 'COLLECTION' }" @click="activeTab = 'COLLECTION'">
            作品集
          </button>
          <button type="button" :class="{ active: activeTab === 'FEEDBACK' }" @click="activeTab = 'FEEDBACK'">
            客户评价
          </button>
        </div>
        <div class="toolbar-commands">
          <span class="section-count">当前选择 {{ selectedCount }} / {{ activeLimit }}</span>
          <button class="icon-command" type="button" aria-label="重新加载" title="重新加载" @click="loadOptions">
            <RefreshCw :size="17" />
          </button>
          <button class="primary-command compact-command" type="button" :disabled="saving" @click="saveFeatures">
            <Save :size="17" />发布配置
          </button>
        </div>
      </div>

      <div class="homepage-candidate-list">
        <article v-for="candidate in candidates" :key="candidate.id" class="homepage-candidate">
          <el-checkbox
            :model-value="Boolean(featureFor(candidate.id))"
            :disabled="candidateDisabled(candidate)"
            :aria-label="`推荐${candidateTitle(candidate)}`"
            @change="toggleCandidate(candidate, $event)"
          />
          <img
            v-if="activeTab !== 'FEEDBACK' && (candidate.coverThumbnailUrl || candidate.coverPreviewUrl)"
            :src="candidate.coverThumbnailUrl || candidate.coverPreviewUrl"
            alt=""
          />
          <div v-else class="homepage-candidate-icon">
            <Star :size="18" />
          </div>
          <div class="homepage-candidate-copy">
            <strong>{{ candidateTitle(candidate) }}</strong>
            <span>{{ candidateMeta(candidate) }}</span>
          </div>
          <template v-if="featureFor(candidate.id)">
            <label class="homepage-order">
              排序
              <el-input-number
                v-model="featureFor(candidate.id).sortOrder"
                :min="0"
                :max="999999"
                controls-position="right"
              />
            </label>
            <label class="homepage-pin">
              置顶
              <el-switch v-model="featureFor(candidate.id).pinned" />
            </label>
          </template>
        </article>
        <div v-if="!loading && candidates.length === 0" class="empty-table">
          当前没有可推荐的公开内容
        </div>
      </div>
    </section>
  </main>
</template>
