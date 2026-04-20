package com.docgen.controller;

import com.docgen.common.Result;
import com.docgen.config.FileStorageConfig;
import com.docgen.dto.TemplateFieldDTO;
import com.docgen.dto.TemplateUploadDTO;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.service.DocumentService;
import com.docgen.service.TemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 模板管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final DocumentService documentService;
    private final FileStorageConfig fileStorageConfig;
    private final ObjectMapper objectMapper;

    /**
     * 上传模板
     * Content-Type: multipart/form-data
     *
     * @param file        模板文件
     * @param name        模板名称
     * @param description 描述
     * @param category    分类
     * @param fieldsJson  字段定义 JSON 字符串
     */
    @PostMapping("/upload")
    public Result<Template> uploadTemplate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "fields", required = false) String fieldsJson) {

        TemplateUploadDTO dto = new TemplateUploadDTO();
        dto.setName(name);
        dto.setDescription(description);
        dto.setCategory(category);

        // 解析字段定义 JSON
        if (fieldsJson != null && !fieldsJson.isBlank()) {
            try {
                List<TemplateFieldDTO> fields = objectMapper.readValue(fieldsJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, TemplateFieldDTO.class));
                dto.setFields(fields);
            } catch (Exception e) {
                return Result.error("字段定义 JSON 格式错误: " + e.getMessage());
            }
        }

        Template template = templateService.uploadTemplate(file, dto);
        return Result.success("模板上传成功", template);
    }

    /**
     * 获取模板列表（支持分页和搜索）
     *
     * @param keyword  搜索关键词
     * @param category 分类
     * @param page     页码（从 0 开始）
     * @param size     每页大小
     */
    @GetMapping
    public Result<Page<Template>> listTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<Template> templates = templateService.listTemplates(keyword, category, page, size);
        return Result.success(templates);
    }

    /**
     * 获取模板详情
     */
    @GetMapping("/{id}")
    public Result<Template> getTemplate(@PathVariable Long id) {
        Template template = templateService.getTemplate(id);
        return Result.success(template);
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success("模板已删除", null);
    }

    /**
     * 获取所有分类
     */
    @GetMapping("/categories")
    public Result<List<String>> getAllCategories() {
        List<String> categories = templateService.getAllCategories();
        return Result.success(categories);
    }

    /**
     * 解析模板字段（提取占位符）
     */
    @PostMapping("/{id}/parse-fields")
    public Result<Map<String, Object>> parseTemplateFields(@PathVariable Long id) {
        Template template = templateService.getTemplate(id);
        List<String> fieldNames = parseFieldsFromDisk(template);

        Map<String, Object> result = new HashMap<>();
        result.put("templateId", id);
        result.put("templateName", template.getName());
        result.put("fieldCount", fieldNames.size());
        result.put("fields", fieldNames);

        return Result.success(result);
    }

    /**
     * 从磁盘上的模板文件解析占位符字段
     */
    private List<String> parseFieldsFromDisk(Template template) {
        Path templateFilePath = fileStorageConfig.getTemplatePath().resolve(template.getFileName());
        if (!Files.exists(templateFilePath)) {
            throw new BusinessException("模板文件不存在: " + template.getFileName());
        }

        Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");
        Set<String> fieldNames = new LinkedHashSet<>();

        try (ZipFile zipFile = new ZipFile(templateFilePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith("word/") && entryName.endsWith(".xml")) {
                    try (InputStream is = zipFile.getInputStream(entry);
                         BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                        StringBuilder content = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line);
                        }
                        Matcher matcher = pattern.matcher(content.toString());
                        while (matcher.find()) {
                            fieldNames.add(matcher.group(1));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析模板字段失败", e);
            throw new BusinessException("解析模板字段失败: " + e.getMessage());
        }

        return new ArrayList<>(fieldNames);
    }

}
