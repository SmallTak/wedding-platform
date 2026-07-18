<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BarChart3,
  Bell,
  CircleUserRound,
  FolderHeart,
  Images,
  Inbox,
  LayoutDashboard,
  LayoutTemplate,
  LogOut,
  Menu,
  MessageSquareText,
  ShieldCheck,
  Tags,
  UsersRound,
  X,
} from '@lucide/vue'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notifications'
import BrandLogo from '../components/BrandLogo.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notifications = useNotificationStore()
const serviceOnline = ref(false)
const mobileMenuOpen = ref(false)

const navItems = [
  { label: '工作台', to: '/', icon: LayoutDashboard, permission: '/dashboard', route: 'dashboard' },
  { label: '站内消息', to: '/notifications', icon: Bell, permission: '/notifications', route: 'notifications' },
  { label: '婚礼项目', to: '/projects', icon: FolderHeart, permission: '/content/projects', route: 'projects' },
  {
    label: '作品集',
    to: '/collections',
    icon: Images,
    permission: '/content/collections',
    routes: ['collections', 'collection-photos'],
  },
  { label: '审核中心', to: '/reviews', icon: ShieldCheck, permission: '/review/collections', route: 'reviews' },
  { label: '创作者', to: '/creators', icon: UsersRound, permission: '/accounts/creators', route: 'creators' },
  { label: '客户账号', to: '/customers', icon: CircleUserRound, permission: '/accounts/customers', route: 'customers' },
  { label: '分类标签', to: '/content-config', icon: Tags, permission: '/config/content', route: 'content-config' },
  { label: '客户评价', to: '/feedback', icon: MessageSquareText, permission: '/operations/feedback', route: 'feedback' },
  { label: '咨询线索', to: '/inquiries', icon: Inbox, permission: '/operations/inquiries', route: 'inquiries' },
  { label: '首页运营', to: '/site-home', icon: LayoutTemplate, permission: '/site/home', route: 'site-home' },
  { label: '数据统计', to: '/analytics', icon: BarChart3, permission: '/analytics', route: 'analytics' },
]

const visibleNavItems = computed(() => navItems.filter((item) => auth.hasPermission(item.permission)))
const pageTitle = computed(() => route.meta.title || '工作台')
const accountLabel = computed(() => (auth.user?.accountType === 'ADMIN' ? '管理员' : '创作者'))
const today = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
}).format(new Date())

onMounted(async () => {
  if (auth.hasPermission('/notifications')) {
    notifications.refreshUnreadCount().catch(() => {})
  }
  try {
    const response = await http.get('/public/status')
    serviceOnline.value = response.data?.status === 'UP'
  } catch {
    serviceOnline.value = false
  }
})

function isActive(item) {
  if (item.routes) return item.routes.includes(route.name)
  return item.route ? route.name === item.route : false
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="console-shell">
    <aside class="sidebar">
      <RouterLink class="console-brand" to="/" aria-label="糖诗·美学工作台">
        <BrandLogo light />
      </RouterLink>

      <nav class="console-nav" aria-label="工作台导航">
        <RouterLink
          v-for="item in visibleNavItems"
          :key="item.label"
          :to="item.to"
          :class="{ active: isActive(item) }"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
          <b
            v-if="item.route === 'notifications' && notifications.unreadCount > 0"
            class="console-nav-badge"
          >
            {{ notifications.unreadCount > 99 ? '99+' : notifications.unreadCount }}
          </b>
        </RouterLink>
      </nav>

      <div class="sidebar-user">
        <img v-if="auth.user?.avatarPath" :src="auth.user.avatarPath" alt="" />
        <CircleUserRound v-else :size="30" />
        <div>
          <strong>{{ auth.user?.displayName || auth.user?.mobile }}</strong>
          <span>{{ accountLabel }}</span>
        </div>
      </div>
    </aside>

    <div class="console-main">
      <header class="console-header">
        <div>
          <p>{{ today }}</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="console-actions">
          <span class="api-state" :class="{ online: serviceOnline }">
            {{ serviceOnline ? 'API 正常' : '等待 API' }}
          </span>
          <button
            class="header-icon mobile-nav-trigger"
            type="button"
            :aria-label="mobileMenuOpen ? '关闭导航' : '打开导航'"
            :aria-expanded="mobileMenuOpen"
            @click="mobileMenuOpen = !mobileMenuOpen"
          >
            <X v-if="mobileMenuOpen" :size="19" />
            <Menu v-else :size="19" />
          </button>
          <RouterLink
            v-if="auth.hasPermission('/notifications')"
            class="header-icon"
            to="/notifications"
            aria-label="站内消息"
            title="站内消息"
          >
            <Bell :size="19" />
            <i v-if="notifications.unreadCount > 0"></i>
          </RouterLink>
          <button class="header-icon" type="button" aria-label="退出登录" title="退出登录" @click="logout">
            <LogOut :size="18" />
          </button>
        </div>
      </header>

      <nav v-if="mobileMenuOpen" class="mobile-navigation" aria-label="手机端工作台导航">
        <RouterLink
          v-for="item in visibleNavItems"
          :key="item.label"
          :to="item.to"
          :class="{ active: isActive(item) }"
          @click="closeMobileMenu"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
          <b
            v-if="item.route === 'notifications' && notifications.unreadCount > 0"
            class="console-nav-badge"
          >
            {{ notifications.unreadCount > 99 ? '99+' : notifications.unreadCount }}
          </b>
        </RouterLink>
      </nav>

      <RouterView />
    </div>
  </div>
</template>
