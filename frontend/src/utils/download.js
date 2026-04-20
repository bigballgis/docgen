/**
 * 文件下载工具函数
 */

/**
 * 从 Blob 创建下载链接并触发浏览器下载
 * @param {Blob} blob - 文件 Blob 对象
 * @param {string} fileName - 下载的文件名
 */
export function downloadBlob(blob, fileName) {
  if (!blob) {
    console.error('downloadBlob: blob 参数不能为空')
    return
  }

  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.style.display = 'none'
  link.setAttribute('download', fileName || 'download')

  document.body.appendChild(link)
  link.click()

  // 延迟释放，确保下载已触发
  setTimeout(() => {
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  }, 100)
}

/**
 * 从响应头 Content-Disposition 中解析文件名
 * 支持格式：
 *   - attachment; filename="xxx.docx"
 *   - attachment; filename*=UTF-8''xxx.docx
 *   - attachment; filename=xxx.docx
 * @param {Object} response - Axios 响应对象
 * @returns {string} 解析出的文件名，默认为 'download'
 */
export function getContentDispositionFileName(response) {
  const disposition = response?.headers?.['content-disposition']
  if (!disposition) {
    return 'download'
  }

  let fileName = 'download'

  // 尝试匹配 filename*=UTF-8''xxx 格式（RFC 5987 编码）
  const utf8Match = disposition.match(/filename\*=(?:UTF-8''|utf-8'')(.+)/i)
  if (utf8Match) {
    fileName = decodeURIComponent(utf8Match[1].replace(/["']/g, '').trim())
    return fileName
  }

  // 尝试匹配 filename="xxx" 格式
  const quotedMatch = disposition.match(/filename="([^"]+)"/i)
  if (quotedMatch) {
    fileName = quotedMatch[1].trim()
    return fileName
  }

  // 尝试匹配 filename=xxx 格式（无引号）
  const plainMatch = disposition.match(/filename=([^\s;]+)/i)
  if (plainMatch) {
    fileName = plainMatch[1].trim().replace(/"/g, '')
    return fileName
  }

  return fileName
}
