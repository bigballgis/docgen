const express = require('express');
const authService = require('../services/authService');
const auditService = require('../services/auditService');
const { generateToken, requireAuth, requireRole, auditLog } = require('../middleware/auth');
const { success, error } = require('../utils/response');
const logger = require('../utils/logger');

const router = express.Router();

/**
 * POST /api/auth/register
 * 用户注册 — 仅 admin 角色可注册新用户
 */
router.post('/register', requireAuth, requireRole('admin'), auditLog('USER_REGISTER', 'user'), async (req, res) => {
  try {
    const { username, password, role } = req.body;

    if (!username || !password) {
      return error(res, 400, '请提供用户名和密码');
    }

    const user = await authService.register(username, password, role || 'user');
    const token = generateToken(user);

    return success(res, { token, user }, '注册成功');
  } catch (err) {
    logger.error('注册失败', { error: err.message });
    return error(res, 400, err.message || '注册失败');
  }
});

/**
 * POST /api/auth/login
 * 用户登录
 */
router.post('/login', async (req, res) => {
  try {
    const { username, password } = req.body;

    if (!username || !password) {
      return error(res, 400, '请提供用户名和密码');
    }

    const user = await authService.login(username, password);
    const token = generateToken(user);

    // 记录登录审计日志
    try {
      auditService.logAudit({
        userId: user.id,
        username: user.username,
        action: 'USER_LOGIN',
        resource: 'auth',
        resourceId: null,
        req,
      });
    } catch (e) {
      // 审计日志失败不影响登录
    }

    return success(res, { token, user }, '登录成功');
  } catch (err) {
    logger.error('登录失败', { error: err.message });
    return error(res, 401, err.message || '登录失败');
  }
});

/**
 * GET /api/auth/profile
 * 获取当前用户信息（需要认证）
 */
router.get('/profile', requireAuth, (req, res) => {
  try {
    return success(res, req.user);
  } catch (err) {
    logger.error('获取用户信息失败', { error: err.message });
    return error(res, 500, err.message || '获取用户信息失败');
  }
});

/**
 * GET /api/auth/users
 * 获取用户列表（仅 admin）
 */
router.get('/users', requireAuth, requireRole('admin'), (req, res) => {
  try {
    const { page, size } = req.query;
    const result = authService.getUserList({ page, size });
    return success(res, result);
  } catch (err) {
    logger.error('获取用户列表失败', { error: err.message });
    return error(res, 500, err.message || '获取用户列表失败');
  }
});

/**
 * PUT /api/auth/users/:id/role
 * 修改用户角色（仅 admin）
 */
router.put('/users/:id/role', requireAuth, requireRole('admin'), auditLog('UPDATE_USER_ROLE', 'user'), (req, res) => {
  try {
    const userId = parseInt(req.params.id, 10);
    const { role } = req.body;

    if (!role) {
      return error(res, 400, '请提供角色信息');
    }

    const user = authService.updateUserRole(userId, role);
    return success(res, user, '角色修改成功');
  } catch (err) {
    logger.error('修改用户角色失败', { error: err.message });
    return error(res, 400, err.message || '修改用户角色失败');
  }
});

/**
 * PUT /api/auth/users/:id/status
 * 启用/禁用用户（仅 admin）
 */
router.put('/users/:id/status', requireAuth, requireRole('admin'), auditLog('UPDATE_USER_STATUS', 'user'), (req, res) => {
  try {
    const userId = parseInt(req.params.id, 10);
    const { status } = req.body;

    if (!status) {
      return error(res, 400, '请提供状态信息');
    }

    const user = authService.updateUserStatus(userId, status);
    return success(res, user, '状态修改成功');
  } catch (err) {
    logger.error('修改用户状态失败', { error: err.message });
    return error(res, 400, err.message || '修改用户状态失败');
  }
});

/**
 * PUT /api/auth/password
 * 修改密码（需验证旧密码）
 */
router.put('/password', requireAuth, async (req, res) => {
  try {
    const { oldPassword, newPassword } = req.body;

    if (!oldPassword || !newPassword) {
      return error(res, 400, '旧密码和新密码不能为空');
    }

    if (newPassword.length < 6) {
      return error(res, 400, '新密码长度不能少于 6 个字符');
    }

    const result = await authService.changePassword(req.user.id, oldPassword, newPassword);

    return success(res, null, '密码修改成功');
  } catch (err) {
    logger.error('修改密码失败', { error: err.message });
    return error(res, 400, err.message || '修改密码失败');
  }
});

module.exports = router;
