<template>
  <div class="document-history">
    <div class="page-card">
      <div class="page-header">
        <div>
          <h2 class="page-title">{{ $t('document.history') }}</h2>
          <p class="page-subtitle">{{ $t('document.historyDesc') }}</p>
        </div>
      </div>

      <!-- 搜索与筛选 -->
      <div class="filter-bar">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-input
              v-model="searchKeyword"
              :placeholder="$t('document.searchPlaceholder')"
              clearable
              :prefix-icon="Search"
              @keyup.enter="handleSearch"
            />
          </el-col>
          <el-col :span="6">
            <el-select
              v-model="filterStatus"
              :placeholder="$t('document.status')"
              clearable
              style="width: 100%"
              @change="handleSearch"
            >
              <el-option :label="$t('document.allStatus')" value="" />
              <el-option :label="$t('document.completed')" value="completed" />
              <el-option :label="$t('document.generating')" value="processing" />
              <el-option :label="$t('document.failed')" value="failed" />
            </el-select>
          </el-col>
          <el-col :span="6">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              :start-placeholder="$t('document.startDate')"
              :end-placeholder="$t('document.endDate')"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              @change="handleSearch"
            />
          </el-col>
          <el-col :span="4">
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              {{ $t('document.query') }}
            </el-button>
            <el-button @click="handleReset">{{ $t('document.reset') }}</el-button>
          </el-col>
        </el-row>
      </div>

      <!-- 操作栏 -->
      <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
        <el-button
          type="danger"
          :disabled="selectedRows.length === 0"
          @click="batchDelete"
        >
          <el-icon><Delete /></el-icon>
          {{ $t('document.batchDelete', { count: selectedRows.length }) }}
        </el-button>
        <el-button type="success" :icon="Download" @click="handleExport">
          {{ $t('document.export') }}
        </el-button>
      </div>

      <!-- 文档列表表格 -->
      <el-table
        :data="documentList"
        stripe
        style="width: 100%"
        v-loading="loading"
        :empty-text="$t('document.noData')"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column prop="name" :label="$t('document.docName')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="templateName" :label="$t('document.template')" width="160" show-overflow-tooltip />
        <el-table-column prop="format" :label="$t('document.format')" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.format || 'DOCX' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('document.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="statusType(row.status)"
            >
              {{ statusLabel(row.status, t) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="$t('document.generatedAt')" width="180" sortable />
        <el-table-column :label="$t('template.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              @click="handleDownload(row)"
              :disabled="row.status !== 'completed'"
            >
              <el-icon><Download /></el-icon>
              {{ $t('document.download') }}
            </el-button>
            <el-button
              type="success"
              link
              size="small"
              @click="handleOpenEditor(row)"
              :disabled="row.status !== 'completed'"
            >
              <el-icon><EditPen /></el-icon>
              {{ $t('document.edit') }}
            </el-button>
            <el-button
              type="danger"
              link
              size="small"
              @click="handleDelete(row)"
            >
              <el-icon><Delete /></el-icon>
              {{ $t('document.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Download, EditPen, Delete } from '@element-plus/icons-vue'
import { getDocumentList, downloadDocument, deleteDocument, exportDocuments } from '@/api/index'
import { downloadBlob, getContentDispositionFileName } from '@/utils/download'
import { extractList, extractTotal } from '@/utils/response'
import { statusType, statusLabel } from '@/utils/status'

const router = useRouter()
const { t } = useI18n()

// ==================== 搜索与筛选 ====================
const searchKeyword = ref('')
const filterStatus = ref('')
const dateRange = ref(null)
const loading = ref(false)
const selectedRows = ref([])

// ==================== 分页 ====================
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// ==================== 文档列表 ====================
const documentList = ref([])

/**
 * 加载文档列表
 */
async function loadDocuments() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value - 1,
      size: pageSize.value
    }
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }
    if (filterStatus.value) {
      params.status = filterStatus.value
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const data = await getDocumentList(params)
    documentList.value = extractList(data)
    total.value = extractTotal(data)
  } catch (e) {
    ElMessage.error(t('common.loadFailed'))
    documentList.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 搜索
 */
function handleSearch() {
  currentPage.value = 1
  loadDocuments()
}

/**
 * 重置搜索条件
 */
function handleReset() {
  searchKeyword.value = ''
  filterStatus.value = ''
  dateRange.value = null
  currentPage.value = 1
  loadDocuments()
}

/**
 * 分页 - 每页条数变化
 */
function handleSizeChange() {
  currentPage.value = 1
  loadDocuments()
}

/**
 * 分页 - 页码变化
 */
function handlePageChange() {
  loadDocuments()
}

/**
 * 下载文档
 */
async function handleDownload(row) {
  try {
    const response = await downloadDocument(row.fileKey || String(row.id))
    const blob = response.data || response
    let fileName = getContentDispositionFileName(response)
    if (fileName === 'download') {
      fileName = row.name
    }
    downloadBlob(blob, fileName)
    ElMessage.success(t('document.downloadSuccess'))
  } catch (e) {
    ElMessage.error(t('common.loadFailed'))
  }
}

/**
 * 表格选择变化
 */
function handleSelectionChange(rows) {
  selectedRows.value = rows
}

/**
 * 批量删除文档
 */
async function batchDelete() {
  if (selectedRows.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      t('document.batchDeleteConfirmMsg', { count: selectedRows.value.length }),
      t('document.batchDelete'),
      {
        confirmButtonText: t('document.confirmDelete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )
  } catch {
    return
  }

  try {
    const ids = selectedRows.value.map(row => row.id)
    await Promise.all(ids.map(id => deleteDocument(id)))
    ElMessage.success(t('document.deleteSuccess'))
    selectedRows.value = []
    loadDocuments()
  } catch (e) {
    ElMessage.error(t('common.loadFailed'))
  }
}

/**
 * 删除文档
 */
function handleOpenEditor(row) {
  router.push(`/editor/${row.fileKey || row.id}`)
}

/**
 * 删除文档
 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('document.deleteConfirmMsg', { name: row.name }),
      t('document.deleteConfirmTitle'),
      {
        confirmButtonText: t('document.confirmDelete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )
  } catch {
    return
  }

  try {
    await deleteDocument(row.id)
    ElMessage.success(t('document.deleteSuccess'))
    loadDocuments()
  } catch (e) {
    ElMessage.error(t('common.loadFailed'))
  }
}

/**
 * 导出文档
 */
async function handleExport() {
  try {
    const response = await exportDocuments('json')
    const blob = response.data || response
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `documents_${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success(t('document.exportSuccess'))
  } catch (e) {
    ElMessage.error(t('document.exportFailed'))
  }
}

// ==================== 页面初始化 ====================
onMounted(() => {
  loadDocuments()
})
</script>

<style scoped>
.document-history {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.filter-bar {
  margin-bottom: 20px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
