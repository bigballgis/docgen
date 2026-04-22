package com.docgen.service;

import com.docgen.config.FileStorageConfig;
import com.docgen.dto.AddFragmentRequest;
import com.docgen.dto.CompositionItem;
import com.docgen.entity.Fragment;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateComposition;
import com.docgen.exception.BusinessException;
import com.docgen.repository.FragmentRepository;
import com.docgen.repository.TemplateCompositionRepository;
import com.docgen.repository.TemplateRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 编排服务
 * 对应 Node.js 的 compositionService.js
 * 负责模板与片段的编排关系管理，包括编排的保存、查询、排序和文档生成
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompositionService {

    private final TemplateCompositionRepository compositionRepository;
    private final TemplateRepository templateRepository;
    private final FragmentRepository fragmentRepository;
    private final FileStorageConfig fileStorageConfig;

    /**
     * 保存编排
     * 事务操作：先删除该模板的所有编排，再批量插入新的编排项
     *
     * @param templateId 模板ID
     * @param items      编排项列表
     * @return 保存后的编排列表
     */
    @Transactional
    public List<TemplateComposition> saveComposition(Long templateId, List<CompositionItem> items) {
        log.info("保存编排, templateId={}, itemsCount={}", templateId, items != null ? items.size() : 0);

        // 验证模板存在
        templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 删除该模板的所有编排
        compositionRepository.deleteByTemplateId(templateId);

        // 批量插入新的编排项
        List<TemplateComposition> compositions = new ArrayList<>();
        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                CompositionItem item = items.get(i);

                // 验证片段存在
                fragmentRepository.findById(item.getFragmentId())
                        .orElseThrow(() -> new BusinessException("片段不存在，id=" + item.getFragmentId()));

                TemplateComposition composition = TemplateComposition.builder()
                        .templateId(templateId)
                        .fragmentId(item.getFragmentId())
                        .sortOrder(i)
                        .sectionTitle(item.getSectionTitle() != null ? item.getSectionTitle() : "")
                        .enabled(item.getEnabled() != null ? item.getEnabled() : true)
                        .createdAt(LocalDateTime.now())
                        .build();
                compositions.add(composition);
            }
            compositions = compositionRepository.saveAll(compositions);
        }

        log.info("编排保存成功, templateId={}, savedCount={}", templateId, compositions.size());
        return compositions;
    }

    /**
     * 获取编排列表
     * 返回模板的编排项列表，按 sortOrder 升序排列，并关联片段详情
     *
     * @param templateId 模板ID
     * @return 编排列表（包含片段详情）
     */
    public List<TemplateComposition> getComposition(Long templateId) {
        log.info("获取编排列表, templateId={}", templateId);

        // 验证模板存在
        templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 查询编排列表
        List<TemplateComposition> compositions = compositionRepository.findByTemplateIdOrderBySortOrder(templateId);

        log.info("编排列表获取成功, templateId={}, count={}", templateId, compositions.size());
        return compositions;
    }

    /**
     * 添加片段到模板编排（供 Controller 的 addFragment 调用）
     *
     * @param templateId 模板ID
     * @param request    添加片段请求
     * @return 添加后的编排列表
     */
    @Transactional
    public List<TemplateComposition> addFragment(Long templateId, AddFragmentRequest request) {
        log.info("添加片段到模板编排, templateId={}, fragmentId={}", templateId, request.getFragmentId());

        // 验证模板存在
        templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 验证片段存在
        fragmentRepository.findById(request.getFragmentId())
                .orElseThrow(() -> new BusinessException("片段不存在，id=" + request.getFragmentId()));

        // 检查是否已存在该片段的编排
        if (compositionRepository.findByTemplateIdAndFragmentId(templateId, request.getFragmentId()).isPresent()) {
            throw new BusinessException("该片段已存在于模板编排中");
        }

        // 查找当前最大 sortOrder
        List<TemplateComposition> existingCompositions = compositionRepository.findByTemplateIdOrderBySortOrder(templateId);
        int maxSortOrder = existingCompositions.stream()
                .mapToInt(TemplateComposition::getSortOrder)
                .max()
                .orElse(-1);

        // 创建新编排项
        TemplateComposition composition = TemplateComposition.builder()
                .templateId(templateId)
                .fragmentId(request.getFragmentId())
                .sortOrder(maxSortOrder + 1)
                .sectionTitle(request.getSectionTitle() != null ? request.getSectionTitle() : "")
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .createdAt(LocalDateTime.now())
                .build();
        composition = compositionRepository.save(composition);

        log.info("片段已添加到模板编排, compositionId={}", composition.getId());

        // 返回更新后的编排列表
        return compositionRepository.findByTemplateIdOrderBySortOrder(templateId);
    }

    /**
     * 从模板编排中移除片段（供 Controller 的 removeFragment 调用）
     *
     * @param templateId 模板ID
     * @param fragmentId 片段ID
     * @return 移除后的编排列表
     */
    @Transactional
    public List<TemplateComposition> removeFragment(Long templateId, Long fragmentId) {
        log.info("从模板编排中移除片段, templateId={}, fragmentId={}", templateId, fragmentId);

        TemplateComposition composition = compositionRepository.findByTemplateIdAndFragmentId(templateId, fragmentId)
                .orElseThrow(() -> new BusinessException("编排项不存在"));

        compositionRepository.delete(composition);

        log.info("片段已从模板编排中移除");

        // 返回更新后的编排列表
        return compositionRepository.findByTemplateIdOrderBySortOrder(templateId);
    }

    /**
     * 重新排序片段（供 Controller 调用，返回编排列表）
     *
     * @param templateId 模板ID
     * @param fragmentIds 按新顺序排列的片段ID列表
     * @return 重排序后的编排列表
     */
    @Transactional
    public List<TemplateComposition> reorderFragments(Long templateId, List<Long> fragmentIds) {
        log.info("重新排序片段, templateId={}, fragmentIds={}", templateId, fragmentIds);

        // 验证模板存在
        templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 一次性查出该模板的所有编排项
        List<TemplateComposition> allCompositions = compositionRepository.findByTemplateIdOrderBySortOrder(templateId);
        Map<Long, TemplateComposition> compositionMap = allCompositions.stream()
                .collect(Collectors.toMap(TemplateComposition::getFragmentId, c -> c, (a, b) -> a));

        // 批量更新 sortOrder
        List<TemplateComposition> toUpdate = new ArrayList<>();
        for (int i = 0; i < fragmentIds.size(); i++) {
            Long fragmentId = fragmentIds.get(i);
            TemplateComposition composition = compositionMap.get(fragmentId);
            if (composition == null) {
                throw new BusinessException("编排项不存在，fragmentId=" + fragmentId);
            }
            composition.setSortOrder(i);
            toUpdate.add(composition);
        }

        // 使用 saveAll 一次性保存
        compositionRepository.saveAll(toUpdate);

        log.info("片段重新排序完成, templateId={}", templateId);

        // 返回更新后的编排列表
        return compositionRepository.findByTemplateIdOrderBySortOrder(templateId);
    }

    /**
     * 预览组合文档 HTML（供 Controller 的 previewComposition 调用）
     *
     * @param templateId 模板ID
     * @return HTML 预览内容
     */
    public String previewComposition(Long templateId) {
        return generateComposedHtml(templateId);
    }

    /**
     * 生成组合文档（供 Controller 的 generateComposition 调用）
     * 生成 docx 文件并写入 response
     *
     * @param templateId 模板ID
     * @param response   HTTP 响应对象
     */
    public void generateComposition(Long templateId, HttpServletResponse response) {
        String filePath = generateComposedDocx(templateId);

        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                throw new BusinessException("生成的文件不存在");
            }

            String fileName = path.getFileName().toString();
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            response.setContentLengthLong(Files.size(path));

            try (OutputStream os = response.getOutputStream();
                 FileInputStream fis = new FileInputStream(path.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }
        } catch (IOException e) {
            log.error("下载组合文档失败: {}", e.getMessage(), e);
            throw new BusinessException("下载组合文档失败: " + e.getMessage());
        }
    }

    /**
     * 生成编排后的 HTML 内容
     * 获取所有已启用的编排项（按 sortOrder 排序），拼接片段的 contentHtml，用 sectionTitle 作为标题
     *
     * @param templateId 模板ID
     * @return 完整的 HTML 内容
     */
    public String generateComposedHtml(Long templateId) {
        log.info("生成编排 HTML, templateId={}", templateId);

        // 验证模板存在
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 使用 JOIN 查询一次性获取编排项和片段，消除 N+1 查询
        List<Object[]> results = compositionRepository.findWithFragments(templateId);

        // 拼接 HTML 内容
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<!DOCTYPE html>\n");
        htmlBuilder.append("<html>\n<head>\n");
        htmlBuilder.append("<meta charset=\"UTF-8\">\n");
        htmlBuilder.append("<title>").append(template.getName()).append("</title>\n");
        htmlBuilder.append("<style>body { font-family: 'Microsoft YaHei', sans-serif; margin: 40px; }</style>\n");
        htmlBuilder.append("</head>\n<body>\n");

        for (Object[] row : results) {
            TemplateComposition composition = (TemplateComposition) row[0];
            Fragment fragment = (Fragment) row[1];

            // 跳过未启用的编排项
            if (!Boolean.TRUE.equals(composition.getEnabled())) {
                continue;
            }

            // 添加章节标题
            String sectionTitle = composition.getSectionTitle();
            if (sectionTitle != null && !sectionTitle.isEmpty()) {
                htmlBuilder.append("<h2>").append(escapeHtml(sectionTitle)).append("</h2>\n");
            }

            // 直接使用 JOIN 查询到的片段内容
            if (fragment != null) {
                String contentHtml = fragment.getContentHtml();
                if (contentHtml != null && !contentHtml.isEmpty()) {
                    htmlBuilder.append("<div class=\"fragment-content\">");
                    htmlBuilder.append(sanitizeHtml(contentHtml));
                    htmlBuilder.append("</div>\n");
                }
            }
        }

        htmlBuilder.append("</body>\n</html>");

        String html = htmlBuilder.toString();
        log.info("编排 HTML 生成成功, templateId={}, length={}", templateId, html.length());
        return html;
    }

    /**
     * 生成编排后的 docx 文件
     * 获取编排的 HTML 内容，使用 Apache POI 创建 docx 文件
     *
     * @param templateId 模板ID
     * @return 生成的 docx 文件路径
     */
    public String generateComposedDocx(Long templateId) {
        log.info("生成编排 docx, templateId={}", templateId);

        // 验证模板存在
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 使用 JOIN 查询一次性获取编排项和片段，消除 N+1 查询
        List<Object[]> results = compositionRepository.findWithFragments(templateId);

        // 构建输出文件路径
        String outputFileName = "composed_" + templateId + "_" + System.currentTimeMillis() + ".docx";
        String outputFilePath = fileStorageConfig.getOutputDir() + "/" + outputFileName;

        try {
            // 确保输出目录存在
            Path outputPath = Paths.get(outputFilePath).toAbsolutePath().normalize();
            Files.createDirectories(outputPath.getParent());

            // 使用 Apache POI 创建 docx 文件
            try (XWPFDocument document = new XWPFDocument()) {
                for (Object[] row : results) {
                    TemplateComposition composition = (TemplateComposition) row[0];
                    Fragment fragment = (Fragment) row[1];

                    // 跳过未启用的编排项
                    if (!Boolean.TRUE.equals(composition.getEnabled())) {
                        continue;
                    }

                    // 添加章节标题
                    String sectionTitle = composition.getSectionTitle();
                    if (sectionTitle != null && !sectionTitle.isEmpty()) {
                        XWPFParagraph titleParagraph = document.createParagraph();
                        XWPFRun titleRun = titleParagraph.createRun();
                        titleRun.setText(sectionTitle);
                        titleRun.setBold(true);
                        titleRun.setFontSize(16);
                    }

                    // 直接使用 JOIN 查询到的片段内容
                    if (fragment != null) {
                        String contentHtml = fragment.getContentHtml();
                        if (contentHtml != null && !contentHtml.isEmpty()) {
                            // 简单去除 HTML 标签，提取纯文本
                            String plainText = contentHtml.replaceAll("<[^>]+>", "");
                            if (!plainText.trim().isEmpty()) {
                                XWPFParagraph contentParagraph = document.createParagraph();
                                XWPFRun contentRun = contentParagraph.createRun();
                                contentRun.setText(plainText);
                                contentRun.setFontSize(12);
                            }
                        }
                    }
                }

                // 写入文件
                try (FileOutputStream out = new FileOutputStream(outputFilePath)) {
                    document.write(out);
                }
            }

            log.info("编排 docx 生成成功, templateId={}, filePath={}", templateId, outputFilePath);
            return outputFilePath;
        } catch (IOException e) {
            log.error("生成编排 docx 失败, templateId={}", templateId, e);
            throw new BusinessException("生成 docx 文件失败：" + e.getMessage());
        }
    }

    private String sanitizeHtml(String html) {
        if (html == null || html.isEmpty()) return html;
        // 移除 script 标签和事件属性
        return html.replaceAll("(?i)<script[^>]*>.*?</script>", "")
                   .replaceAll("(?i)\\son\\w+\\s*=\\s*\"[^\"]*\"", "")
                   .replaceAll("(?i)\\son\\w+\\s*=\\s*'[^']*'", "")
                   .replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "")
                   .replaceAll("(?i)<object[^>]*>.*?</object>", "")
                   .replaceAll("(?i)<embed[^>]*>", "");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
