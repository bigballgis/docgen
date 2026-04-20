import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', titleKey: 'login.title', public: true }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '首页', titleKey: 'nav.home' }
  },
  {
    path: '/templates',
    name: 'TemplateManage',
    component: () => import('@/views/TemplateManage.vue'),
    meta: { title: '模板管理', titleKey: 'nav.templates' }
  },
  {
    path: '/fragments',
    name: 'FragmentLibrary',
    component: () => import('@/views/FragmentLibrary.vue'),
    meta: { title: '片段库', titleKey: 'nav.fragments' }
  },
  {
    path: '/generate',
    name: 'DocumentGenerate',
    component: () => import('@/views/DocumentGenerate.vue'),
    meta: { title: '文档生成', titleKey: 'nav.generate' }
  },
  {
    path: '/history',
    name: 'DocumentHistory',
    component: () => import('@/views/DocumentHistory.vue'),
    meta: { title: '文档历史', titleKey: 'nav.history' }
  },
  {
    path: '/editor/:fileKey',
    name: 'DocumentEditor',
    component: () => import('@/views/DocumentEditor.vue'),
    meta: { title: '在线编辑', titleKey: 'editor.title' }
  },
  {
    path: '/settings',
    name: 'UserSettings',
    component: () => import('@/views/UserSettings.vue'),
    meta: { title: '个人设置', titleKey: 'settings.title' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 白名单路由（不需要登录即可访问）
const whiteList = ['/login']

router.beforeEach((to, from, next) => {
  // 设置页面标题（使用静态 title，因为 i18n 可能还未初始化）
  document.title = `${to.meta.title || '银文通'} - 银文通 DocGen`

  const token = localStorage.getItem('token')

  if (token) {
    // 已登录
    if (to.path === '/login') {
      // 已登录访问登录页，重定向到首页
      next({ path: '/' })
    } else {
      next()
    }
  } else {
    // 未登录
    if (whiteList.includes(to.path) || to.meta.public) {
      // 在白名单中，直接放行
      next()
    } else {
      // 不在白名单中，重定向到登录页
      next(`/login?redirect=${to.path}`)
    }
  }
})

export default router
