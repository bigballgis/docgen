<template>
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
      <LangSwitch v-show="!isCollapsed" />
      <!-- 暗色模式切换 -->
      <DarkModeSwitch :is-collapsed="isCollapsed" />
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
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  Document,
  HomeFilled,
  Files,
  CopyDocument,
  EditPen,
  Clock,
  SwitchButton,
  OfficeBuilding,
  Fold,
  Expand
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import LangSwitch from './LangSwitch.vue'
import DarkModeSwitch from './DarkModeSwitch.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

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
  initSidebarCollapse()
  if (authStore.isLoggedIn && authStore.isAdmin) {
    authStore.fetchTenants()
  }
})
</script>

<style scoped>
/* 侧边栏已在 global.css 中定义基础样式，此处补充 */
.sidebar-logo {
  cursor: pointer;
}

/* 侧边栏折叠 */
.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
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

/* 侧边栏用户区域可点击 */
.sidebar-user {
  cursor: pointer;
}
</style>