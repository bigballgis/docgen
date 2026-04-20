<template>
  <div class="fragment-library">
    <div class="page-card">
      <div class="page-header">
        <div>
          <h2 class="page-title">{{ $t('fragment.manage') }}</h2>
          <p class="page-subtitle">{{ $t('fragment.manageDesc') }}</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon> {{ $t('fragment.create') }}
        </el-button>
      </div>

      <!-- 搜索与筛选 -->
      <el-row :gutter="16" class="filter-bar">
        <el-col :span="8">
          <el-input v-model="searchKeyword" :placeholder="$t('fragment.search')" clearable :prefix-icon="Search" @keyup.enter="handleSearch" />
        </el-col>
        <el-col :span="6">
          <el-select v-model="filterCategory" :placeholder="$t('fragment.category')" clearable style="width: 100%">
            <el-option v-for="cat in categoryList" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> {{ $t('template.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('template.reset') }}</el-button>
        </el-col>
      </el-row>

      <!-- 片段卡片网格 -->
      <div class="fragment-grid" v-loading="loading">
        <div v-for="frag in fragmentList" :key="frag.id" class="fragment-card" @click="handlePreview(frag)">
          <div class="card-header">
            <div class="card-title">
              <span class="card-name">{{ frag.name }}</span>
              <el-tag :type="statusTagType(frag.status)" size="small">{{ frag.status || 'draft' }}</el-tag>
            </div>
            <el-tag v-if="frag.category" size="small" type="info">{{ frag.category }}</el-tag>
          </div>
          <div class="card-desc">{{ frag.description || $t('template.noDesc') }}</div>
          <div class="card-preview">{{ stripHtml(frag.content_html).substring(0, 100) }}{{ stripHtml(frag.content_html).length > 100 ? '...' : '' }}</div>
          <div class="card-footer">
            <div class="card-tags">
              <el-tag v-for="tag in (frag.tags || [])" :key="tag" size="small" effect="plain" class="tag-item">{{ tag }}</el-tag>
            </div>
            <div class="card-meta">
              <span class="meta-version">v{{ frag.current_version || 1 }}</span>
              <span class="meta-fields">{{ (frag.fields || []).length }} fields</span>
            </div>
          </div>
          <div class="card-actions" @click.stop>
            <el-button type="primary" link size="small" @click.stop="handleEdit(frag)">{{ $t('fragment.edit') }}</el-button>
            <el-button type="info" link size="small" @click.stop="handleOpenVersionHistory(frag)">
              <el-icon :size="14"><Clock /></el-icon> {{ $t('fragment.history') }}
            </el-button>
            <el-button type="danger" link size="small" @click.stop="handleDelete(frag)">{{ $t('fragment.delete') }}</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && fragmentList.length === 0" :description="$t('fragment.noData')" />
      </div>

      <!-- 分页 -->
      <div class="pagination-bar" v-if="total > 0">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[12, 24, 48]" :total="total" layout="total, sizes, prev, pager, next" background @size-change="handleSizeChange" @current-change="handlePageChange" />
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="showEditDialog" :title="isEditing ? $t('fragment.edit') : $t('fragment.create')" width="900px" :close-on-click-modal="false" @closed="resetEditForm" top="5vh">
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('fragment.name')" prop="name">
              <el-input v-model="editForm.name" :placeholder="$t('fragment.namePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('fragment.category')">
              <el-select v-model="editForm.category" :placeholder="$t('fragment.categoryPlaceholder')" clearable allow-create filterable style="width: 100%">
                <el-option v-for="cat in categoryList" :key="cat" :label="cat" :value="cat" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('fragment.description')">
          <el-input v-model="editForm.description" type="textarea" :rows="2" :placeholder="$t('fragment.descPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('fragment.tags')">
          <el-select v-model="editForm.tags" multiple allow-create filterable default-first-option :placeholder="$t('fragment.tagsPlaceholder')" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('fragment.content')" prop="contentHtml">
          <FragmentEditor ref="fragmentEditorRef" v-model="editForm.contentHtml" />
        </el-form-item>
        <el-form-item v-if="isEditing" :label="$t('fragment.changeNote')">
          <el-input v-model="editForm.changeNote" type="textarea" :rows="2" :placeholder="$t('fragment.changeNotePlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog v-model="showPreviewDialog" :title="previewTitle" width="800px" append-to-body top="5vh">
      <div class="preview-content" v-html="previewHtml"></div>
    </el-dialog>

    <!-- 版本历史 -->
    <FragmentVersionHistory v-model="showVersionHistory" :fragment-id="versionHistoryFragmentId" @rollback-success="loadFragments" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Clock } from '@element-plus/icons-vue'
import { getFragments, createFragment, updateFragment, deleteFragment, getFragmentCategories, previewFragment } from '@/api/index'
import FragmentEditor from '@/components/FragmentEditor.vue'
import FragmentVersionHistory from '@/components/FragmentVersionHistory.vue'

const { t } = useI18n()

const searchKeyword = ref('')
const filterCategory = ref('')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)
const fragmentList = ref([])
const categoryList = ref([])

// 编辑
const showEditDialog = ref(false)
const isEditing = ref(false)
const editingId = ref(null)
const saving = ref(false)
const editFormRef = ref(null)
const fragmentEditorRef = ref(null)
const editForm = reactive({ name: '', description: '', category: '', tags: [], contentHtml: '', changeNote: '' })
const editRules = computed(() => ({
  name: [{ required: true, message: t('fragment.namePlaceholder'), trigger: 'blur' }],
  contentHtml: [{ required: true, message: t('fragment.contentPlaceholder'), trigger: 'blur' }]
}))

// 预览
const showPreviewDialog = ref(false)
const previewTitle = ref('')
const previewHtml = ref('')

// 版本历史
const showVersionHistory = ref(false)
const versionHistoryFragmentId = ref(null)

function stripHtml(html) {
  if (!html) return ''
  return html.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim()
}

function statusTagType(status) {
  const map = { draft: 'info', published: 'success', archived: 'warning' }
  return map[status] || 'info'
}

async function loadCategories() {
  try {
    categoryList.value = (await getFragmentCategories()) || []
  } catch { categoryList.value = [] }
}

async function loadFragments() {
  loading.value = true
  try {
    const params = { page: currentPage.value - 1, size: pageSize.value }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (filterCategory.value) params.category = filterCategory.value
    const data = await getFragments(params)
    fragmentList.value = data?.content || data?.list || []
    total.value = data?.totalElements || data?.total || 0
  } catch (e) {
    console.error('加载片段列表失败', e)
    fragmentList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() { currentPage.value = 1; loadFragments() }
function handleReset() { searchKeyword.value = ''; filterCategory.value = ''; currentPage.value = 1; loadFragments() }
function handleSizeChange() { currentPage.value = 1; loadFragments() }
function handlePageChange() { loadFragments() }

function openCreateDialog() {
  isEditing.value = false
  editingId.value = null
  editForm.name = ''
  editForm.description = ''
  editForm.category = ''
  editForm.tags = []
  editForm.contentHtml = ''
  editForm.changeNote = ''
  showEditDialog.value = true
}

function handleEdit(frag) {
  isEditing.value = true
  editingId.value = frag.id
  editForm.name = frag.name
  editForm.description = frag.description || ''
  editForm.category = frag.category || ''
  editForm.tags = frag.tags || []
  editForm.contentHtml = frag.content_html || ''
  editForm.changeNote = ''
  showEditDialog.value = true
}

async function handleSave() {
  if (!editFormRef.value) return
  try { await editFormRef.value.validate() } catch { return }

  saving.value = true
  try {
    if (isEditing.value) {
      await updateFragment(editingId.value, {
        name: editForm.name,
        description: editForm.description,
        category: editForm.category,
        tags: editForm.tags,
        contentHtml: editForm.contentHtml,
        changeNote: editForm.changeNote
      })
      ElMessage.success(t('fragment.updateSuccess'))
    } else {
      await createFragment({
        name: editForm.name,
        description: editForm.description,
        category: editForm.category,
        tags: editForm.tags,
        contentHtml: editForm.contentHtml
      })
      ElMessage.success(t('fragment.createSuccess'))
    }
    showEditDialog.value = false
    loadFragments()
    loadCategories()
  } catch (e) {
    console.error('保存片段失败', e)
  } finally {
    saving.value = false
  }
}

function resetEditForm() {
  if (editFormRef.value) editFormRef.value.resetFields()
  editForm.contentHtml = ''
}

async function handlePreview(frag) {
  previewTitle.value = frag.name
  previewHtml.value = ''
  showPreviewDialog.value = true
  try {
    const data = await previewFragment(frag.id)
    previewHtml.value = data?.html || ''
  } catch { previewHtml.value = '<p style="color:red;">预览加载失败</p>' }
}

async function handleDelete(frag) {
  try {
    await ElMessageBox.confirm(t('fragment.deleteConfirm'), t('fragment.delete'), { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' })
  } catch { return }
  try {
    await deleteFragment(frag.id)
    ElMessage.success(t('fragment.deleteSuccess'))
    loadFragments()
  } catch (e) { console.error('删除片段失败', e) }
}

function handleOpenVersionHistory(frag) {
  versionHistoryFragmentId.value = frag.id
  showVersionHistory.value = true
}

onMounted(() => { loadFragments(); loadCategories() })
</script>

<style scoped>
.fragment-library { max-width: 1200px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.filter-bar { margin-bottom: 20px; display: flex; align-items: center; }
.fragment-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 16px; margin-bottom: 20px; }
.fragment-card { border: 1px solid var(--border-color, #e4e7ed); border-radius: 12px; padding: 16px; background: var(--bg-white, #fff); cursor: pointer; transition: all 0.2s; }
.fragment-card:hover { box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08); transform: translateY(-2px); }
.card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }
.card-title { display: flex; align-items: center; gap: 8px; }
.card-name { font-size: 16px; font-weight: 600; color: var(--text-primary, #303133); }
.card-desc { font-size: 13px; color: var(--text-secondary, #909399); margin-bottom: 8px; line-height: 1.5; }
.card-preview { font-size: 12px; color: var(--text-regular, #606266); background: var(--bg, #f5f7fa); padding: 8px 12px; border-radius: 6px; margin-bottom: 12px; line-height: 1.6; max-height: 60px; overflow: hidden; }
.card-footer { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.card-tags { display: flex; gap: 4px; flex-wrap: wrap; }
.tag-item { margin: 0; }
.card-meta { display: flex; gap: 12px; font-size: 12px; color: var(--text-secondary, #909399); }
.card-actions { display: flex; gap: 4px; padding-top: 8px; border-top: 1px solid var(--border-color, #ebeef5); }
.pagination-bar { display: flex; justify-content: flex-end; }
.preview-content { min-height: 300px; max-height: 65vh; overflow-y: auto; padding: 20px; line-height: 1.8; font-size: 14px; border: 1px solid var(--border-color, #e4e7ed); border-radius: 8px; }
.preview-content :deep(.template-placeholder) { background: #e8f4fd; border: 1px dashed #409eff; padding: 2px 8px; border-radius: 3px; color: #409eff; font-weight: 500; }
html.dark .fragment-card { background: var(--bg-card, #1e293b); border-color: var(--border-color, #334155); }
html.dark .card-preview { background: var(--bg, #0f172a); }
html.dark .preview-content { background: var(--bg-card, #1e293b); border-color: var(--border-color, #334155); }
</style>
