<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Pencil, RefreshCw, Search } from '@lucide/vue'
import { inquiryApi } from '../api/operations'
import { apiErrorMessage, formatDateTime, statusTone } from '../utils/content'

const loading = ref(false)
const saving = ref(false)
const leads = ref([])
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const keyword = ref('')
const followStatus = ref('')
const dialogVisible = ref(false)
const editingLead = ref(null)
const form = reactive({
  version: null,
  followStatus: 'NEW',
  followNote: '',
})

const statusLabels = {
  NEW: '新线索',
  CONTACTED: '已联系',
  FOLLOWING: '跟进中',
  COMPLETED: '已完成',
  INVALID: '无效',
}
const newCount = computed(() => leads.value.filter((lead) => lead.followStatus === 'NEW').length)
const activeCount = computed(() => leads.value.filter((lead) => ['CONTACTED', 'FOLLOWING'].includes(lead.followStatus)).length)

onMounted(loadLeads)

async function loadLeads() {
  loading.value = true
  try {
    const { data } = await inquiryApi.list({
      page: page.value,
      size: 20,
      keyword: keyword.value.trim() || undefined,
      followStatus: followStatus.value || undefined,
    })
    leads.value = data.content
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '咨询线索加载失败'))
  } finally {
    loading.value = false
  }
}

function runSearch() {
  page.value = 0
  loadLeads()
}

function resetSearch() {
  keyword.value = ''
  followStatus.value = ''
  page.value = 0
  loadLeads()
}

function editLead(lead) {
  editingLead.value = lead
  Object.assign(form, {
    version: lead.version,
    followStatus: lead.followStatus,
    followNote: lead.followNote || '',
  })
  dialogVisible.value = true
}

async function saveLead() {
  saving.value = true
  try {
    await inquiryApi.update(editingLead.value.id, {
      version: form.version,
      followStatus: form.followStatus,
      followNote: form.followNote.trim() || null,
    })
    dialogVisible.value = false
    ElMessage.success('跟进状态已更新')
    await loadLeads()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '咨询线索更新失败'))
  } finally {
    saving.value = false
  }
}

function changePage(nextPage) {
  page.value = nextPage
  loadLeads()
}
</script>

<template>
  <main class="dashboard-content management-content" v-loading="loading">
    <section class="management-summary" aria-label="咨询概览">
      <div><span>线索总数</span><strong>{{ totalElements }}</strong></div>
      <div><span>本页新线索</span><strong>{{ newCount }}</strong></div>
      <div><span>本页跟进中</span><strong>{{ activeCount }}</strong></div>
    </section>

    <section class="dashboard-section management-panel">
      <form class="management-toolbar operations-toolbar inquiry-toolbar" role="search" @submit.prevent="runSearch">
        <div class="table-search">
          <Search :size="17" />
          <input v-model="keyword" type="search" placeholder="搜索编号、姓名、联系方式或需求" />
        </div>
        <el-select v-model="followStatus" placeholder="全部跟进状态" @change="runSearch">
          <el-option label="全部跟进状态" value="" />
          <el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" />
        </el-select>
        <div class="toolbar-commands">
          <button class="icon-command" type="button" aria-label="重置搜索" title="重置搜索" @click="resetSearch">
            <RefreshCw :size="17" />
          </button>
        </div>
      </form>

      <div class="management-table inquiry-management-table" role="table" aria-label="咨询线索">
        <div class="management-row management-table-head" role="row">
          <span>客户</span><span>婚期与地区</span><span>服务需求</span><span>状态</span><span>提交时间</span><span>操作</span>
        </div>
        <article v-for="lead in leads" :key="lead.id" class="management-row" role="row">
          <div class="primary-cell" data-label="客户">
            <strong>{{ lead.name }}</strong>
            <span>{{ lead.contact }}</span>
            <small>{{ lead.referenceCode }}</small>
          </div>
          <div class="inquiry-schedule" data-label="婚期与地区">
            <span>{{ lead.weddingDate || '婚期待定' }}</span>
            <small>{{ lead.region || '地区待定' }}</small>
          </div>
          <div class="inquiry-needs" data-label="服务需求">
            <strong>{{ lead.serviceNeeds }}</strong>
            <small v-if="lead.remark">{{ lead.remark }}</small>
            <small v-if="lead.followNote">跟进：{{ lead.followNote }}</small>
          </div>
          <span data-label="状态" :class="['state-chip', statusTone(lead.followStatus)]">
            {{ statusLabels[lead.followStatus] }}
          </span>
          <time data-label="提交时间">{{ formatDateTime(lead.createdAt) }}</time>
          <div class="row-commands" data-label="操作">
            <button type="button" aria-label="更新跟进" title="更新跟进" @click="editLead(lead)">
              <Pencil :size="16" />
            </button>
          </div>
        </article>
        <div v-if="!loading && leads.length === 0" class="empty-table">暂无符合条件的咨询线索</div>
      </div>

      <div v-if="totalPages > 1" class="management-pagination">
        <el-button :disabled="page === 0" @click="changePage(page - 1)">上一页</el-button>
        <span>{{ page + 1 }} / {{ totalPages }}</span>
        <el-button :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">下一页</el-button>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" title="更新咨询跟进" width="560px" class="management-dialog">
      <div v-if="editingLead" class="inquiry-dialog-summary">
        <strong>{{ editingLead.name }} · {{ editingLead.contact }}</strong>
        <span>{{ editingLead.serviceNeeds }}</span>
      </div>
      <form id="inquiry-form" class="dialog-form" @submit.prevent="saveLead">
        <label>
          跟进状态
          <el-select v-model="form.followStatus">
            <el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </label>
        <label>
          跟进记录
          <el-input v-model="form.followNote" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </label>
      </form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" native-type="submit" form="inquiry-form" :loading="saving">保存跟进</el-button>
      </template>
    </el-dialog>
  </main>
</template>
