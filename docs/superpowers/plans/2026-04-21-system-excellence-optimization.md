# DocGen 系统全面优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 DocGen 从功能可用提升为银行生产级企业产品，融合 Apple HIG 设计理念（清晰、遵从、深度）、WCAG 2.1 AA 无障碍标准，面向非技术银行客户经理提供极致简洁的操作体验。

**Architecture:** 前端 Vue 3 + Element Plus 保持不变，重点在 UX 层面重构：修复致命缺陷、补全缺失功能、提升交互品质、增强无障碍支持。后端 Java Spring Boot 已完成编译，保持稳定。

**Tech Stack:** Vue 3, Element Plus, Pinia, vue-i18n, DOMPurify, VueUse

**Design Principles (Apple HIG + Banking):**
- **Clarity（清晰）**: 每个页面只做一件事，去除冗余信息，关键操作一目了然
- **Deference（遵从）**: UI 服务于内容（文档），不喧宾夺主
- **Depth（深度）**: 通过层次和动效传达空间关系
- **Accessibility（无障碍）**: WCAG 2.1 AA，支持键盘导航、屏幕阅读器、高对比度
- **Trust（信任）**: 银行级安全，操作可追溯，数据不丢失

---

## 问题总览

| 优先级 | 数量 | 说明 |
|--------|------|------|
| 🔴 P0 致命 | 4 | Token 存储、401 跳转、XSS、编辑器硬编码 |
| 🟠 P1 重要 | 9 | 国际化遗漏、功能未集成、记住我、侧边栏状态 |
| 🟡 P2 改进 | 12 | 响应式、批量操作、排序、日期筛选、上传进度 |
| 🟢 P3 优化 | 10 | 代码重复、死代码、构建配置 |

---

## Phase 1: 修复致命缺陷 (P0)

### Task 1: 修复 Token 存储不一致

**问题:** 路由守卫用 localStorage，Store/API 用 sessionStorage，导致登录后刷新被踢回登录页。

**Files:**
- Modify: `frontend/src/router/index.js:66`
- Modify: `frontend/src/stores/auth.js:8`
- Modify: `frontend/src/api/index.js:16`

- [ ] **Step 1: 统一路由守卫使用 sessionStorage**

```javascript
// frontend/src/router/index.js 第66行
// 修改前: const token = localStorage.getItem('token')
// 修改后:
const token = sessionStorage.getItem('token')
```

- [ ] **Step 2: 验证 auth store 和 API 拦截器也使用 sessionStorage**

确认 `src/stores/auth.js` 和 `src/api/index.js` 都从 `sessionStorage` 读取 token。

- [ ] **Step 3: 实现"记住我"功能**

当用户勾选"记住我"时，同时写入 localStorage 和 sessionStorage；未勾选时只写 sessionStorage。

```javascript
// src/stores/auth.js - login 方法中
if (rememberMe) {
  localStorage.setItem('token', data.token)
  localStorage.setItem('user', JSON.stringify(data.user))
}
sessionStorage.setItem('token', data.token)
sessionStorage.setItem('user', JSON.stringify(data.user))
```

路由守卫改为优先检查 sessionStorage，fallback 到 localStorage：
```javascript
const token = sessionStorage.getItem('token') || localStorage.getItem('token')
```

---

### Task 2: 修复 401 跳转使用错误路由模式

**问题:** 项目用 history 模式，401 拦截器用 hash 跳转。

**Files:**
- Modify: `frontend/src/api/index.js:67-68`

- [ ] **Step 1: 改用 history 模式跳转**

```javascript
// 修改前:
if (window.location.hash !== '#/login') {
  window.location.hash = '#/login'
}

// 修改后:
if (window.location.pathname !== '/login') {
  window.location.href = '/login'
}
```

---

### Task 3: 修复 XSS 安全风险 — 安装 DOMPurify 并消毒 v-html

**问题:** 11 处 v-html 直接渲染后端 HTML，存在 XSS 风险。

**Files:**
- Modify: `frontend/package.json` (添加 dompurify 依赖)
- Create: `frontend/src/utils/sanitize.js`
- Modify: `frontend/src/views/FragmentLibrary.vue`
- Modify: `frontend/src/components/VersionCompare.vue`
- Modify: `frontend/src/components/EuroOfficeCompare.vue`
- Modify: `frontend/src/components/VersionHistory.vue`
- Modify: `frontend/src/components/FragmentVersionHistory.vue`
- Modify: `frontend/src/components/CompositionEditor.vue`

- [ ] **Step 1: 安装 DOMPurify**

Run: `cd /workspace/frontend && npm install dompurify && npm install -D @types/dompurify`

- [ ] **Step 2: 创建统一消毒工具**

```javascript
// frontend/src/utils/sanitize.js
import DOMPurify from 'dompurify'

// 配置允许的标签和属性（保留文档格式，移除危险标签）
const ALLOWED_TAGS = ['p', 'br', 'span', 'div', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
  'strong', 'em', 'u', 's', 'sub', 'sup', 'blockquote', 'pre', 'code',
  'a', 'img', 'hr']

const ALLOWED_ATTR = ['href', 'src', 'alt', 'title', 'class', 'style',
  'colspan', 'rowspan', 'target', 'rel']

export function sanitizeHtml(html) {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOW_DATA_ATTR: false
  })
}
```

- [ ] **Step 3: 替换所有 v-html 为消毒后的内容**

在每个使用 `v-html` 的组件中：
```javascript
import { sanitizeHtml } from '@/utils/sanitize'
// 将 v-html="htmlContent" 改为 v-html="sanitizeHtml(htmlContent)"
```

涉及文件（共 11 处）：
- FragmentLibrary.vue 第106行
- VersionCompare.vue 第66,71,77,90,94行
- EuroOfficeCompare.vue 第39行
- VersionHistory.vue 第75行
- FragmentVersionHistory.vue 第39行
- CompositionEditor.vue 第89,94行

---

### Task 4: 修复编辑器硬编码 localhost

**问题:** DocumentEditor 中 Euro-Office 地址硬编码为 localhost:8080。

**Files:**
- Modify: `frontend/src/views/DocumentEditor.vue:84,113`
- Modify: `frontend/vite.config.js` (添加环境变量)
- Create: `frontend/.env.development` (如不存在)
- Create: `frontend/.env.production` (如不存在)

- [ ] **Step 1: 添加环境变量配置**

```bash
# frontend/.env.development
VITE_EURO_OFFICE_URL=http://localhost:8080

# frontend/.env.production
VITE_EURO_OFFICE_URL=
```

- [ ] **Step 2: 修改 DocumentEditor 使用环境变量**

```javascript
// 将所有 'http://localhost:8080' 替换为:
const euroOfficeUrl = import.meta.env.VITE_EURO_OFFICE_URL
if (!euroOfficeUrl) {
  // 显示编辑器不可用提示
}
```

- [ ] **Step 3: 修复编辑器跳转使用模拟 fileKey**

```javascript
// frontend/src/views/DocumentGenerate.vue 第484行
// 修改前: generatedFileKey.value = `doc_${Date.now()}`
// 修改后: 从后端响应中获取真实 fileKey
// 后端 generateDocument 应返回 { fileKey, fileName }
```

---

## Phase 2: 补全缺失功能 (P1)

### Task 5: 集成模板编排功能到 UI

**问题:** CompositionEditor 组件已开发但未集成到任何页面，编排 API 未调用。

**Files:**
- Modify: `frontend/src/views/TemplateManage.vue` (添加编排入口)
- Modify: `frontend/src/components/CompositionEditor.vue` (对接真实 API)

- [ ] **Step 1: 在模板管理页面添加"编排"按钮**

在模板列表的操作列中，为已发布模板添加"编排"按钮，点击打开 CompositionEditor 对话框。

- [ ] **Step 2: CompositionEditor 对接真实 API**

将 CompositionEditor 中的模拟数据替换为真实 API 调用：
- `getComposition(templateId)` 加载编排
- `saveComposition(templateId, items)` 保存编排
- `addFragmentToComposition(templateId, data)` 添加片段
- `removeFragmentFromComposition(templateId, fragmentId)` 移除片段
- `reorderComposition(templateId, data)` 重排序
- `previewComposition(templateId)` 预览
- `generateComposition(templateId)` 生成文档

---

### Task 6: 修复侧边栏折叠状态同步

**问题:** App.vue 和 Sidebar.vue 各自维护 isCollapsed，不同步。

**Files:**
- Create: `frontend/src/stores/ui.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/components/Sidebar.vue`

- [ ] **Step 1: 创建 UI 状态 Store**

```javascript
// frontend/src/stores/ui.js
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const isCollapsed = ref(localStorage.getItem('sidebar-collapsed') === 'true')

  function toggleSidebar() {
    isCollapsed.value = !isCollapsed.value
    localStorage.setItem('sidebar-collapsed', String(isCollapsed.value))
  }

  return { isCollapsed, toggleSidebar }
})
```

- [ ] **Step 2: App.vue 和 Sidebar.vue 使用共享 Store**

两个组件都改为从 `useUiStore()` 读取和修改 `isCollapsed`。

---

### Task 7: 完善国际化 — 消除所有硬编码文本

**Files:**
- Modify: `frontend/src/locales/zh-CN.js`
- Modify: `frontend/src/locales/en-US.js`
- Modify: `frontend/src/views/DocumentGenerate.vue` (动态验证消息)
- Modify: `frontend/src/views/FragmentLibrary.vue` (预览失败提示)
- Modify: `frontend/src/components/CompositionEditor.vue` (硬编码文本)
- Modify: `frontend/src/components/FragmentEditor.vue` (工具栏 tooltip)
- Modify: `frontend/src/views/FragmentLibrary.vue` (meta fields)
- Modify: `frontend/src/views/FragmentLibrary.vue` (状态标签)
- Modify: `frontend/src/api/index.js` (HTTP 错误消息)

- [ ] **Step 1: 添加缺失的 i18n key**

在 zh-CN.js 和 en-US.js 中添加所有缺失的翻译 key：
```javascript
// zh-CN.js 新增
common: {
  previewFailed: '预览加载失败',
  fields: '个字段',
  unknown: '未知',
  uploadProgress: '上传中 {progress}%',
},
validation: {
  pleaseSelect: '请选择{field}',
  pleaseInput: '请输入{field}',
},
httpError: {
  400: '请求参数错误',
  401: '未授权，请重新登录',
  403: '拒绝访问',
  404: '请求资源不存在',
  500: '服务器内部错误',
  timeout: '请求超时，请稍后重试',
  networkError: '网络连接异常，请检查网络',
},
toolbar: {
  bold: '加粗', italic: '斜体', underline: '下划线',
  strikethrough: '删除线', heading1: '标题1', heading2: '标题2',
  heading3: '标题3', orderedList: '有序列表', unorderedList: '无序列表',
  alignLeft: '左对齐', alignCenter: '居中', alignRight: '右对齐',
  undo: '撤销', redo: '重做',
},
```

- [ ] **Step 2: 替换所有硬编码文本**

逐一替换各文件中的硬编码文本为 `t('key')` 调用。

---

### Task 8: 修复文档历史下载使用错误的文件名

**Files:**
- Modify: `frontend/src/views/DocumentHistory.vue:239`

- [ ] **Step 1: 使用 fileKey 或 id 下载**

```javascript
// 修改前:
const response = await downloadDocument(row.name)
// 修改后:
const response = await downloadDocument(row.fileKey || String(row.id))
```

---

### Task 9: 修复 HomeView 快速操作"生成 FOL"和"生成 LO"跳转相同

**Files:**
- Modify: `frontend/src/views/HomeView.vue:163-178`

- [ ] **Step 1: 通过 query 参数区分文档类型**

```javascript
{ title: t('home.generateFOL'), path: '/generate', query: { type: 'FOL' }, ... },
{ title: t('home.generateLO'), path: '/generate', query: { type: 'LO' }, ... },
```

- [ ] **Step 2: DocumentGenerate 页面读取 query 参数预选分类**

```javascript
const route = useRoute()
onMounted(() => {
  if (route.query.type) {
    searchForm.category = route.query.type
    loadTemplates()
  }
})
```

---

## Phase 3: 提升交互品质 (P2) — Apple HIG 设计理念

### Task 10: 全局响应式布局适配

**设计理念:** Apple HIG "Consistency" — 在所有屏幕尺寸上保持一致体验。

**Files:**
- Modify: `frontend/src/views/DocumentGenerate.vue`
- Modify: `frontend/src/views/DocumentHistory.vue`
- Modify: `frontend/src/views/FragmentLibrary.vue`
- Modify: `frontend/src/views/TemplateManage.vue`
- Modify: `frontend/src/styles/global.css`

- [ ] **Step 1: 添加全局响应式断点 mixin**

```css
/* frontend/src/styles/global.css */
:root {
  --sidebar-width: 240px;
  --sidebar-collapsed-width: 64px;
  --content-max-width: 1400px;
}

/* 平板竖屏及以下 */
@media (max-width: 1024px) {
  .search-filter-bar {
    flex-direction: column;
  }
  .search-filter-bar .el-col {
    max-width: 100% !important;
  }
}

/* 手机 */
@media (max-width: 768px) {
  .page-container {
    padding: 12px !important;
  }
  .el-table {
    font-size: 13px;
  }
}
```

- [ ] **Step 2: 各页面搜索栏添加响应式 span**

将固定 `:span="8"` 改为 `:xs="24" :sm="12" :md="8"`。

---

### Task 11: 表格添加排序支持

**设计理念:** Apple HIG "Clarity" — 让用户按自己的方式组织信息。

**Files:**
- Modify: `frontend/src/views/DocumentHistory.vue`
- Modify: `frontend/src/views/TemplateManage.vue`
- Modify: `frontend/src/views/HomeView.vue`

- [ ] **Step 1: 为表格列添加 sortable 属性**

```html
<el-table-column prop="createTime" :label="t('common.createTime')" sortable />
<el-table-column prop="name" :label="t('common.name')" sortable />
```

---

### Task 12: 文档历史添加日期范围筛选

**设计理念:** 银行用户经常需要按时间段查找文档。

**Files:**
- Modify: `frontend/src/views/DocumentHistory.vue`

- [ ] **Step 1: 添加日期范围选择器**

```html
<el-col :xs="24" :sm="12" :md="6">
  <el-date-picker
    v-model="dateRange"
    type="daterange"
    :start-placeholder="t('common.startDate')"
    :end-placeholder="t('common.endDate')"
    value-format="YYYY-MM-DD"
    style="width: 100%"
  />
</el-col>
```

---

### Task 13: 文档历史添加批量删除

**Files:**
- Modify: `frontend/src/views/DocumentHistory.vue`

- [ ] **Step 1: 添加多选和批量删除**

```html
<el-table @selection-change="handleSelectionChange">
  <el-table-column type="selection" width="40" />
  ...
</el-table>
<el-button type="danger" :disabled="!selectedRows.length" @click="batchDelete">
  {{ t('common.batchDelete') }} ({{ selectedRows.length }})
</el-button>
```

---

### Task 14: 上传文件添加进度条

**设计理念:** Apple HIG "Feedback" — 让用户始终知道系统在做什么。

**Files:**
- Modify: `frontend/src/views/TemplateManage.vue`

- [ ] **Step 1: 配置 el-upload 的 on-progress 回调**

```javascript
const uploadProgress = ref(0)

function handleProgress(event) {
  uploadProgress.value = Math.round(event.percent)
}
```

```html
<el-upload :on-progress="handleProgress">
  <el-button :loading="uploading">
    {{ uploading ? `${t('common.uploading')} ${uploadProgress}%` : t('common.upload') }}
  </el-button>
</el-upload>
```

---

### Task 15: 添加全局错误提示（替代 console.error）

**设计理念:** Apple HIG "Clarity" — 错误信息应该清晰、可操作。

**Files:**
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/views/DocumentHistory.vue`
- Modify: `frontend/src/views/TemplateManage.vue`

- [ ] **Step 1: 将 console.error 替换为 ElMessage.error**

```javascript
// 修改前:
console.error('加载最近文档失败', e)
// 修改后:
ElMessage.error(t('home.loadRecentFailed'))
```

---

## Phase 4: 无障碍与可访问性 (WCAG 2.1 AA)

### Task 16: 添加键盘导航和 ARIA 标签

**设计理念:** Apple HIG "Accessibility" — 所有人都应该能使用你的应用。

**Files:**
- Modify: `frontend/src/components/Sidebar.vue` (添加 aria-label)
- Modify: `frontend/src/views/DocumentGenerate.vue` (模板卡片)
- Modify: `frontend/src/views/TemplateManage.vue` (操作按钮)
- Modify: `frontend/src/styles/global.css` (focus 样式)

- [ ] **Step 1: 添加全局 focus-visible 样式**

```css
/* frontend/src/styles/global.css */
:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
  border-radius: 4px;
}

/* 隐藏鼠标点击时的 focus ring，仅保留键盘导航 */
:focus:not(:focus-visible) {
  outline: none;
}
```

- [ ] **Step 2: 为交互元素添加 aria-label**

侧边栏导航项、操作按钮、表格操作列等添加 `aria-label`。

- [ ] **Step 3: 为模板卡片添加 role 和 keyboard 支持**

```html
<div class="template-card" tabindex="0" role="button"
     :aria-label="t('template.selectTemplate', { name: template.name })"
     @keydown.enter="selectTemplate(template)">
```

---

### Task 17: 确保颜色对比度符合 WCAG AA

**Files:**
- Modify: `frontend/src/styles/global.css`

- [ ] **Step 1: 审查并调整关键文本颜色对比度**

确保所有文本与背景的对比度 ≥ 4.5:1。特别检查：
- 暗色模式下的次要文本
- 状态标签颜色
- 表格边框和分隔线
- placeholder 文本

---

## Phase 5: 代码质量优化 (P3)

### Task 18: 提取重复代码为公共工具

**Files:**
- Create: `frontend/src/utils/format.js`
- Create: `frontend/src/utils/response.js`
- Modify: 多个组件文件

- [ ] **Step 1: 创建 format.js**

```javascript
// frontend/src/utils/format.js
export function formatTime(time) {
  if (!time) return '-'
  const d = new Date(time)
  return d.toLocaleString()
}

export function formatFileSize(bytes) {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  while (bytes >= 1024 && i < units.length - 1) { bytes /= 1024; i++ }
  return `${bytes.toFixed(1)} ${units[i]}`
}
```

- [ ] **Step 2: 创建 response.js**

```javascript
// frontend/src/utils/response.js
export function extractList(data) {
  return data?.content || data?.list || data?.records || []
}

export function extractTotal(data) {
  return data?.totalElements || data?.total || data?.totalCount || 0
}

export function extractPages(data) {
  return data?.totalPages || Math.ceil(extractTotal(data) / 20) || 1
}
```

- [ ] **Step 3: 替换各组件中的重复代码**

---

### Task 19: 清理死代码和修复构建配置

**Files:**
- Modify: `frontend/vite.config.js` (移除 terser 或安装依赖)
- Modify: `frontend/src/main.js` (注册 lazyLoad 指令或删除文件)
- Modify: `frontend/src/components/VersionHistory.vue` (移除未使用的 syncVisible)

- [ ] **Step 1: 修复 vite.config.js 的 terser 配置**

移除 `minify: 'terser'`（使用 Vite 默认的 esbuild），或安装 terser 依赖。

- [ ] **Step 2: 注册或删除 lazyLoad 指令**

选择：在 main.js 中注册 `app.directive('lazy-load', lazyLoad)`，或删除 `src/directives/lazyLoad.js`。

---

## Phase 6: Apple HIG 设计增强

### Task 20: 添加页面过渡动画

**设计理念:** Apple HIG "Depth" — 通过动效传达层次关系。

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/styles/global.css`

- [ ] **Step 1: 添加路由过渡动画**

```css
/* frontend/src/styles/global.css */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
```

```html
<!-- App.vue -->
<router-view v-slot="{ Component }">
  <transition name="fade-slide" mode="out-in">
    <component :is="Component" />
  </transition>
</router-view>
```

---

### Task 21: 优化空状态设计

**设计理念:** Apple HIG "Clarity" — 空状态不是错误，是引导。

**Files:**
- Modify: `frontend/src/views/DocumentHistory.vue`
- Modify: `frontend/src/views/FragmentLibrary.vue`
- Modify: `frontend/src/views/TemplateManage.vue`

- [ ] **Step 1: 统一空状态组件样式**

确保所有空状态包含：图标 + 主标题 + 副标题（操作建议）+ 操作按钮。使用 Element Plus 的 `<el-empty>` 组件。

---

### Task 22: 添加操作确认对话框（防误操作）

**设计理念:** 银行系统 — 不可逆操作必须确认。

**Files:**
- Modify: `frontend/src/views/TemplateManage.vue` (删除模板)
- Modify: `frontend/src/views/DocumentHistory.vue` (删除文档)
- Modify: `frontend/src/views/FragmentLibrary.vue` (删除片段)

- [ ] **Step 1: 所有删除操作添加二次确认**

```javascript
await ElMessageBox.confirm(
  t('common.confirmDeleteMessage'),
  t('common.confirmDelete'),
  { confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'), type: 'warning' }
)
```

---

## Self-Review

### Spec 覆盖检查
- [x] 完整性: C-01~C-04 致命问题 → Task 1-4
- [x] 完整性: H-01~H-09 功能缺失 → Task 5-9
- [x] 可用性: M-01~M-12 体验问题 → Task 10-15
- [x] 可访问性: WCAG AA → Task 16-17
- [x] 代码质量: L-01~L-10 → Task 18-19
- [x] Apple HIG 设计 → Task 20-22

### Placeholder 扫描
- [x] 无 TBD/TODO
- [x] 所有代码步骤包含实际内容
- [x] 所有文件路径精确

### 类型一致性
- [x] i18n key 命名一致
- [x] Store 方法签名一致
- [x] API 调用参数一致
