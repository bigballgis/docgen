const router = require('express').Router();
const { requireAuth, requireRole } = require('../middleware/auth');
const versionService = require('../services/versionService');
const { success, error } = require('../utils/response');
const logger = require('../utils/logger');

// 获取模板版本列表
router.get('/templates/:templateId/versions', requireAuth, (req, res) => {
  try {
    const versions = versionService.getVersionList(req.params.templateId);
    return success(res, versions);
  } catch (err) {
    logger.error('获取版本列表失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

// 对比两个版本（返回 HTML diff）
// 注意：此路由必须在 :version 路由之前，因为 compare 是固定路径
router.get('/templates/:templateId/versions/compare', requireAuth, async (req, res) => {
  try {
    const { v1, v2 } = req.query;
    if (!v1 || !v2) {
      return error(res, 400, '请指定 v1 和 v2 参数');
    }
    const result = await versionService.compareVersions(
      req.params.templateId,
      parseInt(v1),
      parseInt(v2)
    );
    return success(res, result);
  } catch (err) {
    logger.error('对比版本失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

// 获取版本详情
router.get('/templates/:templateId/versions/:version', requireAuth, (req, res) => {
  try {
    const version = versionService.getVersion(
      req.params.templateId,
      parseInt(req.params.version)
    );
    if (!version) {
      return error(res, 404, '版本不存在');
    }
    return success(res, version);
  } catch (err) {
    logger.error('获取版本详情失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

// 获取版本 HTML 预览
router.get('/templates/:templateId/versions/:version/preview', requireAuth, async (req, res) => {
  try {
    const result = await versionService.getVersionHtml(
      req.params.templateId,
      parseInt(req.params.version)
    );
    return success(res, result);
  } catch (err) {
    logger.error('获取版本预览失败', { error: err.message });
    return error(res, 500, err.message);
  }
});

// 回滚到指定版本（仅 admin）
router.post(
  '/templates/:templateId/versions/:version/rollback',
  requireAuth,
  requireRole('admin'),
  async (req, res) => {
    try {
      const result = await versionService.rollbackToVersion(
        req.params.templateId,
        parseInt(req.params.version),
        req.user.id
      );
      return success(res, result, '回滚成功');
    } catch (err) {
      logger.error('回滚版本失败', { error: err.message });
      return error(res, 500, err.message);
    }
  }
);

module.exports = router;
