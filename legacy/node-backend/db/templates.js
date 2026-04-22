const db = require('./init');

/**
 * 模板数据操作层
 */

/**
 * 新增模板
 */
function insertTemplate(template) {
  const stmt = db.prepare(`
    INSERT INTO templates (name, description, file_name, original_file_name, fields, category, user_id, tenant_id)
    VALUES (@name, @description, @file_name, @original_file_name, @fields, @category, @user_id, @tenant_id)
  `);
  const result = stmt.run(template);
  return result.lastInsertRowid;
}

/**
 * 根据 ID 查询模板
 */
function getTemplateById(id) {
  const stmt = db.prepare('SELECT * FROM templates WHERE id = ?');
  return stmt.get(id);
}

/**
 * 根据 ID 和租户查询模板
 */
function getTemplateByIdAndTenant(id, tenantId) {
  const stmt = db.prepare('SELECT * FROM templates WHERE id = ? AND tenant_id = ?');
  return stmt.get(id, tenantId);
}

/**
 * 查询模板列表（分页 + 关键词 + 分类过滤 + 租户过滤 + 状态过滤）
 * @param {Object} params
 * @param {string} [params.keyword] - 关键词搜索
 * @param {string} [params.category] - 分类过滤
 * @param {number} [params.page] - 页码
 * @param {number} [params.size] - 每页数量
 * @param {string} [params.tenantId] - 租户 ID
 * @param {string} [params.status] - 状态过滤（仅 admin 可查看非 published）
 * @param {boolean} [params.showAll] - 是否显示所有状态（admin 用）
 */
function getTemplates({ keyword, category, page, size, tenantId, status, showAll }) {
  const conditions = [];
  const params = [];

  // 租户过滤
  if (tenantId) {
    conditions.push('tenant_id = ?');
    params.push(tenantId);
  }

  // 状态过滤：非 admin 默认只看 published
  if (showAll) {
    // admin 可以看到所有状态
    if (status) {
      conditions.push('status = ?');
      params.push(status);
    }
  } else {
    // 普通用户只看已发布的
    conditions.push("status = 'published'");
  }

  if (keyword) {
    conditions.push('(name LIKE ? OR description LIKE ?)');
    params.push(`%${keyword}%`, `%${keyword}%`);
  }

  if (category) {
    conditions.push('category = ?');
    params.push(category);
  }

  const whereClause = conditions.length > 0 ? 'WHERE ' + conditions.join(' AND ') : '';

  // 查询总数
  const countStmt = db.prepare(`SELECT COUNT(*) as total FROM templates ${whereClause}`);
  const { total } = countStmt.get(...params);

  // 查询分页数据
  const offset = page * size;
  const dataStmt = db.prepare(
    `SELECT * FROM templates ${whereClause} ORDER BY create_time DESC LIMIT ? OFFSET ?`
  );
  const content = dataStmt.all(...params, size, offset);

  return {
    content,
    totalElements: total,
    totalPages: Math.ceil(total / size),
  };
}

/**
 * 删除模板
 */
function deleteTemplate(id) {
  const stmt = db.prepare('DELETE FROM templates WHERE id = ?');
  const result = stmt.run(id);
  return result.changes > 0;
}

/**
 * 更新模板字段
 */
function updateTemplateFields(id, fields) {
  const stmt = db.prepare(`
    UPDATE templates SET fields = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?
  `);
  stmt.run(JSON.stringify(fields), id);
}

/**
 * 更新模板状态
 */
function updateTemplateStatus(id, status, extraFields = {}) {
  const setClauses = ['status = ?'];
  const params = [status];

  if (extraFields.version !== undefined) {
    setClauses.push('version = ?');
    params.push(extraFields.version);
  }
  if (extraFields.published_at !== undefined) {
    setClauses.push('published_at = ?');
    params.push(extraFields.published_at);
  }
  if (extraFields.approved_by !== undefined) {
    setClauses.push('approved_by = ?');
    params.push(extraFields.approved_by);
  }
  if (extraFields.approved_at !== undefined) {
    setClauses.push('approved_at = ?');
    params.push(extraFields.approved_at);
  }
  if (extraFields.reject_reason !== undefined) {
    setClauses.push('reject_reason = ?');
    params.push(extraFields.reject_reason);
  }

  setClauses.push('update_time = CURRENT_TIMESTAMP');
  params.push(id);

  const stmt = db.prepare(`UPDATE templates SET ${setClauses.join(', ')} WHERE id = ?`);
  stmt.run(...params);
}

/**
 * 获取所有分类（按租户过滤）
 */
function getCategories(tenantId) {
  let stmt;
  if (tenantId) {
    stmt = db.prepare("SELECT DISTINCT category FROM templates WHERE category IS NOT NULL AND category != '' AND tenant_id = ? ORDER BY category");
    const rows = stmt.all(tenantId);
    return rows.map((row) => row.category);
  }
  stmt = db.prepare("SELECT DISTINCT category FROM templates WHERE category IS NOT NULL AND category != '' ORDER BY category");
  const rows = stmt.all();
  return rows.map((row) => row.category);
}

/**
 * 查询待审批的模板列表
 */
function getPendingTemplates(tenantId) {
  const conditions = ["status = 'pending'"];
  const params = [];

  if (tenantId) {
    conditions.push('tenant_id = ?');
    params.push(tenantId);
  }

  const whereClause = 'WHERE ' + conditions.join(' AND ');
  const stmt = db.prepare(`SELECT * FROM templates ${whereClause} ORDER BY create_time DESC`);
  return stmt.all(...params);
}

module.exports = {
  insertTemplate,
  getTemplateById,
  getTemplateByIdAndTenant,
  getTemplates,
  deleteTemplate,
  updateTemplateFields,
  updateTemplateStatus,
  getCategories,
  getPendingTemplates,
};
