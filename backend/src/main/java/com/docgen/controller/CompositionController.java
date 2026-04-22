package com.docgen.controller;

import com.docgen.dto.AddFragmentRequest;
import com.docgen.dto.ReorderRequest;
import com.docgen.dto.Result;
import com.docgen.dto.SaveCompositionRequest;
import com.docgen.entity.TemplateComposition;
import com.docgen.middleware.UserDetailsImpl;
import com.docgen.service.CompositionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模板编排控制器
 * 处理模板与片段的组合编排、排序、预览、生成等操作
 */
@RestController
@RequestMapping("/api/v1/templates/{templateId}/composition")
@RequiredArgsConstructor
public class CompositionController {

    private final CompositionService compositionService;

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
     * 获取模板编排
     * 查询指定模板的片段编排列表
     *
     * @param templateId 模板ID
     * @return 编排项列表
     */
    @GetMapping("")
    public Result<List<TemplateComposition>> getComposition(@PathVariable Long templateId) {
        List<TemplateComposition> composition = compositionService.getComposition(templateId);
        return Result.success(composition);
    }

    /**
     * 保存完整编排
     * 使用新的编排列表替换模板的现有编排
     *
     * @param templateId 模板ID
     * @param request    保存编排请求（编排项列表）
     * @return 保存后的编排列表
     */
    @PutMapping("")
    public Result<List<TemplateComposition>> saveComposition(
            @PathVariable Long templateId,
            @Valid @RequestBody SaveCompositionRequest request) {
        List<TemplateComposition> composition = compositionService.saveComposition(templateId, request.getItems());
        return Result.success(composition);
    }

    /**
     * 添加片段到编排
     * 向模板编排中添加一个新的片段项
     *
     * @param templateId 模板ID
     * @param request    添加片段请求（片段ID、章节标题、是否启用）
     * @return 添加后的编排列表
     */
    @PostMapping("/fragments")
    public Result<List<TemplateComposition>> addFragment(
            @PathVariable Long templateId,
            @Valid @RequestBody AddFragmentRequest request) {
        List<TemplateComposition> composition = compositionService.addFragment(templateId, request);
        return Result.success(composition);
    }

    /**
     * 从编排移除片段
     * 从模板编排中移除指定的片段
     *
     * @param templateId 模板ID
     * @param fragmentId 片段ID
     * @return 移除后的编排列表
     */
    @DeleteMapping("/fragments/{fragmentId}")
    public Result<List<TemplateComposition>> removeFragment(
            @PathVariable Long templateId,
            @PathVariable Long fragmentId) {
        List<TemplateComposition> composition = compositionService.removeFragment(templateId, fragmentId);
        return Result.success(composition);
    }

    /**
     * 重排序片段
     * 按照指定的顺序重新排列编排中的片段
     *
     * @param templateId 模板ID
     * @param request    重排序请求（按新顺序排列的片段ID列表）
     * @return 重排序后的编排列表
     */
    @PutMapping("/reorder")
    public Result<List<TemplateComposition>> reorderFragments(
            @PathVariable Long templateId,
            @Valid @RequestBody ReorderRequest request) {
        List<TemplateComposition> composition = compositionService.reorderFragments(templateId, request.getFragmentIds());
        return Result.success(composition);
    }

    /**
     * 预览组合文档 HTML
     * 将模板编排中的所有片段组合渲染为 HTML 进行预览
     *
     * @param templateId 模板ID
     * @return HTML 预览内容
     */
    @PostMapping("/preview")
    public Result<String> previewComposition(@PathVariable Long templateId) {
        String htmlPreview = compositionService.previewComposition(templateId);
        return Result.success(htmlPreview);
    }

    /**
     * 生成组合 docx 文件
     * 将模板编排中的所有片段组合生成 docx 文件，返回文件流
     *
     * @param templateId 模板ID
     * @param response   HTTP 响应对象
     */
    @PostMapping("/generate")
    public void generateComposition(
            @PathVariable Long templateId,
            HttpServletResponse response) {
        compositionService.generateComposition(templateId, response);
    }
}
