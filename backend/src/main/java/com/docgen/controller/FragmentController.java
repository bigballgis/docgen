package com.docgen.controller;

import com.docgen.dto.FragmentRequest;
import com.docgen.dto.FragmentUpdateRequest;
import com.docgen.dto.Result;
import com.docgen.entity.Fragment;
import com.docgen.entity.FragmentVersion;
import com.docgen.exception.BusinessException;
import com.docgen.middleware.TenantContext;
import com.docgen.middleware.UserDetailsImpl;
import com.docgen.service.FragmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 片段控制器
 * 处理文档片段的增删改查、版本管理、对比、预览等操作
 */
@RestController
@RequestMapping("/api/v1/fragments")
@RequiredArgsConstructor
public class FragmentController {

    private final FragmentService fragmentService;

    /**
     * 获取当前登录用户信息
     * 从 SecurityContext 中提取 UserDetailsImpl
     * 未认证时返回 null（用于无需认证的接口）
     *
     * @return 当前登录用户的 UserDetailsImpl，未认证时返回 null
     */
    private UserDetailsImpl getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException(401, "未授权，请先登录");
        }
        return (UserDetailsImpl) authentication.getPrincipal();
    }

    /**
     * 获取片段列表（分页/搜索/分类/标签/状态）
     * 此接口无需认证，已在 SecurityConfig 白名单中配置
     *
     * @param page     页码，从 0 开始，默认为 0
     * @param size     每页大小，默认为 20
     * @param keyword  搜索关键字（可选）
     * @param category 分类筛选（可选）
     * @param tags     标签筛选（可选，多个标签用逗号分隔）
     * @param status   状态筛选（可选）
     * @return 分页片段列表
     */
    @GetMapping("")
    public Result<Page<Fragment>> getFragmentList(
            @RequestParam(defaultValue = "0") int page,
            @Max(100) @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String status) {
        String tenantId = TenantContext.getTenantId();
        Page<Fragment> fragments = fragmentService.getFragmentList(tenantId, page, size, keyword, category, tags, status);
        return Result.success(fragments);
    }

    /**
     * 获取所有片段分类
     *
     * @return 分类列表
     */
    @GetMapping("/categories")
    public Result<List<String>> getFragmentCategories() {
        String tenantId = TenantContext.getTenantId();
        List<String> categories = fragmentService.getFragmentCategories(tenantId);
        return Result.success(categories);
    }

    /**
     * 获取片段详情
     * 此接口无需认证，已在 SecurityConfig 白名单中配置
     *
     * @param id 片段ID
     * @return 片段详细信息
     */
    @GetMapping("/{id}")
    public Result<Fragment> getFragmentById(@PathVariable Long id) {
        Fragment fragment = fragmentService.getFragmentById(id);
        return Result.success(fragment);
    }

    /**
     * 创建片段
     *
     * @param request 创建片段请求（名称、描述、分类、标签、内容）
     * @return 创建成功的片段信息
     */
    @PostMapping("")
    public Result<Fragment> createFragment(@Valid @RequestBody FragmentRequest request) {
        UserDetailsImpl currentUser = getCurrentUser();
        String tenantId = TenantContext.getTenantId();
        Fragment fragment = fragmentService.createFragment(request, currentUser.getId(), tenantId);
        return Result.success(fragment);
    }

    /**
     * 更新片段
     *
     * @param id      片段ID
     * @param request 更新片段请求（所有字段可选）
     * @return 更新后的片段信息
     */
    @PutMapping("/{id}")
    public Result<Fragment> updateFragment(
            @PathVariable Long id,
            @Valid @RequestBody FragmentUpdateRequest request) {
        UserDetailsImpl currentUser = getCurrentUser();
        Fragment fragment = fragmentService.updateFragment(id, request, currentUser.getId());
        return Result.success(fragment);
    }

    /**
     * 删除片段（软删除）
     * 仅管理员可以删除片段
     *
     * @param id 片段ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteFragment(@PathVariable Long id) {
        fragmentService.deleteFragment(id);
        return Result.success(null, "片段删除成功");
    }

    /**
     * 获取片段版本列表
     *
     * @param id 片段ID
     * @return 版本列表
     */
    @GetMapping("/{id}/versions")
    public Result<List<FragmentVersion>> getFragmentVersions(@PathVariable Long id) {
        List<FragmentVersion> versions = fragmentService.getFragmentVersions(id);
        return Result.success(versions);
    }

    /**
     * 获取片段特定版本
     *
     * @param id  片段ID
     * @param ver 版本号
     * @return 指定版本的片段信息
     */
    @GetMapping("/{id}/versions/{ver}")
    public Result<FragmentVersion> getFragmentVersion(
            @PathVariable Long id,
            @PathVariable Integer ver) {
        FragmentVersion version = fragmentService.getFragmentVersion(id, ver);
        return Result.success(version);
    }

    /**
     * 回滚片段版本
     * 仅管理员可以执行版本回滚操作
     *
     * @param id  片段ID
     * @param ver 目标版本号
     * @return 回滚后的片段信息
     */
    @PostMapping("/{id}/versions/{ver}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Fragment> rollbackFragmentVersion(
            @PathVariable Long id,
            @PathVariable Integer ver) {
        UserDetailsImpl currentUser = getCurrentUser();
        Fragment fragment = fragmentService.rollbackFragmentVersion(id, ver, currentUser.getId());
        return Result.success(fragment);
    }

    /**
     * 对比片段版本
     * 比较同一片段的两个不同版本之间的差异
     *
     * @param id 片段ID
     * @param v1 第一个版本号
     * @param v2 第二个版本号
     * @return 版本对比结果
     */
    @PostMapping("/{id}/compare")
    public Result<Map<String, Object>> compareFragmentVersions(
            @PathVariable Long id,
            @RequestParam Integer v1,
            @RequestParam Integer v2) {
        Map<String, Object> comparison = fragmentService.compareFragmentVersions(id, v1, v2);
        return Result.success(comparison);
    }

    /**
     * 预览片段 HTML
     * 将片段内容渲染为 HTML 进行预览
     *
     * @param id 片段ID
     * @return HTML 预览内容
     */
    @GetMapping("/{id}/preview")
    public Result<String> previewFragment(@PathVariable Long id) {
        String htmlPreview = fragmentService.previewFragment(id);
        return Result.success(htmlPreview);
    }
}
