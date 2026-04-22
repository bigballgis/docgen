const config = require('../config');
const logger = require('../utils/logger');
const path = require('path');
const fs = require('fs');

/**
 * Euro-Office 文档对比服务
 * 利用 Euro-Office (ONLYOFFICE fork) Document Builder API 进行真正的 WYSIWYG 文档对比
 * 当 Euro-Office 不可用时，自动降级到 HTML 文本对比
 */
class EuroOfficeCompareService {
  constructor() {
    this.docServerUrl = config.euroOffice?.url || config.euroOffice?.baseUrl || '';
    this.enabled = !!(config.euroOffice?.enabled && this.docServerUrl);
    this.docBuilderTimeout = config.euroOffice?.docBuilderTimeout || 30000;
    this._available = null; // 缓存健康检查结果
    this._lastHealthCheck = 0;
  }

  /**
   * 检查 Euro-Office 服务是否可用
   */
  async isAvailable() {
    // 缓存 60 秒
    const now = Date.now();
    if (this._available !== null && now - this._lastHealthCheck < 60000) {
      return this._available;
    }

    if (!this.enabled) {
      this._available = false;
      return false;
    }

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 5000);
      const resp = await fetch(`${this.docServerUrl}/healthcheck`, {
        method: 'GET',
        signal: controller.signal,
      });
      clearTimeout(timeout);
      this._available = resp.ok;
    } catch {
      this._available = false;
    }

    this._lastHealthCheck = now;
    return this._available;
  }

  /**
   * 使用 Euro-Office Document Builder API 对比两个文档
   * @param {string} docxUrl1 - 原始文档 URL
   * @param {string} docxUrl2 - 修订文档 URL
   * @returns {Promise<{compareDocxUrl: string, key: string}>}
   */
  async compareDocuments(docxUrl1, docxUrl2) {
    const available = await this.isAvailable();
    if (!available) {
      throw new Error('Euro-Office 服务不可用');
    }

    // 构建 .docbuilder 脚本
    const scriptContent = `
builder.OpenFile("${docxUrl1}");
var oRevisedFile = builderJS.OpenTmpFile("${docxUrl2}");
AscCommonWord.CompareDocuments(Api, oRevisedFile, null);
oRevisedFile.Close();
builder.SaveFile("docx", "compare-result.docx");
builder.CloseFile();
`;

    // 将脚本保存为临时文件，通过 HTTP 可访问
    const scriptUrl = await this._saveScriptAsTempFile(scriptContent);

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), this.docBuilderTimeout);

      const resp = await fetch(`${this.docServerUrl}/docbuilder`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          async: false,
          url: scriptUrl,
        }),
        signal: controller.signal,
      });

      clearTimeout(timeout);

      if (!resp.ok) {
        throw new Error(`Euro-Office Document Builder 返回错误: ${resp.status}`);
      }

      const result = await resp.json();

      if (result.error) {
        throw new Error(`Document Builder 错误: ${result.error}`);
      }

      return {
        compareDocxUrl: result.urls?.['compare-result.docx'] || '',
        key: result.key || '',
      };
    } finally {
      // 清理临时脚本文件
      this._cleanupTempScript(scriptUrl);
    }
  }

  /**
   * 对比两个片段版本
   * @param {number} fragmentId
   * @param {number} version1
   * @param {number} version2
   * @param {Object} deps - 依赖注入 { fragmentService }
   * @returns {Promise<{mode: string, compareDocxUrl?: string, fallbackHtml?: string, stats?: Object}>}
   */
  async compareFragmentVersions(fragmentId, version1, version2, deps) {
    const { fragmentService } = deps;

    const v1 = fragmentService.getFragmentVersion(fragmentId, version1);
    const v2 = fragmentService.getFragmentVersion(fragmentId, version2);
    if (!v1 || !v2) throw new Error('版本不存在');

    // 尝试 Euro-Office 对比
    const available = await this.isAvailable();
    if (available) {
      try {
        // 将 HTML 转为临时 docx 文件 URL
        const url1 = await this._htmlToTempDocxUrl(v1.content_html, `v${version1}`);
        const url2 = await this._htmlToTempDocxUrl(v2.content_html, `v${version2}`);

        const result = await this.compareDocuments(url1, url2);
        return {
          mode: 'euro-office',
          compareDocxUrl: result.compareDocxUrl,
          key: result.key,
        };
      } catch (err) {
        logger.warn('Euro-Office 对比失败，降级到 HTML 对比', { error: err.message });
      }
    }

    // 降级到 HTML 对比
    const diff = this._computeHtmlDiff(v1.content_html, v2.content_html);
    return {
      mode: 'html-diff',
      fallbackHtml: diff.renderedHtml,
      stats: diff.stats,
    };
  }

  /**
   * HTML 文本对比（降级方案）
   */
  _computeHtmlDiff(html1, html2) {
    const parseBlocks = (html) => {
      const regex = /<(p|h[1-6]|li|tr|div)[^>]*>([\s\S]*?)<\/\1>/gi;
      const blocks = [];
      let match;
      while ((match = regex.exec(html)) !== null) {
        const text = match[2].replace(/<[^>]*>/g, '').trim();
        if (text.length > 0) {
          blocks.push({ tag: match[1].toLowerCase(), text, html: match[0] });
        }
      }
      return blocks;
    };

    const oldBlocks = parseBlocks(html1);
    const newBlocks = parseBlocks(html2);
    const lcs = this._computeLCS(oldBlocks, newBlocks);

    const changes = [];
    let oi = 0, ni = 0;

    for (const { oldIdx, newIdx } of lcs) {
      while (oi < oldIdx) changes.push({ type: 'deleted', content: oldBlocks[oi++] });
      while (ni < newIdx) changes.push({ type: 'added', content: newBlocks[ni++] });
      changes.push({ type: 'equal', content: oldBlocks[oi] });
      oi = oldIdx + 1;
      ni = newIdx + 1;
    }
    while (oi < oldBlocks.length) changes.push({ type: 'deleted', content: oldBlocks[oi++] });
    while (ni < newBlocks.length) changes.push({ type: 'added', content: newBlocks[ni++] });

    const stats = {
      added: changes.filter((c) => c.type === 'added').length,
      deleted: changes.filter((c) => c.type === 'deleted').length,
      equal: changes.filter((c) => c.type === 'equal').length,
    };

    const renderedHtml = changes
      .map((c) => {
        const cls = c.type === 'added' ? 'diff-added' : c.type === 'deleted' ? 'diff-deleted' : '';
        return `<div class="${cls}" style="${c.type === 'added' ? 'background:#e6ffed;border-left:3px solid #27ae60;padding:4px 8px;margin:2px 0;' : c.type === 'deleted' ? 'background:#ffeef0;border-left:3px solid #e74c3c;padding:4px 8px;margin:2px 0;text-decoration:line-through;opacity:0.7;' : 'padding:4px 8px;margin:2px 0;'}">${c.content.html}</div>`;
      })
      .join('');

    return { renderedHtml, stats, changes };
  }

  _computeLCS(a, b) {
    const m = a.length, n = b.length;
    const dp = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0));
    for (let i = 1; i <= m; i++) {
      for (let j = 1; j <= n; j++) {
        dp[i][j] = a[i - 1].text === b[j - 1].text ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1]);
      }
    }
    const result = [];
    let i = m, j = n;
    while (i > 0 && j > 0) {
      if (a[i - 1].text === b[j - 1].text) {
        result.unshift({ oldIdx: i - 1, newIdx: j - 1 });
        i--; j--;
      } else if (dp[i - 1][j] > dp[i][j - 1]) {
        i--;
      } else {
        j--;
      }
    }
    return result;
  }

  /**
   * 将 HTML 保存为临时 docx 文件并返回 URL
   */
  async _htmlToTempDocxUrl(html, label) {
    const PizZip = require('pizzip');
    const outputDir = path.join(__dirname, '..', '..', 'uploads', 'temp');
    if (!fs.existsSync(outputDir)) fs.mkdirSync(outputDir, { recursive: true });

    const fileName = `compare_${label}_${Date.now()}.docx`;
    const filePath = path.join(outputDir, fileName);

    // 创建最小 docx
    const zip = new PizZip();
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
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>`);

    // 简单 HTML 转 Word XML
    const plainText = html.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim();
    const paragraphs = plainText.split('\n').filter((l) => l.trim());

    const wordXml = paragraphs
      .map((p) => `<w:p><w:r><w:t xml:space="preserve">${p.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')}</w:t></w:r></w:p>`)
      .join('\n');

    zip.file('word/document.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>${wordXml}<w:sectPr><w:pgSz w:w="12240" w:h="15840"/></w:sectPr></w:body>
</w:document>`);

    const content = zip.generate({ type: 'nodebuffer' });
    fs.writeFileSync(filePath, content);

    // 返回可通过 HTTP 访问的 URL
    const publicUrl = config.publicUrl || 'http://localhost:3001';
    return `${publicUrl}/uploads/temp/${fileName}`;
  }

  /**
   * 保存 docbuilder 脚本为临时文件
   */
  async _saveScriptAsTempFile(scriptContent) {
    const outputDir = path.join(__dirname, '..', '..', 'uploads', 'temp');
    if (!fs.existsSync(outputDir)) fs.mkdirSync(outputDir, { recursive: true });

    const fileName = `script_${Date.now()}.docbuilder`;
    const filePath = path.join(outputDir, fileName);
    fs.writeFileSync(filePath, scriptContent);

    const publicUrl = config.publicUrl || 'http://localhost:3001';
    return `${publicUrl}/uploads/temp/${fileName}`;
  }

  _cleanupTempScript(scriptUrl) {
    // 异步清理，不阻塞主流程
    try {
      const fileName = path.basename(new URL(scriptUrl).pathname);
      const filePath = path.join(__dirname, '..', '..', 'uploads', 'temp', fileName);
      setTimeout(() => {
        if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
      }, 5000);
    } catch {
      // 忽略清理失败
    }
  }
}

module.exports = new EuroOfficeCompareService();
