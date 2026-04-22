/**
 * 统一提取分页列表数据
 */
export function extractList(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  return data.content || data.list || data.records || []
}

/**
 * 统一提取分页总数
 */
export function extractTotal(data) {
  if (!data) return 0
  return data.totalElements || data.total || data.totalCount || 0
}

/**
 * 统一提取分页信息
 */
export function extractPages(data) {
  if (!data) return { page: 0, size: 20, total: 0 }
  return {
    page: data.number ?? data.pageNum ?? 0,
    size: data.size ?? data.pageSize ?? 20,
    total: data.totalElements || data.total || data.totalCount || 0
  }
}
