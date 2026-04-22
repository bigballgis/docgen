<template>
  <div class="document-editor">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <el-button text @click="handleBack">
          <el-icon><ArrowLeft /></el-icon>
          {{ $t('editor.back') }}
        </el-button>
        <el-divider direction="vertical" />
        <span class="doc-name">{{ docName || $t('editor.unnamed') }}</span>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="handleSave" :loading="saving">
          <el-icon><Check /></el-icon>
          {{ $t('editor.save') }}
        </el-button>
        <el-button @click="handleDownload">
          <el-icon><Download /></el-icon>
          {{ $t('editor.download') }}
        </el-button>
      </div>
    </div>

    <!-- 编辑器区域 -->
    <div class="editor-body">
      <!-- Euro-Office 可用：嵌入编辑器 -->
      <div v-if="editorAvailable" id="editor-container" class="editor-container"></div>

      <!-- Euro-Office 不可用：显示提示信息 -->
      <div v-else class="editor-unavailable">
        <div class="unavailable-content">
          <div class="unavailable-icon">
            <el-icon :size="64"><WarningFilled /></el-icon>
          </div>
          <h2 class="unavailable-title">{{ $t('editor.notConfigured') }}</h2>
          <p class="unavailable-desc">
            {{ $t('editor.deployHint') }}
          </p>
          <div class="unavailable-steps">
            <h3>{{ $t('editor.deploySteps') }}</h3>
            <el-steps direction="vertical" :active="3" class="deploy-steps">
              <el-step :title="$t('editor.deploySteps')" :description="$t('editor.dockerPull')" />
              <el-step :title="$t('editor.deploySteps')" :description="$t('editor.dockerRun')" />
              <el-step :title="$t('editor.deploySteps')" :description="$t('editor.configureHint')" />
            </el-steps>
          </div>
          <div class="unavailable-actions">
            <el-button type="primary" @click="checkEditorAvailable">
              <el-icon><Refresh /></el-icon>
              {{ $t('editor.recheck') }}
            </el-button>
            <el-button @click="handleBack">{{ $t('editor.backToPrev') }}</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, Download, WarningFilled, Refresh } from '@element-plus/icons-vue'
import { getEditorConfig } from '@/api/index'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const fileKey = route.params.fileKey
const docName = ref('')
const saving = ref(false)
const editorAvailable = ref(false)
let docEditorInstance = null

/**
 * 检测 Euro-Office 是否可用
 */
async function checkEditorAvailable() {
  try {
    const response = await fetch(`${import.meta.env.VITE_EURO_OFFICE_URL || ''}/web-apps/appsapi/documents/api.js`, {
      method: 'HEAD',
      mode: 'no-cors'
    })
    editorAvailable.value = true
    initEditor()
  } catch (e) {
    editorAvailable.value = false
  }
}

/**
 * 初始化 Euro-Office 编辑器
 */
async function initEditor() {
  try {
    // 获取编辑器配置
    let editorConfig = {}
    try {
      editorConfig = await getEditorConfig(fileKey) || {}
    } catch (e) {
      // API 不可用时使用默认配置
      console.warn('获取编辑器配置失败，使用默认配置', e)
    }

    docName.value = editorConfig?.document?.title || `文档_${fileKey}`

    // 动态加载 Euro-Office API 脚本
    const script = document.createElement('script')
    script.src = `${import.meta.env.VITE_EURO_OFFICE_URL || ''}/web-apps/appsapi/documents/api.js`
    script.onload = () => {
      createEditor(editorConfig)
    }
    script.onerror = () => {
      editorAvailable.value = false
      ElMessage.error(t('editor.loadScriptFailed'))
    }
    document.head.appendChild(script)
  } catch (e) {
    editorAvailable.value = false
  }
}

/**
 * 创建编辑器实例
 */
function createEditor(config) {
  if (typeof DocsAPI === 'undefined') {
    editorAvailable.value = false
    return
  }

  const defaultConfig = {
    document: {
      fileType: 'docx',
      key: fileKey,
      title: docName.value || `文档_${fileKey}`,
      url: '', // 文档下载地址，由后端提供
      permissions: {
        download: true,
        edit: true,
        print: true,
        review: false
      }
    },
    documentType: 'word',
    editorConfig: {
      mode: 'edit',
      lang: 'zh-CN',
      customization: {
        autosave: true,
        chat: false,
        comments: true,
        compactHeader: true,
        compactToolbar: true,
        feedback: false,
        forcesave: false,
        help: false,
        hideRightMenu: false,
        logo: {
          image: '',
          imageEmbedded: '',
          url: ''
        },
        toolbarNoTabs: false,
        uiTheme: 'theme-classic-light'
      }
    },
    type: 'desktop',
    width: '100%',
    height: '100%'
  }

  // 合并配置
  const finalConfig = {
    ...defaultConfig,
    ...config,
    document: {
      ...defaultConfig.document,
      ...(config?.document || {})
    },
    editorConfig: {
      ...defaultConfig.editorConfig,
      ...(config?.editorConfig || {})
    }
  }

  // 销毁旧实例
  if (docEditorInstance) {
    docEditorInstance.destroyEditor()
    docEditorInstance = null
  }

  docEditorInstance = new DocsAPI.DocEditor('editor-container', finalConfig)
}

/**
 * 返回
 */
function handleBack() {
  router.back()
}

/**
 * 保存
 */
function handleSave() {
  if (docEditorInstance) {
    // Euro-Office 支持通过 connector.askSave() 触发保存
    try {
      docEditorInstance.applyMethod('SetRevisedFile', [])
      ElMessage.success(t('editor.saved'))
    } catch (e) {
      ElMessage.info(t('editor.autoSaved'))
    }
  } else {
    ElMessage.info(t('editor.notReady'))
  }
}

/**
 * 下载
 */
function handleDownload() {
  ElMessage.info(t('editor.useEditorDownload'))
}

// ==================== 页面初始化与清理 ====================
onMounted(() => {
  checkEditorAvailable()
})

onBeforeUnmount(() => {
  if (docEditorInstance) {
    try {
      docEditorInstance.destroyEditor()
    } catch (e) {
      // 忽略销毁错误
    }
    docEditorInstance = null
  }
})
</script>

<style scoped>
.document-editor {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg);
}

/* 顶部工具栏 */
.editor-toolbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--bg-white);
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-left .el-button {
  color: var(--text-regular);
  font-size: 14px;
}

.toolbar-left .el-button:hover {
  color: var(--primary);
}

.doc-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 编辑器主体 */
.editor-body {
  flex: 1;
  overflow: hidden;
}

.editor-container {
  width: 100%;
  height: 100%;
}

/* 编辑器不可用提示 */
.editor-unavailable {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: var(--bg);
}

.unavailable-content {
  text-align: center;
  max-width: 520px;
  padding: 40px;
}

.unavailable-icon {
  color: var(--warning);
  margin-bottom: 24px;
}

.unavailable-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.unavailable-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.8;
  margin-bottom: 32px;
}

.unavailable-steps {
  text-align: left;
  margin-bottom: 32px;
  background: var(--bg-white);
  border-radius: 8px;
  padding: 24px;
  border: 1px solid var(--border-light);
}

.unavailable-steps h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.deploy-steps {
  margin-top: 8px;
}

.deploy-steps :deep(.el-step__description) {
  font-size: 13px;
  color: var(--text-secondary);
  font-family: 'Courier New', monospace;
}

.unavailable-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
