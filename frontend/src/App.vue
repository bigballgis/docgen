<template>
  <el-config-provider :locale="elementLocale">
    <div class="app-container">
      <!-- 登录页不显示侧边栏 -->
      <template v-if="route.path !== '/login'">
        <!-- 左侧侧边栏 -->
        <Sidebar />

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
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
import { useAuthStore } from '@/stores/auth'
import Sidebar from '@/components/Sidebar.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { locale } = useI18n()

// ==================== 侧边栏折叠状态 ====================
const isCollapsed = ref(false)

function initSidebarCollapse() {
  const saved = localStorage.getItem('sidebarCollapsed')
  isCollapsed.value = saved === 'true'
}

// ==================== Element Plus locale 响应式 ====================
const elementLocale = computed(() => {
  return locale.value === 'en-US' ? en : zhCn
})

// 页面初始化
onMounted(() => {
  initSidebarCollapse()
})
</script>

<style scoped>
.app-container {
  min-height: 100vh;
}

/* 侧边栏折叠 */
.main-content.collapsed {
  margin-left: var(--sidebar-collapsed-width);
}
</style>
