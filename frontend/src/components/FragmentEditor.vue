<template>
  <div class="fragment-editor" :class="{ readonly }">
    <div class="editor-toolbar" v-if="!readonly">
      <el-tooltip :content="$t('fragment.insertPlaceholder')" placement="bottom">
        <el-button size="small" @click="showPlaceholderDialog = true" class="toolbar-btn placeholder-btn">
          <el-icon><Plus /></el-icon> { }
        </el-button>
      </el-tooltip>
      <div class="toolbar-divider"></div>
      <el-tooltip content="Bold" placement="bottom">
        <el-button size="small" @click="execCmd('bold')" class="toolbar-btn"><b>B</b></el-button>
      </el-tooltip>
      <el-tooltip content="Italic" placement="bottom">
        <el-button size="small" @click="execCmd('italic')" class="toolbar-btn"><i>I</i></el-button>
      </el-tooltip>
      <el-tooltip content="Underline" placement="bottom">
        <el-button size="small" @click="execCmd('underline')" class="toolbar-btn"><u>U</u></el-button>
      </el-tooltip>
      <el-tooltip content="Strikethrough" placement="bottom">
        <el-button size="small" @click="execCmd('strikeThrough')" class="toolbar-btn"><s>S</s></el-button>
      </el-tooltip>
      <div class="toolbar-divider"></div>
      <el-tooltip content="Heading 1" placement="bottom">
        <el-button size="small" @click="execCmd('formatBlock', 'h1')" class="toolbar-btn">H1</el-button>
      </el-tooltip>
      <el-tooltip content="Heading 2" placement="bottom">
        <el-button size="small" @click="execCmd('formatBlock', 'h2')" class="toolbar-btn">H2</el-button>
      </el-tooltip>
      <el-tooltip content="Heading 3" placement="bottom">
        <el-button size="small" @click="execCmd('formatBlock', 'h3')" class="toolbar-btn">H3</el-button>
      </el-tooltip>
      <div class="toolbar-divider"></div>
      <el-tooltip content="Ordered List" placement="bottom">
        <el-button size="small" @click="execCmd('insertOrderedList')" class="toolbar-btn">
          <el-icon><List /></el-icon>
        </el-button>
      </el-tooltip>
      <el-tooltip content="Unordered List" placement="bottom">
        <el-button size="small" @click="execCmd('insertUnorderedList')" class="toolbar-btn">
          <el-icon><List /></el-icon>
        </el-button>
      </el-tooltip>
    </div>
    <div
      ref="editorRef"
      class="editor-content"
      :contenteditable="!readonly"
      :placeholder="$t('fragment.contentPlaceholder')"
      @input="onInput"
      @paste="onPaste"
    ></div>

    <el-dialog v-model="showPlaceholderDialog" :title="$t('fragment.insertPlaceholder')" width="400px" append-to-body>
      <el-form @submit.prevent="insertPlaceholder">
        <el-form-item :label="$t('fragment.placeholderName')">
          <el-input v-model="placeholderName" :placeholder="$t('fragment.placeholderNamePlaceholder')" @keyup.enter="insertPlaceholder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPlaceholderDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="insertPlaceholder">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { Plus, List } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  readonly: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const editorRef = ref(null)
const showPlaceholderDialog = ref(false)
const placeholderName = ref('')

function execCmd(command, value = null) {
  editorRef.value?.focus()
  if (command === 'formatBlock') {
    document.execCommand('formatBlock', false, `<${value}>`)
  } else {
    document.execCommand(command, false, value)
  }
  syncContent()
}

function onInput() {
  syncContent()
}

function onPaste(e) {
  e.preventDefault()
  const text = e.clipboardData.getData('text/plain')
  document.execCommand('insertText', false, text)
}

function insertPlaceholder() {
  if (!placeholderName.value.trim()) return
  const name = placeholderName.value.trim()
  const html = `<span class="template-placeholder" contenteditable="false" style="background:#e8f4fd;border:1px dashed #409eff;padding:2px 8px;border-radius:3px;color:#409eff;font-weight:500;margin:0 2px;">{${name}}</span>&nbsp;`
  editorRef.value?.focus()
  document.execCommand('insertHTML', false, html)
  placeholderName.value = ''
  showPlaceholderDialog.value = false
  syncContent()
}

function syncContent() {
  if (editorRef.value) {
    emit('update:modelValue', editorRef.value.innerHTML)
  }
}

function setContent(html) {
  if (editorRef.value) {
    editorRef.value.innerHTML = html || ''
  }
}

onMounted(() => {
  setContent(props.modelValue)
})

watch(() => props.modelValue, (val) => {
  if (editorRef.value && editorRef.value.innerHTML !== val) {
    setContent(val)
  }
})

defineExpose({ setContent, editorRef })
</script>

<style scoped>
.fragment-editor {
  border: 1px solid var(--border-color, #dcdfe6);
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg-white, #fff);
}

.fragment-editor.readonly {
  background: var(--bg, #f5f7fa);
}

.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 8px 12px;
  background: var(--bg, #f5f7fa);
  border-bottom: 1px solid var(--border-color, #dcdfe6);
  flex-wrap: wrap;
}

.toolbar-btn {
  min-width: 32px;
  padding: 4px 8px;
  font-size: 13px;
}

.toolbar-btn b, .toolbar-btn i, .toolbar-btn u, .toolbar-btn s {
  font-style: normal;
}

.placeholder-btn {
  color: #409eff;
  font-weight: 600;
  font-family: monospace;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: var(--border-color, #dcdfe6);
  margin: 0 4px;
}

.editor-content {
  min-height: 300px;
  padding: 16px 20px;
  line-height: 1.8;
  font-size: 14px;
  outline: none;
  color: var(--text-primary, #303133);
}

.editor-content:empty::before {
  content: attr(placeholder);
  color: var(--text-placeholder, #c0c4cc);
  pointer-events: none;
}

.editor-content :deep(h1) { font-size: 24px; font-weight: 700; margin: 16px 0 8px; }
.editor-content :deep(h2) { font-size: 20px; font-weight: 600; margin: 14px 0 6px; }
.editor-content :deep(h3) { font-size: 16px; font-weight: 600; margin: 12px 0 4px; }
.editor-content :deep(p) { margin: 8px 0; }
.editor-content :deep(ul), .editor-content :deep(ol) { padding-left: 24px; margin: 8px 0; }
.editor-content :deep(li) { margin: 4px 0; }
.editor-content :deep(table) { width: 100%; border-collapse: collapse; margin: 12px 0; }
.editor-content :deep(td), .editor-content :deep(th) { border: 1px solid var(--border-color, #dcdfe6); padding: 8px 12px; }

html.dark .editor-toolbar {
  background: var(--bg, #1e293b);
}
html.dark .editor-content {
  color: var(--text-primary, #e2e8f0);
}
</style>
