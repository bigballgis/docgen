const db = require('../db/init');
const fs = require('fs');
const path = require('path');
const config = require('../config');
const docxToHtmlService = require('./docxToHtmlService');

/**
 * 创建新版本（上传模板时自动调用）
 * @param {number} templateId - 模板 ID
 * @param {string} filePath - 模板文件路径
 * @param {number|null} userId - 用户 ID
 * @param {string} changeNote - 变更说明
 * @returns {{ version: number, filePath: string }}
 */
function createVersion(templateId, filePath, userId, changeNote = '') {
  // 获取当前最大版本号
  const row = db
    .prepare('SELECT MAX(version) as max_ver FROM template_versions WHERE template_id = ?')
    .get(templateId);
  const nextVersion = (row && row.max_ver ? row.max_ver : 0) + 1;

  // 复制文件到版本目录
  const versionDir = path.join(config.uploadDir, 'versions', String(templateId));
  if (!fs.existsSync(versionDir)) {
    fs.mkdirSync(versionDir, { recursive: true });
  }
  const versionFileName = `v${nextVersion}_${path.basename(filePath)}`;
  const versionFilePath = path.join(versionDir, versionFileName);
  fs.copyFileSync(filePath, versionFilePath);

  // 插入版本记录
  db.prepare(`
    INSERT INTO template_versions (template_id, version, file_path, file_size, change_note, created_by)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(
    templateId,
    nextVersion,
    versionFilePath,
    fs.statSync(versionFilePath).size,
    changeNote,
    userId
  );

  // 更新模板当前版本号
  db.prepare('UPDATE templates SET current_version = ? WHERE id = ?').run(nextVersion, templateId);

  return { version: nextVersion, filePath: versionFilePath };
}

/**
 * 获取模板的所有版本列表
 * @param {number} templateId - 模板 ID
 * @returns {Array} 版本列表
 */
function getVersionList(templateId) {
  return db
    .prepare(`
    SELECT v.*, u.username as created_by_name
    FROM template_versions v
    LEFT JOIN users u ON v.created_by = u.id
    WHERE v.template_id = ?
    ORDER BY v.version DESC
  `)
    .all(templateId);
}

/**
 * 获取指定版本的信息
 * @param {number} templateId - 模板 ID
 * @param {number} version - 版本号
 * @returns {Object|null} 版本信息
 */
function getVersion(templateId, version) {
  return db
    .prepare(`
    SELECT v.*, u.username as created_by_name
    FROM template_versions v
    LEFT JOIN users u ON v.created_by = u.id
    WHERE v.template_id = ? AND v.version = ?
  `)
    .get(templateId, version);
}

/**
 * 回滚到指定版本
 * @param {number} templateId - 模板 ID
 * @param {number} version - 目标版本号
 * @param {number|null} userId - 操作用户 ID
 * @returns {{ version: number, filePath: string }}
 */
async function rollbackToVersion(templateId, version, userId) {
  const versionInfo = getVersion(templateId, version);
  if (!versionInfo) throw new Error('版本不存在');

  // 复制版本文件为当前模板文件
  const template = db.prepare('SELECT file_name FROM templates WHERE id = ?').get(templateId);
  if (!template) throw new Error('模板不存在');

  const currentFilePath = path.join(config.uploadDir, template.file_name);
  fs.copyFileSync(versionInfo.file_path, currentFilePath);

  // 创建新版本记录（标记为回滚）
  return createVersion(templateId, currentFilePath, userId, `回滚到版本 v${version}`);
}

/**
 * 对比两个版本的 HTML
 * @param {number} templateId - 模板 ID
 * @param {number} version1 - 版本号1
 * @param {number} version2 - 版本号2
 * @returns {Promise<Object>} 对比结果
 */
async function compareVersions(templateId, version1, version2) {
  const v1 = getVersion(templateId, version1);
  const v2 = getVersion(templateId, version2);
  if (!v1 || !v2) throw new Error('版本不存在');

  const [html1, html2] = await Promise.all([
    docxToHtmlService.convertDocxToStyledHtml(v1.file_path),
    docxToHtmlService.convertDocxToStyledHtml(v2.file_path),
  ]);

  const diff = docxToHtmlService.computeHtmlDiff(html1.html, html2.html);

  return {
    version1: {
      version: v1.version,
      createdAt: v1.created_at,
      changeNote: v1.change_note,
      createdBy: v1.created_by_name,
    },
    version2: {
      version: v2.version,
      createdAt: v2.created_at,
      changeNote: v2.change_note,
      createdBy: v2.created_by_name,
    },
    html1: html1.html,
    html2: html2.html,
    diff: diff,
  };
}

/**
 * 获取版本的 HTML 预览
 * @param {number} templateId - 模板 ID
 * @param {number} version - 版本号
 * @returns {Promise<{ version: number, html: string }>}
 */
async function getVersionHtml(templateId, version) {
  const v = getVersion(templateId, version);
  if (!v) throw new Error('版本不存在');
  const result = await docxToHtmlService.convertDocxToStyledHtml(v.file_path);
  return { version: v.version, html: result.html };
}

module.exports = {
  createVersion,
  getVersionList,
  getVersion,
  rollbackToVersion,
  compareVersions,
  getVersionHtml,
};
