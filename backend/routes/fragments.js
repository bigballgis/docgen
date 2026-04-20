const router = require('express').Router();
const { requireAuth, requireRole, auditLog } = require('../middleware/auth');
const { resolveTenant } = require('../middleware/tenant');
const fragmentService = require('../services/fragmentService');
const euroOfficeCompareService = require('../services/euroOfficeCompareService');
const { success, error } = require('../utils/response');
const logger = require('../utils/logger');

// 所有片段路由应用租户中间件
router.use(resolveTenant);

/**
 * GET /api/v1/fragments
 * 获取片段列表
 */
router.get('/', (req, res) => {
  try {
    const { keyword, category, tags, status, page, size } = req.query;
    const result = fragmentService.getFragmentList({
      keyword, category, tags, status,
      page: parseInt(page, 10) || 0,
      size: parseInt(size, 10) || 20,
      tenantId: req.tenantId || 'default',
    });
    return success(res, result);
  } catch (err) {
    logger.error('获取片段列表失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * GET /api/v1/fragments/categories
 * 获取所有分类
 */
router.get('/categories', requireAuth, (req, res) => {
  try {
    const categories = fragmentService.getAllCategories(req.tenantId || 'default');
    return success(res, categories);
  } catch (err) {
    logger.error('获取片段分类失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * GET /api/v1/fragments/:id
 * 获取片段详情
 */
router.get('/:id', (req, res) => {
  try {
    const fragment = fragmentService.getFragment(parseInt(req.params.id, 10));
    if (!fragment) return error(res, 404, '片段不存在');
    return success(res, fragment);
  } catch (err) {
    logger.error('获取片段详情失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * POST /api/v1/fragments
 * 创建片段
 */
router.post('/', requireAuth, auditLog('CREATE_FRAGMENT', 'fragment'), (req, res) => {
  try {
    const { name, description, category, contentHtml, tags } = req.body;
    if (!name) return error(res, 400, '请输入片段名称');
    if (!contentHtml) return error(res, 400, '请输入片段内容');

    const fragment = fragmentService.createFragment({
      name,
      description: description || '',
      category: category || '',
      contentHtml,
      tags: tags || [],
      tenantId: req.tenantId || 'default',
      userId: req.user ? req.user.id : null,
    });
    return success(res, fragment, '片段创建成功');
  } catch (err) {
    logger.error('创建片段失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * PUT /api/v1/fragments/:id
 * 更新片段（内容变化时自动创建新版本）
 */
router.put('/:id', requireAuth, auditLog('UPDATE_FRAGMENT', 'fragment'), (req, res) => {
  try {
    const { name, description, category, contentHtml, tags, status, changeNote } = req.body;
    const fragment = fragmentService.updateFragment(parseInt(req.params.id, 10), {
      name, description, category, contentHtml, tags, status, changeNote,
      userId: req.user ? req.user.id : null,
    });
    return success(res, fragment, '片段更新成功');
  } catch (err) {
    logger.error('更新片段失败', { error: err.message });
    return error(res, err.message.includes('不存在') ? 404 : 500, err.message);
  }
});

/**
 * DELETE /api/v1/fragments/:id
 * 删除片段（软删除）
 */
router.delete('/:id', requireAuth, requireRole('admin'), auditLog('DELETE_FRAGMENT', 'fragment'), (req, res) => {
  try {
    const deleted = fragmentService.deleteFragment(parseInt(req.params.id, 10));
    if (!deleted) return error(res, 404, '片段不存在');
    return success(res, null, '片段删除成功');
  } catch (err) {
    logger.error('删除片段失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * GET /api/v1/fragments/:id/versions
 * 获取片段版本列表
 */
router.get('/:id/versions', requireAuth, (req, res) => {
  try {
    const versions = fragmentService.getFragmentVersionList(parseInt(req.params.id, 10));
    return success(res, versions);
  } catch (err) {
    logger.error('获取片段版本列表失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * GET /api/v1/fragments/:id/versions/:ver
 * 获取片段特定版本
 */
router.get('/:id/versions/:ver', requireAuth, (req, res) => {
  try {
    const version = fragmentService.getFragmentVersion(
      parseInt(req.params.id, 10),
      parseInt(req.params.ver, 10)
    );
    if (!version) return error(res, 404, '版本不存在');
    return success(res, version);
  } catch (err) {
    logger.error('获取片段版本失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * POST /api/v1/fragments/:id/versions/:ver/rollback
 * 回滚到指定版本
 */
router.post('/:id/versions/:ver/rollback', requireAuth, requireRole('admin'), auditLog('ROLLBACK_FRAGMENT', 'fragment'), async (req, res) => {
  try {
    const result = fragmentService.rollbackFragmentVersion(
      parseInt(req.params.id, 10),
      parseInt(req.params.ver, 10),
      req.user ? req.user.id : null
    );
    return success(res, result, '回滚成功');
  } catch (err) {
    logger.error('回滚片段版本失败', { error: err.message });
    return error(res, err.message.includes('不存在') ? 404 : 500, err.message);
  }
});

/**
 * POST /api/v1/fragments/:id/compare
 * 对比两个片段版本（优先使用 Euro-Office，降级到 HTML diff）
 */
router.post('/:id/compare', requireAuth, async (req, res) => {
  try {
    const { v1, v2 } = req.query;
    if (!v1 || !v2) return error(res, 400, '请指定 v1 和 v2 参数');

    const result = await euroOfficeCompareService.compareFragmentVersions(
      parseInt(req.params.id, 10),
      parseInt(v1, 10),
      parseInt(v2, 10),
      { fragmentService }
    );
    return success(res, result);
  } catch (err) {
    logger.error('对比片段版本失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

/**
 * GET /api/v1/fragments/:id/preview
 * 预览片段 HTML
 */
router.get('/:id/preview', requireAuth, (req, res) => {
  try {
    const fragment = fragmentService.getFragment(parseInt(req.params.id, 10));
    if (!fragment) return error(res, 404, '片段不存在');
    return success(res, { html: fragment.content_html });
  } catch (err) {
    logger.error('预览片段失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

module.exports = router;
