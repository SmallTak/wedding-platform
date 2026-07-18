import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/setup',
      name: 'setup',
      component: () => import('../views/FirstLoginView.vue'),
      meta: { title: '完善账号', setup: true },
    },
    {
      path: '/',
      component: () => import('../layouts/ConsoleLayout.vue'),
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('../views/DashboardView.vue'),
          meta: { title: '运营工作台', permission: '/dashboard' },
        },
        {
          path: 'creators',
          name: 'creators',
          component: () => import('../views/CreatorAccountsView.vue'),
          meta: { title: '创作者账号', permission: '/accounts/creators' },
        },
        {
          path: 'projects',
          name: 'projects',
          component: () => import('../views/ProjectsView.vue'),
          meta: { title: '婚礼项目', permission: '/content/projects' },
        },
        {
          path: 'collections',
          name: 'collections',
          component: () => import('../views/CollectionsView.vue'),
          meta: { title: '作品集', permission: '/content/collections' },
        },
        {
          path: 'collections/:collectionId/photos',
          name: 'collection-photos',
          component: () => import('../views/CollectionPhotosView.vue'),
          meta: { title: '图片管理', permission: '/content/collections' },
        },
        {
          path: 'reviews',
          name: 'reviews',
          component: () => import('../views/ReviewCenterView.vue'),
          meta: { title: '审核中心', permission: '/review/collections' },
        },
        {
          path: 'content-config',
          name: 'content-config',
          component: () => import('../views/ContentConfigView.vue'),
          meta: { title: '分类标签', permission: '/config/content' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore(pinia)
  if (to.meta.public) {
    return auth.token ? (auth.setupRequired ? '/setup' : '/') : true
  }
  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!auth.user) {
    try {
      await auth.fetchCurrentUser()
    } catch {
      auth.logout()
      return '/login'
    }
  }
  if (auth.setupRequired && !to.meta.setup) {
    return '/setup'
  }
  if (!auth.setupRequired && to.meta.setup) {
    return '/'
  }
  if (to.meta.permission && !auth.hasPermission(to.meta.permission)) {
    return '/'
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '工作台'} | 糖诗·美学`
})

export default router
