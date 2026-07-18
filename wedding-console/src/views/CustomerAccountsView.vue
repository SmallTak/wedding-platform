<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check,
  CircleUserRound,
  KeyRound,
  Power,
  RefreshCw,
  Search,
  X,
} from '@lucide/vue'
import {
  customerAdminApi,
  customerProjectApplicationApi,
} from '../api/operations'
import { apiErrorMessage, formatDateTime } from '../utils/content'

const activeTab = ref('accounts')
const loadingAccounts = ref(false)
const loadingApplications = ref(false)
const customers = ref([])
const query = ref('')
const applications = ref([])
const applicationStatus = ref('PENDING')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)

const filteredCustomers = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return customers.value
  return customers.value.filter((customer) =>
    [customer.nickname, customer.displayName, customer.mobile]
      .filter(Boolean)
      .some((value) => value.toLowerCase().includes(keyword)),
  )
})
const activeCount = computed(() =>
  customers.value.filter((customer) => customer.accountStatus === 'ACTIVE').length,
)
const resetRequiredCount = computed(() =>
  customers.value.filter((customer) => customer.mustChangePassword).length,
)

const applicationLabels = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

onMounted(async () => {
  await Promise.all([loadCustomers(), loadApplications()])
})

async function loadCustomers() {
  loadingAccounts.value = true
  try {
    const { data } = await customerAdminApi.list()
    customers.value = data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '客户账号加载失败'))
  } finally {
    loadingAccounts.value = false
  }
}

async function loadApplications() {
  loadingApplications.value = true
  try {
    const { data } = await customerProjectApplicationApi.list({
      page: page.value,
      size: 20,
      status: applicationStatus.value || undefined,
    })
    applications.value = data.content
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '项目关联申请加载失败'))
  } finally {
    loadingApplications.value = false
  }
}

async function toggleStatus(customer) {
  const status = customer.accountStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const action = status === 'ACTIVE' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `确认${action}“${customer.nickname || customer.mobile}”吗？${status === 'DISABLED' ? '停用后当前登录会立即失效。' : ''}`,
      `${action}客户账号`,
      {
        confirmButtonText: action,
        cancelButtonText: '取消',
        type: status === 'ACTIVE' ? 'success' : 'warning',
      },
    )
    const { data } = await customerAdminApi.updateStatus(customer.id, status)
    Object.assign(customer, data)
    ElMessage.success(`客户账号已${action}`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, `${action}失败`))
  }
}

async function resetPassword(customer) {
  try {
    const { value } = await ElMessageBox.prompt(
      '设置 8 至 72 位临时密码。客户下次登录客户中心后，必须先修改密码。',
      '重置客户密码',
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPattern: /^.{8,72}$/,
        inputErrorMessage: '临时密码长度需要为 8 至 72 位',
      },
    )
    const { data } = await customerAdminApi.resetPassword(customer.id, value)
    Object.assign(customer, data)
    ElMessage.success('客户密码已重置')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '密码重置失败'))
  }
}

async function approve(application) {
  try {
    await ElMessageBox.confirm(
      `确认将客户“${application.customer?.nickname || application.customer?.mobile}”关联到项目“${application.project?.title || application.project?.projectCode}”吗？`,
      '通过关联申请',
      {
        confirmButtonText: '确认通过',
        cancelButtonText: '取消',
        type: 'success',
      },
    )
    await customerProjectApplicationApi.approve(application.id, application.version)
    ElMessage.success('项目关联申请已通过')
    await loadApplications()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '申请审核失败'))
  }
}

async function reject(application) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请说明项目编号不匹配、身份信息不足或其他驳回原因。客户可修正说明后重新提交。',
      '驳回关联申请',
      {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: (input) => Boolean(input?.trim()) || '请输入驳回原因',
      },
    )
    await customerProjectApplicationApi.reject(application.id, {
      version: application.version,
      reason: value.trim(),
    })
    ElMessage.success('项目关联申请已驳回')
    await loadApplications()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '申请驳回失败'))
  }
}

function runApplicationFilter() {
  page.value = 0
  loadApplications()
}

function changePage(nextPage) {
  page.value = nextPage
  loadApplications()
}

function formatDate(value) {
  return value ? new Intl.DateTimeFormat('zh-CN').format(new Date(value)) : '-'
}
</script>

<template>
  <main class="dashboard-content management-content customer-admin-content">
    <section class="management-summary customer-admin-summary" aria-label="客户账号概览">
      <div><span>客户账号</span><strong>{{ customers.length }}</strong></div>
      <div><span>正常使用</span><strong>{{ activeCount }}</strong></div>
      <div><span>待强制改密</span><strong>{{ resetRequiredCount }}</strong></div>
      <div><span>当前申请结果</span><strong>{{ totalElements }}</strong></div>
    </section>

    <section class="dashboard-section management-panel">
      <el-tabs v-model="activeTab" class="customer-admin-tabs">
        <el-tab-pane label="客户账号" name="accounts">
          <div class="management-toolbar">
            <div class="table-search">
              <Search :size="17" />
              <input v-model="query" type="search" placeholder="搜索昵称或手机号" aria-label="搜索客户账号" />
            </div>
            <button class="icon-command" type="button" aria-label="刷新客户账号" title="刷新" @click="loadCustomers">
              <RefreshCw :size="17" />
            </button>
          </div>

          <div v-loading="loadingAccounts" class="management-table customer-account-table" role="table" aria-label="客户账号">
            <div class="management-row management-table-head" role="row">
              <span>客户</span><span>账号状态</span><span>密码状态</span><span>最近登录</span><span>注册时间</span><span>操作</span>
            </div>
            <article v-for="customer in filteredCustomers" :key="customer.id" class="management-row" role="row">
              <div class="creator-identity" data-label="客户">
                <span>{{ (customer.nickname || customer.mobile).slice(0, 1) }}</span>
                <div>
                  <strong>{{ customer.nickname || customer.displayName || '未填写昵称' }}</strong>
                  <small>{{ customer.mobile }}</small>
                </div>
              </div>
              <span data-label="账号状态" :class="['account-state', { disabled: customer.accountStatus !== 'ACTIVE' }]">
                {{ customer.accountStatus === 'ACTIVE' ? '正常' : '已停用' }}
              </span>
              <span data-label="密码状态" :class="['setup-state', { complete: !customer.mustChangePassword }]">
                {{ customer.mustChangePassword ? '待修改' : '正常' }}
              </span>
              <time data-label="最近登录">{{ customer.lastLoginAt ? formatDateTime(customer.lastLoginAt) : '从未登录' }}</time>
              <time data-label="注册时间">{{ formatDate(customer.createdAt) }}</time>
              <div class="row-commands" data-label="操作">
                <button
                  type="button"
                  :aria-label="customer.accountStatus === 'ACTIVE' ? '停用账号' : '启用账号'"
                  :title="customer.accountStatus === 'ACTIVE' ? '停用账号' : '启用账号'"
                  @click="toggleStatus(customer)"
                >
                  <Power :size="16" />
                </button>
                <button type="button" aria-label="重置密码" title="重置密码" @click="resetPassword(customer)">
                  <KeyRound :size="16" />
                </button>
              </div>
            </article>
            <div v-if="!loadingAccounts && filteredCustomers.length === 0" class="empty-table">暂无匹配的客户账号</div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="项目关联审核" name="applications">
          <div class="customer-admin-guidance">
            <CircleUserRound :size="19" />
            <p>通过前核对客户申请说明、项目编号和项目资料。完全隐藏项目仍可建立关联，但客户中心不会展示项目标题、日期和地点。</p>
          </div>
          <div class="management-toolbar customer-application-toolbar">
            <el-select v-model="applicationStatus" placeholder="申请状态" @change="runApplicationFilter">
              <el-option label="全部申请" value="" />
              <el-option label="待审核" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
            </el-select>
            <button class="icon-command" type="button" aria-label="刷新关联申请" title="刷新" @click="loadApplications">
              <RefreshCw :size="17" />
            </button>
          </div>

          <div v-loading="loadingApplications" class="management-table customer-application-table" role="table" aria-label="客户项目关联申请">
            <div class="management-row management-table-head" role="row">
              <span>客户</span><span>婚礼项目</span><span>申请说明</span><span>状态</span><span>申请时间</span><span>操作</span>
            </div>
            <article v-for="application in applications" :key="application.id" class="management-row" role="row">
              <div class="primary-cell" data-label="客户">
                <strong>{{ application.customer?.nickname || '未填写昵称' }}</strong>
                <small>{{ application.customer?.mobile }}</small>
              </div>
              <div class="primary-cell" data-label="婚礼项目">
                <strong>{{ application.project?.title || '项目已不可用' }}</strong>
                <small>{{ application.project?.projectCode }} · {{ application.project?.locationText || '-' }}</small>
              </div>
              <div class="customer-application-note" data-label="申请说明">
                <p>{{ application.applyNote }}</p>
                <small v-if="application.rejectionReason" class="negative-text">
                  驳回：{{ application.rejectionReason }}
                </small>
              </div>
              <span data-label="状态" :class="['state-chip', application.status.toLowerCase()]">
                {{ applicationLabels[application.status] }}
              </span>
              <time data-label="申请时间">{{ formatDateTime(application.createdAt) }}</time>
              <div class="row-commands" data-label="操作">
                <button
                  v-if="application.status === 'PENDING'"
                  type="button"
                  aria-label="通过申请"
                  title="通过申请"
                  @click="approve(application)"
                >
                  <Check :size="16" />
                </button>
                <button
                  v-if="application.status === 'PENDING'"
                  class="danger-command"
                  type="button"
                  aria-label="驳回申请"
                  title="驳回申请"
                  @click="reject(application)"
                >
                  <X :size="16" />
                </button>
              </div>
            </article>
            <div v-if="!loadingApplications && applications.length === 0" class="empty-table">暂无符合条件的关联申请</div>
          </div>

          <div v-if="totalPages > 1" class="management-pagination">
            <el-button :disabled="page === 0" @click="changePage(page - 1)">上一页</el-button>
            <span>{{ page + 1 }} / {{ totalPages }}</span>
            <el-button :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">下一页</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </main>
</template>
