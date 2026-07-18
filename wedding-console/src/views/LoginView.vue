<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  Eye,
  EyeOff,
  LoaderCircle,
  LockKeyhole,
  Phone,
} from '@lucide/vue'
import { useAuthStore } from '../stores/auth'
import BrandLogo from '../components/BrandLogo.vue'
import loginImage from '../assets/login-wedding.jpg'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const passwordVisible = ref(false)
const errorMessage = ref('')
const form = ref({ mobile: '', password: '' })

async function submit() {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const user = await auth.login(form.value)
    const destination = user.setupRequired ? '/setup' : (route.query.redirect || '/')
    await router.replace(destination)
  } catch (error) {
    const status = error.response?.status
    if (status === 401) {
      errorMessage.value = '手机号或密码不正确'
    } else if (status === 403) {
      errorMessage.value = '当前账号已停用或无权访问工作台'
    } else if (!error.response) {
      errorMessage.value = '无法连接登录服务，请检查网络后重试'
    } else if (status >= 500) {
      errorMessage.value = '登录服务暂时不可用，请稍后重试'
    } else {
      errorMessage.value = error.response?.data?.message || '登录失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section
      class="login-visual"
      :style="{ backgroundImage: `url(${loginImage})` }"
      aria-label="糖诗·美学婚礼影像"
    >
      <div class="login-visual-brand">
        <BrandLogo light />
      </div>
      <div class="login-visual-caption">
        <span>婚礼影像运营工作台</span>
        <strong>让每一次交付，都保持作品应有的秩序与质感。</strong>
      </div>
    </section>
    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-panel-brand"><BrandLogo /></div>
      <div class="login-heading">
        <p>运营工作台</p>
        <h1 id="login-title">登录工作台</h1>
      </div>
      <form class="login-form" @submit.prevent="submit">
        <label for="login-mobile">手机号</label>
        <div class="input-with-icon">
          <Phone :size="18" />
          <input
            id="login-mobile"
            v-model.trim="form.mobile"
            type="tel"
            inputmode="numeric"
            autocomplete="username"
            maxlength="11"
            required
          />
        </div>
        <label for="login-password">密码</label>
        <div class="input-with-icon">
          <LockKeyhole :size="18" />
          <input
            id="login-password"
            v-model="form.password"
            :type="passwordVisible ? 'text' : 'password'"
            autocomplete="current-password"
            required
          />
          <button
            class="password-toggle"
            type="button"
            :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
            :title="passwordVisible ? '隐藏密码' : '显示密码'"
            @click="passwordVisible = !passwordVisible"
          >
            <EyeOff v-if="passwordVisible" :size="18" />
            <Eye v-else :size="18" />
          </button>
        </div>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <button class="primary-command" type="submit" :disabled="loading">
          <LoaderCircle v-if="loading" class="loading-icon" :size="18" />
          <span>{{ loading ? '正在登录' : '登录' }}</span>
          <ArrowRight v-if="!loading" :size="18" />
        </button>
      </form>
    </section>
  </main>
</template>
