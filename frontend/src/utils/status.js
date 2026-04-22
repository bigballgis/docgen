/**
 * 获取文档状态对应的 Element Plus tag 类型
 */
export function statusType(status) {
  const map = {
    completed: 'success',
    processing: 'warning',
    generating: 'warning',
    failed: 'danger',
    draft: 'info',
    pending: 'warning',
    approved: 'success',
    rejected: 'danger',
    published: 'success'
  }
  return map[status] || 'info'
}

/**
 * 获取文档状态的显示标签（需要传入 t 函数）
 */
export function statusLabel(status, t) {
  const keyMap = {
    completed: 'document.completed',
    processing: 'document.generating',
    generating: 'document.generating',
    failed: 'document.failed',
    draft: 'template.status.draft',
    pending: 'template.status.pending',
    approved: 'template.status.approved',
    rejected: 'template.status.rejected',
    published: 'template.status.published'
  }
  const key = keyMap[status]
  return key ? t(key) : status
}
