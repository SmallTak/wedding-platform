<script setup>
import { reactive, ref } from 'vue'
import { KeyRound, Save, UserRound } from '@lucide/vue'
import { useCustomerAuthStore } from '../../stores/customerAuth'

const auth = useCustomerAuthStore()
const profileSaving = ref(false)
const passwordSaving = ref(false)
const profileMessage = ref('')
const passwordMessage = ref('')
const errorMessage = ref('')
const profileForm = reactive({
  nickname: auth.user?.nickname || auth.user?.displayName || '',
})
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

async function saveProfile() {
  if (!profileForm.nickname.trim()) {
    errorMessage.value = '请填写昵称'
    return
  }
  profileSaving.value = true
  errorMessage.value = ''
  profileMessage.value = ''
  try {
    await auth.updateProfile({ nickname: profileForm.nickname.trim() })
    profileMessage.value = '昵称已更新'
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '昵称更新失败'
  } finally {
    profileSaving.value = false
  }
}

async function changePassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    errorMessage.value = '两次输入的新密码不一致'
    return
  }
  passwordSaving.value = true
  errorMessage.value = ''
  passwordMessage.value = ''
  try {
    await auth.changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' })
    passwordMessage.value = '密码已修改'
  } catch (error) {
    const code = error.response?.data?.code
    if (code === 'CURRENT_PASSWORD_INVALID') errorMessage.value = '当前密码不正确'
    else if (code === 'PASSWORD_UNCHANGED') errorMessage.value = '新密码不能与当前密码相同'
    else errorMessage.value = error.response?.data?.message || '密码修改失败'
  } finally {
    passwordSaving.value = false
  }
}
</script>

<template>
  <div class="customer-page">
    <section class="customer-page-heading">
      <div>
        <p>Account settings</p>
        <h1>账号设置</h1>
      </div>
    </section>

    <section v-if="auth.setupRequired" class="customer-context-notice warning">
      管理员已重置您的密码。继续使用项目关联和评价功能前，请先设置只有您本人知道的新密码。
    </section>

    <p v-if="errorMessage" class="customer-form-message error" role="alert">{{ errorMessage }}</p>

    <section class="customer-settings-grid">
      <form class="customer-section customer-settings-form" @submit.prevent="saveProfile">
        <div class="customer-section-heading">
          <div>
            <h2>个人资料</h2>
            <p>公开评价使用昵称生成脱敏后的客户称呼。</p>
          </div>
          <UserRound :size="22" />
        </div>
        <label>
          手机号
          <input :value="auth.user?.mobile" type="text" disabled />
          <small>手机号是登录账号，当前不支持自行修改。</small>
        </label>
        <label>
          昵称
          <input
            v-model.trim="profileForm.nickname"
            type="text"
            maxlength="100"
            :disabled="auth.setupRequired"
            required
          />
          <small>例如“林女士”，请勿填写不希望公开展示的完整姓名。</small>
        </label>
        <p v-if="profileMessage" class="customer-form-message success">{{ profileMessage }}</p>
        <button class="customer-primary-command" type="submit" :disabled="profileSaving || auth.setupRequired">
          <Save :size="17" />
          {{ profileSaving ? '正在保存' : '保存资料' }}
        </button>
      </form>

      <form class="customer-section customer-settings-form" @submit.prevent="changePassword">
        <div class="customer-section-heading">
          <div>
            <h2>修改密码</h2>
            <p>新密码需要包含 8 至 72 个字符。</p>
          </div>
          <KeyRound :size="22" />
        </div>
        <label>
          当前密码
          <input v-model="passwordForm.currentPassword" type="password" autocomplete="current-password" required />
          <small v-if="auth.setupRequired">请填写运营人员提供的临时密码。</small>
        </label>
        <label>
          新密码
          <input
            v-model="passwordForm.newPassword"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="72"
            required
          />
        </label>
        <label>
          确认新密码
          <input
            v-model="passwordForm.confirmPassword"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="72"
            required
          />
        </label>
        <p v-if="passwordMessage" class="customer-form-message success">{{ passwordMessage }}</p>
        <button class="customer-primary-command" type="submit" :disabled="passwordSaving">
          <KeyRound :size="17" />
          {{ passwordSaving ? '正在修改' : '修改密码' }}
        </button>
      </form>
    </section>
  </div>
</template>
