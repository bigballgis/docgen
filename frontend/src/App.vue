<template>
  <el-config-provider :locale="elementLocale">
    <div class="app-container">
      <!-- 登录页不显示侧边栏 -->
      <template v-if="route.path !== '/login'">
        <!-- 左侧侧边栏 -->
        <aside class="sidebar" :class="{ collapsed: isCollapsed }">
          <!-- Logo 区域 -->
          <div class="sidebar-logo" @click="$router.push('/')">
            <div class="logo-icon">
              <el-icon :size="20"><Document /></el-icon>
            </div>
            <span class="logo-text" v-show="!isCollapsed">{{ $t('app.name') }}</span>
          </div>

          <!-- 折叠/展开按钮 -->
          <div class="sidebar-collapse-btn" @click="toggleCollapse">
            <el-icon :size="18">
              <Fold v-if="!isCollapsed" />
              <Expand v-else />
            </el-icon>
            <span class="collapse-label" v-show="!isCollapsed">{{ $t('common.collapse') }}</span>
          </div>

          <!-- 租户切换器（仅 admin 可见） -->
          <div v-if="authStore.isAdmin" class="tenant-switcher" v-show="!isCollapsed">
            <el-select
              :model-value="authStore.tenantId"
              :placeholder="$t('tenant.switch')"
              size="small"
              style="width: 100%"
              @change="handleTenantChange"
            >
              <template #prefix>
                <el-icon :size="14"><OfficeBuilding /></el-icon>
              </template>
              <el-option
                v-for="tenant in authStore.tenants"
                :key="tenant.id || tenant.tenantId"
                :label="tenant.name || tenant.tenantName"
                :value="tenant.id || tenant.tenantId"
              />
            </el-select>
          </div>

          <!-- 导航菜单 -->
          <nav class="sidebar-nav">
            <router-link
              v-for="nav in navItems"
              :key="nav.path"
              :to="nav.path"
              :class="['sidebar-nav-item', { active: isActive(nav.path) }]"
            >
              <el-icon class="nav-icon"><component :is="nav.icon" /></el-icon>
              <span class="nav-label" v-show="!isCollapsed">{{ nav.label }}</span>
            </router-link>
          </nav>

          <!-- 底部用户信息 -->
          <div class="sidebar-footer">
            <!-- 语言切换 -->
            <div class="lang-switch" v-show="!isCollapsed">
              <el-dropdown trigger="click" @command="handleLocaleChange">
                <span class="lang-switch-btn">
                  <el-icon :size="14"><Setting /></el-icon>
                  <span>{{ currentLocaleLabel }}</span>
                  <el-icon :size="12"><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="zh-CN">中文</el-dropdown-item>
                    <el-dropdown-item command="en-US">English</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <!-- 暗色模式切换 -->
            <div class="dark-mode-switch">
              <div class="dark-mode-btn" @click="toggleDarkMode">
                <el-icon :size="16">
                  <Sunny v-if="isDark" />
                  <Moon v-else />
                </el-icon>
                <span class="dark-mode-label" v-show="!isCollapsed">
                  {{ isDark ? $t('settings.lightMode') : $t('settings.darkMode') }}
                </span>
              </div>
            </div>
            <div class="sidebar-user" @click="$router.push('/settings')">
              <div class="user-avatar">
                {{ authStore.username.charAt(0) }}
              </div>
              <span class="user-name" v-show="!isCollapsed">{{ authStore.username }}</span>
              <el-tooltip :content="$t('common.logout')" placement="top" :show-after="500">
                <div class="logout-btn" @click.stop="handleLogout">
                  <el-icon :size="16"><SwitchButton /></el-icon>
                </div>
              </el-tooltip>
            </div>
          </div>
        </aside>

        <!-- 右侧主区域 -->
        <div class="main-content" :class="{ collapsed: isCollapsed }">
          <!-- 顶部面包屑 -->
          <div class="breadcrumb-bar">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/' }">{{ $t('nav.home') }}</el-breadcrumb-item>
              <el-breadcrumb-item v-if="route.meta.title && route.path !== '/'">
                {{ $t(route.meta.titleKey || 'nav.home') }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>

          <!-- 主内容区 -->
          <div class="content-area">
            <router-view v-slot="{ Component }">
              <transition name="fade" mode="out-in">
                <component :is="Component" />
              </transition>
            </router-view>
          </div>
        </div>
      </template>

      <!-- 登录页直接渲染 -->
      <router-view v-else />
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
import {
  Document,
  HomeFilled,
  Files,
  CopyDocument,
  EditPen,
  Clock,
  SwitchButton,
  Setting,
  ArrowDown,
  OfficeBuilding,
  Fold,
  Expand,
  Sunny,
  Moon
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { locale, t } = useI18n()

// ==================== 暗色模式 ====================
const isDark = ref(false)

function initDarkMode() {
  const saved = localStorage.getItem('darkMode')
  if (saved === 'true') {
    isDark.value = true
    document.documentElement.classList.add('dark')
  } else if (saved === 'false') {
    isDark.value = false
    document.documentElement.classList.remove('dark')
  } else {
    // 跟随系统偏好
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    isDark.value = prefersDark
    if (prefersDark) {
      document.documentElement.classList.add('dark')
    }
  }
}

function toggleDarkMode() {
  isDark.value = !isDark.value
  document.documentElement.classList.toggle('dark', isDark.value)
  localStorage.setItem('darkMode', String(isDark.value))
}

// ==================== 侧边栏折叠 ====================
const isCollapsed = ref(false)

function initSidebarCollapse() {
  const saved = localStorage.getItem('sidebarCollapsed')
  isCollapsed.value = saved === 'true'
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('sidebarCollapsed', String(isCollapsed.value))
}

// ==================== Element Plus locale 响应式 ====================
const elementLocale = computed(() => {
  return locale.value === 'en-US' ? en : zhCn
})

// 当前语言标签
const currentLocaleLabel = computed(() => {
  return locale.value === 'en-US' ? 'English' : '中文'
})

// 导航菜单（使用 i18n key）
const navItems = computed(() => [
  { path: '/', label: t('nav.home'), icon: 'HomeFilled' },
  { path: '/templates', label: t('nav.templates'), icon: 'Files' },
  { path: '/fragments', label: t('nav.fragments'), icon: 'CopyDocument' },
  { path: '/generate', label: t('nav.generate'), icon: 'EditPen' },
  { path: '/history', label: t('nav.history'), icon: 'Clock' }
])

const isActive = (path) => {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

const handleLogout = () => {
  authStore.logout()
}

const handleLocaleChange = (newLocale) => {
  locale.value = newLocale
  localStorage.setItem('locale', newLocale)
}

/**
 * 切换租户
 */
function handleTenantChange(tenantId) {
  const tenant = authStore.tenants.find(
    t => (t.id || t.tenantId) === tenantId
  )
  if (tenant) {
    authStore.setTenant(
      tenant.id || tenant.tenantId,
      tenant.name || tenant.tenantName
    )
    // 刷新页面数据
    window.location.reload()
  }
}

// 页面初始化
onMounted(() => {
  initDarkMode()
  initSidebarCollapse()
  if (authStore.isLoggedIn && authStore.isAdmin) {
    authStore.fetchTenants()
  }
})
</script>

<style scoped>
.app-container {
  min-height: 100vh;
}

/* 侧边栏已在 global.css 中定义基础样式，此处补充 */
.sidebar-logo {
  cursor: pointer;
}

/* 侧边栏折叠 */
.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
}

.main-content.collapsed {
  margin-left: var(--sidebar-collapsed-width);
}

/* 折叠按钮 */
.sidebar-collapse-btn {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  margin: 0 12px 4px;
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  transition: all 0.2s;
  font-size: 13px;
  gap: 10px;
  user-select: none;
}

.sidebar-collapse-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

.sidebar.collapsed .sidebar-collapse-btn {
  justify-content: center;
  padding: 8px;
  margin: 0 12px 4px;
}

.collapse-label {
  white-space: nowrap;
}

/* 租户切换器 */
.tenant-switcher {
  padding: 8px 12px 4px;
}

.tenant-switcher :deep(.el-select) {
  --el-select-border-color-hover: rgba(255, 255, 255, 0.3);
}

.tenant-switcher :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: none;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
}

.tenant-switcher :deep(.el-input__wrapper:hover) {
  border-color: rgba(255, 255, 255, 0.3);
}

.tenant-switcher :deep(.el-input__inner) {
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
}

.tenant-switcher :deep(.el-input__prefix .el-icon) {
  color: rgba(255, 255, 255, 0.7);
}

.tenant-switcher :deep(.el-select__suffix .el-icon) {
  color: rgba(255, 255, 255, 0.7);
}

/* 语言切换 */
.lang-switch {
  padding: 0 12px 8px;
}

.lang-switch-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.lang-switch-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

/* 暗色模式切换 */
.dark-mode-switch {
  padding: 0 12px 8px;
}

.dark-mode-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.dark-mode-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

.sidebar.collapsed .dark-mode-btn {
  justify-content: center;
  padding: 6px;
}

.dark-mode-label {
  white-space: nowrap;
}

/* 侧边栏用户区域可点击 */
.sidebar-user {
  cursor: pointer;
}
</style>
