const jwt = require('jsonwebtoken');
const config = require('../config');
const authService = require('../services/authService');
const auditService = require('../services/auditService');
const logger = require('../utils/logger');

// Token 黑名单（存储已注销的 token）
const tokenBlacklist = new Set();

/**
 * JWT 认证中间件
 * 支持可选模式：如果请求未携带 token，不会拦截，而是将 req.user 设为 null
 */

/**
 * 必须认证中间件 — 未携带 token 则返回 401
 */
function requireAuth(req, res, next) {
  const token = extractToken(req);
  if (!token) {
    return res.status(401).json({ code: 401, message: '未提供认证令牌' });
  }

  // 检查 token 是否在黑名单中
  if (tokenBlacklist.has(token)) {
    return res.status(401).json({ code: 401, message: '令牌已被注销' });
  }

  try {
    const decoded = jwt.verify(token, config.jwtSecret);
    const user = authService.getUserById(decoded.userId);
    if (!user) {
      return res.status(401).json({ code: 401, message: '用户不存在' });
    }
    // 检查用户状态
    if (user.status && user.status === 'disabled') {
      return res.status(403).json({ code: 403, message: '用户已被禁用' });
    }
    req.user = user;
    next();
  } catch (err) {
    if (err.name === 'TokenExpiredError') {
      return res.status(401).json({ code: 401, message: '令牌已过期' });
    }
    return res.status(401).json({ code: 401, message: '无效的认证令牌' });
  }
}

/**
 * 可选认证中间件 — 有 token 则解析，无 token 则放行（req.user = null）
 */
function optionalAuth(req, res, next) {
  const token = extractToken(req);
  if (!token) {
    req.user = null;
    return next();
  }

  // 检查 token 是否在黑名单中
  if (tokenBlacklist.has(token)) {
    req.user = null;
    return next();
  }

  try {
    const decoded = jwt.verify(token, config.jwtSecret);
    const user = authService.getUserById(decoded.userId);
    // 检查用户状态
    if (user && user.status && user.status === 'disabled') {
      req.user = null;
    } else {
      req.user = user || null;
    }
  } catch (err) {
    // token 无效时静默忽略，视为未登录
    req.user = null;
  }
  next();
}

/**
 * RBAC 角色校验中间件工厂
 * @param {...string} roles - 允许的角色列表
 * @returns {Function} Express 中间件
 */
function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.user) {
      return res.status(401).json({ code: 401, message: '未提供认证令牌' });
    }
    if (!roles.includes(req.user.role)) {
      logger.warn('权限不足', {
        userId: req.user.id,
        username: req.user.username,
        role: req.user.role,
        requiredRoles: roles,
        path: req.originalUrl,
      });
      return res.status(403).json({ code: 403, message: '权限不足，需要以下角色之一: ' + roles.join(', ') });
    }
    next();
  };
}

/**
 * 审计日志中间件工厂
 * @param {string} action - 操作名称（如 'CREATE_TEMPLATE', 'DELETE_USER'）
 * @param {string} resource - 资源类型（如 'template', 'user'）
 * @param {Function} [resourceIdExtractor] - 从 req 中提取 resourceId 的函数，默认从 req.params.id 获取
 * @returns {Function} Express 中间件
 */
function auditLog(action, resource, resourceIdExtractor) {
  return (req, res, next) => {
    // 在响应完成后记录审计日志
    const originalEnd = res.end;
    res.end = function (...args) {
      res.end = originalEnd;
      res.end.apply(res, args);

      // 仅在成功响应时记录（2xx）
      if (res.statusCode >= 200 && res.statusCode < 300 && req.user) {
        const resourceId = resourceIdExtractor
          ? resourceIdExtractor(req)
          : (req.params.id || null);

        try {
          auditService.logAudit({
            userId: req.user.id,
            username: req.user.username,
            action,
            resource,
            resourceId: resourceId ? String(resourceId) : null,
            req,
          });
        } catch (err) {
          logger.error('审计日志记录失败', { error: err.message, action, resource });
        }
      }
    };
    next();
  };
}

/**
 * 从请求中提取 JWT token
 */
function extractToken(req) {
  // 优先从 Authorization header 获取
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith('Bearer ')) {
    return authHeader.substring(7);
  }

  // 其次从查询参数获取
  if (req.query && req.query.token) {
    return req.query.token;
  }

  return null;
}

/**
 * 生成 JWT token
 * @param {Object} user - 用户信息
 * @returns {string} JWT token
 */
function generateToken(user) {
  const payload = {
    userId: user.id,
    username: user.username,
    role: user.role,
    tenantId: user.tenant_id || 'default',
  };
  return jwt.sign(payload, config.jwtSecret, { 
    expiresIn: config.jwtExpiresIn,
    algorithm: 'HS256'
  });
}

/**
 * 注销 token（添加到黑名单）
 * @param {string} token - JWT token
 */
function revokeToken(token) {
  if (token) {
    tokenBlacklist.add(token);
    // 清理过期的 token（可选，定期执行）
    cleanupBlacklist();
  }
}

/**
 * 清理黑名单中过期的 token
 */
function cleanupBlacklist() {
  const now = Date.now();
  const tokensToRemove = [];
  
  for (const token of tokenBlacklist) {
    try {
      const decoded = jwt.decode(token);
      if (decoded && decoded.exp) {
        const exp = decoded.exp * 1000;
        if (exp < now) {
          tokensToRemove.push(token);
        }
      }
    } catch (err) {
      // 无效 token，直接移除
      tokensToRemove.push(token);
    }
  }
  
  for (const token of tokensToRemove) {
    tokenBlacklist.delete(token);
  }
}

// 定期清理黑名单（每小时）
setInterval(cleanupBlacklist, 60 * 60 * 1000);

module.exports = {
  requireAuth,
  optionalAuth,
  requireRole,
  auditLog,
  generateToken,
  revokeToken,
};