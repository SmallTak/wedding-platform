<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { KeyRound, Power, Search, Trash2, UserPlus } from '@lucide/vue'
import http from '../api/http'

const loading = ref(false)
const saving = ref(false)
const deletingId = ref(null)
const dialogVisible = ref(false)
const query = ref('')
const creators = ref([])
const professionalRoles = ref([])
const form = reactive({ mobile: '', initialPassword: '', displayName: '', professionalRoleIds: [] })

const filteredCreators = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return creators.value
  return creators.value.filter((creator) =>
    [creator.displayName, creator.mobile, ...creator.professionalRoles.map((role) => role.name)]
      .filter(Boolean)
      .some((value) => value.toLowerCase().includes(keyword)),
  )
})

const activeCount = computed(() => creators.value.filter((creator) => creator.accountStatus === 'ACTIVE').length)
const pendingSetupCount = computed(() => creators.value.filter((creator) => creator.setupRequired).length)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [creatorResponse, roleResponse] = await Promise.all([
      http.get('/admin/creators'),
      http.get('/admin/professional-roles'),
    ])
    creators.value = creatorResponse.data
    professionalRoles.value = roleResponse.data
  } catch {
    ElMessage.error('创作者账号加载失败')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  Object.assign(form, { mobile: '', initialPassword: '', displayName: '', professionalRoleIds: [] })
  dialogVisible.value = true
}

async function createCreator() {
  saving.value = true
  try {
    const { data } = await http.post('/admin/creators', form)
    creators.value.unshift(data)
    dialogVisible.value = false
    ElMessage.success('创作者账号已开通')
  } catch (error) {
    const code = error.response?.data?.code
    ElMessage.error(code === 'MOBILE_EXISTS' ? '该手机号已存在' : (error.response?.data?.message || '账号开通失败'))
  } finally {
    saving.value = false
  }
}

async function toggleStatus(creator) {
  const status = creator.accountStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const action = status === 'ACTIVE' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${action}“${creator.displayName || creator.mobile}”吗？`, `${action}账号`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: status === 'ACTIVE' ? 'success' : 'warning',
    })
    const { data } = await http.patch(`/admin/creators/${creator.id}/status`, { status })
    Object.assign(creator, data)
    ElMessage.success(`账号已${action}`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(`${action}失败`)
  }
}

async function resetPassword(creator) {
  try {
    const { value } = await ElMessageBox.prompt('设置新的初始密码，创作者下次登录时必须修改。', '重置密码', {
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
      inputType: 'password',
      inputPattern: /^.{8,72}$/,
      inputErrorMessage: '密码长度需要为 8 至 72 位',
    })
    const { data } = await http.post(`/admin/creators/${creator.id}/reset-password`, { initialPassword: value })
    Object.assign(creator, data)
    ElMessage.success('密码已重置')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('密码重置失败')
  }
}

async function deleteCreator(creator) {
  const label = creator.displayName || creator.mobile
  try {
    await ElMessageBox.confirm(
      `确认删除“${label}”？该账号将立即无法登录，但历史项目、作品集和审计记录会保留。`,
      '删除创作者账号',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  deletingId.value = creator.id
  try {
    await http.delete(`/admin/creators/${creator.id}`, { params: { version: creator.version } })
    creators.value = creators.value.filter((item) => item.id !== creator.id)
    ElMessage.success('创作者账号已删除')
  } catch (error) {
    const code = error.response?.data?.code
    if (code === 'CREATOR_VERSION_CONFLICT') await loadData()
    ElMessage.error(code === 'CREATOR_VERSION_CONFLICT'
      ? '账号已被其他人修改，请刷新后重试'
      : (error.response?.data?.message || '创作者账号删除失败'))
  } finally {
    deletingId.value = null
  }
}

function formatDate(value) {
  return value ? new Intl.DateTimeFormat('zh-CN').format(new Date(value)) : '-'
}
</script>

<template>
  <main class="dashboard-content account-content" v-loading="loading">
    <section class="account-summary" aria-label="创作者账号概览">
      <div><span>账号总数</span><strong>{{ creators.length }}</strong></div>
      <div><span>正常使用</span><strong>{{ activeCount }}</strong></div>
      <div><span>待完成首次登录</span><strong>{{ pendingSetupCount }}</strong></div>
    </section>

    <section class="dashboard-section account-section">
      <div class="account-toolbar">
        <div class="table-search">
          <Search :size="17" />
          <input v-model="query" type="search" placeholder="搜索姓名、手机号或职业" aria-label="搜索创作者" />
        </div>
        <button class="primary-command compact-command" type="button" @click="openCreateDialog">
          <UserPlus :size="17" />开通账号
        </button>
      </div>

      <div class="creator-table" role="table" aria-label="创作者账号">
        <div class="creator-row creator-table-head" role="row">
          <span>创作者</span><span>职业角色</span><span>首次登录</span><span>状态</span><span>开通时间</span><span>操作</span>
        </div>
        <article v-for="creator in filteredCreators" :key="creator.id" class="creator-row" role="row">
          <div class="creator-identity" data-label="创作者">
            <img v-if="creator.avatarPath" :src="creator.avatarPath" alt="" />
            <span v-else>{{ (creator.displayName || creator.mobile).slice(0, 1) }}</span>
            <div><strong>{{ creator.displayName || '未填写姓名' }}</strong><small>{{ creator.mobile }}</small></div>
          </div>
          <div class="role-list" data-label="职业角色">
            <span v-for="role in creator.professionalRoles" :key="role.id">{{ role.name }}</span>
          </div>
          <span data-label="首次登录" :class="['setup-state', { complete: !creator.setupRequired }]">
            {{ creator.setupRequired ? '待完成' : '已完成' }}
          </span>
          <span data-label="状态" :class="['account-state', { disabled: creator.accountStatus !== 'ACTIVE' }]">
            {{ creator.accountStatus === 'ACTIVE' ? '正常' : '已停用' }}
          </span>
          <time data-label="开通时间">{{ formatDate(creator.createdAt) }}</time>
          <div class="row-commands" data-label="操作">
            <button type="button" :aria-label="creator.accountStatus === 'ACTIVE' ? '停用账号' : '启用账号'" :title="creator.accountStatus === 'ACTIVE' ? '停用账号' : '启用账号'" @click="toggleStatus(creator)"><Power :size="16" /></button>
            <button type="button" aria-label="重置密码" title="重置密码" @click="resetPassword(creator)"><KeyRound :size="16" /></button>
            <button class="danger-command" type="button" aria-label="删除账号" title="删除账号" :disabled="deletingId === creator.id" @click="deleteCreator(creator)"><Trash2 :size="16" /></button>
          </div>
        </article>
        <div v-if="!loading && filteredCreators.length === 0" class="empty-table">暂无匹配的创作者账号</div>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" title="开通创作者账号" width="520px" class="account-dialog">
      <form id="creator-form" class="dialog-form" @submit.prevent="createCreator">
        <label>手机号<el-input v-model="form.mobile" maxlength="11" inputmode="numeric" required /></label>
        <label>初始密码<el-input v-model="form.initialPassword" type="password" minlength="8" maxlength="72" show-password required /></label>
        <label>姓名<el-input v-model="form.displayName" maxlength="100" placeholder="可由创作者首次登录时补充" /></label>
        <fieldset>
          <legend>职业角色</legend>
          <el-checkbox-group v-model="form.professionalRoleIds">
            <el-checkbox v-for="role in professionalRoles" :key="role.id" :value="role.id">{{ role.name }}</el-checkbox>
          </el-checkbox-group>
        </fieldset>
      </form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" native-type="submit" form="creator-form" :loading="saving" :disabled="form.professionalRoleIds.length === 0">确认开通</el-button>
      </template>
    </el-dialog>
  </main>
</template>
