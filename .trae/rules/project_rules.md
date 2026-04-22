---
description: 银文通 DocGen 项目规则 - 面向银行客户经理的企业级文档生成平台
globs:
  - "**/*"
---

# 银文通 DocGen - 项目规则

## 项目概述

银文通 DocGen 是面向国际大型银行的企业级文档生成平台，目标用户为银行普通客户经理等非技术员工。采用前后端分离架构。

## 技术栈约束

### 后端 (backend/)
- **语言**: Java 17
- **框架**: Spring Boot 3.2.5
- **ORM**: Spring Data JPA (Hibernate)
- **数据库**: PostgreSQL (生产) / H2 (开发)
- **安全**: Spring Security + JWT (jjwt 0.12.5)
- **文档引擎**: Apache POI 5.2.5 + docx4j 11.4.9
- **构建**: Maven
- **包路径**: `com.docgen`
- **API 前缀**: `/api/v1/`
- **端口**: 3001

### 前端 (frontend/)
- **框架**: Vue 3.4 (Composition API + `<script setup>`)
- **构建**: Vite 5
- **UI 库**: Element Plus 2.6
- **状态管理**: Pinia
- **路由**: Vue Router 4 (history mode)
- **国际化**: vue-i18n (中文 zh-CN / 英文 en-US)
- **HTTP**: Axios
- **XSS 防护**: DOMPurify (所有 v-html 必须经过 sanitizeHtml())
- **包管理器**: npm

### 基础设施
- **网关**: Kong
- **缓存**: Redis
- **对象存储**: MinIO
- **消息队列**: Kafka (可选)
- **文档编辑器**: Euro-Office (可选)
- **监控**: Prometheus + Grafana

## 架构规范

### 后端分层
```
controller/ → service/ → repository/ → entity/
     ↓              ↓
    dto/        util/
```

- **Controller**: 只做参数校验和响应封装，不含业务逻辑
- **Service**: 业务逻辑层，事务在 Service 层管理
- **Repository**: 使用 JPA `@Query` + 动态条件，禁止全表扫描后内存过滤
- **DTO**: 请求/响应统一使用 DTO，禁止直接暴露 Entity
- **统一响应**: `Result<T>` 格式 `{code, message, data}`

### 前端规范
- **组件风格**: 统一使用 `<script setup>` Composition API
- **样式**: Scoped CSS + CSS 变量 (var(--xxx))，支持暗色模式
- **状态**: 全局状态用 Pinia store，组件状态用 ref/reactive
- **国际化**: 所有用户可见文本必须使用 `$t()` 或 `t()`
- **错误处理**: 使用 `ElMessage.error(t('xxx'))`，禁止 `console.error`
- **API 调用**: 统一通过 `@/api/index.js`，不直接使用 axios

## 安全规范

- 所有 API 需 JWT 认证（白名单除外：auth/login, health）
- 多租户隔离：通过 `X-Tenant-Id` 请求头
- 密码存储：PBKDF2WithHmacSHA512 (100k iterations)
- XSS 防护：所有 `v-html` 必须使用 `sanitizeHtml()` 包裹
- 文件上传：限制 .docx 格式，最大 10MB
- 审计日志：关键操作自动记录

## 代码风格

### Java
- 遵循标准 Java 命名规范
- Controller 方法使用 `@GetMapping` / `@PostMapping` 等注解
- Service 方法使用 `@Transactional` 管理事务
- 使用 Lombok `@Slf4j` 记录日志

### JavaScript/Vue
- 缩进: 2 空格
- 引号: 单引号
- 分号: 必须
- 尾逗号: ES5
- 行宽: 100 字符
- 禁止 `var`，使用 `const` / `let`
- 禁止 `console.error`，使用 `ElMessage.error`

## 数据库

### 核心表 (10张)
- `users` - 用户表
- `tenants` - 租户表
- `templates` - 模板表
- `documents` - 文档记录表
- `audit_logs` - 审计日志表
- `template_versions` - 模板版本表
- `template_approvals` - 模板审批表
- `fragments` - 片段表
- `fragment_versions` - 片段版本表
- `template_compositions` - 模板编排表

## 设计原则

- **Apple HIG**: 清晰 (Clarity)、遵从 (Deference)、深度 (Depth)
- **WCAG 2.1 AA**: 焦点可见、色彩对比度 ≥ 4.5:1、键盘导航
- **银行 UX**: 简洁直观、操作可逆、错误友好提示、加载状态反馈
- **多语言**: 中文为主，英文为辅，所有文本走 i18n
