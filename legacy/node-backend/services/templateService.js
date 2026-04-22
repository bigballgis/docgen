const path = require('path');
const fs = require('fs');
const PizZip = require('pizzip');
const templateDb = require('../db/templates');
const config = require('../config');
const versionService = require('./versionService');

/**
 * 模板业务逻辑层
 */

/**
 * 上传模板
 * @param {Object} file - multer 上传的文件对象
 * @param {string} name - 模板名称
 * @param {string} description - 模板描述
 * @param {string} category - 模板分类
 * @param {string} fieldsJson - 字段 JSON 字符串
 * @param {number|null} userId - 用户 ID（可选）
 * @param {string} tenantId - 租户 ID
 * @param {string} changeNote - 变更说明（可选）
 */
function uploadTemplate(file, name, description, category, fieldsJson, userId = null, tenantId = 'default', changeNote = '') {
  // 生成唯一文件名
  const ext = path.extname(file.originalname);
  const fileName = `${Date.now()}-${Math.random().toString(36).substring(2, 8)}${ext}`;

  // 移动文件到上传目录
  const destPath = path.join(config.uploadDir, fileName);
  fs.renameSync(file.path, destPath);

  // 解析字段
  let fields = [];
  if (fieldsJson) {
    try {
      fields = JSON.parse(fieldsJson);
    } catch (e) {
      // 如果解析失败，尝试自动解析模板字段
      fields = parseFieldsFromFile(destPath);
    }
  } else {
    fields = parseFieldsFromFile(destPath);
  }

  // 存入数据库（关联 user_id 和 tenant_id）
  const id = templateDb.insertTemplate({
    name,
    description,
    file_name: fileName,
    original_file_name: file.originalname,
    fields: JSON.stringify(fields),
    category,
    user_id: userId,
    tenant_id: tenantId,
  });

  // 创建版本记录
  try {
    versionService.createVersion(id, destPath, userId, changeNote || '初始版本');
  } catch (e) {
    // 版本创建失败不影响模板上传
    console.warn('[版本控制] 创建初始版本失败:', e.message);
  }

  return templateDb.getTemplateById(id);
}

/**
 * 获取模板列表
 * @param {Object} params
 * @param {string} [params.keyword] - 关键词搜索
 * @param {string} [params.category] - 分类过滤
 * @param {number} [params.page] - 页码
 * @param {number} [params.size] - 每页数量
 * @param {string} [params.tenantId] - 租户 ID
 * @param {string} [params.status] - 状态过滤
 * @param {boolean} [params.showAll] - 是否显示所有状态
 */
function getTemplateList({ keyword, category, page, size, tenantId, status, showAll }) {
  const pageVal = parseInt(page, 10) || 0;
  const sizeVal = parseInt(size, 10) || 10;
  return templateDb.getTemplates({ keyword, category, page: pageVal, size: sizeVal, tenantId, status, showAll });
}

/**
 * 获取模板详情
 */
function getTemplateDetail(id) {
  const template = templateDb.getTemplateById(id);
  if (!template) return null;

  // 解析 fields JSON
  try {
    template.fields = JSON.parse(template.fields);
  } catch (e) {
    template.fields = [];
  }

  return template;
}

/**
 * 删除模板
 */
function removeTemplate(id) {
  const template = templateDb.getTemplateById(id);
  if (!template) return false;

  // 删除文件
  const filePath = path.join(config.uploadDir, template.file_name);
  if (fs.existsSync(filePath)) {
    fs.unlinkSync(filePath);
  }

  // 删除数据库记录
  return templateDb.deleteTemplate(id);
}

/**
 * 获取所有分类
 * @param {string} [tenantId] - 租户 ID
 */
function getAllCategories(tenantId) {
  return templateDb.getCategories(tenantId);
}

/**
 * 解析模板中的占位符字段
 * @param {number} id - 模板 ID
 */
function parseTemplateFields(id) {
  const template = templateDb.getTemplateById(id);
  if (!template) return null;

  const filePath = path.join(config.uploadDir, template.file_name);
  const fields = parseFieldsFromFile(filePath);

  // 更新数据库中的字段
  templateDb.updateTemplateFields(id, fields);

  return {
    fields,
    fieldCount: fields.length,
  };
}

/**
 * 从 docx 文件中解析占位符字段
 * @param {string} filePath - docx 文件路径
 * @returns {Array} 字段列表
 */
function parseFieldsFromFile(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error('模板文件不存在');
  }

  const content = fs.readFileSync(filePath, 'binary');
  const zip = new PizZip(content);

  // 读取 word/document.xml
  const documentXml = zip.file('word/document.xml');
  if (!documentXml) {
    throw new Error('无效的 docx 文件：缺少 word/document.xml');
  }

  const xmlContent = documentXml.asText();

  // 匹配 {xxx} 或 ${xxx} 占位符
  // docxtemplater 的占位符可能被 XML 节点分割，需要处理
  const placeholderRegex = /\{(\$\{)?([a-zA-Z_][a-zA-Z0-9_]*)\}?/g;

  const fieldNames = new Set();
  let match;
  while ((match = placeholderRegex.exec(xmlContent)) !== null) {
    fieldNames.add(match[2]);
  }

  // 转换为字段对象数组
  const fields = Array.from(fieldNames).map((name) => ({
    name,
    label: name,
    type: guessFieldType(name),
  }));

  return fields;
}

/**
 * 根据字段名猜测字段类型
 */
function guessFieldType(name) {
  const lower = name.toLowerCase();
  if (lower.includes('date') || lower.includes('time') || lower.includes('日期') || lower.includes('时间')) {
    return 'date';
  }
  if (lower.includes('amount') || lower.includes('price') || lower.includes('money') || lower.includes('金额') || lower.includes('价格')) {
    return 'number';
  }
  if (lower.includes('phone') || lower.includes('mobile') || lower.includes('电话') || lower.includes('手机')) {
    return 'text';
  }
  if (lower.includes('email') || lower.includes('邮箱')) {
    return 'text';
  }
  if (lower.includes('list') || lower.includes('items') || lower.includes('列表') || lower.includes('明细')) {
    return 'array';
  }
  return 'text';
}

module.exports = {
  uploadTemplate,
  getTemplateList,
  getTemplateDetail,
  removeTemplate,
  getAllCategories,
  parseTemplateFields,
};
