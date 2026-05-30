import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue')
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/Register.vue')
    },
    {
      path: '/home',
      name: 'Home',
      component: () => import('@/views/Home.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      redirect: '/home'
    },
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('@/views/Profile.vue')
    },
    {
      path: '/favorites',
      name: 'Favorites',
      component: () => import('@/views/Favorites.vue')
    },
    {
      path: '/notifications',
      name: 'Notifications',
      component: () => import('@/views/Notifications.vue')
    },
    {
      path: '/admin/users',
      name: 'UserManagement',
      component: () => import('@/views/admin/UserManagement.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/schedule-import',
      name: 'ScheduleImport',
      component: () => import('@/views/ScheduleImport.vue')
    },
    {
      path: '/schedule/share/:token',
      name: 'ShareSchedule',
      component: () => import('@/views/ShareSchedule.vue'),
      meta: { public: true }
    },
    {
      path: '/tools',
      name: 'Tools',
      component: () => import('@/views/Tools.vue'),
      meta: { public: true }
    },
    {
      path: '/recommend/new',
      name: 'RecommendNew',
      component: () => import('@/views/RecommendEditor.vue')
    },
    {
      path: '/recommend/:id',
      name: 'RecommendDetail',
      component: () => import('@/views/RecommendDetail.vue')
    },
    {
      path: '/recommend/:id/edit',
      name: 'RecommendEdit',
      component: () => import('@/views/RecommendEditor.vue')
    },
    {
      path: '/tool/new',
      name: 'ToolNew',
      component: () => import('@/views/RecommendEditor.vue')
    },
    {
      path: '/tool/:id/edit',
      name: 'ToolEdit',
      component: () => import('@/views/RecommendEditor.vue')
    },
    {
      path: '/electricity',
      name: 'Electricity',
      component: () => import('@/views/ElectricityDetail.vue')
    }
  ]
})

// 公开页面（不需要登录）
const publicPages = ['/login', '/register', '/home', '/tools']

// 路由守卫：默认需要登录，公开页面除外
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  const isPublic = publicPages.includes(to.path) || to.meta.public === true

  if (!token && !isPublic) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 管理员页面额外校验
  if (to.meta.requiresAdmin) {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || 'null')
    const isAdmin = userInfo?.username === 'admin' || userInfo?.role === 'admin'
    if (!isAdmin) {
      next('/home')
      return
    }
  }

  next()
})

export default router
