package com.docgen.controller;

import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.Result;
import com.docgen.entity.Document;
import com.docgen.middleware.TenantContext;
import com.docgen.middleware.UserDetailsImpl;
import com.docgen.service.DocumentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 文档控制器
 * 处理文档生成、下载、历史查询、状态查询、导出等操作
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 获取当前登录用户信息
     * 从 SecurityContext 中提取 UserDetailsImpl
     *
     * @return 当前登录用户的 UserDetailsImpl
     */
    private UserDetailsImpl getCurrentUser() {
        return (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * 生成文档
     * 根据模板和字段数据生成文档，返回文件流
     * 前端使用 responseType: 'blob' 接收
     *
     * @param request  生成文档请求（模板ID、字段数据、输出格式）
     * @param response HTTP 响应对象
     */
    @PostMapping("/generate")
    public void generateDocument(
            @Valid @RequestBody GenerateDocumentRequest request,
            HttpServletResponse response) {
        UserDetailsImpl currentUser = getCurrentUser();
        String tenantId = TenantContext.getTenantId();
        documentService.generateDocument(request, currentUser.getId(), tenantId, response);
    }

    /**
     * 同步生成文档
     * 同步方式生成文档，返回生成结果
     *
     * @param request 生成文档请求（模板ID、字段数据、输出格式）
     * @return 生成结果（包含文档信息和下载地址）
     */
    @PostMapping("/generate/sync")
    public Result<Map<String, Object>> generateDocumentSync(@Valid @RequestBody GenerateDocumentRequest request) {
        UserDetailsImpl currentUser = getCurrentUser();
        String tenantId = TenantContext.getTenantId();
        Map<String, Object> result = documentService.generateDocumentSync(request, currentUser.getId(), tenantId);
        return Result.success(result);
    }

    /**
     * 下载文档
     * 根据文件名下载已生成的文档，返回文件流
     *
     * @param fileName 文件名
     * @param response HTTP 响应对象
     */
    @GetMapping("/download/{fileName}")
    public void downloadDocument(
            @PathVariable String fileName,
            HttpServletResponse response) {
        documentService.downloadDocument(fileName, response);
    }

    /**
     * 获取文档历史列表（分页）
     * 此接口为可选认证，已在 SecurityConfig 白名单中配置
     *
     * @param page 页码，从 0 开始，默认为 0
     * @param size 每页大小，默认为 20
     * @return 分页文档历史列表
     */
    @GetMapping("")
    public Result<Page<Document>> getDocumentList(
            @RequestParam(defaultValue = "0") int page,
            @Max(100) @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        String tenantId = TenantContext.getTenantId();
        Page<Document> documents = documentService.getDocumentList(keyword, status, page, size, tenantId, null);
        return Result.success(documents);
    }

    /**
     * 查询任务状态
     * 根据任务ID查询文档生成任务的当前状态
     * 此接口为可选认证，已在 SecurityConfig 白名单中配置
     *
     * @param taskId 任务ID
     * @return 任务状态信息
     */
    @GetMapping("/status/{taskId}")
    public Result<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        Map<String, Object> status = documentService.getTaskStatus(taskId);
        return Result.success(status);
    }

    /**
     * 删除文档（软删除）
     *
     * @param id 文档ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return Result.success(null, "文档删除成功");
    }

    /**
     * 导出文档列表
     * 支持导出为 CSV 或 JSON 格式
     *
     * @param format  导出格式（csv/json），默认为 csv
     * @param response HTTP 响应对象
     */
    @GetMapping("/export")
    public void exportDocuments(
            @RequestParam(defaultValue = "csv") String format,
            HttpServletResponse response) {
        String tenantId = TenantContext.getTenantId();
        documentService.exportDocuments(tenantId, format, response);
    }
}
