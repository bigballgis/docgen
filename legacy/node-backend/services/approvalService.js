const db = require('../db/init');
const templateDb = require('../db/templates');
const logger = require('../utils/logger');

/**
 * 模板审批服务
 */

/**
 * 提交审批（draft -> pending）
 * @param {number} templateId - 模板 ID
 * @param {number} userId - 提交人 ID
 * @returns {Object} 更新后的模板信息
 */
function submitForApproval(templateId, userId) {
  const template = templateDb.getTemplateById(templateId);
  if (!template) {
    throw new Error('模板不存在');
  }

  if (template.status !== 'draft' && template.status !== 'rejected') {
    throw new Error(`当前状态不允许提交审批，当前状态: ${template.status}`);
  }

  // 更新模板状态为 pending
  templateDb.updateTemplateStatus(templateId, 'pending');

  // 记录审批历史
  const stmt = db.prepare(
    'INSERT INTO template_approvals (template_id, action, reviewer_id, comment, status) VALUES (?, ?, ?, ?, ?)'
  );
  stmt.run(templateId, 'submit', userId, '提交审批', 'approved');

  logger.info('模板已提交审批', { templateId, userId });

  return templateDb.getTemplateById(templateId);
}

/**
 * 审批通过（pending -> published）
 * @param {number} templateId - 模板 ID
 * @param {number} reviewerId - 审批人 ID
 * @param {string} [comment] - 审批意见
 * @returns {Object} 更新后的模板信息
 */
function approveTemplate(templateId, reviewerId, comment = '') {
  const template = templateDb.getTemplateById(templateId);
  if (!template) {
    throw new Error('模板不存在');
  }

  if (template.status !== 'pending') {
    throw new Error(`当前状态不允许审批，当前状态: ${template.status}`);
  }

  const now = new Date().toISOString();

  // 更新模板状态为 published，版本号 +1
  const newVersion = (template.version || 1) + 1;
  templateDb.updateTemplateStatus(templateId, 'published', {
    version: newVersion,
    published_at: now,
    approved_by: reviewerId,
    approved_at: now,
  });

  // 记录审批历史
  const stmt = db.prepare(
    'INSERT INTO template_approvals (template_id, action, reviewer_id, comment, status) VALUES (?, ?, ?, ?, ?)'
  );
  stmt.run(templateId, 'approve', reviewerId, comment, 'approved');

  logger.info('模板审批通过', { templateId, reviewerId, version: newVersion });

  return templateDb.getTemplateById(templateId);
}

/**
 * 审批驳回（pending -> rejected）
 * @param {number} templateId - 模板 ID
 * @param {number} reviewerId - 审批人 ID
 * @param {string} reason - 驳回原因
 * @returns {Object} 更新后的模板信息
 */
function rejectTemplate(templateId, reviewerId, reason) {
  const template = templateDb.getTemplateById(templateId);
  if (!template) {
    throw new Error('模板不存在');
  }

  if (template.status !== 'pending') {
    throw new Error(`当前状态不允许驳回，当前状态: ${template.status}`);
  }

  if (!reason) {
    throw new Error('请提供驳回原因');
  }

  // 更新模板状态为 rejected
  templateDb.updateTemplateStatus(templateId, 'rejected', {
    reject_reason: reason,
  });

  // 记录审批历史
  const stmt = db.prepare(
    'INSERT INTO template_approvals (template_id, action, reviewer_id, comment, status) VALUES (?, ?, ?, ?, ?)'
  );
  stmt.run(templateId, 'reject', reviewerId, reason, 'rejected');

  logger.info('模板审批驳回', { templateId, reviewerId, reason });

  return templateDb.getTemplateById(templateId);
}

/**
 * 获取待审批列表
 * @param {string} [tenantId] - 租户 ID
 * @returns {Array} 待审批的模板列表
 */
function getPendingApprovals(tenantId) {
  return templateDb.getPendingTemplates(tenantId);
}

/**
 * 获取模板的审批历史
 * @param {number} templateId - 模板 ID
 * @returns {Array} 审批历史列表
 */
function getApprovalHistory(templateId) {
  const stmt = db.prepare(
    'SELECT * FROM template_approvals WHERE template_id = ? ORDER BY created_at DESC'
  );
  return stmt.all(templateId);
}

module.exports = {
  submitForApproval,
  approveTemplate,
  rejectTemplate,
  getPendingApprovals,
  getApprovalHistory,
};
