<template>
  <div class="composition-editor">
    <div class="comp-left">
      <div class="panel-header">{{ $t('composition.fragmentPicker') }}</div>
      <el-input v-model="searchText" :placeholder="$t('composition.searchFragment')" clearable size="small" class="search-input" />
      <div class="fragment-list" v-loading="loadingFragments">
        <div
          v-for="frag in filteredFragments"
          :key="frag.id"
          class="fragment-item"
          draggable="true"
          @dragstart="onDragStart($event, frag)"
        >
          <div class="frag-name">{{ frag.name }}</div>
          <el-tag v-if="frag.category" size="small" type="info">{{ frag.category }}</el-tag>
        </div>
        <el-empty v-if="!loadingFragments && filteredFragments.length === 0" :description="$t('fragment.noData')" :image-size="60" />
      </div>
    </div>

    <div class="comp-right">
      <div class="panel-header">
        {{ $t('composition.composition') }}
        <div class="comp-actions">
          <el-button size="small" @click="handlePreview" :disabled="compositionItems.length === 0">
            <el-icon><View /></el-icon> {{ $t('composition.preview') }}
          </el-button>
          <el-button size="small" type="primary" @click="handleSave" :loading="saving">
            <el-icon><Check /></el-icon> {{ $t('composition.save') }}
          </el-button>
        </div>
      </div>

      <div
        class="composition-canvas"
        @dragover.prevent="onDragOver"
        @drop="onDrop"
        v-loading="loadingComposition"
      >
        <div v-if="compositionItems.length === 0" class="empty-canvas">
          <el-icon :size="48" color="#c0c4cc"><Plus /></el-icon>
          <p>{{ $t('composition.dropHint') }}</p>
          <p class="hint">{{ $t('composition.dragHint') }}</p>
        </div>

        <div
          v-for="(item, index) in compositionItems"
          :key="item.id || index"
          class="comp-item"
          :class="{ 'drag-over': dragOverIndex === index, disabled: !item.enabled }"
          draggable="true"
          @dragstart="onItemDragStart($event, index)"
          @dragover.prevent="onItemDragOver($event, index)"
          @drop.stop="onItemDrop($event, index)"
          @dragend="onDragEnd"
        >
          <div class="comp-item-handle" v-if="!readonly">
            <el-icon><Rank /></el-icon>
          </div>
          <div class="comp-item-body">
            <div class="comp-item-header">
              <span class="comp-item-name">{{ item.fragment_name || item.name || 'Unknown' }}</span>
              <el-tag v-if="item.fragment_category" size="small" type="info">{{ item.fragment_category }}</el-tag>
            </div>
            <el-input
              v-if="item.section_title !== undefined"
              v-model="item.section_title"
              :placeholder="$t('composition.sectionTitlePlaceholder')"
              size="small"
              class="section-title-input"
              @change="markDirty"
            />
          </div>
          <div class="comp-item-actions" v-if="!readonly">
            <el-switch v-model="item.enabled" size="small" @change="markDirty" />
            <el-button type="primary" link size="small" @click="handlePreviewItem(item)">
              <el-icon><View /></el-icon>
            </el-button>
            <el-button type="danger" link size="small" @click="handleRemoveItem(index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 预览对话框 -->
    <el-dialog v-model="showPreview" :title="$t('composition.preview')" width="800px" append-to-body top="5vh">
      <div class="preview-content" v-loading="previewLoading" v-html="previewHtml"></div>
    </el-dialog>

    <!-- 片段预览对话框 -->
    <el-dialog v-model="showItemPreview" :title="itemPreviewTitle" width="700px" append-to-body top="5vh">
      <div class="preview-content" v-html="itemPreviewHtml"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, View, Check, Delete, Rank } from '@element-plus/icons-vue'
import { getFragments, getComposition, saveComposition, previewComposition, previewFragment } from '@/api/index'

const props = defineProps({
  templateId: { type: [Number, String], required: true },
  readonly: { type: Boolean, default: false }
})

const emit = defineEmits(['saved'])

const { t } = useI18n()

const searchText = ref('')
const loadingFragments = ref(false)
const loadingComposition = ref(false)
const saving = ref(false)
const allFragments = ref([])
const compositionItems = ref([])
const dragOverIndex = ref(null)
const dragSourceIndex = ref(null)
const isDirty = ref(false)

// 预览
const showPreview = ref(false)
const previewLoading = ref(false)
const previewHtml = ref('')
const showItemPreview = ref(false)
const itemPreviewTitle = ref('')
const itemPreviewHtml = ref('')

const filteredFragments = computed(() => {
  if (!searchText.value) return allFragments.value
  const kw = searchText.value.toLowerCase()
  return allFragments.value.filter(f =>
    f.name.toLowerCase().includes(kw) || (f.description || '').toLowerCase().includes(kw)
  )
})

async function loadFragments() {
  loadingFragments.value = true
  try {
    const data = await getFragments({ size: 100 })
    allFragments.value = data?.content || data?.list || []
  } catch (e) {
    console.error('加载片段列表失败', e)
  } finally {
    loadingFragments.value = false
  }
}

async function loadComposition() {
  loadingComposition.value = true
  try {
    const data = await getComposition(props.templateId)
    compositionItems.value = Array.isArray(data) ? data.map(item => ({
      ...item,
      enabled: item.enabled !== false && item.enabled !== 0
    })) : []
    isDirty.value = false
  } catch (e) {
    compositionItems.value = []
  } finally {
    loadingComposition.value = false
  }
}

// ==================== 拖拽逻辑 ====================
let dragFragment = null

function onDragStart(e, frag) {
  dragFragment = frag
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('text/plain', frag.id)
}

function onDragOver(e) {
  e.dataTransfer.dropEffect = dragFragment ? 'copy' : 'move'
}

function onDrop(e) {
  e.preventDefault()
  if (dragFragment) {
    // 从片段库拖入
    const exists = compositionItems.value.some(item => item.fragment_id === dragFragment.id)
    if (exists) {
      ElMessage.warning(t('composition.fragmentExists') || '片段已存在')
    } else {
      compositionItems.value.push({
        fragment_id: dragFragment.id,
        fragment_name: dragFragment.name,
        fragment_category: dragFragment.category,
        sort_order: compositionItems.value.length,
        section_title: '',
        enabled: true
      })
      markDirty()
    }
    dragFragment = null
  }
}

function onItemDragStart(e, index) {
  dragSourceIndex.value = index
  dragFragment = null
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', 'reorder')
}

function onItemDragOver(e, index) {
  dragOverIndex.value = index
}

function onItemDrop(e, index) {
  e.preventDefault()
  dragOverIndex.value = null
  if (dragSourceIndex.value !== null && dragSourceIndex.value !== index) {
    const items = [...compositionItems.value]
    const [moved] = items.splice(dragSourceIndex.value, 1)
    items.splice(index, 0, moved)
    compositionItems.value = items.map((item, i) => ({ ...item, sort_order: i }))
    markDirty()
  }
  dragSourceIndex.value = null
}

function onDragEnd() {
  dragOverIndex.value = null
  dragSourceIndex.value = null
  dragFragment = null
}

function handleRemoveItem(index) {
  compositionItems.value.splice(index, 1)
  compositionItems.value = compositionItems.value.map((item, i) => ({ ...item, sort_order: i }))
  markDirty()
}

function markDirty() {
  isDirty.value = true
}

async function handleSave() {
  saving.value = true
  try {
    const fragments = compositionItems.value.map((item, i) => ({
      fragmentId: item.fragment_id,
      sortOrder: i,
      sectionTitle: item.section_title || '',
      enabled: item.enabled !== false
    }))
    await saveComposition(props.templateId, { fragments })
    ElMessage.success(t('composition.saveSuccess'))
    isDirty.value = false
    emit('saved')
  } catch (e) {
    console.error('保存编排失败', e)
  } finally {
    saving.value = false
  }
}

async function handlePreview() {
  showPreview.value = true
  previewLoading.value = true
  previewHtml.value = ''
  try {
    const data = await previewComposition(props.templateId)
    previewHtml.value = data?.html || ''
  } catch (e) {
    previewHtml.value = '<p style="color:red;">预览加载失败</p>'
  } finally {
    previewLoading.value = false
  }
}

async function handlePreviewItem(item) {
  if (!item.fragment_id) return
  itemPreviewTitle.value = item.fragment_name || ''
  itemPreviewHtml.value = ''
  showItemPreview.value = true
  try {
    const data = await previewFragment(item.fragment_id)
    itemPreviewHtml.value = data?.html || ''
  } catch (e) {
    itemPreviewHtml.value = '<p style="color:red;">预览加载失败</p>'
  }
}

onMounted(() => {
  loadFragments()
  loadComposition()
})

watch(() => props.templateId, () => {
  loadComposition()
})

defineExpose({ isDirty })
</script>

<style scoped>
.composition-editor {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  height: 600px;
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 8px;
  overflow: hidden;
}

.comp-left, .comp-right {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--bg-sidebar, #1a365d);
  color: white;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}

.comp-actions {
  display: flex;
  gap: 8px;
}

.search-input {
  margin: 8px 12px;
  flex-shrink: 0;
}

.fragment-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 12px;
}

.fragment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  margin-bottom: 6px;
  background: var(--bg-white, #fff);
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 6px;
  cursor: grab;
  transition: all 0.2s;
  font-size: 13px;
}

.fragment-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.fragment-item:active {
  cursor: grabbing;
}

.frag-name {
  font-weight: 500;
  color: var(--text-primary, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160px;
}

.composition-canvas {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: var(--bg, #f5f7fa);
}

.empty-canvas {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-placeholder, #c0c4cc);
}

.empty-canvas p {
  margin-top: 12px;
  font-size: 14px;
}

.empty-canvas .hint {
  font-size: 12px;
  color: var(--text-secondary, #909399);
  margin-top: 4px;
}

.comp-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  margin-bottom: 8px;
  background: var(--bg-white, #fff);
  border: 2px solid var(--border-color, #e4e7ed);
  border-radius: 8px;
  transition: all 0.2s;
}

.comp-item.drag-over {
  border-color: #409eff;
  background: #ecf5ff;
}

.comp-item.disabled {
  opacity: 0.5;
}

.comp-item-handle {
  cursor: grab;
  color: var(--text-secondary, #909399);
  padding-top: 4px;
}

.comp-item-body {
  flex: 1;
  min-width: 0;
}

.comp-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.comp-item-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary, #303133);
}

.section-title-input {
  margin-top: 4px;
}

.comp-item-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.preview-content {
  min-height: 300px;
  max-height: 65vh;
  overflow-y: auto;
  padding: 20px;
  line-height: 1.8;
  font-size: 14px;
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 8px;
}

.preview-content :deep(h1) { font-size: 24px; font-weight: 700; margin: 16px 0 8px; }
.preview-content :deep(h2) { font-size: 20px; font-weight: 600; margin: 14px 0 6px; }
.preview-content :deep(p) { margin: 8px 0; }
.preview-content :deep(.template-placeholder) {
  background: #e8f4fd;
  border: 1px dashed #409eff;
  padding: 2px 8px;
  border-radius: 3px;
  color: #409eff;
  font-weight: 500;
}

html.dark .composition-editor {
  border-color: var(--border-color, #334155);
}
html.dark .fragment-item {
  background: var(--bg-card, #1e293b);
  border-color: var(--border-color, #334155);
}
html.dark .comp-item {
  background: var(--bg-card, #1e293b);
  border-color: var(--border-color, #334155);
}
html.dark .composition-canvas {
  background: var(--bg, #0f172a);
}
</style>
