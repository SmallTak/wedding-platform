<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BarChart3,
  Bell,
  CircleUserRound,
  FolderHeart,
  Images,
  LayoutDashboard,
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

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const serviceOnline = ref(false)
const mobileMenuOpen = ref(false)

const navItems = [
  { label: '工作台', to: '/', icon: LayoutDashboard, permission: '/dashboard', route: 'dashboard' },
  { label: '婚礼项目', to: '/projects', icon: FolderHeart, permission: '/content/projects', route: 'projects' },
  {
    label: '作品集',
    to: '/collections',
    icon: Images,
    permission: '/content/collections',
    routes: ['collections', 'collection-photos'],
  },
  { label: '审核中心', to: '/#review', icon: ShieldCheck, permission: '/review/collections' },
  { label: '创作者', to: '/creators', icon: UsersRound, permission: '/accounts/creators', route: 'creators' },
  { label: '分类标签', to: '/content-config', icon: Tags, permission: '/config/content', route: 'content-config' },
  { label: '客户反馈', to: '/', icon: MessageSquareText, permission: '/accounts/customers' },
  { label: '数据统计', to: '/', icon: BarChart3, permission: '/analytics' },
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
      <RouterLink class="console-brand" to="/" aria-label="Wedding Console 工作台">
        <span>WA</span>
        <strong>Wedding Console</strong>
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
          <button class="header-icon" type="button" aria-label="站内消息" title="站内消息">
            <Bell :size="19" />
            <i></i>
          </button>
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
        </RouterLink>
      </nav>

      <RouterView />
    </div>
  </div>
</template>
