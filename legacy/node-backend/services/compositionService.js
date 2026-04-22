const db = require('../db/init');
const logger = require('../utils/logger');

/**
 * 模板编排业务逻辑层
 * 管理模板与片段的组装关系
 */

/**
 * 保存完整编排（替换式）
 * @param {number} templateId - 模板 ID
 * @param {Array} fragments - 片段列表 [{fragmentId, sortOrder, sectionTitle, enabled}]
 * @returns {Object} 保存结果
 */
function saveComposition(templateId, fragments) {
  // 验证模板存在
  const template = db.prepare('SELECT id FROM templates WHERE id = ? AND deleted_at IS NULL').get(templateId);
  if (!template) throw new Error('模板不存在');

  // 事务式保存：先删除旧编排，再插入新编排
  const deleteStmt = db.prepare('DELETE FROM template_compositions WHERE template_id = ?');
  const insertStmt = db.prepare(`
    INSERT INTO template_compositions (template_id, fragment_id, sort_order, section_title, enabled)
    VALUES (?, ?, ?, ?, ?)
  `);

  const transaction = db.transaction((items) => {
    deleteStmt.run(templateId);
    for (const item of items) {
      // 验证片段存在
      const frag = db.prepare('SELECT id FROM fragments WHERE id = ? AND deleted_at IS NULL').get(item.fragmentId);
      if (!frag) {
        logger.warn(`编排保存: 片段 ${item.fragmentId} 不存在，已跳过`);
        continue;
      }
      insertStmt.run(
        templateId,
        item.fragmentId,
        item.sortOrder || 0,
        item.sectionTitle || '',
        item.enabled !== false ? 1 : 0
      );
    }
  });

  transaction(fragments);
  return getComposition(templateId);
}

/**
 * 获取模板编排
 * @param {number} templateId - 模板 ID
 * @returns {Array} 编排列表（含片段详情）
 */
function getComposition(templateId) {
  const rows = db.prepare(`
    SELECT tc.*, f.name as fragment_name, f.description as fragment_description,
           f.category as fragment_category, f.content_html as fragment_content_html,
           f.fields as fragment_fields, f.tags as fragment_tags, f.status as fragment_status
    FROM template_compositions tc
    LEFT JOIN fragments f ON tc.fragment_id = f.id
    WHERE tc.template_id = ?
    ORDER BY tc.sort_order ASC
  `).all(templateId);

  return rows.map(normalizeComposition);
}

/**
 * 添加单个片段到模板
 */
function addFragmentToTemplate(templateId, fragmentId, sortOrder, sectionTitle) {
  // 验证模板和片段存在
  const template = db.prepare('SELECT id FROM templates WHERE id = ? AND deleted_at IS NULL').get(templateId);
  if (!template) throw new Error('模板不存在');

  const frag = db.prepare('SELECT id FROM fragments WHERE id = ? AND deleted_at IS NULL').get(fragmentId);
  if (!frag) throw new Error('片段不存在');

  // 检查是否已存在
  const existing = db.prepare(
    'SELECT id FROM template_compositions WHERE template_id = ? AND fragment_id = ?'
  ).get(templateId, fragmentId);
  if (existing) throw new Error('片段已存在于该模板中');

  // 如果未指定排序，追加到末尾
  let order = sortOrder;
  if (order === undefined || order === null) {
    const maxOrder = db.prepare(
      'SELECT MAX(sort_order) as max_order FROM template_compositions WHERE template_id = ?'
    ).get(templateId);
    order = (maxOrder && maxOrder.max_order !== null ? maxOrder.max_order : -1) + 1;
  }

  db.prepare(`
    INSERT INTO template_compositions (template_id, fragment_id, sort_order, section_title, enabled)
    VALUES (?, ?, ?, ?, 1)
  `).run(templateId, fragmentId, order, sectionTitle || '');

  return getComposition(templateId);
}

/**
 * 从模板中移除片段
 */
function removeFragmentFromTemplate(templateId, fragmentId) {
  const result = db.prepare(
    'DELETE FROM template_compositions WHERE template_id = ? AND fragment_id = ?'
  ).run(templateId, fragmentId);
  return result.changes > 0;
}

/**
 * 重排序片段
 * @param {number} templateId
 * @param {Array} fragmentOrders - [{fragmentId, sortOrder}]
 */
function reorderFragments(templateId, fragmentOrders) {
  const updateStmt = db.prepare(
    'UPDATE template_compositions SET sort_order = ? WHERE template_id = ? AND fragment_id = ?'
  );

  const transaction = db.transaction((items) => {
    for (const item of items) {
      updateStmt.run(item.sortOrder, templateId, item.fragmentId);
    }
  });

  transaction(fragmentOrders);
  return getComposition(templateId);
}

/**
 * 生成组合 HTML 预览
 * @param {number} templateId
 * @returns {string} 组合后的 HTML
 */
function generateComposedHtml(templateId) {
  const composition = getComposition(templateId);
  if (!composition || composition.length === 0) {
    return '<p style="color:#999;text-align:center;">模板编排为空</p>';
  }

  let html = '';
  for (const item of composition) {
    if (!item.enabled) continue;
    if (item.section_title) {
      html += `<h2 style="border-bottom:2px solid #1a365d;padding-bottom:8px;margin:24px 0 12px;">${escapeHtml(item.section_title)}</h2>`;
    }
    if (item.fragment_content_html) {
      html += item.fragment_content_html;
    }
  }
  return html;
}

/**
 * 生成组合 docx 文件路径
 * 使用 PizZip 创建简单的 docx 文件
 */
function generateComposedDocx(templateId) {
  const PizZip = require('pizzip');
  const fs = require('fs');
  const path = require('path');
  const config = require('../config');

  const html = generateComposedHtml(templateId);
  const composition = getComposition(templateId);

  // 读取一个空白 docx 作为模板
  const templatePath = path.join(__dirname, '..', 'templates', 'blank.docx');
  let zip;
  if (fs.existsSync(templatePath)) {
    const content = fs.readFileSync(templatePath, 'binary');
    zip = new PizZip(content);
  } else {
    // 创建最小 docx 结构
    zip = new PizZip();
    zip.file('[Content_Types].xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>`);
    zip.file('_rels/.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`);
    zip.file('word/_rels/document.xml.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>`);
  }

  // HTML 转简单 Word XML
  const wordXml = htmlToWordXml(html);

  zip.file('word/document.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
${wordXml}
    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
    </w:sectPr>
  </w:body>
</w:document>`);

  // 生成输出文件
  if (!fs.existsSync(config.outputDir)) {
    fs.mkdirSync(config.outputDir, { recursive: true });
  }

  const fileName = `composed_${templateId}_${Date.now()}.docx`;
  const filePath = path.join(config.outputDir, fileName);

  const docxContent = zip.generate({ type: 'nodebuffer' });
  fs.writeFileSync(filePath, docxContent);

  return { fileName, filePath };
}

/**
 * 简单 HTML 转 Word XML
 */
function htmlToWordXml(html) {
  // 移除 script/style 标签
  let text = html.replace(/<script[\s\S]*?<\/script>/gi, '');
  text = text.replace(/<style[\s\S]*?<\/style>/gi, '');

  // 处理标题
  text = text.replace(/<h1[^>]*>(.*?)<\/h1>/gi, (m, p1) =>
    `<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="48"/></w:rPr><w:t xml:space="preserve">${stripHtml(p1)}</w:t></w:r></w:p>`
  );
  text = text.replace(/<h2[^>]*>(.*?)<\/h2>/gi, (m, p1) =>
    `<w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="36"/></w:rPr><w:t xml:space="preserve">${stripHtml(p1)}</w:t></w:r></w:p>`
  );
  text = text.replace(/<h3[^>]*>(.*?)<\/h3>/gi, (m, p1) =>
    `<w:p><w:pPr><w:pStyle w:val="Heading3"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="28"/></w:rPr><w:t xml:space="preserve">${stripHtml(p1)}</w:t></w:r></w:p>`
  );

  // 处理段落
  text = text.replace(/<p[^>]*>(.*?)<\/p>/gi, (m, p1) =>
    `<w:p><w:r><w:t xml:space="preserve">${stripHtml(p1)}</w:t></w:r></w:p>`
  );

  // 处理换行
  text = text.replace(/<br\s*\/?>/gi, '<w:r><w:br/></w:r>');

  // 处理加粗
  text = text.replace(/<strong[^>]*>(.*?)<\/strong>/gi, (m, p1) =>
    `<w:r><w:rPr><w:b/></w:rPr><w:t xml:space="preserve">${stripHtml(p1)}</w:t></w:r>`
  );
  text = text.replace(/<b[^>]*>(.*?)<\/b>/gi, (m, p1) =>
    `<w:r><w:rPr><w:b/></w:rPr><w:t xml:space="preserve">${stripHtml(p1)}</w:t></w:r>`
  );

  // 处理斜体
  text = text.replace(/<em[^>]*>(.*?)<\/em>/gi, (m, p1) =>
    `<w:r><w:rPr><w:i/></w:rPr><w:t xml:space="preserve">${stripHtml(p1)}</w:t></w:r>`
  );

  // 处理列表项
  text = text.replace(/<li[^>]*>(.*?)<\/li>/gi, (m, p1) =>
    `<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t xml:space="preserve">• ${stripHtml(p1)}</w:t></w:r></w:p>`
  );

  // 移除剩余标签
  text = stripHtml(text);

  // 将连续文本包裹在段落中
  const lines = text.split('\n').filter((l) => l.trim());
  return lines
    .map((line) => {
      const trimmed = line.trim();
      if (!trimmed) return '';
      if (trimmed.startsWith('<w:')) return trimmed;
      return `<w:p><w:r><w:t xml:space="preserve">${escapeXml(trimmed)}</w:t></w:r></w:p>`;
    })
    .join('\n');
}

function stripHtml(html) {
  return html.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').trim();
}

function escapeXml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function normalizeComposition(row) {
  if (!row) return null;
  return {
    ...row,
    enabled: !!row.enabled,
    fragment_fields: safeJsonParse(row.fragment_fields, []),
    fragment_tags: safeJsonParse(row.fragment_tags, []),
  };
}

function safeJsonParse(str, fallback) {
  if (typeof str === 'string') {
    try { return JSON.parse(str); } catch { return fallback; }
  }
  return str || fallback;
}

module.exports = {
  saveComposition,
  getComposition,
  addFragmentToTemplate,
  removeFragmentFromTemplate,
  reorderFragments,
  generateComposedHtml,
  generateComposedDocx,
};
