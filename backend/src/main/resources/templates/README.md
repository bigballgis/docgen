# 合同模板示例 (contract_template.docx)

## 概述

本目录用于存放 Word 模板文件。由于无法直接创建 `.docx` 二进制文件，请按照以下说明手动制作模板。

## 如何制作合同模板

### 步骤 1: 创建 Word 文档

使用 Microsoft Word 或 WPS Office 创建一个新的 `.docx` 文档，文件名为 `contract_template.docx`。

### 步骤 2: 编写模板内容

在文档中输入以下内容，**注意占位符必须原样保留**（包括 `${}` 符号）：

---

**${title}**

**合同编号：** HT-2024-001

**甲方（以下简称"甲方"）：** ${partyA}

**乙方（以下简称"乙方"）：** ${partyB}

鉴于甲乙双方本着平等互利、诚实信用的原则，经友好协商，就相关事宜达成如下协议：

---

### 第一条 合同标的

${content}

### 第二条 合同金额

本合同总金额为人民币（大写）**${amount}** 元整。

### 第三条 合同期限

本合同自 ${contractDate} 起生效。

### 第四条 违约责任

任何一方违反本合同约定的，应当承担违约责任，赔偿对方因此遭受的损失。

### 第五条 争议解决

本合同在履行过程中发生的争议，由双方协商解决；协商不成的，提交有管辖权的人民法院诉讼解决。

---

**甲方（盖章）：** ____________________　　**乙方（盖章）：** ____________________

**签订日期：** ${signDate}

---

### 步骤 3: 保存文件

将文件保存为 `.docx` 格式，放置到本目录（`src/main/resources/templates/`）下。

## 占位符说明

| 占位符 | 含义 | 类型 | 示例值 | 是否必填 |
|--------|------|------|--------|----------|
| `${title}` | 合同标题 | text | 劳动合同 | 是 |
| `${partyA}` | 甲方名称 | text | 北京XX科技有限公司 | 是 |
| `${partyB}` | 乙方名称 | text | 上海XX贸易有限公司 | 是 |
| `${contractDate}` | 合同生效日期 | date | 2024-01-01 | 是 |
| `${amount}` | 合同金额 | text | 壹拾万元整（100,000.00） | 是 |
| `${content}` | 合同主要内容 | textarea | 甲方向乙方采购... | 是 |
| `${signDate}` | 签订日期 | date | 2024-01-15 | 是 |

## 上传模板

模板制作完成后，通过以下 API 上传：

```
POST /api/templates/upload
Content-Type: multipart/form-data

参数：
- file: 模板文件 (.docx)
- name: 模板名称（如"劳动合同模板"）
- description: 模板描述
- category: 分类（如"合同"、"通知"、"报告"）
- fields: 字段定义 JSON（可选，也可通过解析接口自动生成）
```

### 字段定义 JSON 示例

```json
[
  {
    "name": "title",
    "label": "合同标题",
    "type": "text",
    "required": true
  },
  {
    "name": "partyA",
    "label": "甲方名称",
    "type": "text",
    "required": true
  },
  {
    "name": "partyB",
    "label": "乙方名称",
    "type": "text",
    "required": true
  },
  {
    "name": "contractDate",
    "label": "合同生效日期",
    "type": "date",
    "required": true
  },
  {
    "name": "amount",
    "label": "合同金额",
    "type": "text",
    "required": true
  },
  {
    "name": "content",
    "label": "合同内容",
    "type": "textarea",
    "required": true
  },
  {
    "name": "signDate",
    "label": "签订日期",
    "type": "date",
    "required": true
  }
]
```

## 自动解析字段

也可以在上传模板后，通过以下接口自动解析模板中的占位符字段：

```
POST /api/templates/{id}/parse-fields
```

该接口会自动提取模板中所有 `${xxx}` 格式的占位符，返回字段名列表。

## 生成文档

使用以下接口生成文档：

```
POST /api/documents/generate
Content-Type: application/json

{
  "templateId": 1,
  "data": {
    "title": "技术服务合同",
    "partyA": "北京科技创新有限公司",
    "partyB": "上海软件开发有限公司",
    "contractDate": "2024-03-01",
    "amount": "伍拾万元整（500,000.00）",
    "content": "甲方委托乙方进行系统开发，乙方应按照甲方需求完成全部开发工作...",
    "signDate": "2024-03-01"
  },
  "outputFormat": "docx"
}
```

`outputFormat` 可选值为 `docx`（默认）或 `pdf`。

## 注意事项

1. **占位符格式**：必须使用 `${fieldName}` 格式，其中 `fieldName` 只能包含字母、数字和下划线
2. **Word 兼容性**：建议使用 Microsoft Word 2016+ 或 WPS Office 制作模板
3. **特殊字符**：避免在占位符前后插入多余空格或特殊格式字符
4. **图片和表格**：模板中可以包含图片和表格，docx-stamper 会保留这些元素
5. **条件表达式**：docx-stamper 支持 `${#if condition}...${/if}` 等条件表达式，可用于可选内容的显示控制
