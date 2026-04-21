<template>
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
</template>

<script setup>
import { ref, onMounted, defineProps } from 'vue'
import { useI18n } from 'vue-i18n'
import { Sunny, Moon } from '@element-plus/icons-vue'

const props = defineProps({
  isCollapsed: {
    type: Boolean,
    default: false
  }
})

const { t } = useI18n()
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

// 页面初始化
onMounted(() => {
  initDarkMode()
})
</script>

<style scoped>
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

:deep(.sidebar.collapsed) .dark-mode-btn {
  justify-content: center;
  padding: 6px;
}

.dark-mode-label {
  white-space: nowrap;
}
</style>