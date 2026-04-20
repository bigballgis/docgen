const express = require('express');
const db = require('../db/init');
const { requireAuth, requireRole, auditLog } = require('../middleware/auth');
const { resolveTenant } = require('../middleware/tenant');
const { success, error } = require('../utils/response');
const logger = require('../utils/logger');

const router = express.Router();

/**
 * GET /api/v1/tenants
 * 获取租户列表（仅 admin）
 */
router.get('/', requireAuth, requireRole('admin'), (req, res) => {
  try {
    const { page, size } = req.query;
    const pageVal = parseInt(page, 10) || 0;
    const sizeVal = parseInt(size, 10) || 20;
    const offset = pageVal * sizeVal;

    const countStmt = db.prepare('SELECT COUNT(*) as total FROM tenants');
    const { total } = countStmt.get();

    const dataStmt = db.prepare('SELECT * FROM tenants ORDER BY created_at DESC LIMIT ? OFFSET ?');
    const content = dataStmt.all(sizeVal, offset);

    return success(res, {
      content,
      totalElements: total,
      totalPages: Math.ceil(total / sizeVal),
    });
  } catch (err) {
    logger.error('获取租户列表失败', { error: err.message });
    return error(res, 500, err.message || '获取租户列表失败');
  }
});

/**
 * POST /api/v1/tenants
 * 创建租户（仅 admin）
 */
router.post('/', requireAuth, requireRole('admin'), auditLog('CREATE_TENANT', 'tenant'), (req, res) => {
  try {
    const { name, code, config: tenantConfig } = req.body;

    if (!name || !code) {
      return error(res, 400, '请提供租户名称和编码');
    }

    // 检查编码是否已存在
    const existing = db.prepare('SELECT id FROM tenants WHERE code = ?').get(code);
    if (existing) {
      return error(res, 400, '租户编码已存在');
    }

    // 检查名称是否已存在
    const existingName = db.prepare('SELECT id FROM tenants WHERE name = ?').get(name);
    if (existingName) {
      return error(res, 400, '租户名称已存在');
    }

    const configStr = tenantConfig ? JSON.stringify(tenantConfig) : null;
    const stmt = db.prepare(
      'INSERT INTO tenants (name, code, config) VALUES (?, ?, ?)'
    );
    const result = stmt.run(name, code, configStr);

    const tenant = db.prepare('SELECT * FROM tenants WHERE id = ?').get(result.lastInsertRowid);
    return success(res, tenant, '租户创建成功');
  } catch (err) {
    logger.error('创建租户失败', { error: err.message });
    return error(res, 500, err.message || '创建租户失败');
  }
});

/**
 * PUT /api/v1/tenants/:id
 * 更新租户（仅 admin）
 */
router.put('/:id', requireAuth, requireRole('admin'), auditLog('UPDATE_TENANT', 'tenant'), (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const { name, status, config: tenantConfig } = req.body;

    const tenant = db.prepare('SELECT * FROM tenants WHERE id = ?').get(id);
    if (!tenant) {
      return error(res, 404, '租户不存在');
    }

    const setClauses = [];
    const params = [];

    if (name) {
      setClauses.push('name = ?');
      params.push(name);
    }
    if (status) {
      const allowedStatuses = ['active', 'inactive'];
      if (!allowedStatuses.includes(status)) {
        return error(res, 400, `无效的状态，允许的状态: ${allowedStatuses.join(', ')}`);
      }
      setClauses.push('status = ?');
      params.push(status);
    }
    if (tenantConfig !== undefined) {
      setClauses.push('config = ?');
      params.push(tenantConfig ? JSON.stringify(tenantConfig) : null);
    }

    if (setClauses.length === 0) {
      return error(res, 400, '没有需要更新的字段');
    }

    setClauses.push('updated_at = CURRENT_TIMESTAMP');
    params.push(id);

    db.prepare(`UPDATE tenants SET ${setClauses.join(', ')} WHERE id = ?`).run(...params);

    const updated = db.prepare('SELECT * FROM tenants WHERE id = ?').get(id);
    return success(res, updated, '租户更新成功');
  } catch (err) {
    logger.error('更新租户失败', { error: err.message });
    return error(res, 500, err.message || '更新租户失败');
  }
});

/**
 * GET /api/v1/tenants/current
 * 获取当前租户信息
 */
router.get('/current', resolveTenant, (req, res) => {
  try {
    const tenantId = req.tenantId || 'default';
    const tenant = db.prepare('SELECT * FROM tenants WHERE code = ?').get(tenantId);

    if (!tenant) {
      // 如果租户不存在，返回默认租户信息
      return success(res, {
        id: 0,
        name: '默认租户',
        code: 'default',
        status: 'active',
      });
    }

    // 解析 config JSON
    if (tenant.config) {
      try {
        tenant.config = JSON.parse(tenant.config);
      } catch (e) {
        tenant.config = null;
      }
    }

    return success(res, tenant);
  } catch (err) {
    logger.error('获取当前租户信息失败', { error: err.message });
    return error(res, 500, err.message || '获取当前租户信息失败');
  }
});

module.exports = router;
