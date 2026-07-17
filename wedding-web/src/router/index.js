import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

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
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '婚礼作品集'} | Wedding Archive`
})

export default router
