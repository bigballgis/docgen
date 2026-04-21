<template>
  <div class="lang-switch">
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
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Setting, ArrowDown } from '@element-plus/icons-vue'

const { locale } = useI18n()

// 当前语言标签
const currentLocaleLabel = computed(() => {
  return locale.value === 'en-US' ? 'English' : '中文'
})

const handleLocaleChange = (newLocale) => {
  locale.value = newLocale
  localStorage.setItem('locale', newLocale)
}
</script>

<style scoped>
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
</style>