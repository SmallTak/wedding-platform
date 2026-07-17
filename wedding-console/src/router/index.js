import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView,
      meta: {
        title: '工作台',
      },
    },
  ],
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '工作台'} | Wedding Console`
})

export default router

