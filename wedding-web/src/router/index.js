import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { publicApi } from '../api/public'
import { pinia } from '../stores'
import { useCustomerAuthStore } from '../stores/customerAuth'

const trackedPublicRoutes = new Set([
  'home',
  'collection-detail',
  'reviews',
])

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: {
        title: '婚礼作品集',
      },
    },
    {
      path: '/collections/:collectionId',
      name: 'collection-detail',
      component: () => import('../views/CollectionDetailView.vue'),
      meta: {
        title: '婚礼作品',
      },
    },
    {
      path: '/reviews',
      name: 'reviews',
      component: () => import('../views/ReviewsView.vue'),
      meta: {
        title: '客户评价',
      },
    },
    {
      path: '/customer/login',
      name: 'customer-login',
      component: () => import('../views/customer/CustomerAuthView.vue'),
      meta: { title: '客户登录', customerGuest: true },
    },
    {
      path: '/customer/register',
      name: 'customer-register',
      component: () => import('../views/customer/CustomerAuthView.vue'),
      meta: { title: '客户注册', customerGuest: true },
    },
    {
      path: '/customer',
      component: () => import('../layouts/CustomerCenterLayout.vue'),
      meta: { customer: true },
      children: [
        { path: '', redirect: { name: 'customer-feedback' } },
        {
          path: 'messages',
          name: 'customer-messages',
          component: () => import('../views/customer/CustomerMessagesView.vue'),
          meta: { title: '站内消息', customer: true },
        },
        {
          path: 'feedback',
          name: 'customer-feedback',
          component: () => import('../views/customer/CustomerFeedbackView.vue'),
          meta: { title: '我的评价', customer: true },
        },
        {
          path: 'settings',
          name: 'customer-settings',
          component: () => import('../views/customer/CustomerSettingsView.vue'),
          meta: { title: '账号设置', customer: true },
        },
      ],
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const auth = useCustomerAuthStore(pinia)

  if (to.meta.customerGuest) {
    if (!auth.token) return true
    try {
      if (!auth.user) await auth.fetchCurrentUser()
      return auth.setupRequired
        ? { name: 'customer-settings' }
        : { name: 'customer-feedback' }
    } catch {
      auth.logout()
      return true
    }
  }

  if (!to.meta.customer) return true
  if (!auth.token) {
    return { name: 'customer-login', query: { redirect: to.fullPath } }
  }
  if (!auth.user) {
    try {
      await auth.fetchCurrentUser()
    } catch {
      auth.logout()
      return { name: 'customer-login' }
    }
  }
  if (auth.setupRequired && to.name !== 'customer-settings') {
    return { name: 'customer-settings' }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '婚礼作品集'} | 糖诗·美学`
  if (trackedPublicRoutes.has(to.name)) {
    publicApi.trackVisit('SITE').catch(() => {})
  }
})

export default router
