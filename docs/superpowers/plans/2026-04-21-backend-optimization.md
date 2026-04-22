# DocGen Java 后端深度优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Java 后端的编译错误、安全漏洞、性能问题和 API 兼容性问题，使其可以正常编译运行并与前端完全对接。

**Architecture:** 保持 Spring Boot 3.2 + JPA + PostgreSQL 分层架构不变，修复 Controller-Service 方法签名不一致、收紧安全配置、优化数据库查询性能、统一响应格式。

**Tech Stack:** Java 17, Spring Boot 3.2.5, Spring Data JPA, Spring Security, jjwt 0.12.5, Apache POI, Lombok

---

## 问题总览

| 严重程度 | 数量 | 说明 |
|----------|------|------|
| 🔴 严重 | ~45 | 编译错误(~40处)、安全漏洞(5处) |
| 🟡 中等 | ~15 | 逻辑错误、性能问题、API不匹配(3处) |
| 🟢 轻微 | ~20 | 代码规范、冗余代码 |

---

## Task 1: 修复 pom.xml 依赖问题

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: 移除重复的 jjwt 依赖**

移除单独的 `jjwt-api`、`jjwt-impl`、`jjwt-jackson` 模块（已被聚合包 `jjwt` 包含），添加 `spring-boot-configuration-processor`。

```xml
<!-- 移除这三个 -->
<!-- jjwt-api 0.12.5 -->
<!-- jjwt-impl 0.12.5 runtime -->
<!-- jjwt-jackson 0.12.5 runtime -->
<!-- 保留聚合包即可 -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>0.12.5</version>
</dependency>

<!-- 添加配置处理器 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 2: 验证 pom.xml 修改**

Run: `cd /workspace/backend && mvn dependency:tree -q | head -50`
Expected: 无重复 jjwt 模块

---

## Task 2: 修复 application.yml 配置

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 修复 Hibernate Dialect 和安全配置**

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
    # 移除已废弃的 dialect 配置，让 Hibernate 自动检测
    # properties:
    #   hibernate:
    #     dialect: org.hibernate.dialect.PostgreSQLDialect

  # 生产安全配置
  h2:
    console:
      enabled: false  # 默认禁用 H2 控制台

---
# 开发环境 profile
spring:
  config:
    activate:
      on-profile: dev
  h2:
    console:
      enabled: true  # 仅开发环境启用
```

- [ ] **Step 2: 添加 JWT 密钥环境变量提示**

在注释中标注生产环境必须通过环境变量设置 JWT_SECRET。

---

## Task 3: 统一响应格式，消除 Result vs ResponseUtil 重复

**Files:**
- Modify: `backend/src/main/java/com/docgen/dto/Result.java`
- Modify: `backend/src/main/java/com/docgen/exception/GlobalExceptionHandler.java`
- Delete: `backend/src/main/java/com/docgen/util/ResponseUtil.java`

- [ ] **Step 1: 增强 Result.java 为统一响应类**

```java
package com.docgen.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    private int code;
    private String message;
    private T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success(T data, String message) {
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
```

- [ ] **Step 2: 更新 GlobalExceptionHandler 使用 Result**

将所有 `ResponseUtil.error()` 替换为 `Result.error()`，`ResponseUtil.success()` 替换为 `Result.success()`。

- [ ] **Step 3: 删除 ResponseUtil.java**

确认所有引用已替换后删除文件。

---

## Task 4: 修复 AuthService 编译错误

**Files:**
- Modify: `backend/src/main/java/com/docgen/service/AuthService.java`

- [ ] **Step 1: 修复 PasswordUtil 注入问题**

`PasswordUtil` 是静态工具类，不应通过构造器注入。改为直接使用静态方法调用。

```java
// 移除: private final PasswordUtil passwordUtil;
// 所有 passwordUtil.hash() 改为 PasswordUtil.hash()
// 所有 passwordUtil.verify() 改为 PasswordUtil.verify()
```

- [ ] **Step 2: 修复所有方法签名，与 AuthController 调用对齐**

| 方法 | 当前签名 | 修复为 |
|------|---------|--------|
| `login` | `(String, String)` | `(LoginRequest)` |
| `getUserList` | `(int, int)` | `(String tenantId, int page, int size)` |
| `updateUserRole` | `void` | `UserInfo` |
| `changePassword` | `(Long, String, String)` | `(Long, ChangePasswordRequest)` |
| `getProfile` | 不存在 | 新增 `getProfile(Long userId)` |
| `updateUserStatus` | 不存在 | 新增 `updateUserStatus(Long userId, String status)` |

- [ ] **Step 3: 验证编译**

Run: `cd /workspace/backend && mvn compile -pl . -q 2>&1 | grep -i "error" | head -20`

---

## Task 5: 修复 TemplateController + TemplateService 编译错误

**Files:**
- Modify: `backend/src/main/java/com/docgen/controller/TemplateController.java`
- Modify: `backend/src/main/java/com/docgen/service/TemplateService.java`

- [ ] **Step 1: 在 TemplateController 中注入 ApprovalService**

审批相关方法（submit/approve/reject/pending）应调用 `ApprovalService` 而非 `TemplateService`。

```java
private final ApprovalService approvalService;
```

- [ ] **Step 2: 修复 TemplateController 方法调用**

| 调用 | 修复 |
|------|------|
| `templateService.getPendingTemplates(...)` | `approvalService.getPendingApprovals(tenantId)` |
| `templateService.submitForApproval(...)` | `approvalService.submitForApproval(id, userId)` |
| `templateService.approveTemplate(...)` | `approvalService.approveTemplate(id, userId, comment)` |
| `templateService.rejectTemplate(...)` | `approvalService.rejectTemplate(id, userId, reason)` |
| `templateService.deleteTemplate(id)` | `templateService.removeTemplate(id)` |
| `templateService.getCategories(tenantId)` | `templateService.getAllCategories(tenantId)` |
| `templateService.exportTemplates(...)` | 新增导出方法或移除 |

- [ ] **Step 3: 修复 TemplateService.getTemplateList 参数顺序**

使参数顺序与 Controller 调用一致。

- [ ] **Step 4: 修复 rejectTemplate 的 DTO 字段名**

前端发送 `{ reason: "..." }`，后端 `ApprovalRequest` 需要支持 `reason` 字段。

在 `ApprovalRequest.java` 中添加 `reason` 字段作为 `comment` 的别名：
```java
private String comment;
// 添加 reason 字段，在 Service 中优先使用 reason
private String reason;
```

在 `ApprovalService.rejectTemplate` 中：`String reason = (request.getReason() != null) ? request.getReason() : request.getComment();`

---

## Task 6: 修复 DocumentController + DocumentService 编译错误和 API 路径

**Files:**
- Modify: `backend/src/main/java/com/docgen/controller/DocumentController.java`
- Modify: `backend/src/main/java/com/docgen/service/DocumentService.java`

- [ ] **Step 1: 修复 getDocumentList 路径（前端调用 GET /documents）**

```java
// 将 @GetMapping("/list") 改为:
@GetMapping("")
public Result<?> getDocumentList(
    @RequestParam(required = false) String keyword,
    @RequestParam(required = false) String status,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) { ... }
```

- [ ] **Step 2: 修复 DocumentService 方法签名**

| 方法 | 修复 |
|------|------|
| `generateDocument` | 签名改为 `(GenerateDocumentRequest, Long userId, String tenantId, HttpServletResponse)` |
| `getDocumentList` | 添加 `keyword` 和 `status` 参数 |
| `getDocumentStatus` | 返回 `Map<String, Object>` 而非 `String` |
| `generateDocumentSync` | 新增或移除 Controller 中的调用 |
| `exportDocuments` | 新增或移除 Controller 中的调用 |

- [ ] **Step 3: 添加缺失的 UUID 导入**

```java
import java.util.UUID;
```

---

## Task 7: 修复 EditorController 路径参数问题

**Files:**
- Modify: `backend/src/main/java/com/docgen/controller/EditorController.java`

- [ ] **Step 1: 将 fileKey 从查询参数改为路径参数**

前端调用: `GET /editor/config/${fileKey}`

```java
// 修改前:
@GetMapping("/config")
public Result<?> getConfig(@RequestParam String fileKey) { ... }

// 修改后:
@GetMapping("/config/{fileKey}")
public Result<?> getConfig(@PathVariable String fileKey) { ... }
```

- [ ] **Step 2: 修复 EditorService 方法名**

| Controller 调用 | Service 方法名 | 修复 |
|----------------|---------------|------|
| `handleCallback(data)` | `handleEditorCallback(Map)` | 统一为 `handleEditorCallback` |
| `downloadFile(fileKey, response)` | 不存在 | 新增 `downloadEditorFile(String, HttpServletResponse)` |

---

## Task 8: 修复 CompositionController + CompositionService 编译错误

**Files:**
- Modify: `backend/src/main/java/com/docgen/controller/CompositionController.java`
- Modify: `backend/src/main/java/com/docgen/service/CompositionService.java`

- [ ] **Step 1: 修复所有方法名和返回类型不匹配**

| Controller 调用 | Service 方法 | 修复 |
|----------------|-------------|------|
| `addFragment(templateId, request)` | `addFragmentToTemplate(Long, Long, String, Boolean)` | Controller 内解包 request 后调用 |
| `removeFragment(templateId, fragmentId)` | `removeFragmentFromTemplate(Long, Long)` 返回 void | Controller 调用后返回 `Result.success()` |
| `reorderFragments(...)` 返回 `List` | `reorderFragments(...)` 返回 void | Service 改为返回更新后的列表 |
| `previewComposition(templateId)` | `generateComposedHtml(Long)` | Controller 方法名改为调用 `generateComposedHtml` |
| `generateComposition(templateId, response)` | `generateComposedDocx(Long)` 返回 String | Controller 接收路径后写入 response |

- [ ] **Step 2: 修复 getComposition 返回类型**

Service 返回 `List<Map<String, Object>>`，Controller 包装为 `Result<List<Map<String, Object>>>`。

---

## Task 9: 修复 FragmentController + FragmentService 编译错误

**Files:**
- Modify: `backend/src/main/java/com/docgen/controller/FragmentController.java`
- Modify: `backend/src/main/java/com/docgen/service/FragmentService.java`

- [ ] **Step 1: 修复方法名和参数顺序**

| Controller 调用 | 修复 |
|----------------|------|
| `getFragmentCategories(tenantId)` | 改为 `getAllCategories(tenantId)` |
| `getFragmentVersions(id)` | 改为 `getFragmentVersionList(id)` |
| `getFragmentList` 参数顺序 | Controller 内按 Service 期望的顺序传参 |

- [ ] **Step 2: 修复 tags 参数类型转换**

Controller 接收 `String tags`（逗号分隔），需转换为 `List<String>`：
```java
List<String> tagList = tags != null ? Arrays.asList(tags.split(",")) : null;
```

- [ ] **Step 3: 修复 FragmentService 中的无效代码**

移除第 319-323 行的无意义 `map` 操作。

---

## Task 10: 修复 VersionController + VersionService 编译错误

**Files:**
- Modify: `backend/src/main/java/com/docgen/controller/VersionController.java`
- Modify: `backend/src/main/java/com/docgen/service/VersionService.java`

- [ ] **Step 1: 修复方法名**

| Controller 调用 | Service 方法 | 修复 |
|----------------|-------------|------|
| `getVersionDetail(...)` | `getVersion(Long, Integer)` | 改为 `getVersion` |
| `getVersionPreview(...)` | `getVersionHtml(Long, Integer)` | 改为 `getVersionHtml` |
| `rollbackVersion(...)` 返回 Map | `rollbackToVersion(...)` 返回 TemplateVersion | Controller 转换为 Map 或直接返回 Result |

---

## Task 11: 修复 TenantController + TenantService 编译错误

**Files:**
- Modify: `backend/src/main/java/com/docgen/controller/TenantController.java`
- Modify: `backend/src/main/java/com/docgen/service/TenantService.java`

- [ ] **Step 1: 修复参数类型**

| Controller 调用 | 修复 |
|----------------|------|
| `createTenant(Map)` | Controller 内提取 name/code 后调用 `createTenant(String, String)` |
| `updateTenant(id, Map)` | Controller 内提取字段后调用 `updateTenant(Long, String, String, String)` |
| `getCurrentTenant()` | 传入 `TenantContext.getTenantId()` |

---

## Task 12: 修复安全配置

**Files:**
- Modify: `backend/src/main/java/com/docgen/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/docgen/config/WebConfig.java`
- Modify: `backend/src/main/java/com/docgen/middleware/TenantInterceptor.java`

- [ ] **Step 1: 收紧 SecurityConfig 白名单**

移除 `/api/v1/templates`、`/api/v1/fragments`、`/api/v1/documents` 的 `permitAll()`。
这些接口应为可选认证（有 token 则解析，无 token 也放行），使用自定义 `OptionalAuthFilter` 实现。

```java
// 白名单仅保留:
// POST /api/v1/auth/login
// POST /api/v1/auth/register
// GET /api/v1/health
// GET /api/v1/tenants/current
// /h2-console/**
```

- [ ] **Step 2: 添加可选认证过滤器**

创建 `OptionalAuthFilter`，对特定路径（templates/fragments/documents 的 GET 请求）不要求认证但解析 token。

- [ ] **Step 3: 在 WebConfig 中注册拦截器**

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(tenantInterceptor).addPathPatterns("/api/v1/**");
    registry.addInterceptor(auditLogInterceptor).addPathPatterns("/api/v1/**");
}
```

- [ ] **Step 4: 租户 ID 校验**

在 `TenantInterceptor` 中验证租户是否存在：
```java
Tenant tenant = tenantRepository.findByCode(tenantId).orElse(null);
if (tenant == null) {
    // 使用默认租户
    tenantId = "default";
}
```

---

## Task 13: 修复性能问题 — 消除全表扫描

**Files:**
- Modify: `backend/src/main/java/com/docgen/repository/TemplateRepository.java`
- Modify: `backend/src/main/java/com/docgen/repository/FragmentRepository.java`
- Modify: `backend/src/main/java/com/docgen/repository/DocumentRepository.java`
- Modify: `backend/src/main/java/com/docgen/repository/TenantRepository.java`
- Modify: `backend/src/main/java/com/docgen/service/TemplateService.java`
- Modify: `backend/src/main/java/com/docgen/service/FragmentService.java`
- Modify: `backend/src/main/java/com/docgen/service/DocumentService.java`
- Modify: `backend/src/main/java/com/docgen/service/TenantService.java`
- Modify: `backend/src/main/java/com/docgen/service/ApprovalService.java`

- [ ] **Step 1: 在 Repository 中添加 JPA Specification 支持或自定义 @Query**

为 TemplateRepository、FragmentRepository、DocumentRepository 添加带动态条件的查询方法：

```java
// TemplateRepository.java
@Query("SELECT t FROM Template t WHERE t.tenantId = :tenantId " +
       "AND (:keyword IS NULL OR t.name LIKE %:keyword% OR t.description LIKE %:keyword%) " +
       "AND (:category IS NULL OR t.category = :category) " +
       "AND (:status IS NULL OR t.status = :status) " +
       "AND t.deletedAt IS NULL " +
       "ORDER BY t.createTime DESC")
Page<Template> findWithFilters(
    @Param("tenantId") String tenantId,
    @Param("keyword") String keyword,
    @Param("category") String category,
    @Param("status") String status,
    Pageable pageable
);
```

类似地为 FragmentRepository 和 DocumentRepository 添加。

- [ ] **Step 2: 更新 Service 层使用新的 Repository 方法**

替换所有 `findAll()` + 内存过滤为直接调用带过滤条件的 Repository 方法。

- [ ] **Step 3: 修复 TenantService 唯一性检查**

```java
// 替换 findAll() 检查
boolean nameExists = tenantRepository.findByName(name).isPresent();
boolean codeExists = tenantRepository.findByCode(code).isPresent();
```

- [ ] **Step 4: 修复 ApprovalService.getPendingApprovals**

使用数据库查询替代内存过滤：
```java
return templateRepository.findByTenantIdAndStatus(tenantId, "pending");
```

---

## Task 14: 修复性能问题 — 消除 N+1 查询

**Files:**
- Modify: `backend/src/main/java/com/docgen/repository/TemplateCompositionRepository.java`
- Modify: `backend/src/main/java/com/docgen/service/CompositionService.java`

- [ ] **Step 1: 在 TemplateCompositionRepository 中添加 JOIN 查询**

```java
@Query("SELECT tc, f FROM TemplateComposition tc LEFT JOIN Fragment f ON tc.fragmentId = f.id " +
       "WHERE tc.templateId = :templateId ORDER BY tc.sortOrder")
List<Object[]> findWithFragments(@Param("templateId") Long templateId);
```

- [ ] **Step 2: 更新 CompositionService 使用 JOIN 查询**

替换循环中的 `fragmentRepository.findById()` 为批量 JOIN 查询。

---

## Task 15: 修复逻辑错误

**Files:**
- Modify: `backend/src/main/java/com/docgen/service/ApprovalService.java`
- Modify: `backend/src/main/java/com/docgen/service/DashboardService.java`
- Modify: `backend/src/main/java/com/docgen/middleware/AuditLogInterceptor.java`

- [ ] **Step 1: 修复 ApprovalService 审批状态**

```java
// submitForApproval 中审批记录状态应为 "pending" 而非 "approved"
approval.setStatus("pending");
```

- [ ] **Step 2: 修复 DashboardService 跨租户数据泄露**

```java
// 所有 count 查询添加 tenantId 过滤
long documentCount = documentRepository.countByTenantId(tenantId);
long userCount = userRepository.countByTenantId(tenantId);
long fragmentCount = fragmentRepository.countByTenantId(tenantId);
```

需要为 UserRepository 和 FragmentRepository 添加 `countByTenantId` 方法。

- [ ] **Step 3: 启用 AuditLogInterceptor 持久化**

取消注释 `auditLogRepository`，实现审计日志写入数据库。

---

## Task 16: 清理冗余代码和 DTO

**Files:**
- Delete: `backend/src/main/java/com/docgen/dto/PageRequest.java` (未使用，与 Spring PageRequest 冲突)
- Delete: `backend/src/main/java/com/docgen/dto/TemplateUploadRequest.java` (未使用)
- Modify: 多个 DTO 添加校验注解

- [ ] **Step 1: 删除未使用的 DTO**

- [ ] **Step 2: 为关键 DTO 添加校验注解**

```java
// CompositionItem.java
@NotNull(message = "片段ID不能为空")
private Long fragmentId;

// AddFragmentRequest.java
@NotNull(message = "片段ID不能为空")
private Long fragmentId;

// ReorderRequest.java
@NotEmpty(message = "片段ID列表不能为空")
private List<Long> fragmentIds;

// GenerateDocumentRequest.java
@Pattern(regexp = "docx|pdf", message = "不支持的输出格式")
private String format = "docx";
```

- [ ] **Step 3: 重命名 AuditLog 注解避免与实体类冲突**

将 `com.docgen.middleware.AuditLog` 注解重命名为 `@Audited`。

---

## Task 17: 修复 Controller 尾部斜杠问题

**Files:**
- Modify: 所有使用 `@GetMapping("/")` 的 Controller

- [ ] **Step 1: 统一移除尾部斜杠**

将所有 `@GetMapping("/")` 改为 `@GetMapping("")`，`@PostMapping("/")` 改为 `@PostMapping("")`，以此类推。

涉及文件：
- TemplateController.java
- FragmentController.java
- CompositionController.java
- VersionController.java
- TenantController.java

---

## Task 18: 编译验证和最终检查

**Files:**
- 所有修改过的文件

- [ ] **Step 1: 全量编译**

Run: `cd /workspace/backend && mvn clean compile 2>&1`
Expected: BUILD SUCCESS

- [ ] **Step 2: 检查所有编译警告**

Run: `cd /workspace/backend && mvn compile 2>&1 | grep -i "warning"`
Expected: 无严重警告

- [ ] **Step 3: 验证 API 路径完整性**

对照 `frontend/src/api/index.js` 检查所有 40+ 个接口路径是否匹配。

- [ ] **Step 4: 提交**

```bash
cd /workspace && git add -A && git commit -m "fix: 深度优化Java后端 - 修复编译错误、安全漏洞、性能问题和API兼容性"
```
