package com.docgen.controller;

import com.docgen.dto.Result;
import com.docgen.entity.TemplateVersion;
import com.docgen.middleware.UserDetailsImpl;
import com.docgen.service.VersionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模板版本控制器
 * 处理模板版本的管理、对比、预览、回滚等操作
 */
@RestController
@RequestMapping("/api/v1/templates/{templateId}/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

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
     * 获取模板版本列表
     *
     * @param templateId 模板ID
     * @return 版本列表
     */
    @GetMapping("")
    public Result<List<TemplateVersion>> getVersionList(@PathVariable Long templateId) {
        List<TemplateVersion> versions = versionService.getVersionList(templateId);
        return Result.success(versions);
    }

    /**
     * 对比两个版本
     * 比较同一模板的两个不同版本之间的差异
     *
     * @param templateId 模板ID
     * @param v1         第一个版本号
     * @param v2         第二个版本号
     * @return 版本对比结果
     */
    @GetMapping("/compare")
    public Result<Map<String, Object>> compareVersions(
            @PathVariable Long templateId,
            @RequestParam Integer v1,
            @RequestParam Integer v2) {
        Map<String, Object> comparison = versionService.compareVersions(templateId, v1, v2);
        return Result.success(comparison);
    }

    /**
     * 获取版本详情
     *
     * @param templateId 模板ID
     * @param version    版本号
     * @return 版本详细信息
     */
    @GetMapping("/{version}")
    public Result<TemplateVersion> getVersionDetail(
            @PathVariable Long templateId,
            @PathVariable Integer version) {
        TemplateVersion templateVersion = versionService.getVersionDetail(templateId, version);
        return Result.success(templateVersion);
    }

    /**
     * 获取版本 HTML 预览
     * 将指定版本的模板文件转换为 HTML 进行预览
     *
     * @param templateId 模板ID
     * @param version    版本号
     * @return HTML 预览内容
     */
    @GetMapping("/{version}/preview")
    public Result<String> getVersionPreview(
            @PathVariable Long templateId,
            @PathVariable Integer version) {
        String htmlPreview = versionService.getVersionPreview(templateId, version);
        return Result.success(htmlPreview);
    }

    /**
     * 回滚到指定版本
     * 仅管理员可以执行版本回滚操作
     *
     * @param templateId 模板ID
     * @param version    目标版本号
     * @return 回滚后的模板信息
     */
    @PostMapping("/{version}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> rollbackVersion(
            @PathVariable Long templateId,
            @PathVariable Integer version) {
        UserDetailsImpl currentUser = getCurrentUser();
        Map<String, Object> result = versionService.rollbackVersion(templateId, version, currentUser.getId());
        return Result.success(result);
    }
}
