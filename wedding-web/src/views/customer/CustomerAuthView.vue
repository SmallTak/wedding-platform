<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  Eye,
  EyeOff,
  LoaderCircle,
  LockKeyhole,
  Phone,
  UserRound,
} from '@lucide/vue'
import BrandLogo from '../../components/BrandLogo.vue'
import heroImage from '../../assets/wedding-hero.jpg'
import { useCustomerAuthStore } from '../../stores/customerAuth'

const route = useRoute()
const router = useRouter()
const auth = useCustomerAuthStore()
const submitting = ref(false)
const passwordVisible = ref(false)
const errorMessage = ref('')
const isRegister = computed(() => route.name === 'customer-register')
const form = reactive({
  mobile: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

watch(isRegister, () => {
  errorMessage.value = ''
  form.password = ''
  form.confirmPassword = ''
})

async function submit() {
  if (submitting.value) return
  if (isRegister.value && form.password !== form.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    if (isRegister.value) {
      await auth.register({
        mobile: form.mobile.trim(),
        nickname: form.nickname.trim(),
        password: form.password,
      })
      await router.replace({ name: 'customer-feedback' })
    } else {
      const user = await auth.login({
        mobile: form.mobile.trim(),
        password: form.password,
      })
      const destination = user.setupRequired
        ? { name: 'customer-settings' }
        : (route.query.redirect || { name: 'customer-feedback' })
      await router.replace(destination)
    }
  } catch (error) {
    const code = error.response?.data?.code || error.code
    if (code === 'INVALID_CREDENTIALS') errorMessage.value = '手机号或密码不正确'
    else if (code === 'MOBILE_EXISTS') errorMessage.value = '该手机号已经注册，请直接登录'
    else if (code === 'CUSTOMER_ACCOUNT_REQUIRED') errorMessage.value = '该账号不是客户账号，请使用客户手机号登录'
    else if (!error.response) errorMessage.value = '暂时无法连接服务，请检查网络后重试'
    else errorMessage.value = error.response?.data?.message || '操作失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="customer-auth-page">
    <section class="customer-auth-visual" :style="{ backgroundImage: `url(${heroImage})` }">
      <RouterLink class="customer-auth-brand" to="/" aria-label="糖诗·美学首页">
        <BrandLogo light />
      </RouterLink>
      <div class="customer-auth-caption">
        <span>糖诗影像 · 客户中心</span>
        <strong>妥善收好每一次联系，也认真回应婚礼之后的每一份感受。</strong>
      </div>
    </section>

    <section class="customer-auth-panel">
      <RouterLink class="customer-auth-back" to="/">
        <ArrowLeft :size="17" />
        返回官网
      </RouterLink>
      <div class="customer-auth-heading">
        <p>{{ isRegister ? '创建客户账号' : '欢迎回来' }}</p>
        <h1>{{ isRegister ? '注册客户中心' : '登录客户中心' }}</h1>
      </div>

      <form class="customer-auth-form" @submit.prevent="submit">
        <label for="customer-mobile">手机号</label>
        <div class="customer-input-with-icon">
          <Phone :size="18" />
          <input
            id="customer-mobile"
            v-model.trim="form.mobile"
            type="tel"
            inputmode="numeric"
            autocomplete="username"
            maxlength="11"
            pattern="1[0-9]{10}"
            required
          />
        </div>

        <template v-if="isRegister">
          <label for="customer-nickname">昵称</label>
          <div class="customer-input-with-icon">
            <UserRound :size="18" />
            <input
              id="customer-nickname"
              v-model.trim="form.nickname"
              type="text"
              maxlength="100"
              autocomplete="nickname"
              placeholder="例如：林女士"
              required
            />
          </div>
        </template>

        <label for="customer-password">密码</label>
        <div class="customer-input-with-icon">
          <LockKeyhole :size="18" />
          <input
            id="customer-password"
            v-model="form.password"
            :type="passwordVisible ? 'text' : 'password'"
            :autocomplete="isRegister ? 'new-password' : 'current-password'"
            minlength="8"
            maxlength="72"
            required
          />
          <button
            type="button"
            :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
            :title="passwordVisible ? '隐藏密码' : '显示密码'"
            @click="passwordVisible = !passwordVisible"
          >
            <EyeOff v-if="passwordVisible" :size="18" />
            <Eye v-else :size="18" />
          </button>
        </div>

        <template v-if="isRegister">
          <label for="customer-password-confirm">确认密码</label>
          <div class="customer-input-with-icon">
            <LockKeyhole :size="18" />
            <input
              id="customer-password-confirm"
              v-model="form.confirmPassword"
              :type="passwordVisible ? 'text' : 'password'"
              autocomplete="new-password"
              minlength="8"
              maxlength="72"
              required
            />
          </div>
        </template>

        <p v-if="errorMessage" class="customer-form-message error" role="alert">{{ errorMessage }}</p>
        <p v-if="!isRegister" class="customer-auth-help">忘记密码请联系运营人员，由管理员核验身份后重置。</p>

        <button class="customer-primary-command" type="submit" :disabled="submitting">
          <LoaderCircle v-if="submitting" class="customer-loading-icon" :size="18" />
          <span>{{ submitting ? '正在提交' : (isRegister ? '注册并进入' : '登录') }}</span>
          <ArrowRight v-if="!submitting" :size="18" />
        </button>
      </form>

      <p class="customer-auth-switch">
        {{ isRegister ? '已经注册？' : '还没有客户账号？' }}
        <RouterLink :to="{ name: isRegister ? 'customer-login' : 'customer-register' }">
          {{ isRegister ? '直接登录' : '立即注册' }}
        </RouterLink>
      </p>
    </section>
  </main>
</template>
