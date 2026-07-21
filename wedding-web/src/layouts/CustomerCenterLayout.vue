<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  CircleUserRound,
  House,
  LogOut,
  Menu,
  MessageSquareText,
  Settings,
  X,
} from '@lucide/vue'
import BrandLogo from '../components/BrandLogo.vue'
import { useCustomerAuthStore } from '../stores/customerAuth'
import { useCustomerNotificationStore } from '../stores/customerNotifications'

const route = useRoute()
const router = useRouter()
const auth = useCustomerAuthStore()
const notifications = useCustomerNotificationStore()
const menuOpen = ref(false)

const navigation = [
  { label: '站内消息', name: 'customer-messages', icon: Bell },
  { label: '我的评价', name: 'customer-feedback', icon: MessageSquareText },
  { label: '账号设置', name: 'customer-settings', icon: Settings },
]
const pageTitle = computed(() => route.meta.title || '客户中心')

onMounted(() => {
  if (!auth.setupRequired) notifications.refreshUnreadCount().catch(() => {})
})

watch(
  () => auth.setupRequired,
  (setupRequired, previousValue) => {
    if (previousValue && !setupRequired) notifications.refreshUnreadCount().catch(() => {})
  },
)

function logout() {
  auth.logout()
  router.replace({ name: 'customer-login' })
}
</script>

<template>
  <div class="customer-shell">
    <header class="customer-header">
      <RouterLink class="brand" to="/" aria-label="糖诗·美学首页">
        <BrandLogo />
      </RouterLink>
      <div class="customer-header-title">
        <span>TANGSHI CLIENT</span>
        <strong>{{ pageTitle }}</strong>
      </div>
      <div class="customer-header-actions">
        <RouterLink class="customer-icon-button" to="/" aria-label="返回官网" title="返回官网">
          <House :size="18" />
        </RouterLink>
        <button
          class="customer-icon-button customer-menu-trigger"
          type="button"
          :aria-label="menuOpen ? '关闭导航' : '打开导航'"
          :aria-expanded="menuOpen"
          @click="menuOpen = !menuOpen"
        >
          <X v-if="menuOpen" :size="19" />
          <Menu v-else :size="19" />
        </button>
        <button class="customer-icon-button" type="button" aria-label="退出登录" title="退出登录" @click="logout">
          <LogOut :size="18" />
        </button>
      </div>
    </header>

    <div class="customer-workspace">
      <aside :class="['customer-sidebar', { open: menuOpen }]">
        <div class="customer-identity">
          <CircleUserRound :size="34" />
          <div>
            <strong>{{ auth.user?.nickname || auth.user?.displayName || '客户' }}</strong>
            <span>{{ auth.user?.mobile }}</span>
          </div>
        </div>
        <nav aria-label="客户中心导航">
          <RouterLink
            v-for="item in navigation"
            :key="item.name"
            :to="{ name: item.name }"
            @click="menuOpen = false"
          >
            <component :is="item.icon" :size="18" />
            <span>{{ item.label }}</span>
            <strong
              v-if="item.name === 'customer-messages' && notifications.unreadCount > 0"
              class="customer-nav-badge"
            >
              {{ notifications.unreadCount > 99 ? '99+' : notifications.unreadCount }}
            </strong>
          </RouterLink>
        </nav>
      </aside>

      <main class="customer-main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
