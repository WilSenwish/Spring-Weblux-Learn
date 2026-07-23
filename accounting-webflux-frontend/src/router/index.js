import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/RegisterView.vue')
  },
  {
    path: '/',
    component: () => import('../layout/Layout.vue'),
    children: [
      {
        path: 'bills',
        name: 'Bills',
        component: () => import('../views/BillListView.vue')
      },
      {
        path: 'categories',
        name: 'Categories',
        component: () => import('../views/CategoryView.vue')
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('../views/StatisticsView.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const whiteList = ['/login', '/register']

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  if (whiteList.includes(to.path)) {
    next()
  } else if (!authStore.isLoggedIn) {
    next('/login')
  } else {
    next()
  }
})

export default router
