const db = require('../db/init');
const logger = require('../utils/logger');

/**
 * 片段业务逻辑层
 */

/**
 * 从 HTML 内容中提取占位符字段
 * @param {string} html - HTML 内容
 * @returns {Array} 字段列表 [{name, label, type}]
 */
function parseFragmentFields(html) {
  if (!html) return [];
  // 匹配 {xxx} 或 ${xxx} 占位符
  const regex = /\{(\$?\{)?([a-zA-Z_][a-zA-Z0-9_]*)\}?\}/g;
  const fieldNames = new Set();
  let match;
  while ((match = regex.exec(html)) !== null) {
    fieldNames.add(match[2]);
  }
  return Array.from(fieldNames).map((name) => ({
    name,
    label: name,
    type: guessFieldType(name),
  }));
}

/**
 * 根据字段名猜测字段类型
 */
function guessFieldType(name) {
  const lower = name.toLowerCase();
  if (lower.includes('date') || lower.includes('time') || lower.includes('日期') || lower.includes('时间')) return 'date';
  if (lower.includes('amount') || lower.includes('price') || lower.includes('money') || lower.includes('金额') || lower.includes('价格')) return 'number';
  if (lower.includes('list') || lower.includes('items') || lower.includes('列表') || lower.includes('明细')) return 'array';
  return 'text';
}

/**
 * 创建片段
 */
function createFragment(data) {
  const { name, description, category, contentHtml, tags, tenantId, userId } = data;

  const fields = parseFragmentFields(contentHtml || '');

  const result = db.prepare(`
    INSERT INTO fragments (name, description, category, content_html, fields, tags, tenant_id, status, current_version, created_by)
    VALUES (?, ?, ?, ?, ?, ?, ?, 'draft', 1, ?)
  `).run(
    name,
    description || '',
    category || '',
    contentHtml || '',
    JSON.stringify(fields),
    JSON.stringify(tags || []),
    tenantId || 'default',
    userId || null
  );

  const fragmentId = result.lastInsertRowid;

  // 创建初始版本
  db.prepare(`
    INSERT INTO fragment_versions (fragment_id, version, content_html, fields, change_note, created_by)
    VALUES (?, 1, ?, ?, '初始版本', ?)
  `).run(fragmentId, contentHtml || '', JSON.stringify(fields), userId || null);

  return getFragment(fragmentId);
}

/**
 * 更新片段（内容变化时自动创建新版本）
 */
function updateFragment(id, data) {
  const existing = db.prepare('SELECT * FROM fragments WHERE id = ? AND deleted_at IS NULL').get(id);
  if (!existing) throw new Error('片段不存在');

  const { name, description, category, contentHtml, tags, status, changeNote, userId } = data;

  // 检查内容是否变化
  const contentChanged = contentHtml !== undefined && contentHtml !== existing.content_html;
  const fields = contentChanged ? parseFragmentFields(contentHtml || '') : existing.fields;

  db.prepare(`
    UPDATE fragments SET
      name = ?, description = ?, category = ?, fields = ?, tags = ?,
      status = COALESCE(?, status), updated_at = datetime('now'),
      current_version = current_version + ?
    WHERE id = ?
  `).run(
    name || existing.name,
    description !== undefined ? description : existing.description,
    category !== undefined ? category : existing.category,
    contentChanged ? JSON.stringify(fields) : existing.fields,
    tags !== undefined ? JSON.stringify(tags) : existing.tags,
    status || null,
    contentChanged ? 1 : 0,
    id
  );

  // 内容变化时创建新版本
  if (contentChanged) {
    const currentVersion = db.prepare('SELECT current_version FROM fragments WHERE id = ?').get(id).current_version;
    db.prepare(`
      INSERT INTO fragment_versions (fragment_id, version, content_html, fields, change_note, created_by)
      VALUES (?, ?, ?, ?, ?, ?)
    `).run(
      id,
      currentVersion,
      contentHtml || '',
      JSON.stringify(fields),
      changeNote || '更新内容',
      userId || null
    );
  }

  return getFragment(id);
}

/**
 * 获取片段详情
 */
function getFragment(id) {
  const row = db.prepare('SELECT * FROM fragments WHERE id = ? AND deleted_at IS NULL').get(id);
  if (!row) return null;
  return normalizeFragment(row);
}

/**
 * 获取片段列表
 */
function getFragmentList(params) {
  const { keyword, category, tags, status, page, size, tenantId } = params;
  const pageVal = parseInt(page, 10) || 0;
  const sizeVal = parseInt(size, 10) || 20;
  const offset = pageVal * sizeVal;

  let where = 'WHERE deleted_at IS NULL';
  const queryParams = [];

  if (tenantId) {
    where += ' AND tenant_id = ?';
    queryParams.push(tenantId);
  }
  if (keyword) {
    where += ' AND (name LIKE ? OR description LIKE ?)';
    queryParams.push(`%${keyword}%`, `%${keyword}%`);
  }
  if (category) {
    where += ' AND category = ?';
    queryParams.push(category);
  }
  if (status) {
    where += ' AND status = ?';
    queryParams.push(status);
  }
  if (tags) {
    const tagList = Array.isArray(tags) ? tags : [tags];
    for (const tag of tagList) {
      where += ' AND tags LIKE ?';
      queryParams.push(`%"${tag}"%`);
    }
  }

  const countResult = db.prepare(`SELECT COUNT(*) as total FROM fragments ${where}`).get(...queryParams);
  const total = countResult ? countResult.total : 0;

  const rows = db.prepare(`SELECT * FROM fragments ${where} ORDER BY updated_at DESC LIMIT ? OFFSET ?`).all(...queryParams, sizeVal, offset);

  return {
    content: rows.map(normalizeFragment),
    totalElements: total,
    totalPages: Math.ceil(total / sizeVal),
    page: pageVal,
    size: sizeVal,
  };
}

/**
 * 删除片段（软删除）
 */
function deleteFragment(id) {
  const existing = db.prepare('SELECT * FROM fragments WHERE id = ? AND deleted_at IS NULL').get(id);
  if (!existing) return false;
  db.prepare("UPDATE fragments SET deleted_at = datetime('now') WHERE id = ?").run(id);
  return true;
}

/**
 * 获取所有分类
 */
function getAllCategories(tenantId) {
  const rows = db.prepare(
    "SELECT DISTINCT category FROM fragments WHERE deleted_at IS NULL AND tenant_id = ? AND category != '' ORDER BY category"
  ).all(tenantId || 'default');
  return rows.map((r) => r.category);
}

/**
 * 获取片段版本列表
 */
function getFragmentVersionList(fragmentId) {
  return db.prepare(
    'SELECT v.*, u.username as created_by_name FROM fragment_versions v LEFT JOIN users u ON v.created_by = u.id WHERE v.fragment_id = ? ORDER BY v.version DESC'
  ).all(fragmentId);
}

/**
 * 获取片段特定版本
 */
function getFragmentVersion(fragmentId, version) {
  return db.prepare(
    'SELECT v.*, u.username as created_by_name FROM fragment_versions v LEFT JOIN users u ON v.created_by = u.id WHERE v.fragment_id = ? AND v.version = ?'
  ).get(fragmentId, version);
}

/**
 * 回滚片段到指定版本
 */
function rollbackFragmentVersion(fragmentId, version, userId) {
  const ver = getFragmentVersion(fragmentId, version);
  if (!ver) throw new Error('版本不存在');

  const fragment = getFragment(fragmentId);
  if (!fragment) throw new Error('片段不存在');

  // 更新片段内容为指定版本的内容
  const currentVersion = fragment.current_version + 1;
  db.prepare(`
    UPDATE fragments SET
      content_html = ?, fields = ?, current_version = ?, updated_at = datetime('now')
    WHERE id = ?
  `).run(ver.content_html, ver.fields, currentVersion, fragmentId);

  // 创建新版本记录（标记为回滚）
  db.prepare(`
    INSERT INTO fragment_versions (fragment_id, version, content_html, fields, change_note, created_by)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(fragmentId, currentVersion, ver.content_html, ver.fields, `回滚到版本 v${version}`, userId || null);

  return { version: currentVersion };
}

/**
 * 标准化片段数据
 */
function normalizeFragment(row) {
  if (!row) return null;
  return {
    ...row,
    fields: safeJsonParse(row.fields, []),
    tags: safeJsonParse(row.tags, []),
  };
}

function safeJsonParse(str, fallback) {
  if (typeof str === 'string') {
    try { return JSON.parse(str); } catch { return fallback; }
  }
  return str || fallback;
}

module.exports = {
  createFragment,
  updateFragment,
  getFragment,
  getFragmentList,
  deleteFragment,
  getAllCategories,
  parseFragmentFields,
  getFragmentVersionList,
  getFragmentVersion,
  rollbackFragmentVersion,
};
