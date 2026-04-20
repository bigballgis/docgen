const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');
const PizZip = require('pizzip');
const Docxtemplater = require('docxtemplater');
const templateDb = require('../db/templates');
const db = require('../db/init');
const config = require('../config');

/**
 * 文档生成服务层
 * 降级策略：Kafka 不可用时，文档生成改为同步执行
 */

/**
 * 生成文档
 * @param {number} templateId - 模板 ID
 * @param {Object} data - 填充数据 { key: value }
 * @param {string} outputFormat - 输出格式 "docx" 或 "pdf"
 * @param {number|null} userId - 用户 ID（可选）
 * @param {string} [tenantId='default'] - 租户 ID
 * @returns {{ filePath: string, fileName: string, taskId: string }} 生成的文件信息
 */
async function generateDocument(templateId, data, outputFormat, userId = null, tenantId = 'default') {
  // 获取模板信息
  const template = templateDb.getTemplateById(templateId);
  if (!template) {
    throw new Error('模板不存在');
  }

  const templatePath = path.join(config.uploadDir, template.file_name);
  if (!fs.existsSync(templatePath)) {
    throw new Error('模板文件不存在');
  }

  // 确保输出目录存在
  if (!fs.existsSync(config.outputDir)) {
    fs.mkdirSync(config.outputDir, { recursive: true });
  }

  // 生成输出文件名
  const baseName = path.basename(template.original_file_name, path.extname(template.original_file_name));
  const timestamp = Date.now();
  const fileKey = `${baseName}_${timestamp}`;
  const docxFileName = `${fileKey}.docx`;
  const docxOutputPath = path.join(config.outputDir, docxFileName);

  // 使用 docxtemplater 填充模板
  const content = fs.readFileSync(templatePath, 'binary');
  const zip = new PizZip(content);
  const doc = new Docxtemplater(zip, {
    paragraphLoop: true,
    linebreaks: true,
  });

  doc.render(data);
  const buf = doc.getZip().generate({ type: 'nodebuffer' });
  fs.writeFileSync(docxOutputPath, buf);

  let finalFileName = docxFileName;
  let finalFilePath = docxOutputPath;
  let finalFormat = 'docx';

  // 如果需要 PDF 格式，进行转换
  if (outputFormat === 'pdf') {
    const pdfFileName = `${fileKey}.pdf`;
    const pdfOutputPath = path.join(config.outputDir, pdfFileName);

    try {
      await convertToPdf(docxOutputPath, config.outputDir);
      finalFileName = pdfFileName;
      finalFilePath = pdfOutputPath;
      finalFormat = 'pdf';
    } catch (err) {
      console.error('PDF 转换失败:', err.message);
      // 转换失败时返回 docx 文件
    }
  }

  // 记录文档历史（同步生成，直接标记为 completed）
  try {
    const stmt = db.prepare(`
      INSERT INTO documents (template_id, template_name, file_key, file_name, output_format, status, user_id, tenant_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);
    const result = stmt.run(
      templateId,
      template.name,
      fileKey,
      finalFileName,
      finalFormat,
      'completed',
      userId,
      tenantId
    );

    // 返回结果包含文档 ID 作为 taskId
    return {
      filePath: finalFilePath,
      fileName: finalFileName,
      taskId: String(result.lastInsertRowid),
    };
  } catch (err) {
    console.error('记录文档历史失败:', err.message);
    // 即使记录失败，仍然返回文件
    return {
      filePath: finalFilePath,
      fileName: finalFileName,
      taskId: fileKey,
    };
  }
}

/**
 * 使用 LibreOffice 将 docx 转换为 pdf
 * 使用 spawn 替代 exec 防止命令注入
 * @param {string} docxPath - docx 文件路径
 * @param {string} outputDir - 输出目录
 */
async function convertToPdf(docxPath, outputDir) {
  // 安全校验：确保路径在允许的目录内
  const resolvedDocxPath = path.resolve(docxPath);
  const resolvedOutputDir = path.resolve(outputDir);
  const allowedBase = path.resolve(config.outputDir);

  if (!resolvedDocxPath.startsWith(allowedBase) || !resolvedOutputDir.startsWith(allowedBase)) {
    throw new Error('文件路径不在允许的目录范围内');
  }

  // 使用 spawn 避免命令注入（参数数组传递，不经过 shell）
  return new Promise((resolve, reject) => {
    const child = spawn('libreoffice', [
      '--headless',
      '--convert-to', 'pdf',
      '--outdir', resolvedOutputDir,
      resolvedDocxPath,
    ], {
      timeout: 60000,
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    let stderr = '';
    child.stderr.on('data', (data) => { stderr += data.toString(); });

    child.on('close', (code) => {
      if (code !== 0 && stderr && !stderr.includes('Warning')) {
        console.warn('LibreOffice stderr:', stderr);
      }

      const pdfPath = path.join(
        resolvedOutputDir,
        path.basename(resolvedDocxPath, path.extname(resolvedDocxPath)) + '.pdf'
      );

      if (!fs.existsSync(pdfPath)) {
        reject(new Error('PDF 文件生成失败'));
      } else {
        resolve(pdfPath);
      }
    });

    child.on('error', (err) => {
      reject(new Error(`LibreOffice 启动失败: ${err.message}`));
    });
  });
}

/**
 * 获取已生成文档的路径（带路径遍历防护）
 * @param {string} fileName - 文件名
 * @returns {string|null} 文件路径
 */
function getOutputFilePath(fileName) {
  // 安全校验：禁止路径遍历
  if (!fileName || typeof fileName !== 'string') {
    return null;
  }

  // 移除任何路径分隔符，只保留文件名
  const sanitized = path.basename(fileName);

  // 二次校验：确保清理后的文件名不包含路径遍历字符
  if (sanitized !== fileName || sanitized.includes('..') || sanitized.includes('/') || sanitized.includes('\\')) {
    console.warn('路径遍历攻击尝试被拦截:', fileName);
    return null;
  }

  // 只允许特定扩展名
  const allowedExt = ['.docx', '.pdf', '.xlsx', '.pptx'];
  const ext = path.extname(sanitized).toLowerCase();
  if (!allowedExt.includes(ext)) {
    return null;
  }

  const filePath = path.join(config.outputDir, sanitized);
  // 最终校验：解析后的路径必须在输出目录内
  const resolved = path.resolve(filePath);
  const allowedBase = path.resolve(config.outputDir);

  if (!resolved.startsWith(allowedBase)) {
    console.warn('路径遍历攻击尝试被拦截（解析后）:', fileName);
    return null;
  }

  if (fs.existsSync(resolved)) {
    return resolved;
  }
  return null;
}

/**
 * 获取文档历史列表
 * @param {Object} params - 查询参数
 * @param {number} [params.page] - 页码
 * @param {number} [params.size] - 每页数量
 * @param {number} [params.userId] - 用户 ID
 * @param {string} [params.tenantId] - 租户 ID
 * @returns {Object} 分页结果
 */
function getDocumentList({ page, size, userId, tenantId }) {
  const pageVal = parseInt(page, 10) || 0;
  const sizeVal = parseInt(size, 10) || 10;
  const offset = pageVal * sizeVal;

  const conditions = [];
  const params = [];

  // 租户过滤
  if (tenantId) {
    conditions.push('tenant_id = ?');
    params.push(tenantId);
  }

  // 如果指定了用户 ID，只查询该用户的文档
  if (userId) {
    conditions.push('user_id = ?');
    params.push(userId);
  }

  const whereClause = conditions.length > 0 ? 'WHERE ' + conditions.join(' AND ') : '';

  // 查询总数
  const countStmt = db.prepare(`SELECT COUNT(*) as total FROM documents ${whereClause}`);
  const { total } = countStmt.get(...params);

  // 查询分页数据
  const dataStmt = db.prepare(
    `SELECT * FROM documents ${whereClause} ORDER BY created_at DESC LIMIT ? OFFSET ?`
  );
  const content = dataStmt.all(...params, sizeVal, offset);

  return {
    content,
    totalElements: total,
    totalPages: Math.ceil(total / sizeVal),
  };
}

/**
 * 查询任务状态
 * 降级策略：同步生成，直接返回 completed
 * @param {string} taskId - 任务 ID（即 documents 表的 ID）
 * @returns {Object} 任务状态
 */
function getDocumentStatus(taskId) {
  const id = parseInt(taskId, 10);
  const doc = db.prepare('SELECT * FROM documents WHERE id = ?').get(id);

  if (!doc) {
    return {
      taskId,
      status: 'not_found',
      message: '任务不存在',
    };
  }

  return {
    taskId: doc.id,
    fileKey: doc.file_key,
    fileName: doc.file_name,
    status: doc.status,
    outputFormat: doc.output_format,
    createdAt: doc.created_at,
  };
}

module.exports = {
  generateDocument,
  getOutputFilePath,
  getDocumentList,
  getDocumentStatus,
};
