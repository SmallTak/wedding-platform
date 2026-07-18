<script setup>
import markDark from '../assets/brand/mark-dark.svg'
import markLight from '../assets/brand/mark-light.svg'

defineProps({
  light: {
    type: Boolean,
    default: false,
  },
  compact: {
    type: Boolean,
    default: false,
  },
})
</script>

<template>
  <span :class="['brand-lockup', { 'is-compact': compact }]">
    <img :src="light ? markLight : markDark" alt="" />
    <span>
      <strong>糖诗·美学</strong>
      <small v-if="!compact">TANGSHI AESTHETICS</small>
    </span>
  </span>
</template>
