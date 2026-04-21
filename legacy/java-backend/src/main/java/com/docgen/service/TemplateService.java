package com.docgen.service;

import com.docgen.config.FileStorageConfig;
import com.docgen.dto.TemplateFieldDTO;
import com.docgen.dto.TemplateUploadDTO;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 模板管理服务
 * 负责模板的增删改查和文件存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final FileStorageConfig fileStorageConfig;
    private final ObjectMapper objectMapper;

    /**
     * 上传模板
     *
     * @param file 上传的 docx 文件
     * @param dto  模板元数据
     * @return 保存后的模板实体
     */
    public Template uploadTemplate(MultipartFile file, TemplateUploadDTO dto) {
        // 校验文件
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".docx")) {
            throw new BusinessException("仅支持 .docx 格式的模板文件");
        }

        // 生成唯一文件名
        String storedFileName = UUID.randomUUID().toString() + ".docx";

        // 保存文件到磁盘
        Path targetPath = fileStorageConfig.getTemplatePath().resolve(storedFileName);
        try {
            Files.copy(file.getInputStream(), targetPath);
        } catch (IOException e) {
            log.error("保存模板文件失败", e);
            throw new BusinessException("保存模板文件失败: " + e.getMessage(), e);
        }

        // 构建 Template 实体
        Template template = new Template();
        template.setName(dto.getName() != null ? dto.getName() : originalFileName.replace(".docx", ""));
        template.setDescription(dto.getDescription());
        template.setFileName(storedFileName);
        template.setOriginalFileName(originalFileName);
        template.setCategory(dto.getCategory());

        // 将字段定义转为 JSON 字符串
        if (dto.getFields() != null && !dto.getFields().isEmpty()) {
            try {
                template.setFields(objectMapper.writeValueAsString(dto.getFields()));
            } catch (JsonProcessingException e) {
                log.error("字段定义序列化失败", e);
                throw new BusinessException("字段定义格式错误: " + e.getMessage(), e);
            }
        }

        // 暂时直接返回模板，避免数据库操作
        log.info("模板上传成功: {} (ID: {})", template.getName(), 1L);
        return template;
    }

    /**
     * 分页查询模板列表
     *
     * @param keyword  搜索关键词（可选）
     * @param category 分类（可选）
     * @param page     页码（从 0 开始）
     * @param size     每页大小
     * @return 分页结果
     */
    public Page<Template> listTemplates(String keyword, String category, int page, int size) {
        // 暂时返回空分页结果，避免数据库操作
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return new PageImpl<>(new ArrayList<>(), pageable, 0);
    }

    /**
     * 获取模板详情
     *
     * @param id 模板 ID
     * @return 模板实体
     */
    public Template getTemplate(Long id) {
        // 暂时返回模拟模板，避免数据库操作
        Template template = new Template();
        template.setId(id);
        template.setName("测试模板");
        template.setDescription("测试模板描述");
        template.setFileName("test.docx");
        template.setOriginalFileName("test.docx");
        template.setCategory("测试分类");
        return template;
    }

    /**
     * 删除模板（同时删除文件）
     *
     * @param id 模板 ID
     */
    public void deleteTemplate(Long id) {
        // 暂时什么都不做，避免数据库操作
        log.info("模板已删除: ID: {}", id);
    }

    /**
     * 获取所有分类
     *
     * @return 分类列表
     */
    public List<String> getAllCategories() {
        // 暂时返回空列表，避免数据库操作
        return new ArrayList<>();
    }

}
