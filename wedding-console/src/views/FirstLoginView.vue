<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Camera, CircleUserRound, LogOut } from '@lucide/vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const fileInput = ref(null)
const loading = ref(false)
const uploading = ref(false)
const errorMessage = ref('')

const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const profileForm = reactive({
  displayName: auth.user?.displayName || '',
  avatarPath: auth.user?.avatarPath || '',
  positionText: auth.user?.positionText || '',
  serviceArea: auth.user?.serviceArea || '',
  introduction: auth.user?.introduction || '',
})

async function chooseAvatar(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploading.value = true
  errorMessage.value = ''
  try {
    profileForm.avatarPath = await auth.uploadAvatar(file)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '头像上传失败'
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

async function submit() {
  errorMessage.value = ''
  if (auth.user?.mustChangePassword && passwordForm.newPassword !== passwordForm.confirmPassword) {
    errorMessage.value = '两次输入的新密码不一致'
    return
  }
  if (!auth.user?.profileCompleted && !profileForm.avatarPath) {
    errorMessage.value = '请先上传头像'
    return
  }

  loading.value = true
  try {
    if (auth.user?.mustChangePassword) {
      await auth.changePassword({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      })
    }
    if (!auth.user?.profileCompleted) {
      await auth.updateProfile(profileForm)
    }
    await router.replace('/')
  } catch (error) {
    const code = error.response?.data?.code
    errorMessage.value = code === 'CURRENT_PASSWORD_INVALID' ? '当前密码不正确' : (error.response?.data?.message || '资料保存失败')
  } finally {
    loading.value = false
  }
}

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<template>
  <main class="setup-page">
    <header class="setup-header">
      <div class="login-brand"><span>WA</span><strong>Wedding Console</strong></div>
      <button class="icon-command" type="button" aria-label="退出登录" title="退出登录" @click="logout"><LogOut :size="18" /></button>
    </header>
    <section class="setup-content" aria-labelledby="setup-title">
      <div class="setup-heading">
        <p>First sign-in</p>
        <h1 id="setup-title">完善账号信息</h1>
      </div>
      <form class="setup-form" @submit.prevent="submit">
        <fieldset v-if="auth.user?.mustChangePassword">
          <legend><span>01</span>修改初始密码</legend>
          <div class="form-grid three-columns">
            <label>当前密码<input v-model="passwordForm.currentPassword" type="password" autocomplete="current-password" required /></label>
            <label>新密码<input v-model="passwordForm.newPassword" type="password" minlength="8" maxlength="72" autocomplete="new-password" required /></label>
            <label>确认新密码<input v-model="passwordForm.confirmPassword" type="password" minlength="8" maxlength="72" autocomplete="new-password" required /></label>
          </div>
        </fieldset>

        <fieldset v-if="!auth.user?.profileCompleted">
          <legend><span>02</span>补齐个人资料</legend>
          <div class="profile-editor">
            <div class="avatar-editor">
              <div class="avatar-preview">
                <img v-if="profileForm.avatarPath" :src="profileForm.avatarPath" alt="头像预览" />
                <CircleUserRound v-else :size="48" />
              </div>
              <input ref="fileInput" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="chooseAvatar" />
              <button class="secondary-command" type="button" :disabled="uploading" @click="fileInput?.click()">
                <Camera :size="17" />{{ uploading ? '上传中' : '上传头像' }}
              </button>
            </div>
            <div class="form-grid profile-fields">
              <label>姓名<input v-model.trim="profileForm.displayName" maxlength="100" required /></label>
              <label>职位<input v-model.trim="profileForm.positionText" maxlength="100" placeholder="摄影师、化妆师等" required /></label>
              <label class="full-field">服务地区<input v-model.trim="profileForm.serviceArea" maxlength="300" placeholder="杭州、湖州、绍兴" /></label>
              <label class="full-field">个人简介<textarea v-model.trim="profileForm.introduction" maxlength="1000" rows="4"></textarea></label>
            </div>
          </div>
        </fieldset>

        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <div class="form-actions">
          <button class="primary-command" type="submit" :disabled="loading || uploading">
            <span>{{ loading ? '正在保存' : '进入工作台' }}</span><ArrowRight :size="18" />
          </button>
        </div>
      </form>
    </section>
  </main>
</template>
