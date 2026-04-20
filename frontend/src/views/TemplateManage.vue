<template>
  <div class="template-manage">
    <div class="page-card">
      <div class="page-header">
        <div>
          <h2 class="page-title">{{ $t('template.manage') }}</h2>
          <p class="page-subtitle">{{ $t('template.manageDesc') }}</p>
        </div>
        <el-button type="primary" @click="openUploadDialog">
          <el-icon><Upload /></el-icon>
          {{ $t('template.upload') }}
        </el-button>
      </div>

      <!-- 标签页 -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 全部模板 -->
        <el-tab-pane :label="$t('template.all')" name="all">
          <!-- 搜索与筛选 -->
          <el-row :gutter="16" class="filter-bar">
            <el-col :span="8">
              <el-input
                v-model="searchKeyword"
                :placeholder="$t('template.search')"
                clearable
                :prefix-icon="Search"
                @keyup.enter="handleSearch"
              />
            </el-col>
            <el-col :span="6">
              <el-select
                v-model="filterCategory"
                :placeholder="$t('template.category')"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="cat in categoryList"
                  :key="cat"
                  :label="cat"
                  :value="cat"
                />
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-button type="primary" @click="handleSearch">
                <el-icon><Search /></el-icon>
                {{ $t('template.query') }}
              </el-button>
              <el-button @click="handleReset">{{ $t('template.reset') }}</el-button>
            </el-col>
          </el-row>

          <!-- 模板列表表格 -->
          <el-table
            :data="templateList"
            stripe
            style="width: 100%"
            v-loading="loading"
            :empty-text="$t('template.noData')"
          >
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" :label="$t('template.name')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="category" :label="$t('template.category')" width="120">
              <template #default="{ row }">
                <el-tag size="small" type="info">
                  {{ row.category || $t('template.uncategorized') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" :label="$t('approval.statusLabel')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ $t(`approval.status.${row.status || 'draft'}`) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" :label="$t('template.description')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createTime" :label="$t('template.createdAt')" width="180" />
            <el-table-column :label="$t('template.actions')" width="360" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleViewDetail(row)">
                  {{ $t('template.viewDetail') }}
                </el-button>
                <el-button type="success" link size="small" @click="handleParseFields(row)">
                  {{ $t('template.parseFields') }}
                </el-button>
                <el-button type="info" link size="small" @click="handleOpenVersionHistory(row)">
                  <el-icon :size="14"><Clock /></el-icon>
                  {{ $t('version.history') }}
                </el-button>
                <!-- draft 状态：提交审批 -->
                <el-button
                  v-if="row.status === 'draft' || !row.status"
                  type="warning"
                  link
                  size="small"
                  @click="handleSubmitApproval(row)"
                >
                  {{ $t('approval.submit') }}
                </el-button>
                <!-- pending 状态：审批中标签 -->
                <el-tag v-if="row.status === 'pending'" type="warning" size="small" class="ml-8">
                  {{ $t('approval.pending') }}
                </el-tag>
                <!-- rejected 状态：查看原因 + 重新编辑 -->
                <el-button
                  v-if="row.status === 'rejected'"
                  type="info"
                  link
                  size="small"
                  @click="handleViewRejectReason(row)"
                >
                  {{ $t('approval.viewReason') }}
                </el-button>
                <el-button
                  v-if="row.status === 'rejected'"
                  type="warning"
                  link
                  size="small"
                  @click="handleSubmitApproval(row)"
                >
                  {{ $t('approval.resubmit') }}
                </el-button>
                <el-button type="danger" link size="small" @click="handleDelete(row)">
                  {{ $t('template.delete') }}
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
        </el-tab-pane>

        <!-- 待审批（仅 admin 可见） -->
        <el-tab-pane
          v-if="authStore.isAdmin"
          :label="$t('approval.pending')"
          name="pending"
        >
          <el-table
            :data="pendingList"
            stripe
            style="width: 100%"
            v-loading="pendingLoading"
            :empty-text="$t('approval.noPending')"
          >
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" :label="$t('template.name')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="category" :label="$t('template.category')" width="120">
              <template #default="{ row }">
                <el-tag size="small" type="info">
                  {{ row.category || $t('template.uncategorized') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="submitter" :label="$t('approval.submitter')" width="120" />
            <el-table-column prop="submitTime" :label="$t('approval.submitTime')" width="180" />
            <el-table-column :label="$t('template.actions')" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleViewApprovalDetail(row)">
                  {{ $t('template.viewDetail') }}
                </el-button>
                <el-button type="success" link size="small" @click="handleApprove(row)">
                  {{ $t('approval.approve') }}
                </el-button>
                <el-button type="danger" link size="small" @click="handleOpenRejectDialog(row)">
                  {{ $t('approval.reject') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 上传对话框 -->
    <el-dialog
      v-model="showUploadDialog"
      :title="$t('template.uploadTitle')"
      width="560px"
      :close-on-click-modal="false"
      @closed="resetUploadForm"
    >
      <el-form
        :model="uploadForm"
        :rules="uploadRules"
        ref="uploadFormRef"
        label-width="100px"
      >
        <el-form-item :label="$t('template.name')" prop="name">
          <el-input v-model="uploadForm.name" :placeholder="$t('template.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('template.description')" prop="description">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            :placeholder="$t('template.descPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="$t('template.category')" prop="category">
          <el-select
            v-model="uploadForm.category"
            :placeholder="$t('template.categoryPlaceholder')"
            clearable
            allow-create
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="cat in categoryList"
              :key="cat"
              :label="cat"
              :value="cat"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('template.file')" prop="file">
          <el-upload
            ref="uploadRef"
            drag
            :auto-upload="false"
            :limit="1"
            accept=".docx"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
            :on-remove="handleFileRemove"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              {{ $t('template.fileHint') }}
            </div>
            <template #tip>
              <div class="el-upload__tip">
                {{ $t('template.fileTip') }}
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item :label="$t('version.changeNote')">
          <el-input
            v-model="uploadForm.changeNote"
            type="textarea"
            :rows="2"
            :placeholder="$t('version.changeNotePlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading">
          {{ $t('template.confirmUpload') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 模板详情对话框 -->
    <el-dialog
      v-model="showDetailDialog"
      :title="$t('template.detail')"
      width="640px"
    >
      <el-descriptions :column="2" border v-loading="detailLoading">
        <el-descriptions-item :label="$t('template.name')" :span="2">
          {{ templateDetail.name }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('template.category')">
          <el-tag size="small" type="info">{{ templateDetail.category || $t('template.uncategorized') }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('approval.statusLabel')">
          <el-tag :type="statusTagType(templateDetail.status)" size="small">
            {{ $t(`approval.status.${templateDetail.status || 'draft'}`) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('template.createdAt')">
          {{ templateDetail.createTime }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('approval.submitter')">
          {{ templateDetail.submitter || templateDetail.createdBy || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('template.description')" :span="2">
          {{ templateDetail.description || $t('template.noDesc') }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('template.fields')" :span="2" v-if="templateDetail.fields && templateDetail.fields.length">
          <div class="fields-list">
            <el-tag
              v-for="field in templateDetail.fields"
              :key="field.name"
              size="small"
              class="field-tag"
            >
              {{ field.name }}
              <span class="field-type">({{ field.type }})</span>
            </el-tag>
          </div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="info" @click="handleOpenVersionHistory(templateDetail)">
          <el-icon :size="14"><Clock /></el-icon>
          {{ $t('version.history') }}
        </el-button>
        <el-button @click="showDetailDialog = false">{{ $t('template.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- 审批详情对话框 -->
    <el-dialog
      v-model="showApprovalDetailDialog"
      :title="$t('approval.detailTitle')"
      width="640px"
    >
      <el-descriptions :column="2" border v-loading="approvalDetailLoading">
        <el-descriptions-item :label="$t('template.name')" :span="2">
          {{ approvalDetail.name }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('template.category')">
          <el-tag size="small" type="info">{{ approvalDetail.category || $t('template.uncategorized') }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('approval.submitter')">
          {{ approvalDetail.submitter || approvalDetail.createdBy || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('approval.submitTime')">
          {{ approvalDetail.submitTime || approvalDetail.createTime || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('template.description')" :span="2">
          {{ approvalDetail.description || $t('template.noDesc') }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('template.fields')" :span="2" v-if="approvalDetail.fields && approvalDetail.fields.length">
          <div class="fields-list">
            <el-tag
              v-for="field in approvalDetail.fields"
              :key="field.name"
              size="small"
              class="field-tag"
            >
              {{ field.name }}
              <span class="field-type">({{ field.type }})</span>
            </el-tag>
          </div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="showApprovalDetailDialog = false">{{ $t('template.close') }}</el-button>
        <el-button type="success" @click="handleApprove(approvalDetail)">
          {{ $t('approval.approve') }}
        </el-button>
        <el-button type="danger" @click="handleOpenRejectDialog(approvalDetail)">
          {{ $t('approval.reject') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 驳回对话框 -->
    <el-dialog
      v-model="showRejectDialog"
      :title="$t('approval.rejectTitle')"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules">
        <el-form-item :label="$t('template.name')">
          <span>{{ rejectTarget?.name }}</span>
        </el-form-item>
        <el-form-item :label="$t('approval.rejectReason')" prop="reason">
          <el-input
            v-model="rejectForm.reason"
            type="textarea"
            :rows="4"
            :placeholder="$t('approval.rejectReasonPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRejectDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" @click="handleReject" :loading="rejecting">
          {{ $t('approval.confirmReject') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 驳回原因查看对话框 -->
    <el-dialog
      v-model="showRejectReasonDialog"
      :title="$t('approval.rejectReason')"
      width="480px"
    >
      <el-descriptions :column="1" border>
        <el-descriptions-item :label="$t('template.name')">
          {{ rejectReasonTarget?.name }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('approval.rejectReason')">
          {{ rejectReasonTarget?.rejectReason || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="showRejectReasonDialog = false">{{ $t('template.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史抽屉 -->
    <VersionHistory
      v-model="showVersionHistory"
      :template-id="versionHistoryTemplateId"
      @rollback-success="handleRollbackSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Search, UploadFilled, Clock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import VersionHistory from '@/components/VersionHistory.vue'
import {
  getTemplates,
  getTemplate,
  deleteTemplate,
  getCategories,
  uploadTemplate,
  parseTemplateFields,
  submitForApproval,
  approveTemplate,
  rejectTemplate,
  getPendingApprovals
} from '@/api/index'

const { t } = useI18n()
const authStore = useAuthStore()

// ==================== 标签页 ====================
const activeTab = ref('all')

function handleTabChange(tab) {
  if (tab === 'pending') {
    loadPendingApprovals()
  } else {
    loadTemplates()
  }
}

// ==================== 搜索与筛选 ====================
const searchKeyword = ref('')
const filterCategory = ref('')
const loading = ref(false)

// ==================== 分页 ====================
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// ==================== 模板列表 ====================
const templateList = ref([])

// ==================== 分类列表 ====================
const categoryList = ref([])

/**
 * 加载分类列表
 */
async function loadCategories() {
  try {
    const data = await getCategories()
    categoryList.value = data || []
  } catch (e) {
    // 分类加载失败不阻塞页面
    console.warn('加载分类列表失败', e)
  }
}

/**
 * 加载模板列表
 */
async function loadTemplates() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value - 1,
      size: pageSize.value
    }
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }
    if (filterCategory.value) {
      params.category = filterCategory.value
    }
    const data = await getTemplates(params)
    // 后端返回格式可能是 { content: [], totalElements: 0 } 或 { list: [], total: 0 }
    let list = data?.content || data?.list || data?.records || []
    // 解析每个模板的 fields JSON 字符串为数组
    list = list.map(item => {
      if (item.fields && typeof item.fields === 'string') {
        try {
          item.fields = JSON.parse(item.fields)
        } catch {
          item.fields = []
        }
      }
      return item
    })
    templateList.value = list
    total.value = data?.totalElements || data?.total || data?.totalCount || 0
  } catch (e) {
    console.error('加载模板列表失败', e)
  } finally {
    loading.value = false
  }
}

/**
 * 搜索
 */
function handleSearch() {
  currentPage.value = 1
  loadTemplates()
}

/**
 * 重置搜索条件
 */
function handleReset() {
  searchKeyword.value = ''
  filterCategory.value = ''
  currentPage.value = 1
  loadTemplates()
}

/**
 * 分页 - 每页条数变化
 */
function handleSizeChange() {
  currentPage.value = 1
  loadTemplates()
}

/**
 * 分页 - 页码变化
 */
function handlePageChange() {
  loadTemplates()
}

// ==================== 状态标签 ====================

/**
 * 获取状态对应的 Tag type
 */
function statusTagType(status) {
  const map = {
    draft: 'info',
    pending: 'warning',
    approved: 'success',
    rejected: 'danger',
    published: 'success'
  }
  return map[status] || 'info'
}

// ==================== 审批操作 ====================

/**
 * 提交审批
 */
async function handleSubmitApproval(row) {
  try {
    await ElMessageBox.confirm(
      t('approval.submitConfirmMsg', { name: row.name }),
      t('approval.submitConfirm'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'info'
      }
    )
  } catch {
    return
  }

  try {
    await submitForApproval(row.id)
    ElMessage.success(t('approval.submitSuccess'))
    loadTemplates()
  } catch (e) {
    console.error('提交审批失败', e)
  }
}

/**
 * 审批通过
 */
async function handleApprove(row) {
  try {
    await ElMessageBox.confirm(
      t('approval.approveConfirmMsg', { name: row.name }),
      t('approval.approve'),
      {
        confirmButtonText: t('approval.approve'),
        cancelButtonText: t('common.cancel'),
        type: 'success'
      }
    )
  } catch {
    return
  }

  try {
    await approveTemplate(row.id, '')
    ElMessage.success(t('approval.approveSuccess'))
    showApprovalDetailDialog.value = false
    loadPendingApprovals()
    loadTemplates()
  } catch (e) {
    console.error('审批通过失败', e)
  }
}

/**
 * 打开驳回对话框
 */
function handleOpenRejectDialog(row) {
  rejectTarget.value = row
  rejectForm.reason = ''
  showRejectDialog.value = true
}

/**
 * 确认驳回
 */
async function handleReject() {
  if (!rejectFormRef.value) return
  try {
    await rejectFormRef.value.validate()
  } catch {
    return
  }

  if (!rejectForm.reason.trim()) {
    ElMessage.warning(t('approval.rejectReasonRequired'))
    return
  }

  rejecting.value = true
  try {
    await rejectTemplate(rejectTarget.value.id, rejectForm.reason)
    ElMessage.success(t('approval.rejectSuccess'))
    showRejectDialog.value = false
    showApprovalDetailDialog.value = false
    loadPendingApprovals()
    loadTemplates()
  } catch (e) {
    console.error('驳回失败', e)
  } finally {
    rejecting.value = false
  }
}

/**
 * 查看驳回原因
 */
function handleViewRejectReason(row) {
  rejectReasonTarget.value = row
  showRejectReasonDialog.value = true
}

// ==================== 待审批列表 ====================
const pendingList = ref([])
const pendingLoading = ref(false)

/**
 * 加载待审批列表
 */
async function loadPendingApprovals() {
  pendingLoading.value = true
  try {
    const data = await getPendingApprovals()
    let list = Array.isArray(data) ? data : (data?.content || data?.list || data?.records || [])
    list = list.map(item => {
      if (item.fields && typeof item.fields === 'string') {
        try {
          item.fields = JSON.parse(item.fields)
        } catch {
          item.fields = []
        }
      }
      return item
    })
    pendingList.value = list
  } catch (e) {
    console.error('加载待审批列表失败', e)
    pendingList.value = []
  } finally {
    pendingLoading.value = false
  }
}

// ==================== 审批详情对话框 ====================
const showApprovalDetailDialog = ref(false)
const approvalDetailLoading = ref(false)
const approvalDetail = ref({})

async function handleViewApprovalDetail(row) {
  showApprovalDetailDialog.value = true
  approvalDetailLoading.value = true
  approvalDetail.value = {}
  try {
    const data = await getTemplate(row.id)
    const detail = data || {}
    if (detail.fields && typeof detail.fields === 'string') {
      try {
        detail.fields = JSON.parse(detail.fields)
      } catch {
        detail.fields = []
      }
    }
    approvalDetail.value = detail
  } catch (e) {
    console.error('获取审批详情失败', e)
    approvalDetail.value = row
  } finally {
    approvalDetailLoading.value = false
  }
}

// ==================== 驳回对话框 ====================
const showRejectDialog = ref(false)
const rejecting = ref(false)
const rejectFormRef = ref(null)
const rejectTarget = ref(null)
const rejectForm = reactive({
  reason: ''
})

const rejectRules = computed(() => ({
  reason: [{ required: true, message: t('approval.rejectReasonRequired'), trigger: 'blur' }]
}))

// ==================== 驳回原因查看对话框 ====================
const showRejectReasonDialog = ref(false)
const rejectReasonTarget = ref(null)

// ==================== 上传对话框 ====================
const showUploadDialog = ref(false)
const uploading = ref(false)
const uploadFormRef = ref(null)
const uploadRef = ref(null)

const uploadForm = reactive({
  name: '',
  description: '',
  category: '',
  file: null,
  changeNote: ''
})

const uploadRules = computed(() => ({
  name: [{ required: true, message: t('template.nameRequired'), trigger: 'blur' }],
  file: [{ required: true, message: t('template.fileRequired'), trigger: 'change' }]
}))

function openUploadDialog() {
  showUploadDialog.value = true
}

function handleFileChange(file) {
  // 校验文件格式
  const isDocx = file.name.endsWith('.docx')
  if (!isDocx) {
    ElMessage.error(t('template.onlyDocx'))
    uploadForm.file = null
    if (uploadRef.value) {
      uploadRef.value.clearFiles()
    }
    return
  }
  // 校验文件大小（10MB）
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error(t('template.fileTooLarge'))
    uploadForm.file = null
    if (uploadRef.value) {
      uploadRef.value.clearFiles()
    }
    return
  }
  uploadForm.file = file.raw
}

function handleFileRemove() {
  uploadForm.file = null
}

function handleExceed() {
  ElMessage.warning(t('template.exceedLimit'))
}

async function handleUpload() {
  if (!uploadFormRef.value) return
  try {
    await uploadFormRef.value.validate()
  } catch {
    return
  }

  if (!uploadForm.file) {
    ElMessage.warning(t('template.fileRequired'))
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadForm.file)
    formData.append('name', uploadForm.name)
    if (uploadForm.description) {
      formData.append('description', uploadForm.description)
    }
    if (uploadForm.category) {
      formData.append('category', uploadForm.category)
    }
    if (uploadForm.changeNote) {
      formData.append('changeNote', uploadForm.changeNote)
    }
    await uploadTemplate(formData)
    ElMessage.success(t('template.uploadSuccess'))
    showUploadDialog.value = false
    // 刷新列表和分类
    loadTemplates()
    loadCategories()
  } catch (e) {
    console.error('上传模板失败', e)
  } finally {
    uploading.value = false
  }
}

function resetUploadForm() {
  if (uploadFormRef.value) {
    uploadFormRef.value.resetFields()
  }
  uploadForm.file = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

// ==================== 模板详情 ====================
const showDetailDialog = ref(false)
const detailLoading = ref(false)
const templateDetail = ref({})

async function handleViewDetail(row) {
  showDetailDialog.value = true
  detailLoading.value = true
  templateDetail.value = {}
  try {
    const data = await getTemplate(row.id)
    const detail = data || {}
    // 后端 fields 字段是 JSON 字符串，需要解析为数组
    if (detail.fields && typeof detail.fields === 'string') {
      try {
        detail.fields = JSON.parse(detail.fields)
      } catch {
        detail.fields = []
      }
    }
    templateDetail.value = detail
  } catch (e) {
    console.error('获取模板详情失败', e)
  } finally {
    detailLoading.value = false
  }
}

// ==================== 解析字段 ====================
async function handleParseFields(row) {
  try {
    await ElMessageBox.confirm(
      t('template.parseConfirmMsg', { name: row.name }),
      t('template.parseConfirm'),
      {
        confirmButtonText: t('template.confirmParse'),
        cancelButtonText: t('common.cancel'),
        type: 'info'
      }
    )
  } catch {
    return
  }

  try {
    const data = await parseTemplateFields(row.id)
    const fieldCount = data?.fieldCount || data?.fields?.length || 0
    ElMessage.success(t('template.parseSuccess', { count: fieldCount }))
    // 刷新列表
    loadTemplates()
  } catch (e) {
    console.error('解析字段失败', e)
  }
}

// ==================== 删除模板 ====================
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('template.deleteConfirmMsg', { name: row.name }),
      t('template.deleteConfirmTitle'),
      {
        confirmButtonText: t('template.confirmDelete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )
  } catch {
    return
  }

  try {
    await deleteTemplate(row.id)
    ElMessage.success(t('template.deleteSuccess'))
    loadTemplates()
  } catch (e) {
    console.error('删除模板失败', e)
  }
}

// ==================== 版本历史 ====================
const showVersionHistory = ref(false)
const versionHistoryTemplateId = ref(null)

/**
 * 打开版本历史抽屉
 */
function handleOpenVersionHistory(row) {
  versionHistoryTemplateId.value = row.id
  showVersionHistory.value = true
}

/**
 * 版本回滚成功回调
 */
function handleRollbackSuccess() {
  loadTemplates()
}

// ==================== 页面初始化 ====================
onMounted(() => {
  loadTemplates()
  loadCategories()
  // admin 角色预加载待审批列表
  if (authStore.isAdmin) {
    loadPendingApprovals()
  }
})
</script>

<style scoped>
.template-manage {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.filter-bar {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.fields-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.field-tag {
  margin: 0;
}

.field-type {
  color: #909399;
  font-size: 12px;
}

.ml-8 {
  margin-left: 8px;
}
</style>
