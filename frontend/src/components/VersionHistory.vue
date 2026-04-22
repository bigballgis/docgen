<template>
  <el-drawer
    v-model="visible"
    :title="$t('version.history')"
    size="640px"
    :close-on-click-modal="false"
    @open="loadVersions"
  >
    <div class="version-history" v-loading="loading">
      <!-- 无版本数据 -->
      <el-empty v-if="!loading && versions.length === 0" :description="$t('version.noVersions')" />

      <!-- 版本时间线 -->
      <el-timeline v-else>
        <el-timeline-item
          v-for="(ver, index) in versions"
          :key="ver.version"
          :timestamp="formatTime(ver.created_at || ver.createdAt)"
          placement="top"
          :type="index === 0 ? 'primary' : 'info'"
          :hollow="index !== 0"
          size="large"
        >
          <div class="version-item">
            <div class="version-item-header">
              <span class="version-number">
                {{ $t('version.versionInfo', { version: ver.version }) }}
              </span>
              <el-tag v-if="index === 0" type="success" size="small">
                {{ $t('version.current') }}
              </el-tag>
            </div>
            <div class="version-item-meta">
              <span class="meta-author">
                <el-icon :size="12"><User /></el-icon>
                {{ ver.createdBy || ver.created_by || '-' }}
              </span>
            </div>
            <div class="version-item-note" v-if="ver.changeNote || ver.change_note">
              {{ ver.changeNote || ver.change_note }}
            </div>
            <div class="version-item-actions">
              <el-button type="primary" link size="small" @click="handlePreview(ver)">
                <el-icon :size="14"><View /></el-icon>
                {{ $t('version.preview') }}
              </el-button>
              <el-button type="warning" link size="small" @click="handleOpenCompare(ver)">
                <el-icon :size="14"><Sort /></el-icon>
                {{ $t('version.compare') }}
              </el-button>
              <el-button
                v-if="index !== 0"
                type="danger"
                link
                size="small"
                @click="handleRollback(ver)"
              >
                <el-icon :size="14"><RefreshLeft /></el-icon>
                {{ $t('version.rollback') }}
              </el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- 版本预览对话框 -->
    <el-dialog
      v-model="showPreviewDialog"
      :title="previewTitle"
      width="720px"
      append-to-body
      top="5vh"
    >
      <div class="preview-content" v-loading="previewLoading" v-html="sanitizeHtml(previewHtml)"></div>
    </el-dialog>

    <!-- 版本对比对话框 -->
    <el-dialog
      v-model="showCompareDialog"
      :title="$t('version.compare')"
      width="90%"
      top="3vh"
      append-to-body
      destroy-on-close
    >
      <VersionCompare
        v-if="showCompareDialog"
        ref="versionCompareRef"
        :template-id="templateId"
        :versions="versions"
        :init-v1="compareV1"
        :init-v2="compareV2"
      />
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, View, Sort, RefreshLeft } from '@element-plus/icons-vue'
import { getVersionList, getVersionPreview, rollbackVersion } from '@/api/index'
import VersionCompare from './VersionCompare.vue'
import { sanitizeHtml } from '@/utils/sanitize'
import { formatTime } from '@/utils/format'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  templateId: {
    type: [Number, String],
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'rollback-success'])

const { t } = useI18n()

const visible = ref(false)
const loading = ref(false)
const versions = ref([])

// 预览相关
const showPreviewDialog = ref(false)
const previewTitle = ref('')
const previewHtml = ref('')
const previewLoading = ref(false)

// 对比相关
const showCompareDialog = ref(false)
const compareV1 = ref(null)
const compareV2 = ref(null)
const versionCompareRef = ref(null)

// 双向绑定 v-model
const syncVisible = (val) => {
  visible.value = val
  emit('update:modelValue', val)
}

// 监听 props.modelValue
import { watch } from 'vue'
watch(() => props.modelValue, (val) => {
  visible.value = val
})
watch(visible, (val) => {
  emit('update:modelValue', val)
})

/**
 * 加载版本列表
 */
async function loadVersions() {
  loading.value = true
  versions.value = []
  try {
    const data = await getVersionList(props.templateId)
    const list = Array.isArray(data) ? data : (data?.content || data?.list || data?.records || [])
    // 按版本号降序排列（最新版本在前）
    versions.value = list.sort((a, b) => (b.version || 0) - (a.version || 0))
  } catch (e) {
    ElMessage.error(t('common.loadFailed'))
    versions.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 预览版本
 */
async function handlePreview(ver) {
  showPreviewDialog.value = true
  previewTitle.value = t('version.versionInfo', { version: ver.version })
  previewHtml.value = ''
  previewLoading.value = true
  try {
    const data = await getVersionPreview(props.templateId, ver.version)
    previewHtml.value = data?.html || data || ''
  } catch (e) {
    ElMessage.error(t('common.loadFailed'))
    previewHtml.value = ''
  } finally {
    previewLoading.value = false
  }
}

/**
 * 打开对比对话框
 */
function handleOpenCompare(ver) {
  compareV1.value = ver.version
  // 默认与最新版本对比
  if (versions.value.length > 0 && versions.value[0].version !== ver.version) {
    compareV2.value = versions.value[0].version
  } else if (versions.value.length > 1) {
    compareV2.value = versions.value[1].version
  } else {
    compareV2.value = ver.version
  }
  showCompareDialog.value = true

  // 打开后自动加载对比
  nextTick(() => {
    if (versionCompareRef.value) {
      versionCompareRef.value.loadCompare()
    }
  })
}

/**
 * 回滚版本
 */
async function handleRollback(ver) {
  try {
    await ElMessageBox.confirm(
      t('version.rollbackConfirm', { version: ver.version }),
      t('version.rollback'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )
  } catch {
    return
  }

  try {
    await rollbackVersion(props.templateId, ver.version)
    ElMessage.success(t('version.rollbackSuccess'))
    // 刷新版本列表
    loadVersions()
    emit('rollback-success')
  } catch (e) {
    ElMessage.error(t('version.rollbackFailed'))
  }
}
</script>

<style scoped>
.version-history {
  padding: 0 8px;
}

.version-item {
  padding: 4px 0;
}

.version-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.version-number {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #303133);
}

.version-item-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 6px;
}

.meta-author {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary, #909399);
}

.version-item-note {
  font-size: 13px;
  color: var(--text-regular, #606266);
  background: var(--bg, #f0f2f5);
  padding: 6px 12px;
  border-radius: 4px;
  margin-bottom: 8px;
  line-height: 1.5;
}

.version-item-actions {
  display: flex;
  gap: 4px;
}

.preview-content {
  min-height: 300px;
  max-height: 70vh;
  overflow-y: auto;
  padding: 20px;
  line-height: 1.8;
  font-size: 14px;
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 8px;
}

.preview-content :deep(h1) {
  font-size: 24px;
  font-weight: 700;
  margin: 16px 0 8px;
}

.preview-content :deep(h2) {
  font-size: 20px;
  font-weight: 600;
  margin: 14px 0 6px;
}

.preview-content :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  margin: 12px 0 4px;
}

.preview-content :deep(p) {
  margin: 8px 0;
}

.preview-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.preview-content :deep(td),
.preview-content :deep(th) {
  border: 1px solid var(--border-color, #e4e7ed);
  padding: 8px 12px;
}

/* 暗色模式 */
html.dark .version-item-note {
  background: var(--bg, #0f172a);
}

html.dark .preview-content {
  background: var(--bg-card, #1e293b);
  border-color: var(--border-color, #334155);
}
</style>
