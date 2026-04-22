const path = require('path');
const fs = require('fs');
const config = require('../config');

/**
 * Euro-Office 编辑器服务
 * 当 Euro-Office 不可用时，返回降级提示
 */

/**
 * 获取编辑器配置
 * @param {string} fileKey - 文件标识
 * @returns {Object} 编辑器配置
 */
function getEditorConfig(fileKey) {
  if (!config.euroOffice.enabled || !config.euroOffice.baseUrl) {
    // 降级：Euro-Office 不可用
    return {
      available: false,
      message: '编辑器服务未配置',
      fallback: '当前环境未启用 Euro-Office 编辑器服务，文档编辑功能暂不可用。',
    };
  }

  // Euro-Office 可用时的配置
  const fileUrl = `${config.euroOffice.baseUrl}/files/${fileKey}`;
  const callbackUrl = config.euroOffice.callbackUrl
    ? `${config.euroOffice.callbackUrl}?fileKey=${fileKey}`
    : '';

  return {
    available: true,
    editorUrl: `${config.euroOffice.baseUrl}/editor`,
    fileUrl,
    fileKey,
    callbackUrl,
    apiKey: config.euroOffice.apiKey,
  };
}

/**
 * 处理编辑保存回调
 * @param {Object} body - 回调请求体
 * @returns {Object} 处理结果
 */
function handleEditorCallback(body) {
  const { fileKey, status, downloadUrl, content } = body;

  if (!fileKey) {
    throw new Error('缺少 fileKey 参数');
  }

  console.log(`[Editor] 收到编辑回调: fileKey=${fileKey}, status=${status || 'unknown'}`);

  // 如果有下载链接，可以下载保存到本地
  // 这里只做日志记录，实际保存逻辑可根据需求扩展
  if (downloadUrl) {
    console.log(`[Editor] 下载链接: ${downloadUrl}`);
  }

  return {
    success: true,
    fileKey,
    status: status || 'saved',
    message: '回调处理成功',
  };
}

/**
 * 获取文档下载路径
 * @param {string} fileKey - 文件标识
 * @returns {string|null} 文件路径
 */
function getEditorFileDownloadPath(fileKey) {
  // 先尝试从 outputs 目录查找
  const outputDir = config.outputDir;
  if (fs.existsSync(outputDir)) {
    const files = fs.readdirSync(outputDir);
    const matched = files.find((f) => f.includes(fileKey));
    if (matched) {
      return path.join(outputDir, matched);
    }
  }

  // 再尝试从 uploads 目录查找
  const uploadDir = config.uploadDir;
  if (fs.existsSync(uploadDir)) {
    const files = fs.readdirSync(uploadDir);
    const matched = files.find((f) => f.includes(fileKey));
    if (matched) {
      return path.join(uploadDir, matched);
    }
  }

  return null;
}

module.exports = {
  getEditorConfig,
  handleEditorCallback,
  getEditorFileDownloadPath,
};
