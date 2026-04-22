const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const dbModule = require('../db/init');
const templateService = require('../services/templateService');
const approvalService = require('../services/approvalService');
const { requireAuth, optionalAuth, requireRole, auditLog } = require('../middleware/auth');
const { resolveTenant } = require('../middleware/tenant');
const { success, error } = require('../utils/response');
const config = require('../config');
const logger = require('../utils/logger');

const router = express.Router();

// 确保 upload 目录存在
if (!fs.existsSync(config.uploadDir)) {
  fs.mkdirSync(config.uploadDir, { recursive: true });
}

// 配置 multer 存储
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, config.uploadDir);
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname);
    cb(null, `${Date.now()}-${Math.random().toString(36).substring(2, 8)}${ext}`);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: config.maxFileSize },
  fileFilter: (req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    if (ext === '.docx') {
      cb(null, true);
    } else {
      cb(new Error('仅支持 .docx 格式的文件'));
    }
  },
});

// 所有模板路由应用租户中间件
router.use(resolveTenant);

/**
 * POST /api/templates/upload
 * 上传模板（需要登录）
 */
router.post('/upload', requireAuth, auditLog('CREATE_TEMPLATE', 'template'), upload.single('file'), (req, res) => {
  try {
    if (!req.file) {
      return error(res, 400, '请上传文件');
    }

    const { name, description, category, fields, changeNote } = req.body;

    if (!name) {
      // 删除已上传的文件
      fs.unlinkSync(req.file.path);
      return error(res, 400, '请输入模板名称');
    }

    const userId = req.user ? req.user.id : null;
    const tenantId = req.tenantId || 'default';
    const template = templateService.uploadTemplate(
      req.file,
      name,
      description || '',
      category || '',
      fields || '',
      userId,
      tenantId,
      changeNote || ''
    );

    // 解析 fields 返回给前端
    try {
      template.fields = JSON.parse(template.fields);
    } catch (e) {
      template.fields = [];
    }

    return success(res, template, 'success');
  } catch (err) {
    logger.error('上传模板失败', { error: err.message });
    return error(res, 500, err.message || '上传模板失败');
  }
});

/**
 * GET /api/templates/pending
 * 获取待审批列表（仅 admin）
 */
router.get('/pending', requireAuth, requireRole('admin'), (req, res) => {
  try {
    const tenantId = req.tenantId || 'default';
    const pendingList = approvalService.getPendingApprovals(tenantId);

    // 解析每个模板的 fields
    const content = pendingList.map((t) => {
      try {
        t.fields = JSON.parse(t.fields);
      } catch (e) {
        t.fields = [];
      }
      return t;
    });

    return success(res, { content, totalElements: content.length });
  } catch (err) {
    logger.error('获取待审批列表失败', { error: err.message });
    return error(res, 500, err.message || '获取待审批列表失败');
  }
});

/**
 * GET /api/templates
 * 获取模板列表
 */
router.get('/', optionalAuth, (req, res) => {
  try {
    const { keyword, category, page, size, status } = req.query;
    const tenantId = req.tenantId || 'default';
    const isAdmin = req.user && req.user.role === 'admin';
    const result = templateService.getTemplateList({
      keyword,
      category,
      page,
      size,
      tenantId,
      status,
      showAll: isAdmin,
    });

    // 解析每个模板的 fields
    result.content = result.content.map((t) => {
      try {
        t.fields = JSON.parse(t.fields);
      } catch (e) {
        t.fields = [];
      }
      return t;
    });

    return success(res, result);
  } catch (err) {
    logger.error('获取模板列表失败', { error: err.message });
    return error(res, 500, err.message || '获取模板列表失败');
  }
});

/**
 * GET /api/templates/categories
 * 获取所有分类
 */
router.get('/categories', optionalAuth, (req, res) => {
  try {
    const tenantId = req.tenantId || 'default';
    const categories = templateService.getAllCategories(tenantId);
    return success(res, categories);
  } catch (err) {
    logger.error('获取分类失败', { error: err.message });
    return error(res, 500, err.message || '获取分类失败');
  }
});

/**
 * POST /api/templates/:id/submit
 * 提交审批（draft -> pending）
 */
router.post('/:id/submit', requireAuth, auditLog('SUBMIT_TEMPLATE_APPROVAL', 'template'), (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const userId = req.user ? req.user.id : null;
    const result = approvalService.submitForApproval(id, userId);
    return success(res, result, '提交审批成功');
  } catch (err) {
    logger.error('提交审批失败', { error: err.message });
    return error(res, 400, err.message || '提交审批失败');
  }
});

/**
 * POST /api/templates/:id/approve
 * 审批通过（pending -> published）仅 admin
 */
router.post('/:id/approve', requireAuth, requireRole('admin'), auditLog('APPROVE_TEMPLATE', 'template'), (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const reviewerId = req.user ? req.user.id : null;
    const { comment } = req.body;
    const result = approvalService.approveTemplate(id, reviewerId, comment);
    return success(res, result, '审批通过');
  } catch (err) {
    logger.error('审批通过失败', { error: err.message });
    return error(res, 400, err.message || '审批通过失败');
  }
});

/**
 * POST /api/templates/:id/reject
 * 审批驳回（pending -> rejected）仅 admin
 */
router.post('/:id/reject', requireAuth, requireRole('admin'), auditLog('REJECT_TEMPLATE', 'template'), (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const reviewerId = req.user ? req.user.id : null;
    const { reason } = req.body;

    if (!reason) {
      return error(res, 400, '请提供驳回原因');
    }

    const result = approvalService.rejectTemplate(id, reviewerId, reason);
    return success(res, result, '审批驳回');
  } catch (err) {
    logger.error('审批驳回失败', { error: err.message });
    return error(res, 400, err.message || '审批驳回失败');
  }
});

/**
 * GET /api/templates/:id
 * 获取模板详情
 */
router.get('/:id', optionalAuth, (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const template = templateService.getTemplateDetail(id);

    if (!template) {
      return error(res, 404, '模板不存在');
    }

    return success(res, template);
  } catch (err) {
    logger.error('获取模板详情失败', { error: err.message });
    return error(res, 500, err.message || '获取模板详情失败');
  }
});

/**
 * DELETE /api/templates/:id
 * 删除模板（仅 admin）
 */
router.delete('/:id', requireAuth, requireRole('admin'), auditLog('DELETE_TEMPLATE', 'template'), (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const deleted = templateService.removeTemplate(id);

    if (!deleted) {
      return error(res, 404, '模板不存在');
    }

    return success(res, null, 'success');
  } catch (err) {
    logger.error('删除模板失败', { error: err.message });
    return error(res, 500, err.message || '删除模板失败');
  }
});

/**
 * POST /api/templates/:id/parse-fields
 * 解析模板中的占位符字段（需要登录）
 */
router.post('/:id/parse-fields', requireAuth, (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const result = templateService.parseTemplateFields(id);

    if (!result) {
      return error(res, 404, '模板不存在');
    }

    return success(res, result);
  } catch (err) {
    logger.error('解析模板字段失败', { error: err.message });
    return error(res, 500, err.message || '解析模板字段失败');
  }
});

/**
 * GET /api/templates/export
 * 导出模板列表
 * 支持格式: csv, json
 */
router.get('/export', requireAuth, (req, res) => {
  try {
    const { format } = req.query;
    const tenantId = req.tenantId || 'default';

    const templates = dbModule.prepare(
      'SELECT * FROM templates WHERE deleted_at IS NULL AND tenant_id = ? ORDER BY created_at DESC'
    ).all(tenantId);

    const exportData = templates.map(t => ({
      id: t.id,
      name: t.name,
      description: t.description,
      category: t.category,
      fileKey: t.file_key,
      fileSize: t.file_size,
      fields: t.fields,
      uploadedBy: t.uploaded_by,
      createdAt: t.created_at,
      updatedAt: t.updated_at,
    }));

    if (format === 'json') {
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.setHeader('Content-Disposition', 'attachment; filename="templates_export.json"');
      return res.json(exportData);
    }

    // 默认 CSV 格式
    const csvHeaders = ['ID', 'Name', 'Description', 'Category', 'FileSize', 'Fields', 'UploadedBy', 'CreatedAt', 'UpdatedAt'];
    const csvRows = exportData.map(t => [
      t.id, t.name, t.description, t.category, t.fileSize, t.fields, t.uploadedBy, t.createdAt, t.updatedAt
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
    res.setHeader('Content-Disposition', 'attachment; filename="templates_export.csv"');
    return res.send('\uFEFF' + csvLines);
  } catch (err) {
    logger.error('导出模板列表失败', { error: err.message });
    return error(res, 500, err.message || '导出模板列表失败');
  }
});

module.exports = router;
