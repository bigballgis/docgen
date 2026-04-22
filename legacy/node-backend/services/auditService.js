const db = require('../db/init');
const logger = require('../utils/logger');

/**
 * 审计日志服务
 */

/**
 * 记录审计日志
 * @param {Object} params
 * @param {number} params.userId - 用户 ID
 * @param {string} params.username - 用户名
 * @param {string} params.action - 操作名称（如 'CREATE_TEMPLATE', 'DELETE_USER'）
 * @param {string} params.resource - 资源类型（如 'template', 'user'）
 * @param {string} [params.resourceId] - 资源 ID
 * @param {Object} params.req - Express 请求对象（用于提取 IP 和 User-Agent）
 */
function logAudit({ userId, username, action, resource, resourceId, req }) {
  try {
    const ip = req ? (req.ip || req.headers['x-forwarded-for'] || req.connection?.remoteAddress || '') : '';
    const userAgent = req ? (req.headers['user-agent'] || '') : '';

    const stmt = db.prepare(`
      INSERT INTO audit_logs (user_id, username, action, resource, resource_id, ip, user_agent)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `);
    stmt.run(userId, username, action, resource, resourceId, ip, userAgent);

    logger.debug('审计日志已记录', { userId, username, action, resource, resourceId });
  } catch (err) {
    logger.error('写入审计日志失败', { error: err.message, action, resource });
  }
}

/**
 * 查询审计日志（分页 + 过滤）
 * @param {Object} params
 * @param {number} [params.page=0] - 页码（从 0 开始）
 * @param {number} [params.size=20] - 每页数量
 * @param {number} [params.userId] - 按用户 ID 过滤
 * @param {string} [params.action] - 按操作名称过滤
 * @param {string} [params.startDate] - 起始日期（ISO 格式）
 * @param {string} [params.endDate] - 结束日期（ISO 格式）
 * @returns {{ content: Array, totalElements: number, totalPages: number }}
 */
function getAuditLogs({ page, size, userId, action, startDate, endDate }) {
  const pageVal = parseInt(page, 10) || 0;
  const sizeVal = parseInt(size, 10) || 20;
  const offset = pageVal * sizeVal;

  const conditions = [];
  const params = [];

  if (userId) {
    conditions.push('user_id = ?');
    params.push(userId);
  }

  if (action) {
    conditions.push('action = ?');
    params.push(action);
  }

  if (startDate) {
    conditions.push('created_at >= ?');
    params.push(startDate);
  }

  if (endDate) {
    conditions.push('created_at <= ?');
    params.push(endDate);
  }

  const whereClause = conditions.length > 0 ? 'WHERE ' + conditions.join(' AND ') : '';

  // 查询总数
  const countStmt = db.prepare(`SELECT COUNT(*) as total FROM audit_logs ${whereClause}`);
  const { total } = countStmt.get(...params);

  // 查询分页数据
  const dataStmt = db.prepare(
    `SELECT * FROM audit_logs ${whereClause} ORDER BY created_at DESC LIMIT ? OFFSET ?`
  );
  const content = dataStmt.all(...params, sizeVal, offset);

  return {
    content,
    totalElements: total,
    totalPages: Math.ceil(total / sizeVal),
  };
}

module.exports = {
  logAudit,
  getAuditLogs,
};
