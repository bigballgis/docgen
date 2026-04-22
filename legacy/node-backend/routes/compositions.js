const router = require('express').Router();
const { requireAuth, auditLog } = require('../middleware/auth');
const { resolveTenant } = require('../middleware/tenant');
const compositionService = require('../services/compositionService');
const { success, error } = require('../utils/response');
const logger = require('../utils/logger');
const fs = require('fs');
const path = require('path');

// 所有编排路由应用租户中间件
router.use(resolveTenant);

/**
 * GET /api/v1/templates/:templateId/composition
 * 获取模板编排
 */
router.get('/:templateId/composition', requireAuth, (req, res) => {
  try {
    const composition = compositionService.getComposition(parseInt(req.params.templateId, 10));
    return success(res, composition);
  } catch (err) {
    logger.error('获取模板编排失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * PUT /api/v1/templates/:templateId/composition
 * 保存完整编排（替换式）
 */
router.put('/:templateId/composition', requireAuth, auditLog('SAVE_COMPOSITION', 'template'), (req, res) => {
  try {
    const { fragments } = req.body;
    if (!Array.isArray(fragments)) return error(res, 400, 'fragments 必须是数组');

    const composition = compositionService.saveComposition(
      parseInt(req.params.templateId, 10),
      fragments
    );
    return success(res, composition, '编排保存成功');
  } catch (err) {
    logger.error('保存模板编排失败', { error: err.message });
    return error(res, err.message.includes('不存在') ? 404 : 500, err.message);
  }
});

/**
 * POST /api/v1/templates/:templateId/composition/fragments
 * 添加片段到模板
 */
router.post('/:templateId/composition/fragments', requireAuth, auditLog('ADD_FRAGMENT_TO_TEMPLATE', 'template'), (req, res) => {
  try {
    const { fragmentId, sortOrder, sectionTitle } = req.body;
    if (!fragmentId) return error(res, 400, '请指定 fragmentId');

    const composition = compositionService.addFragmentToTemplate(
      parseInt(req.params.templateId, 10),
      parseInt(fragmentId, 10),
      sortOrder,
      sectionTitle
    );
    return success(res, composition, '片段添加成功');
  } catch (err) {
    logger.error('添加片段到模板失败', { error: err.message });
    return error(res, err.message.includes('不存在') ? 404 : 400, err.message);
  }
});

/**
 * DELETE /api/v1/templates/:templateId/composition/fragments/:fragmentId
 * 从模板中移除片段
 */
router.delete('/:templateId/composition/fragments/:fragmentId', requireAuth, auditLog('REMOVE_FRAGMENT_FROM_TEMPLATE', 'template'), (req, res) => {
  try {
    const removed = compositionService.removeFragmentFromTemplate(
      parseInt(req.params.templateId, 10),
      parseInt(req.params.fragmentId, 10)
    );
    if (!removed) return error(res, 404, '片段不在该模板编排中');
    return success(res, null, '片段移除成功');
  } catch (err) {
    logger.error('移除片段失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * PUT /api/v1/templates/:templateId/composition/reorder
 * 重排序片段
 */
router.put('/:templateId/composition/reorder', requireAuth, auditLog('REORDER_COMPOSITION', 'template'), (req, res) => {
  try {
    const { fragmentOrders } = req.body;
    if (!Array.isArray(fragmentOrders)) return error(res, 400, 'fragmentOrders 必须是数组');

    const composition = compositionService.reorderFragments(
      parseInt(req.params.templateId, 10),
      fragmentOrders
    );
    return success(res, composition, '排序更新成功');
  } catch (err) {
    logger.error('重排序片段失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * POST /api/v1/templates/:templateId/composition/preview
 * 预览组合文档 HTML
 */
router.post('/:templateId/composition/preview', requireAuth, (req, res) => {
  try {
    const html = compositionService.generateComposedHtml(parseInt(req.params.templateId, 10));
    return success(res, { html });
  } catch (err) {
    logger.error('预览组合文档失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * POST /api/v1/templates/:templateId/composition/generate
 * 生成组合 docx 文件
 */
router.post('/:templateId/composition/generate', requireAuth, (req, res) => {
  try {
    const result = compositionService.generateComposedDocx(parseInt(req.params.templateId, 10));
    if (!fs.existsSync(result.filePath)) {
      return error(res, 500, '文档生成失败');
    }
    res.download(result.filePath, result.fileName);
  } catch (err) {
    logger.error('生成组合文档失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

module.exports = router;
