<template>
  <div class="version-compare">
    <!-- 顶部选择栏 -->
    <div class="compare-header">
      <div class="version-selectors">
        <div class="selector">
          <label>{{ $t('version.oldVersion') }}</label>
          <el-select v-model="selectedV1" placeholder="Select version" style="width: 220px">
            <el-option
              v-for="v in versions"
              :key="v.version"
              :label="`v${v.version} - ${formatTime(v.created_at || v.createdAt)}`"
              :value="v.version"
            />
          </el-select>
        </div>
        <el-button @click="swapVersions" :icon="Sort" class="swap-btn">
          {{ $t('version.swap') }}
        </el-button>
        <div class="selector">
          <label>{{ $t('version.newVersion') }}</label>
          <el-select v-model="selectedV2" placeholder="Select version" style="width: 220px">
            <el-option
              v-for="v in versions"
              :key="v.version"
              :label="`v${v.version} - ${formatTime(v.created_at || v.createdAt)}`"
              :value="v.version"
            />
          </el-select>
        </div>
      </div>
      <el-button type="primary" @click="loadCompare" :loading="loading">
        {{ $t('version.compare') }}
      </el-button>
    </div>

    <!-- 对比模式切换 -->
    <div class="compare-modes" v-if="compareResult">
      <el-radio-group v-model="compareMode">
        <el-radio-button value="sideBySide">{{ $t('version.sideBySide') }}</el-radio-button>
        <el-radio-button value="inline">{{ $t('version.inline') }}</el-radio-button>
        <el-radio-button value="slider">{{ $t('version.slider') }}</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 版本元信息 -->
    <div class="version-meta" v-if="compareResult">
      <div class="meta-item">
        <span class="meta-label">{{ $t('version.versionInfo', { version: compareResult.version1.version }) }}</span>
        <span class="meta-time">{{ formatTime(compareResult.version1.createdAt || compareResult.version1.created_at) }}</span>
        <span class="meta-note">{{ compareResult.version1.changeNote || compareResult.version1.change_note || $t('version.noVersions') }}</span>
        <span class="meta-author">{{ $t('version.createdBy') }}: {{ compareResult.version1.createdBy || compareResult.version1.created_by || '-' }}</span>
      </div>
      <div class="meta-item">
        <span class="meta-label">{{ $t('version.versionInfo', { version: compareResult.version2.version }) }}</span>
        <span class="meta-time">{{ formatTime(compareResult.version2.createdAt || compareResult.version2.created_at) }}</span>
        <span class="meta-note">{{ compareResult.version2.changeNote || compareResult.version2.change_note || $t('version.noVersions') }}</span>
        <span class="meta-author">{{ $t('version.createdBy') }}: {{ compareResult.version2.createdBy || compareResult.version2.created_by || '-' }}</span>
      </div>
    </div>

    <!-- 并排对比模式 -->
    <div class="compare-content side-by-side" v-if="compareMode === 'sideBySide' && compareResult">
      <div class="compare-panel">
        <div class="panel-header">{{ $t('version.oldVersion') }} (v{{ selectedV1 }})</div>
        <div class="panel-body" v-html="compareResult.html1"></div>
      </div>
      <div class="compare-divider"></div>
      <div class="compare-panel">
        <div class="panel-header">{{ $t('version.newVersion') }} (v{{ selectedV2 }})</div>
        <div class="panel-body" v-html="compareResult.html2"></div>
      </div>
    </div>

    <!-- 行内对比模式（高亮增删） -->
    <div class="compare-content inline-diff" v-if="compareMode === 'inline' && compareResult">
      <div class="panel-body" v-html="renderedDiff"></div>
    </div>

    <!-- 滑动对比模式 -->
    <div class="compare-content slider-diff" v-if="compareMode === 'slider' && compareResult">
      <div
        class="slider-container"
        ref="sliderContainer"
        @mousedown="onSliderDown"
        @mousemove="onSliderMove"
        @mouseup="onSliderUp"
        @mouseleave="onSliderUp"
      >
        <div class="slider-layer old-layer" v-html="compareResult.html1"></div>
        <div
          class="slider-layer new-layer"
          :style="{ clipPath: `inset(0 0 0 ${sliderPosition}%)` }"
          v-html="compareResult.html2"
        ></div>
        <div class="slider-line" :style="{ left: sliderPosition + '%' }">
          <div class="slider-handle">&lt; &gt;</div>
        </div>
      </div>
    </div>

    <!-- 变更统计 -->
    <div class="change-stats" v-if="compareResult && compareResult.diff">
      <el-tag type="success">+{{ compareResult.diff.addedCount }} {{ $t('version.added') }}</el-tag>
      <el-tag type="danger">-{{ compareResult.diff.deletedCount }} {{ $t('version.deleted') }}</el-tag>
      <el-tag type="warning">~{{ compareResult.diff.modifiedCount }} {{ $t('version.modified') }}</el-tag>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Sort } from '@element-plus/icons-vue'
import { compareVersions, getVersionPreview } from '@/api/index'

const props = defineProps({
  templateId: {
    type: [Number, String],
    required: true
  },
  versions: {
    type: Array,
    default: () => []
  },
  /** 可选：初始选中的版本号对 */
  initV1: {
    type: [Number, String],
    default: null
  },
  initV2: {
    type: [Number, String],
    default: null
  }
})

const { t } = useI18n()

const selectedV1 = ref(null)
const selectedV2 = ref(null)
const compareMode = ref('sideBySide')
const loading = ref(false)
const compareResult = ref(null)
const sliderPosition = ref(50)
const sliderContainer = ref(null)
const isDragging = ref(false)

/**
 * 格式化时间
 */
function formatTime(time) {
  if (!time) return '-'
  const d = new Date(time)
  if (isNaN(d.getTime())) return time
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 交换版本选择
 */
function swapVersions() {
  const tmp = selectedV1.value
  selectedV1.value = selectedV2.value
  selectedV2.value = tmp
}

/**
 * 加载版本对比数据
 */
async function loadCompare() {
  if (!selectedV1.value || !selectedV2.value) return
  if (selectedV1.value === selectedV2.value) return

  loading.value = true
  compareResult.value = null
  try {
    const data = await compareVersions(props.templateId, selectedV1.value, selectedV2.value)
    compareResult.value = data || null
  } catch (e) {
    console.error('加载版本对比失败', e)
    compareResult.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 行内对比渲染：将 diff 结果渲染为带高亮的 HTML
 */
const renderedDiff = computed(() => {
  if (!compareResult.value || !compareResult.value.diff) return ''
  const diff = compareResult.value.diff

  // 如果后端返回了行内 diff HTML，直接使用
  if (diff.inlineHtml) return diff.inlineHtml

  // 否则根据 changes 数组自行渲染
  if (diff.changes && Array.isArray(diff.changes)) {
    return diff.changes.map(change => {
      switch (change.type) {
        case 'added':
          return `<div class="diff-added">${change.content || change.html || ''}</div>`
        case 'deleted':
          return `<div class="diff-deleted">${change.content || change.html || ''}</div>`
        case 'modified':
          return `<div class="diff-modified">${change.content || change.html || ''}</div>`
        default:
          return `<div>${change.content || change.html || ''}</div>`
      }
    }).join('')
  }

  // 兜底：直接显示 html2
  return compareResult.value.html2 || ''
})

// ==================== 滑动对比拖拽 ====================

function onSliderDown(e) {
  isDragging.value = true
  updateSliderPosition(e)
}

function onSliderMove(e) {
  if (!isDragging.value) return
  updateSliderPosition(e)
}

function onSliderUp() {
  isDragging.value = false
}

function updateSliderPosition(e) {
  if (!sliderContainer.value) return
  const rect = sliderContainer.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  const percentage = Math.max(0, Math.min(100, (x / rect.width) * 100))
  sliderPosition.value = percentage
}

/**
 * 初始化版本选择
 */
function initSelection() {
  if (props.versions && props.versions.length >= 2) {
    const sorted = [...props.versions].sort((a, b) => a.version - b.version)
    selectedV1.value = props.initV1 || sorted[0].version
    selectedV2.value = props.initV2 || sorted[sorted.length - 1].version
  } else if (props.versions && props.versions.length === 1) {
    selectedV1.value = props.versions[0].version
    selectedV2.value = props.versions[0].version
  }
}

watch(() => props.versions, () => {
  initSelection()
}, { immediate: true })

watch(() => [props.initV1, props.initV2], ([v1, v2]) => {
  if (v1) selectedV1.value = v1
  if (v2) selectedV2.value = v2
}, { immediate: true })

/**
 * 暴露方法供父组件调用
 */
defineExpose({
  loadCompare,
  selectedV1,
  selectedV2
})
</script>

<style scoped>
.version-compare {
  width: 100%;
}

.compare-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.version-selectors {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.selector {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.selector label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.swap-btn {
  margin-bottom: 1px;
}

.compare-modes {
  margin-bottom: 16px;
}

.version-meta {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--bg, #f0f2f5);
  border-radius: 8px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  flex: 1;
  min-width: 240px;
}

.meta-label {
  font-weight: 600;
  color: var(--primary, #1a365d);
}

.meta-time {
  font-size: 12px;
  color: var(--text-secondary, #909399);
}

.meta-note {
  font-size: 12px;
  color: var(--text-regular, #606266);
  background: var(--bg-white, #fff);
  padding: 2px 8px;
  border-radius: 4px;
}

.meta-author {
  font-size: 12px;
  color: var(--text-secondary, #909399);
}

/* 对比内容区 */
.compare-content {
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 8px;
  overflow: hidden;
  min-height: 400px;
  background: var(--bg-white, #fff);
}

/* 并排对比 */
.side-by-side {
  display: grid;
  grid-template-columns: 1fr 2px 1fr;
}

.compare-panel {
  overflow: auto;
  max-height: 600px;
}

.compare-divider {
  background: var(--border-color, #e4e7ed);
}

.panel-header {
  padding: 12px 16px;
  background: var(--bg-sidebar, #1a365d);
  color: white;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
  font-size: 14px;
}

.panel-body {
  padding: 20px;
  line-height: 1.8;
  font-size: 14px;
}

.panel-body :deep(h1) {
  font-size: 24px;
  font-weight: 700;
  margin: 16px 0 8px;
}

.panel-body :deep(h2) {
  font-size: 20px;
  font-weight: 600;
  margin: 14px 0 6px;
}

.panel-body :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  margin: 12px 0 4px;
}

.panel-body :deep(p) {
  margin: 8px 0;
}

.panel-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.panel-body :deep(td),
.panel-body :deep(th) {
  border: 1px solid var(--border-color, #e4e7ed);
  padding: 8px 12px;
}

/* 行内对比高亮 */
.inline-diff .panel-body :deep(.diff-added) {
  background: #e6ffed;
  border-left: 3px solid #27ae60;
  padding: 2px 8px;
  margin: 4px 0;
  border-radius: 0 4px 4px 0;
}

.inline-diff .panel-body :deep(.diff-deleted) {
  background: #ffeef0;
  border-left: 3px solid #e74c3c;
  padding: 2px 8px;
  margin: 4px 0;
  border-radius: 0 4px 4px 0;
  text-decoration: line-through;
  opacity: 0.7;
}

.inline-diff .panel-body :deep(.diff-modified) {
  background: #fff8e1;
  border-left: 3px solid #f39c12;
  padding: 2px 8px;
  margin: 4px 0;
  border-radius: 0 4px 4px 0;
}

/* 滑动对比 */
.slider-container {
  position: relative;
  height: 600px;
  overflow: hidden;
  cursor: ew-resize;
}

.slider-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow-y: auto;
  padding: 20px;
  line-height: 1.8;
  font-size: 14px;
}

.slider-layer :deep(h1) {
  font-size: 24px;
  font-weight: 700;
  margin: 16px 0 8px;
}

.slider-layer :deep(h2) {
  font-size: 20px;
  font-weight: 600;
  margin: 14px 0 6px;
}

.slider-layer :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  margin: 12px 0 4px;
}

.slider-layer :deep(p) {
  margin: 8px 0;
}

.slider-layer :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.slider-layer :deep(td),
.slider-layer :deep(th) {
  border: 1px solid var(--border-color, #e4e7ed);
  padding: 8px 12px;
}

.slider-line {
  position: absolute;
  top: 0;
  width: 4px;
  height: 100%;
  background: white;
  transform: translateX(-50%);
  z-index: 10;
  cursor: ew-resize;
  box-shadow: 0 0 8px rgba(0, 0, 0, 0.3);
}

.slider-handle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: white;
  border-radius: 50%;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  cursor: ew-resize;
  font-size: 14px;
  color: #666;
  user-select: none;
}

/* 变更统计 */
.change-stats {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}

/* 暗色模式适配 */
html.dark .version-meta {
  background: var(--bg, #0f172a);
}

html.dark .compare-content {
  background: var(--bg-card, #1e293b);
}

html.dark .slider-handle {
  background: var(--bg-card, #1e293b);
  color: var(--text-regular, #cbd5e1);
}

html.dark .slider-line {
  background: var(--accent, #c9a96e);
}

html.dark .inline-diff .panel-body :deep(.diff-added) {
  background: rgba(39, 174, 96, 0.15);
}

html.dark .inline-diff .panel-body :deep(.diff-deleted) {
  background: rgba(231, 76, 60, 0.15);
}

html.dark .inline-diff .panel-body :deep(.diff-modified) {
  background: rgba(243, 156, 18, 0.15);
}
</style>
