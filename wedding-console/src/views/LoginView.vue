<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, LockKeyhole, Phone } from '@lucide/vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const form = ref({ mobile: '', password: '' })

async function submit() {
  loading.value = true
  errorMessage.value = ''
  try {
    const user = await auth.login(form.value)
    const destination = user.setupRequired ? '/setup' : (route.query.redirect || '/')
    await router.replace(destination)
  } catch (error) {
    errorMessage.value = error.response?.status === 401 ? '手机号或密码不正确' : '暂时无法登录，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-brand"><span>WA</span><strong>Wedding Console</strong></div>
      <div class="login-heading">
        <p>Account access</p>
        <h1 id="login-title">登录工作台</h1>
      </div>
      <form class="login-form" @submit.prevent="submit">
        <label for="login-mobile">手机号</label>
        <div class="input-with-icon">
          <Phone :size="18" />
          <input id="login-mobile" v-model.trim="form.mobile" inputmode="numeric" autocomplete="username" maxlength="11" required />
        </div>
        <label for="login-password">密码</label>
        <div class="input-with-icon">
          <LockKeyhole :size="18" />
          <input id="login-password" v-model="form.password" type="password" autocomplete="current-password" required />
        </div>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <button class="primary-command" type="submit" :disabled="loading">
          <span>{{ loading ? '正在登录' : '登录' }}</span><ArrowRight :size="18" />
        </button>
      </form>
    </section>
  </main>
</template>
