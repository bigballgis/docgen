import axios from 'axios'
import { ElMessage } from 'element-plus'

// 延迟获取 i18n 实例，避免循环依赖
function getI18n() {
  try {
    return window.__vue_app__?.config?.globalProperties?.$t || ((key) => key)
  } catch {
    return (key) => key
  }
}

function getStatusMessage(status) {
  const t = getI18n()
  const messages = {
    400: t('httpError.badRequest'),
    401: t('httpError.unauthorized'),
    403: t('httpError.forbidden'),
    404: t('httpError.notFound'),
    500: t('httpError.serverError')
  }
  return messages[status] || t('httpError.unknown')
}

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = sessionStorage.getItem('token') || localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // 自动附加 X-Tenant-Id header
    const tenantId = sessionStorage.getItem('tenantId')
    if (tenantId) {
      config.headers['X-Tenant-Id'] = tenantId
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const { data } = response
    // 如果是文件下载（blob 类型），直接返回整个 response
    if (response.config.responseType === 'blob') {
      return response
    }
    // 如果后端返回统一的响应格式，可在此处做解包处理
    if (data && (data.code === 200 || data.code === 0)) {
      return data.data
    }
    // 如果后端直接返回数据（没有 code 字段，如 Spring Boot ResponseEntity），直接返回
    if (data && data.code === undefined) {
      return data
    }
    ElMessage.error(data.message || '请求失败')
    return Promise.reject(new Error(data.message || '请求失败'))
  },
  (error) => {
    const { response } = error
    if (response) {
      const message = getStatusMessage(response.status)
      ElMessage.error(message)

      // 401 状态码自动跳转登录页
      if (response.status === 401) {
        sessionStorage.removeItem('token')
        sessionStorage.removeItem('user')
        sessionStorage.removeItem('tenantId')
        sessionStorage.removeItem('tenantName')
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        localStorage.removeItem('tenantId')
        localStorage.removeItem('tenantName')
        // 避免在登录页重复跳转
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      }
    } else if (error.code === 'ECONNABORTED') {
      const t = getI18n()
      ElMessage.error(t('httpError.timeout'))
    } else {
      const t = getI18n()
      ElMessage.error(t('httpError.networkError'))
    }
    return Promise.reject(error)
  }
)

// ==================== 认证 API ====================

/**
 * 用户登录
 * @param {Object} data - { username, password }
 */
export function login(data) {
  return request.post('/auth/login', data)
}

/**
 * 获取当前用户信息
 */
export function getProfile() {
  return request.get('/auth/profile')
}

/**
 * 修改密码
 * @param {Object} data - { oldPassword, newPassword }
 */
export function updatePassword(data) {
  return request.put('/auth/password', data)
}

// ==================== 租户 API ====================

// 用户管理（管理员）
export function getUserList(params) {
  return request.get('/auth/users', { params })
}

export function updateUserRole(userId, role) {
  return request.put(`/auth/users/${userId}/role`, null, { params: { role } })
}

export function updateUserStatus(userId, status) {
  return request.put(`/auth/users/${userId}/status`, null, { params: { status } })
}

/**
 * 获取租户列表
 */
export function getTenants() {
  return request.get('/tenants')
}

// 租户管理（管理员）
export function getTenantList(params) {
  return request.get('/tenants', { params })
}

export function createTenant(data) {
  return request.post('/tenants', data)
}

export function updateTenant(id, data) {
  return request.put(`/tenants/${id}`, data)
}

// ==================== 模板管理 API ====================

/**
 * 上传模板
 * @param {FormData} formData - 包含 file, name, description, category 的 FormData
 */
export function uploadTemplate(formData, config = {}) {
  return request.post('/templates/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    ...config
  })
}

/**
 * 获取模板列表
 * @param {Object} params - 查询参数 { keyword, category, page, size, status }
 */
export function getTemplates(params) {
  return request.get('/templates', { params })
}

/**
 * 获取模板详情
 * @param {number|string} id - 模板 ID
 */
export function getTemplate(id) {
  return request.get(`/templates/${id}`)
}

/**
 * 删除模板
 * @param {number|string} id - 模板 ID
 */
export function deleteTemplate(id) {
  return request.delete(`/templates/${id}`)
}

/**
 * 获取模板分类列表
 */
export function getCategories() {
  return request.get('/templates/categories')
}

/**
 * 解析模板字段
 * @param {number|string} id - 模板 ID
 */
export function parseTemplateFields(id) {
  return request.post(`/templates/${id}/parse-fields`)
}

// ==================== 模板审批 API ====================

/**
 * 提交模板审批
 * @param {number|string} templateId - 模板 ID
 */
export function submitForApproval(templateId) {
  return request.post(`/templates/${templateId}/submit`)
}

/**
 * 审批通过模板
 * @param {number|string} templateId - 模板 ID
 * @param {string} comment - 审批意见
 */
export function approveTemplate(templateId, comment) {
  return request.post(`/templates/${templateId}/approve`, { comment })
}

/**
 * 驳回模板
 * @param {number|string} templateId - 模板 ID
 * @param {string} reason - 驳回原因
 */
export function rejectTemplate(templateId, reason) {
  return request.post(`/templates/${templateId}/reject`, { reason })
}

/**
 * 获取待审批模板列表
 */
export function getPendingApprovals() {
  return request.get('/templates/pending')
}

// ==================== 文档生成 API ====================

/**
 * 生成文档
 * @param {Object} data - { templateId, fields, format }
 */
export function generateDocument(data) {
  return request.post('/documents/generate', data, {
    responseType: 'blob'
  })
}

/**
 * 下载文档
 * @param {string} fileName - 文件名
 */
export function downloadDocument(fileName) {
  return request.get(`/documents/download/${fileName}`, {
    responseType: 'blob'
  })
}

// ==================== 文档历史 API ====================

/**
 * 获取文档列表
 * @param {Object} params - { keyword, status, page, size }
 */
export function getDocumentList(params) {
  return request.get('/documents', { params })
}

/**
 * 删除文档
 * @param {number|string} id - 文档 ID
 */
export function deleteDocument(id) {
  return request.delete(`/documents/${id}`)
}

// ==================== 仪表盘 API ====================

/**
 * 获取仪表盘统计数据
 */
export function getDashboardStats() {
  return request.get('/dashboard/stats')
}

// ==================== 编辑器 API ====================

/**
 * 获取编辑器配置
 * @param {string} fileKey - 文件标识
 */
export function getEditorConfig(fileKey) {
  return request.get(`/editor/config/${fileKey}`)
}

// ==================== 版本管理 API ====================

/**
 * 获取模板版本列表
 * @param {number|string} templateId - 模板 ID
 */
export function getVersionList(templateId) {
  return request.get(`/templates/${templateId}/versions`)
}

/**
 * 对比两个版本
 * @param {number|string} templateId - 模板 ID
 * @param {number|string} v1 - 版本号 1
 * @param {number|string} v2 - 版本号 2
 */
export function compareVersions(templateId, v1, v2) {
  return request.get(`/templates/${templateId}/versions/compare`, {
    params: { v1, v2 }
  })
}

/**
 * 获取版本预览 HTML
 * @param {number|string} templateId - 模板 ID
 * @param {number|string} version - 版本号
 */
export function getVersionPreview(templateId, version) {
  return request.get(`/templates/${templateId}/versions/${version}/preview`)
}

/**
 * 回滚到指定版本
 * @param {number|string} templateId - 模板 ID
 * @param {number|string} version - 版本号
 */
export function rollbackVersion(templateId, version) {
  return request.post(`/templates/${templateId}/versions/${version}/rollback`)
}

// ==================== 片段 API ====================

/**
 * 获取片段列表
 * @param {Object} params - 查询参数 { keyword, category, page, size }
 */
export function getFragments(params) {
  return request.get('/fragments', { params })
}

/**
 * 创建片段
 * @param {Object} data - { name, description, category, tags, content }
 */
export function createFragment(data) {
  return request.post('/fragments', data)
}

/**
 * 更新片段
 * @param {number|string} id - 片段 ID
 * @param {Object} data - { name, description, category, tags, content, changeNote }
 */
export function updateFragment(id, data) {
  return request.put(`/fragments/${id}`, data)
}

/**
 * 删除片段
 * @param {number|string} id - 片段 ID
 */
export function deleteFragment(id) {
  return request.delete(`/fragments/${id}`)
}

/**
 * 获取片段分类列表
 */
export function getFragmentCategories() {
  return request.get('/fragments/categories')
}

/**
 * 获取片段版本列表
 * @param {number|string} fragmentId - 片段 ID
 */
export function getFragmentVersionList(fragmentId) {
  return request.get(`/fragments/${fragmentId}/versions`)
}

/**
 * 获取片段指定版本详情
 * @param {number|string} fragmentId - 片段 ID
 * @param {number|string} version - 版本号
 */
export function getFragmentVersion(fragmentId, version) {
  return request.get(`/fragments/${fragmentId}/versions/${version}`)
}

/**
 * 回滚片段版本
 * @param {number|string} fragmentId - 片段 ID
 * @param {number|string} version - 版本号
 */
export function rollbackFragmentVersion(fragmentId, version) {
  return request.post(`/fragments/${fragmentId}/versions/${version}/rollback`)
}

/**
 * 对比片段两个版本
 * @param {number|string} fragmentId - 片段 ID
 * @param {number|string} v1 - 版本号 1
 * @param {number|string} v2 - 版本号 2
 */
export function compareFragmentVersions(fragmentId, v1, v2) {
  return request.post(`/fragments/${fragmentId}/compare`, null, { params: { v1, v2 } })
}

/**
 * 预览片段
 * @param {number|string} fragmentId - 片段 ID
 */
export function previewFragment(fragmentId) {
  return request.get(`/fragments/${fragmentId}/preview`)
}

// ==================== 编排 API ====================

/**
 * 获取模板编排
 * @param {number|string} templateId - 模板 ID
 */
export function getComposition(templateId) {
  return request.get(`/templates/${templateId}/composition`)
}

/**
 * 保存模板编排
 * @param {number|string} templateId - 模板 ID
 * @param {Object} data - { items: [...] }
 */
export function saveComposition(templateId, data) {
  return request.put(`/templates/${templateId}/composition`, data)
}

/**
 * 预览编排文档
 * @param {number|string} templateId - 模板 ID
 */
export function previewComposition(templateId) {
  return request.post(`/templates/${templateId}/composition/preview`)
}

/**
 * 生成编排文档
 * @param {number|string} templateId - 模板 ID
 */
export function generateComposition(templateId) {
  return request.post(`/templates/${templateId}/composition/generate`, null, { responseType: 'blob' })
}

// ==================== 导出 API ====================

/**
 * 文档导出
 * @param {string} format - 导出格式 (json, excel 等)
 */
export function exportDocuments(format) {
  return request.get('/documents/export', { params: { format }, responseType: 'blob' })
}

/**
 * 模板导出
 * @param {string} format - 导出格式 (json, excel 等)
 */
export function exportTemplates(format) {
  return request.get('/templates/export', { params: { format }, responseType: 'blob' })
}

// ==================== 编辑器文件下载 API ====================

/**
 * 下载编辑器文件
 * @param {string} fileKey - 文件标识
 */
export function downloadEditorFile(fileKey) {
  return request.get(`/editor/download/${fileKey}`, { responseType: 'blob' })
}

export default request
