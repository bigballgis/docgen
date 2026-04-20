const mammoth = require('mammoth');
const fs = require('fs');
const path = require('path');

/**
 * 将 docx 文件转换为 HTML
 * @param {string} filePath - docx 文件路径
 * @returns {Promise<{html: string, messages: Array}>}
 */
async function convertDocxToHtml(filePath) {
  const result = await mammoth.convertToHtml({ path: filePath });
  return {
    html: result.value,
    messages: result.messages,
  };
}

/**
 * 将 docx 文件转换为带样式的 HTML（保留更多格式）
 * @param {string} filePath
 * @returns {Promise<{html: string, styles: string}>}
 */
async function convertDocxToStyledHtml(filePath) {
  const result = await mammoth.convertToHtml(
    { path: filePath },
    {
      styleMap: [
        "p[style-name='Heading 1'] => h1:fresh",
        "p[style-name='Heading 2'] => h2:fresh",
        "p[style-name='Heading 3'] => h3:fresh",
        "p[style-name='Title'] => h1.doc-title:fresh",
      ],
      convertImage: mammoth.images.imgElement(function (image) {
        return image.read('base64').then(function (imageBuffer) {
          return { src: 'data:' + image.contentType + ';base64,' + imageBuffer };
        });
      }),
    }
  );
  return {
    html: result.value,
    styles: '', // mammoth 可以生成默认样式
  };
}

/**
 * 获取两个 HTML 的 diff（纯文本级别）
 * 返回结构化的增删改信息
 * @param {string} html1 - 旧版本 HTML
 * @param {string} html2 - 新版本 HTML
 * @returns {{ oldHtml: string, newHtml: string, oldParagraphs: Array, newParagraphs: Array, changes: Array }}
 */
function computeHtmlDiff(html1, html2) {
  // 由于没有 DOM 环境，使用简单的正则解析
  const parseParagraphsSimple = (html) => {
    // 匹配块级元素：p, h1-h6, li, tr
    const regex = /<(p|h[1-6]|li|tr)[^>]*>([\s\S]*?)<\/\1>/gi;
    const blocks = [];
    let match;
    while ((match = regex.exec(html)) !== null) {
      const text = match[2].replace(/<[^>]*>/g, '').trim();
      if (text.length > 0) {
        blocks.push({
          tag: match[1].toLowerCase(),
          text: text,
          html: match[0],
        });
      }
    }
    return blocks;
  };

  const oldParas = parseParagraphsSimple(html1);
  const newParas = parseParagraphsSimple(html2);

  // 使用 LCS (最长公共子序列) 算法找出差异
  const lcs = computeLCS(oldParas, newParas);

  // 标记每个段落的变更类型
  const changes = [];
  let oldIdx = 0;
  let newIdx = 0;

  for (let i = 0; i < lcs.length; i++) {
    const { oldIdx: oIdx, newIdx: nIdx } = lcs[i];

    // 处理旧版本中被删除的段落
    while (oldIdx < oIdx) {
      changes.push({
        type: 'deleted',
        oldIndex: oldIdx,
        newIndex: null,
        content: oldParas[oldIdx],
      });
      oldIdx++;
    }

    // 处理新版本中新增的段落
    while (newIdx < nIdx) {
      changes.push({
        type: 'added',
        oldIndex: null,
        newIndex: newIdx,
        content: newParas[newIdx],
      });
      newIdx++;
    }

    // 匹配的段落（未变更）
    changes.push({
      type: 'equal',
      oldIndex: oIdx,
      newIndex: nIdx,
      content: oldParas[oIdx],
    });

    oldIdx = oIdx + 1;
    newIdx = nIdx + 1;
  }

  // 处理剩余的旧版本段落（被删除）
  while (oldIdx < oldParas.length) {
    changes.push({
      type: 'deleted',
      oldIndex: oldIdx,
      newIndex: null,
      content: oldParas[oldIdx],
    });
    oldIdx++;
  }

  // 处理剩余的新版本段落（新增）
  while (newIdx < newParas.length) {
    changes.push({
      type: 'added',
      oldIndex: null,
      newIndex: newIdx,
      content: newParas[newIdx],
    });
    newIdx++;
  }

  return {
    oldHtml: html1,
    newHtml: html2,
    oldParagraphs: oldParas,
    newParagraphs: newParas,
    changes: changes,
  };
}

/**
 * LCS (最长公共子序列) 算法
 * @param {Array} a - 旧段落数组
 * @param {Array} b - 新段落数组
 * @returns {Array} 匹配结果数组，每项包含 {type, oldIdx, newIdx}
 */
function computeLCS(a, b) {
  const m = a.length;
  const n = b.length;
  const dp = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0));

  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (a[i - 1].text === b[j - 1].text) {
        dp[i][j] = dp[i - 1][j - 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
      }
    }
  }

  // 回溯找出匹配
  const result = [];
  let i = m;
  let j = n;
  while (i > 0 && j > 0) {
    if (a[i - 1].text === b[j - 1].text) {
      result.unshift({ type: 'equal', oldIdx: i - 1, newIdx: j - 1 });
      i--;
      j--;
    } else if (dp[i - 1][j] > dp[i][j - 1]) {
      i--;
    } else {
      j--;
    }
  }
  return result;
}

module.exports = {
  convertDocxToHtml,
  convertDocxToStyledHtml,
  computeHtmlDiff,
};
