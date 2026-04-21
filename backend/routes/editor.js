const express = require('express');
const path = require('path');
const fs = require('fs');
const editorService = require('../services/editorService');
const { requireAuth, optionalAuth, auditLog } = require('../middleware/auth');
const { success, error } = require('../utils/response');
const logger = require('../utils/logger');

const router = express.Router();

/**
 * GET /api/editor/config?fileKey=xxx
 * 获取编辑器配置
 */
router.get('/config', optionalAuth, (req, res) => {
  try {
    const { fileKey } = req.query;

    if (!fileKey) {
      return error(res, 400, '请提供 fileKey 参数');
    }

    const config = editorService.getEditorConfig(fileKey);
    return success(res, config);
  } catch (err) {
    logger.error('获取编辑器配置失败', { error: err.message });
    return error(res, 500, err.message || '获取编辑器配置失败');
  }
});

/**
 * POST /api/editor/callback
 * 接收编辑保存回调（需要认证）
 */
router.post('/callback', requireAuth, auditLog('EDITOR_CALLBACK', 'editor', (req) => req.body?.fileKey), (req, res) => {
  try {
    const result = editorService.handleEditorCallback(req.body);
    return success(res, result, '回调处理成功');
  } catch (err) {
    logger.error('编辑器回调处理失败', { error: err.message });
    return error(res, 400, err.message || '回调处理失败');
  }
});

/**
 * GET /api/editor/download/:fileKey
 * 下载文档（需要登录）
 */
router.get('/download/:fileKey', requireAuth, (req, res) => {
  try {
    const { fileKey } = req.params;
    const filePath = editorService.getEditorFileDownloadPath(fileKey);

    if (!filePath) {
      return error(res, 404, '文件不存在');
    }

    const fileName = path.basename(filePath);
    const ext = path.extname(fileName).toLowerCase();
    let mimeType = 'application/octet-stream';
    if (ext === '.docx') {
      mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
    } else if (ext === '.pdf') {
      mimeType = 'application/pdf';
    }

    const encodedFileName = encodeURIComponent(fileName);

    res.setHeader('Content-Type', mimeType);
    res.setHeader('Content-Disposition', `attachment; filename="${encodedFileName}"; filename*=UTF-8''${encodedFileName}`);

    res.sendFile(filePath, (err) => {
      if (err) {
        logger.error('下载文件失败', { error: err.message });
        if (!res.headersSent) {
          error(res, 500, '下载文件失败');
        }
      }
    });
  } catch (err) {
    logger.error('下载文件失败', { error: err.message });
    return error(res, 500, err.message || '下载文件失败');
  }
});

module.exports = router;
