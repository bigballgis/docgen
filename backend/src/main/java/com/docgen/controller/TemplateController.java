package com.docgen.controller;

import com.docgen.dto.ApprovalRequest;
import com.docgen.dto.Result;
import com.docgen.entity.Template;
import com.docgen.middleware.TenantContext;
import com.docgen.middleware.UserDetailsImpl;
import com.docgen.service.TemplateService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 模板控制器
 * 处理模板的上传、查询、审批、解析、导出等操作
 */
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /**
     * 获取当前登录用户信息
     * 从 SecurityContext 中提取 UserDetailsImpl
     *
     * @return 当前登录用户的 UserDetailsImpl，未认证时返回 null
     */
    private UserDetailsImpl getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return (UserDetailsImpl) authentication.getPrincipal();
    }

    /**
     * 上传模板
     * 支持上传 docx 模板文件，并可选设置名称、描述和分类
     *
     * @param file        模板文件（MultipartFile）
     * @param name        模板名称（可选）
     * @param description 模板描述（可选）
     * @param category    模板分类（可选）
     * @return 上传成功的模板信息
     */
    @PostMapping("/upload")
    public Result<Template> uploadTemplate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category) {
        UserDetailsImpl currentUser = getCurrentUser();
        String tenantId = TenantContext.getTenantId();
        Template template = templateService.uploadTemplate(file, name, description, category,
                currentUser != null ? currentUser.getId() : null, tenantId);
        return Result.success(template);
    }

    /**
     * 获取模板列表（分页/搜索/分类/状态过滤）
     * 此接口为可选认证，已在 SecurityConfig 白名单中配置
     *
     * @param page     页码，从 0 开始，默认为 0
     * @param size     每页大小，默认为 20
     * @param keyword  搜索关键字（可选）
     * @param category 分类筛选（可选）
     * @param status   状态筛选（可选）
     * @return 分页模板列表
     */
    @GetMapping("")
    public Result<Page<Template>> getTemplateList(
            @RequestParam(defaultValue = "0") int page,
            @Max(100) @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        String tenantId = TenantContext.getTenantId();
        Page<Template> templates = templateService.getTemplateList(tenantId, page, size, keyword, category, status);
        return Result.success(templates);
    }

    /**
     * 获取所有模板分类
     * 此接口为可选认证，已在 SecurityConfig 白名单中配置
     *
     * @return 分类列表
     */
    @GetMapping("/categories")
    public Result<List<String>> getCategories() {
        String tenantId = TenantContext.getTenantId();
        List<String> categories = templateService.getCategories(tenantId);
        return Result.success(categories);
    }

    /**
     * 获取待审批模板列表
     * 仅管理员可以访问
     *
     * @param page 页码，从 0 开始，默认为 0
     * @param size 每页大小，默认为 20
     * @return 待审批模板分页列表
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<Template>> getPendingTemplates(
            @RequestParam(defaultValue = "0") int page,
            @Max(100) @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.getTenantId();
        Page<Template> templates = templateService.getPendingTemplates(tenantId, page, size);
        return Result.success(templates);
    }

    /**
     * 提交模板审批
     * 将模板状态从草稿变更为待审批
     *
     * @param id 模板ID
     * @return 更新后的模板信息
     */
    @PostMapping("/{id}/submit")
    public Result<Template> submitForApproval(@PathVariable Long id) {
        UserDetailsImpl currentUser = getCurrentUser();
        Template template = templateService.submitForApproval(id, currentUser.getId());
        return Result.success(template);
    }

    /**
     * 审批通过
     * 仅管理员可以审批模板
     *
     * @param id      模板ID
     * @param request 审批请求（审批意见）
     * @return 更新后的模板信息
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Template> approveTemplate(
            @PathVariable Long id,
            @RequestBody(required = false) ApprovalRequest request) {
        UserDetailsImpl currentUser = getCurrentUser();
        Template template = templateService.approveTemplate(id, currentUser.getId(),
                request != null ? request.getComment() : null);
        return Result.success(template);
    }

    /**
     * 审批驳回
     * 仅管理员可以驳回模板
     *
     * @param id      模板ID
     * @param request 审批请求（驳回原因）
     * @return 更新后的模板信息
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Template> rejectTemplate(
            @PathVariable Long id,
            @RequestBody(required = false) ApprovalRequest request) {
        UserDetailsImpl currentUser = getCurrentUser();
        Template template = templateService.rejectTemplate(id, currentUser.getId(),
                request != null ? (request.getReason() != null ? request.getReason() : request.getComment()) : null);
        return Result.success(template);
    }

    /**
     * 获取模板详情
     * 此接口为可选认证，已在 SecurityConfig 白名单中配置
     *
     * @param id 模板ID
     * @return 模板详细信息
     */
    @GetMapping("/{id}")
    public Result<Template> getTemplateById(@PathVariable Long id) {
        Template template = templateService.getTemplateById(id);
        return Result.success(template);
    }

    /**
     * 删除模板
     * 仅管理员可以删除模板（软删除）
     *
     * @param id 模板ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success(null, "模板删除成功");
    }

    /**
     * 解析模板字段
     * 解析 docx 模板中的占位符字段
     *
     * @param id 模板ID
     * @return 解析出的字段列表
     */
    @PostMapping("/{id}/parse-fields")
    public Result<List<Map<String, Object>>> parseTemplateFields(@PathVariable Long id) {
        List<Map<String, Object>> fields = templateService.parseTemplateFields(id);
        return Result.success(fields);
    }

    /**
     * 导出模板列表
     * 支持导出为 CSV 或 JSON 格式
     *
     * @param format 导出格式（csv/json），默认为 csv
     * @param response HTTP 响应对象
     */
    @GetMapping("/export")
    public void exportTemplates(
            @RequestParam(defaultValue = "csv") String format,
            HttpServletResponse response) {
        String tenantId = TenantContext.getTenantId();
        templateService.exportTemplates(tenantId, format, response);
    }
}
