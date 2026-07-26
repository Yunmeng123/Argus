import { createRouter, createWebHashHistory } from 'vue-router'

// hash 路由: 构建产物由 Spring Boot static/ 直出, 无需服务端路由回退配置
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: () => import('../views/HomeView.vue') },
    { path: '/new', component: () => import('../views/ReviewNew.vue') },
    { path: '/reviews', component: () => import('../views/ReviewList.vue') },
    { path: '/reviews/:id', component: () => import('../views/ReviewDetail.vue') },
    { path: '/dashboard', component: () => import('../views/DashboardView.vue') },
    { path: '/authors', component: () => import('../views/AuthorsView.vue') },
    { path: '/authors/:author', component: () => import('../views/AuthorDetailView.vue') },
    { path: '/config', component: () => import('../views/ConfigView.vue') }
  ]
})

export default router
