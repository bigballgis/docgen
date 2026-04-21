const logger = require('../utils/logger');

/**
 * 多租户中间件
 * 从 JWT token 或请求头中提取租户信息
 */

/**
 * 解析当前请求的租户 ID
 * 优先级：X-Tenant-Id header > JWT payload > 默认 'default'
 */
function resolveTenant(req, res, next) {
  // 优先从 header X-Tenant-Id 获取
  const headerTenantId = req.headers['x-tenant-id'];
  if (headerTenantId) {
    req.tenantId = headerTenantId;
    logger.debug('租户从 header 解析', { tenantId: headerTenantId });
    return next();
  }

  // 其次从 JWT payload 中获取
  if (req.user && req.user.tenantId) {
    req.tenantId = req.user.tenantId;
    logger.debug('租户从 JWT 解析', { tenantId: req.user.tenantId });
    return next();
  }

  // 最后使用默认租户
  req.tenantId = 'default';
  logger.debug('使用默认租户');
  next();
}

module.exports = {
  resolveTenant,
};
