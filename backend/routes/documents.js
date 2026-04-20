const express = require('express');
const path = require('path');
const dbModule = require('../db/init');
const documentService = require('../services/documentService');
const kafkaService = require('../services/kafkaService');
const { requireAuth, optionalAuth, auditLog } = require('../middleware/auth');
const { resolveTenant } = require('../middleware/tenant');
const { success, error } = require('../utils/response');
const logger = require('../utils/logger');

const router = express.Router();

// 所有文档路由应用租户中间件
router.use(resolveTenant);

/**
 * POST /api/documents/generate
 * 生成文档（异步模式）
 * - Kafka 可用：发送到 Kafka，返回 { taskId, status: 'pending' }
 * - Kafka 不可用：同步生成，返回文件（兼容现有行为）
 */
router.post('/generate', requireAuth, auditLog('GENERATE_DOCUMENT', 'document'), async (req, res) => {
  try {
    const { templateId, data, outputFormat } = req.body;

    if (!templateId) {
      return error(res, 400, '请指定模板 ID');
    }
    if (!data || typeof data !== 'object') {
      return error(res, 400, '请提供填充数据');
    }

    const format = outputFormat || 'docx';
    const userId = req.user ? req.user.id : null;
    const tenantId = req.tenantId || 'default';

    // 如果 Kafka 可用，使用异步模式
    if (kafkaService.isKafkaConnected()) {
      const taskId = kafkaService.generateTaskId();
      const task = {
        taskId,
        templateId,
        data,
        outputFormat: format,
        userId,
        tenantId,
      };

      const result = await kafkaService.sendDocumentTask(task);
      return success(res, result, '文档生成任务已提交');
    }

    // Kafka 不可用，同步生成（兼容现有行为）
    const result = await documentService.generateDocument(templateId, data, format, userId, tenantId);

    // 设置响应头，返回文件流
    const ext = path.extname(result.fileName);
    const mimeType = ext === '.pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

    // 对中文文件名进行编码
    const encodedFileName = encodeURIComponent(result.fileName);

    res.setHeader('Content-Type', mimeType);
    res.setHeader('Content-Disposition', `attachment; filename="${encodedFileName}"; filename*=UTF-8''${encodedFileName}`);
    res.setHeader('Content-Length', require('fs').statSync(result.filePath).size);

    // 发送文件
    res.sendFile(result.filePath, (err) => {
      if (err) {
        logger.error('发送文件失败', { error: err.message });
        if (!res.headersSent) {
          error(res, 500, '发送文件失败');
        }
      }
    });
  } catch (err) {
    logger.error('生成文档失败', { error: err.message });
    return error(res, 500, err.message || '生成文档失败');
  }
});

/**
 * POST /api/documents/generate/sync
 * 同步生成文档（直接返回文件）
 */
router.post('/generate/sync', requireAuth, auditLog('GENERATE_DOCUMENT_SYNC', 'document'), async (req, res) => {
  try {
    const { templateId, data, outputFormat } = req.body;

    if (!templateId) {
      return error(res, 400, '请指定模板 ID');
    }
    if (!data || typeof data !== 'object') {
      return error(res, 400, '请提供填充数据');
    }

    const format = outputFormat || 'docx';
    const userId = req.user ? req.user.id : null;
    const tenantId = req.tenantId || 'default';
    const result = await documentService.generateDocument(templateId, data, format, userId, tenantId);

    // 设置响应头，返回文件流
    const ext = path.extname(result.fileName);
    const mimeType = ext === '.pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

    const encodedFileName = encodeURIComponent(result.fileName);

    res.setHeader('Content-Type', mimeType);
    res.setHeader('Content-Disposition', `attachment; filename="${encodedFileName}"; filename*=UTF-8''${encodedFileName}`);
    res.setHeader('Content-Length', require('fs').statSync(result.filePath).size);

    // 发送文件
    res.sendFile(result.filePath, (err) => {
      if (err) {
        logger.error('发送文件失败', { error: err.message });
        if (!res.headersSent) {
          error(res, 500, '发送文件失败');
        }
      }
    });
  } catch (err) {
    logger.error('同步生成文档失败', { error: err.message });
    return error(res, 500, err.message || '同步生成文档失败');
  }
});

/**
 * GET /api/documents/download/:fileName
 * 下载已生成的文档（需要登录）
 */
router.get('/download/:fileName', requireAuth, (req, res) => {
  try {
    const { fileName } = req.params;
    const filePath = documentService.getOutputFilePath(fileName);

    if (!filePath) {
      return error(res, 404, '文件不存在');
    }

    const ext = path.extname(fileName);
    const mimeType = ext === '.pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

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

/**
 * GET /api/documents/list
 * 获取文档历史列表
 */
router.get('/list', optionalAuth, (req, res) => {
  try {
    const { page, size } = req.query;
    const userId = req.user ? req.user.id : null;
    const tenantId = req.tenantId || 'default';
    const result = documentService.getDocumentList({ page, size, userId, tenantId });
    return success(res, result);
  } catch (err) {
    logger.error('获取文档列表失败', { error: err.message });
    return error(res, 500, err.message || '获取文档列表失败');
  }
});

/**
 * GET /api/documents/status/:taskId
 * 查询任务状态
 * - 优先从内存 taskStatusMap 获取（Kafka 异步任务）
 * - 降级从 documents 表获取（同步生成任务）
 */
router.get('/status/:taskId', optionalAuth, (req, res) => {
  try {
    const { taskId } = req.params;

    // 优先从内存 taskStatusMap 获取
    const memoryStatus = kafkaService.getTaskStatus(taskId);
    if (memoryStatus.status !== 'not_found') {
      return success(res, {
        taskId,
        ...memoryStatus,
      });
    }

    // 降级从 documents 表获取
    const result = documentService.getDocumentStatus(taskId);
    return success(res, result);
  } catch (err) {
    logger.error('查询任务状态失败', { error: err.message });
    return error(res, 500, err.message || '查询任务状态失败');
  }
});

/**
 * GET /api/documents/export
 * 导出文档列表
 * 支持格式: csv, json
 * 支持状态过滤: ?status=all|pending|completed|failed
 */
router.get('/export', requireAuth, (req, res) => {
  try {
    const { format, status } = req.query;
    const tenantId = req.tenantId || 'default';
    const userId = req.user ? req.user.id : null;

    let query = 'SELECT * FROM documents WHERE deleted_at IS NULL';
    const params = [];

    // 如果有用户 ID，过滤该用户的文档
    if (userId) {
      query += ' AND user_id = ?';
      params.push(userId);
    }

    // 租户过滤
    if (tenantId) {
      query += ' AND tenant_id = ?';
      params.push(tenantId);
    }

    if (status && status !== 'all') {
      query += ' AND status = ?';
      params.push(status);
    }

    query += ' ORDER BY created_at DESC';

    const documents = dbModule.prepare(query).all(...params);

    const exportData = documents.map(d => ({
      id: d.id,
      taskId: d.task_id,
      templateId: d.template_id,
      templateName: d.template_name,
      outputFormat: d.output_format,
      status: d.status,
      fileKey: d.file_key,
      fileSize: d.file_size,
      errorMessage: d.error_message,
      createdAt: d.created_at,
      completedAt: d.completed_at,
    }));

    if (format === 'json') {
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.setHeader('Content-Disposition', 'attachment; filename="documents_export.json"');
      return res.json(exportData);
    }

    // 默认 CSV 格式
    const csvHeaders = ['ID', 'TaskID', 'TemplateID', 'TemplateName', 'OutputFormat', 'Status', 'FileSize', 'ErrorMessage', 'CreatedAt', 'CompletedAt'];
    const csvRows = exportData.map(d => [
      d.id, d.taskId, d.templateId, d.templateName, d.outputFormat,
      d.status, d.fileSize, d.errorMessage, d.createdAt, d.completedAt
    ]);

    // CSV 转义处理
    const escapeCsv = (val) => {
      if (val === null || val === undefined) return '';
      const str = String(val);
      if (str.includes(',') || str.includes('"') || str.includes('\n')) {
        return `"${str.replace(/"/g, '""')}"`;
      }
      return str;
    };

    const csvLines = [
      csvHeaders.map(escapeCsv).join(','),
      ...csvRows.map(row => row.map(escapeCsv).join(','))
    ].join('\n');

    // 添加 BOM 以支持 Excel 正确识别 UTF-8
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', 'attachment; filename="documents_export.csv"');
    return res.send('\uFEFF' + csvLines);
  } catch (err) {
    logger.error('导出文档列表失败', { error: err.message });
    return error(res, 500, err.message || '导出文档列表失败');
  }
});

module.exports = router;
