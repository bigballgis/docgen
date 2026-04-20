<template>
  <div class="euro-office-compare">
    <!-- 对比模式横幅 -->
    <div class="compare-banner" :class="mode">
      <el-icon v-if="mode === 'euro-office'"><SuccessFilled /></el-icon>
      <el-icon v-else><WarningFilled /></el-icon>
      <span v-if="mode === 'euro-office'">{{ $t('fragment.euroOffice') }}</span>
      <span v-else>{{ $t('fragment.euroOfficeUnavailable') }}</span>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="compare-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>{{ $t('common.loading') }}</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="compare-error">
      <el-icon :size="32" color="#f56c6c"><CircleCloseFilled /></el-icon>
      <p>{{ error }}</p>
      <el-button type="primary" size="small" @click="$emit('retry')">{{ $t('editor.recheck') }}</el-button>
    </div>

    <!-- Euro-Office iframe 对比 -->
    <div v-else-if="mode === 'euro-office' && compareDocxUrl" class="compare-iframe-container">
      <iframe
        :src="iframeSrc"
        class="compare-iframe"
        allowfullscreen
      ></iframe>
    </div>

    <!-- HTML 文本对比降级 -->
    <div v-else-if="mode === 'html-diff' && fallbackHtml" class="compare-html-diff">
      <div class="diff-stats" v-if="stats">
        <el-tag type="success">+{{ stats.added }} {{ $t('version.added') }}</el-tag>
        <el-tag type="danger">-{{ stats.deleted }} {{ $t('version.deleted') }}</el-tag>
      </div>
      <div class="diff-content" v-html="fallbackHtml"></div>
    </div>

    <!-- 无数据 -->
    <div v-else class="compare-empty">
      <el-empty :description="$t('version.noVersions')" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { SuccessFilled, WarningFilled, Loading, CircleCloseFilled } from '@element-plus/icons-vue'

const props = defineProps({
  mode: { type: String, default: 'html-diff' }, // 'euro-office' | 'html-diff'
  compareDocxUrl: { type: String, default: '' },
  fallbackHtml: { type: String, default: '' },
  stats: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

defineEmits(['retry'])

const iframeSrc = computed(() => {
  if (!props.compareDocxUrl) return ''
  // Euro-Office embedded mode URL
  const docServerUrl = window.__EURO_OFFICE_URL__ || ''
  if (!docServerUrl) return props.compareDocxUrl
  return `${docServerUrl}/embedded?src=${encodeURIComponent(props.compareDocxUrl)}`
})
</script>

<style scoped>
.euro-office-compare {
  width: 100%;
}

.compare-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 8px 8px 0 0;
  font-size: 13px;
  font-weight: 500;
}

.compare-banner.euro-office {
  background: #e6ffed;
  color: #27ae60;
  border: 1px solid #b7eb8f;
}

.compare-banner.html-diff {
  background: #fff8e1;
  color: #f39c12;
  border: 1px solid #ffe58f;
}

.compare-loading, .compare-error, .compare-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 12px;
  color: var(--text-secondary, #909399);
}

.compare-iframe-container {
  width: 100%;
  height: 600px;
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 0 0 8px 8px;
  overflow: hidden;
}

.compare-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.compare-html-diff {
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 0 0 8px 8px;
  overflow: hidden;
}

.diff-stats {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  background: var(--bg, #f5f7fa);
  border-bottom: 1px solid var(--border-color, #e4e7ed);
}

.diff-content {
  max-height: 550px;
  overflow-y: auto;
  padding: 16px;
  line-height: 1.8;
}

.diff-content :deep(.diff-added) {
  background: #e6ffed;
  border-left: 3px solid #27ae60;
  padding: 4px 8px;
  margin: 4px 0;
  border-radius: 0 4px 4px 0;
}

.diff-content :deep(.diff-deleted) {
  background: #ffeef0;
  border-left: 3px solid #e74c3c;
  padding: 4px 8px;
  margin: 4px 0;
  border-radius: 0 4px 4px 0;
  text-decoration: line-through;
  opacity: 0.7;
}

html.dark .compare-banner.euro-office {
  background: rgba(39, 174, 96, 0.15);
  border-color: rgba(39, 174, 96, 0.3);
}

html.dark .compare-banner.html-diff {
  background: rgba(243, 156, 18, 0.15);
  border-color: rgba(243, 156, 18, 0.3);
}

html.dark .compare-html-diff {
  border-color: var(--border-color, #334155);
}

html.dark .diff-content :deep(.diff-added) {
  background: rgba(39, 174, 96, 0.15);
}

html.dark .diff-content :deep(.diff-deleted) {
  background: rgba(231, 76, 60, 0.15);
}
</style>
