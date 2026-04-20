/**
 * create-templates.js
 *
 * 使用 PizZip + docxtemplater 程序化生成银行文档模板 (.docx)
 * 生成的模板包含 {fieldName} 格式的占位符，供后端填充数据
 *
 * 使用方法:
 *   npm install pizzip docxtemplater
 *   node create-templates.js
 */

const PizZip = require('pizzip')
const Docxtemplater = require('docxtemplater')
const fs = require('fs')
const path = require('path')

// ============================================================
// FOL (Form of Letter) -- 银行函件模板
// ============================================================

const FOL_TEMPLATE = `
{bankName}
{bankAddress}

Ref: {refNumber}
Date: {date}

{recipientName}
{recipientAddress}

Subject: {subject}

{salutation}

{bodyContent}

{closingRemark}


_______________________
{signatoryName}
{signatoryTitle}
{department}
`

// ============================================================
// LO (Letter of Credit) -- 信用证模板
// ============================================================

const LO_TEMPLATE = `
IRREVOCABLE DOCUMENTARY CREDIT


Credit Number:        {lcNumber}
Issuing Bank:         {issuingBank}
Date of Issue:        {issuingDate}
Expiry Date:          {expiryDate}
Expiry Place:         {expiryPlace}

--------------------------------------------------------------------------------

APPLICANT
{applicant}
{applicantAddress}

BENEFICIARY
{beneficiary}
{beneficiaryAddress}

--------------------------------------------------------------------------------

CURRENCY AND AMOUNT:  {currency} {lcAmount}
AVAILABLE WITH/BY:    {availableWith}
PARTIAL SHIPMENT:     {partialShipment}
TRANSSHIPMENT:        {transshipment}
LATEST SHIPMENT:      {latestShipmentDate}
PORT OF LOADING:      {portOfLoading}
PORT OF DISCHARGE:    {portOfDischarge}

--------------------------------------------------------------------------------

DESCRIPTION OF GOODS
{descriptionOfGoods}

--------------------------------------------------------------------------------

DOCUMENTS REQUIRED
{documentsRequired}

--------------------------------------------------------------------------------

ADDITIONAL CONDITIONS
{additionalConditions}

--------------------------------------------------------------------------------

CHARGES
{charges}

--------------------------------------------------------------------------------

CONFIRMATION INSTRUCTIONS: {confirmation}

--------------------------------------------------------------------------------

INSTRUCTIONS TO BANK
{instructionsToBank}

--------------------------------------------------------------------------------

This credit is subject to the Uniform Customs and Practice for Documentary Credits (UCP 600).

Issuing Bank Reference: {issuingBankRef}


_______________________
For and on behalf of
{issuingBank}
`

// ============================================================
// Helper: Create a minimal valid .docx file with content
// ============================================================

/**
 * Creates a minimal .docx file from text content using PizZip + docxtemplater.
 * This approach builds a valid DOCX (which is a ZIP of XML files) from scratch.
 *
 * @param {string} content - The template content with {placeholders}
 * @returns {Buffer} The .docx file as a Buffer
 */
function createDocxFromContent(content) {
  // A minimal valid .docx is a ZIP containing specific XML files.
  // We use PizZip to create the ZIP structure and docxtemplater to process the template.

  // Read the minimal template (we'll create it inline)
  const zip = new PizZip()

  // [Content_Types].xml - Required by OOXML
  zip.file(
    '[Content_Types].xml',
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>`
  )

  // _rels/.rels - Root relationships
  zip.file(
    '_rels/.rels',
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`
  )

  // word/_rels/document.xml.rels - Document relationships
  zip.file(
    'word/_rels/document.xml.rels',
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>`
  )

  // word/document.xml - Main document content
  // Convert plain text content to Word XML paragraphs
  const paragraphs = content
    .split('\n')
    .map((line) => {
      // Escape XML special characters
      const escaped = line
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;')

      // Determine paragraph style based on content
      let style = ''
      if (line.startsWith('IRREVOCABLE')) {
        style = '<w:pPr><w:pStyle w:val="Title"/><w:jc w:val="center"/></w:pPr>'
      } else if (line.startsWith('---')) {
        style = '<w:pPr><w:pBdr><w:bottom w:val="single" w:sz="4" w:space="1" w:color="1a365d"/></w:pBdr></w:pPr>'
      } else if (line.startsWith('DESCRIPTION OF GOODS') || line.startsWith('DOCUMENTS REQUIRED') ||
                 line.startsWith('ADDITIONAL CONDITIONS') || line.startsWith('CHARGES') ||
                 line.startsWith('CONFIRMATION') || line.startsWith('INSTRUCTIONS TO BANK') ||
                 line.startsWith('APPLICANT') || line.startsWith('BENEFICIARY')) {
        style = '<w:pPr><w:rPr><w:b/></w:rPr></w:pPr>'
      } else if (line.startsWith('Subject:') || line.startsWith('Ref:') || line.startsWith('Date:') ||
                 line.startsWith('Credit Number:') || line.startsWith('Issuing Bank:') ||
                 line.startsWith('Date of Issue:') || line.startsWith('Expiry Date:') ||
                 line.startsWith('Expiry Place:') || line.startsWith('CURRENCY AND AMOUNT:') ||
                 line.startsWith('AVAILABLE WITH/BY:') || line.startsWith('PARTIAL SHIPMENT:') ||
                 line.startsWith('TRANSSHIPMENT:') || line.startsWith('LATEST SHIPMENT:') ||
                 line.startsWith('PORT OF LOADING:') || line.startsWith('PORT OF DISCHARGE:') ||
                 line.startsWith('CONFIRMATION INSTRUCTIONS:') ||
                 line.startsWith('Issuing Bank Reference:')) {
        style = '<w:pPr><w:rPr><w:b/></w:rPr></w:pPr>'
      } else if (line.trim() === '') {
        return '<w:p/>'
      }

      return `<w:p>${style}<w:r><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/></w:rPr><w:t xml:space="preserve">${escaped}</w:t></w:r></w:p>`
    })
    .join('\n')

  zip.file(
    'word/document.xml',
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas"
            xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
            xmlns:o="urn:schemas-microsoft-com:office:office"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"
            xmlns:v="urn:schemas-microsoft-com:vml"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:w10="urn:schemas-microsoft-com:office:word"
            xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml"
            xmlns:wpg="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup"
            xmlns:wpi="http://schemas.microsoft.com/office/word/2010/wordprocessingInk"
            xmlns:wne="http://schemas.microsoft.com/office/word/2006/wordml"
            xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
            mc:Ignorable="w14 wp14">
  <w:body>
    ${paragraphs}
    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>`
  )

  // Now use docxtemplater to process the template (it will keep the placeholders as-is
  // since we're not providing data - we just want to validate the structure)
  const doc = new Docxtemplater(zip, {
    paragraphLoop: true,
    linebreaks: true
  })

  // Render with empty data to keep placeholders intact
  doc.render({})

  const buf = doc.getZip().generate({
    type: 'nodebuffer',
    compression: 'DEFLATE'
  })

  return buf
}

// ============================================================
// Main: Generate template files
// ============================================================

function main() {
  const outputDir = __dirname

  console.log('=== 银文通 - 银行文档模板生成器 ===\n')

  // Generate FOL template
  try {
    console.log('正在生成 FOL (银行函件) 模板...')
    const folBuffer = createDocxFromContent(FOL_TEMPLATE.trim())
    const folPath = path.join(outputDir, 'FOL_银行函件模板.docx')
    fs.writeFileSync(folPath, folBuffer)
    console.log(`  [OK] 已生成: ${folPath} (${(folBuffer.length / 1024).toFixed(1)} KB)`)
  } catch (error) {
    console.error(`  [ERROR] FOL 模板生成失败: ${error.message}`)
  }

  // Generate LO template
  try {
    console.log('正在生成 LO (信用证) 模板...')
    const loBuffer = createDocxFromContent(LO_TEMPLATE.trim())
    const loPath = path.join(outputDir, 'LO_信用证模板.docx')
    fs.writeFileSync(loPath, loBuffer)
    console.log(`  [OK] 已生成: ${loPath} (${(loBuffer.length / 1024).toFixed(1)} KB)`)
  } catch (error) {
    console.error(`  [ERROR] LO 模板生成失败: ${error.message}`)
  }

  console.log('\n=== 模板生成完成 ===')
  console.log('\n生成的模板文件包含 {fieldName} 格式的占位符。')
  console.log('后端可使用 docxtemplater 填充实际数据生成最终文档。')
}

// Run
main()
