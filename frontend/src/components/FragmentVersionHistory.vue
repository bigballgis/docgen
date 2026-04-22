<template>
  <el-drawer v-model="visible" :title="$t('fragment.history')" size="640px" :close-on-click-modal="false" @open="loadVersions">
    <div class="version-history" v-loading="loading">
      <el-empty v-if="!loading && versions.length === 0" :description="$t('fragment.noData')" />
      <el-timeline v-else>
        <el-timeline-item v-for="(ver, index) in versions" :key="ver.version" :timestamp="formatTime(ver.created_at || ver.createdAt)" placement="top" :type="index === 0 ? 'primary' : 'info'" :hollow="index !== 0" size="large">
          <div class="version-item">
            <div class="version-item-header">
              <span class="version-number">v{{ ver.version }}</span>
              <el-tag v-if="index === 0" type="success" size="small">{{ $t('fragment.current') }}</el-tag>
            </div>
            <div class="version-item-meta">
              <span class="meta-author">
                <el-icon :size="12"><User /></el-icon>
                {{ ver.created_by_name || ver.createdByName || '-' }}
              </span>
            </div>
            <div class="version-item-note" v-if="ver.change_note || ver.changeNote">
              {{ ver.change_note || ver.changeNote }}
            </div>
            <div class="version-item-actions">
              <el-button type="primary" link size="small" @click="handlePreview(ver)">
                <el-icon :size="14"><View /></el-icon> {{ $t('fragment.preview') }}
              </el-button>
              <el-button type="warning" link size="small" @click="handleOpenCompare(ver)">
                <el-icon :size="14"><Sort /></el-icon> {{ $t('fragment.compare') }}
              </el-button>
              <el-button v-if="authStore.isAdmin && index !== 0" type="danger" link size="small" @click="handleRollback(ver)">
                <el-icon :size="14"><RefreshLeft /></el-icon> {{ $t('fragment.rollback') }}
              </el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- 预览对话框 -->
    <el-dialog v-model="showPreviewDialog" :title="previewTitle" width="720px" append-to-body top="5vh">
      <div class="preview-content" v-loading="previewLoading" v-html="sanitizeHtml(previewHtml)"></div>
    </el-dialog>

    <!-- 版本对比对话框 -->
    <el-dialog v-model="showCompareDialog" :title="$t('fragment.compare')" width="90%" top="3vh" append-to-body destroy-on-close>
      <EuroOfficeCompare
        v-if="showCompareDialog"
        :mode="compareMode"
        :compare-docx-url="compareDocxUrl"
        :fallback-html="fallbackHtml"
        :stats="compareStats"
        :loading="compareLoading"
        :error="compareError"
        @retry="loadCompare"
      />
      <div class="compare-version-selectors" v-if="showCompareDialog && !compareLoading">
        <el-select v-model="compareV1" size="small" style="width: 180px">
          <el-option v-for="v in versions" :key="v.version" :label="`v${v.version}`" :value="v.version" />
        </el-select>
        <span class="compare-arrow">→</span>
        <el-select v-model="compareV2" size="small" style="width: 180px">
          <el-option v-for="v in versions" :key="v.version" :label="`v${v.version}`" :value="v.version" />
        </el-select>
        <el-button type="primary" size="small" @click="loadCompare">{{ $t('fragment.compare') }}</el-button>
      </div>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, View, Sort, RefreshLeft } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getFragmentVersionList, getFragmentVersion, rollbackFragmentVersion, compareFragmentVersions } from '@/api/index'
import EuroOfficeCompare from './EuroOfficeCompare.vue'
import { sanitizeHtml } from '@/utils/sanitize'
import { formatTime } from '@/utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  fragmentId: { type: [Number, String], required: true }
})

const emit = defineEmits(['update:modelValue', 'rollback-success'])
const { t } = useI18n()
const authStore = useAuthStore()

const visible = ref(false)
const loading = ref(false)
const versions = ref([])

// 预览
const showPreviewDialog = ref(false)
const previewTitle = ref('')
const previewHtml = ref('')
const previewLoading = ref(false)

// 对比
const showCompareDialog = ref(false)
const compareV1 = ref(null)
const compareV2 = ref(null)
const compareMode = ref('html-diff')
const compareDocxUrl = ref('')
const fallbackHtml = ref('')
const compareStats = ref(null)
const compareLoading = ref(false)
const compareError = ref('')

watch(() => props.modelValue, (val) => { visible.value = val })
watch(visible, (val) => { emit('update:modelValue', val) })

async function loadVersions() {
  loading.value = true
  versions.value = []
  try {
    const data = await getFragmentVersionList(props.fragmentId)
    const list = Array.isArray(data) ? data : (data?.content || data?.list || [])
    versions.value = list.sort((a, b) => (b.version || 0) - (a.version || 0))
  } catch (e) {
    versions.value = []
  } finally {
    loading.value = false
  }
}

async function handlePreview(ver) {
  showPreviewDialog.value = true
  previewTitle.value = `v${ver.version}`
  previewHtml.value = ''
  previewLoading.value = true
  try {
    const data = await getFragmentVersion(props.fragmentId, ver.version)
    previewHtml.value = data?.content_html || data?.contentHtml || ''
  } catch (e) {
    previewHtml.value = ''
  } finally {
    previewLoading.value = false
  }
}

function handleOpenCompare(ver) {
  compareV1.value = ver.version
  if (versions.value.length > 0 && versions.value[0].version !== ver.version) {
    compareV2.value = versions.value[0].version
  } else if (versions.value.length > 1) {
    compareV2.value = versions.value[1].version
  } else {
    compareV2.value = ver.version
  }
  showCompareDialog.value = true
  nextTick(() => loadCompare())
}

async function loadCompare() {
  if (!compareV1.value || !compareV2.value) return
  compareLoading.value = true
  compareError.value = ''
  compareDocxUrl.value = ''
  fallbackHtml.value = ''
  compareStats.value = null
  compareMode.value = 'html-diff'

  try {
    const data = await compareFragmentVersions(props.fragmentId, compareV1.value, compareV2.value)
    if (data) {
      compareMode.value = data.mode || 'html-diff'
      compareDocxUrl.value = data.compareDocxUrl || ''
      fallbackHtml.value = data.fallbackHtml || ''
      compareStats.value = data.stats || null
    }
  } catch (e) {
    compareError.value = e.message || t('version.compareFailed')
  } finally {
    compareLoading.value = false
  }
}

async function handleRollback(ver) {
  try {
    await ElMessageBox.confirm(
      t('fragment.rollbackConfirm', { version: ver.version }),
      t('fragment.rollback'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch { return }

  try {
    await rollbackFragmentVersion(props.fragmentId, ver.version)
    ElMessage.success(t('fragment.rollbackSuccess'))
    loadVersions()
    emit('rollback-success')
  } catch (e) {
    ElMessage.error(t('fragment.rollbackFailed'))
  }
}
</script>

<style scoped>
.version-history { padding: 0 8px; }
.version-item { padding: 4px 0; }
.version-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.version-number { font-size: 15px; font-weight: 600; color: var(--text-primary, #303133); }
.version-item-meta { display: flex; align-items: center; gap: 16px; margin-bottom: 6px; }
.meta-author { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--text-secondary, #909399); }
.version-item-note { font-size: 13px; color: var(--text-regular, #606266); background: var(--bg, #f0f2f5); padding: 6px 12px; border-radius: 4px; margin-bottom: 8px; line-height: 1.5; }
.version-item-actions { display: flex; gap: 4px; }
.preview-content { min-height: 300px; max-height: 70vh; overflow-y: auto; padding: 20px; line-height: 1.8; font-size: 14px; border: 1px solid var(--border-color, #e4e7ed); border-radius: 8px; }
.preview-content :deep(.template-placeholder) { background: #e8f4fd; border: 1px dashed #409eff; padding: 2px 8px; border-radius: 3px; color: #409eff; font-weight: 500; }
.compare-version-selectors { display: flex; align-items: center; gap: 12px; margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border-color, #e4e7ed); }
.compare-arrow { font-size: 18px; color: var(--text-secondary, #909399); font-weight: bold; }
html.dark .version-item-note { background: var(--bg, #0f172a); }
html.dark .preview-content { background: var(--bg-card, #1e293b); border-color: var(--border-color, #334155); }
</style>
