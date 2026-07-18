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
    <h1>请输入访问密码</h1>
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
          {{ loading ? '验证中...' : '进入' }}
        </button>
      </div>
      <p v-if="errorMessage" class="access-error">{{ errorMessage }}</p>
    </form>
  </section>
</template>
