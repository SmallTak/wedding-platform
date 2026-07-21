<script setup>
import { ref } from 'vue'
import { KeyRound } from '@lucide/vue'

defineProps({
  loading: {
    type: Boolean,
    default: false,
  },
  errorMessage: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['submit'])
const password = ref('')

function submit() {
  emit('submit', password.value)
}
</script>

<template>
  <section class="access-gate">
    <KeyRound :size="30" stroke-width="1.5" />
    <p class="section-kicker">Private archive</p>
    <h1>这是一卷私藏影像</h1>
    <p class="access-gate-copy">请输入摄影团队提供的访问密码，继续查看完整作品。</p>
    <form @submit.prevent="submit">
      <label for="content-access-password">访问密码</label>
      <div>
        <input
          id="content-access-password"
          v-model="password"
          type="password"
          minlength="6"
          maxlength="64"
          autocomplete="current-password"
          autofocus
        />
        <button type="submit" :disabled="loading">
          {{ loading ? '验证中...' : '启封' }}
        </button>
      </div>
      <p v-if="errorMessage" class="access-error">{{ errorMessage }}</p>
    </form>
  </section>
</template>
