<script setup>
import { reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  targetLabel: {
    type: String,
    default: '内容',
  },
  actionLabel: {
    type: String,
    default: '发布',
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue', 'submit'])

const form = reactive({
  visibility: 'PUBLIC',
  accessPassword: '',
})

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      form.visibility = 'PUBLIC'
      form.accessPassword = ''
    }
  },
)

function close() {
  emit('update:modelValue', false)
}

function submit() {
  if (form.visibility === 'PASSWORD') {
    const passwordBytes = new TextEncoder().encode(form.accessPassword).length
    if (form.accessPassword.length < 6 || form.accessPassword.length > 64 || passwordBytes > 72) {
      ElMessage.error('访问密码需为 6 至 64 个字符，且不能超过 72 字节')
      return
    }
  }
  emit('submit', {
    visibility: form.visibility,
    accessPassword: form.visibility === 'PASSWORD' ? form.accessPassword : null,
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${actionLabel}${targetLabel}`"
    width="520px"
    class="management-dialog publication-dialog"
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <form id="publication-form" class="publication-form" @submit.prevent="submit">
      <label>
        访问方式
        <el-radio-group v-model="form.visibility">
          <el-radio-button value="PUBLIC">公开</el-radio-button>
          <el-radio-button value="PASSWORD">密码访问</el-radio-button>
          <el-radio-button value="HIDDEN">隐藏</el-radio-button>
        </el-radio-group>
      </label>
      <label v-if="form.visibility === 'PASSWORD'">
        访问密码
        <el-input
          v-model="form.accessPassword"
          type="password"
          maxlength="64"
          show-password
          autocomplete="new-password"
        />
      </label>
    </form>
    <template #footer>
      <el-button :disabled="loading" @click="close">取消</el-button>
      <el-button type="primary" native-type="submit" form="publication-form" :loading="loading">
        确认{{ actionLabel }}
      </el-button>
    </template>
  </el-dialog>
</template>
