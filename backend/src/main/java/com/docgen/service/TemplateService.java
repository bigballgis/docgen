package com.docgen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.docgen.config.FileStorageConfig;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVersion;
import com.docgen.exception.BusinessException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVersionRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 模板服务
 * 提供模板上传、查询、删除、字段解析等功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final FileStorageConfig fileStorageConfig;
    private final ObjectMapper objectMapper;

    /** 占位符正则表达式，匹配 {xxx} 和 ${xxx} 格式 */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$?\\{([^}]+)\\}");

    /**
     * 上传模板文件
     * 验证文件格式、保存文件、解析模板字段、创建数据库记录
     *
     * @param file        上传的模板文件
     * @param name        模板名称
     * @param description 模板描述
     * @param category    模板分类
     * @param userId      上传用户ID
     * @param tenantId    租户ID
     * @return 创建的模板实体
     */
    @Transactional
    public Template uploadTemplate(MultipartFile file, String name, String description,
                                   String category, Long userId, String tenantId) {
        // 验证文件是否为 .docx 格式
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".docx")) {
            throw new BusinessException("仅支持 .docx 格式的模板文件");
        }

        try {
            // 生成唯一文件名（UUID + 原始扩展名）
            String uniqueFileName = UUID.randomUUID() + ".docx";
            Path uploadDir = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize();
            Path targetPath = uploadDir.resolve(uniqueFileName);

            // 保存文件到上传目录
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 解析模板字段
            List<Map<String, String>> fields = parseFieldsFromFile(targetPath.toString());
            String fieldsJson = convertFieldsToJson(fields);

            // 创建 Template 记录
            Template template = Template.builder()
                    .name(name)
                    .description(description)
                    .fileName(uniqueFileName)
                    .originalFileName(originalFileName)
                    .fields(fieldsJson)
                    .category(category)
                    .userId(userId)
                    .tenantId(tenantId != null ? tenantId : "default")
                    .status("draft")
                    .version(1)
                    .currentVersion(1)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            template = templateRepository.save(template);

            // 获取文件大小
            long fileSize = Files.size(targetPath);

            // 创建初始 TemplateVersion 记录
            TemplateVersion templateVersion = TemplateVersion.builder()
                    .templateId(template.getId())
                    .version(1)
                    .filePath(uniqueFileName)
                    .fileSize(fileSize)
                    .changeNote("初始版本")
                    .createdBy(userId)
                    .createdAt(LocalDateTime.now())
                    .build();
            templateVersionRepository.save(templateVersion);

            log.info("模板上传成功: {} (ID: {})", name, template.getId());
            return template;

        } catch (IOException e) {
            log.error("模板文件保存失败: {}", e.getMessage(), e);
            throw new BusinessException("模板文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 获取模板列表（分页）
     * 支持关键字搜索、分类过滤、状态过滤
     *
     * @param tenantId 租户ID
     * @param page     页码
     * @param size     每页大小
     * @param keyword  搜索关键字（匹配名称和描述）
     * @param category 分类过滤
     * @param status   状态过滤（仅管理员可用）
     * @return 模板分页列表
     */
    public Page<Template> getTemplateList(String tenantId, int page, int size,
                                          String keyword, String category, String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));

        // 处理空字符串参数，转换为 null 以匹配 @Query 中的 IS NULL 条件
        String kw = (keyword != null && !keyword.isBlank()) ? keyword : null;
        String cat = (category != null && !category.isBlank()) ? category : null;
        String st = (status != null && !status.isBlank()) ? status : null;

        // 使用数据库层面过滤的查询方法
        return templateRepository.findWithFilters(tenantId, kw, cat, st, pageable);
    }

    /**
     * 根据ID获取模板详情
     *
     * @param id 模板ID
     * @return 模板实体
     */
    public Template getTemplateById(Long id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模板不存在"));
        return template;
    }

    /**
     * 删除模板（调用 removeTemplate）
     *
     * @param id 模板ID
     */
    public void deleteTemplate(Long id) {
        removeTemplate(id);
    }

    /**
     * 删除模板（硬删除，仅管理员可用）
     * 同时删除模板文件和数据库记录
     *
     * @param id 模板ID
     */
    @Transactional
    public void removeTemplate(Long id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模板不存在"));

        // 删除模板文件
        if (template.getFileName() != null) {
            Path filePath = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize()
                    .resolve(template.getFileName());
            try {
                Files.deleteIfExists(filePath);
                log.info("模板文件已删除: {}", filePath);
            } catch (IOException e) {
                log.warn("模板文件删除失败: {}", filePath, e);
            }
        }

        // 删除关联的版本记录
        List<TemplateVersion> versions = templateVersionRepository.findByTemplateIdOrderByVersionDesc(id);
        templateVersionRepository.deleteAll(versions);

        // 删除模板记录
        templateRepository.delete(template);
        log.info("模板已删除: {} (ID: {})", template.getName(), id);
    }

    /**
     * 获取所有不重复的分类名称
     *
     * @param tenantId 租户ID
     * @return 分类名称列表
     */
    public List<String> getCategories(String tenantId) {
        return getAllCategories(tenantId);
    }

    /**
     * 获取所有不重复的分类名称
     *
     * @param tenantId 租户ID
     * @return 分类名称列表
     */
    public List<String> getAllCategories(String tenantId) {
        return templateRepository.findDistinctCategories(tenantId);
    }

    /**
     * 获取待审批模板列表（分页）
     *
     * @param tenantId 租户ID
     * @param page     页码
     * @param size     每页大小
     * @return 待审批模板分页列表
     */
    public Page<Template> getPendingTemplates(String tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        // 使用数据库层面过滤的查询方法
        return templateRepository.findPendingTemplates(tenantId, pageable);
    }

    /**
     * 提交模板审批
     * 将模板状态从草稿变更为待审批
     *
     * @param templateId 模板ID
     * @param userId     提交人用户ID
     * @return 更新后的模板信息
     */
    @Transactional
    public Template submitForApproval(Long templateId, Long userId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在"));

        if (!"draft".equals(template.getStatus()) && !"rejected".equals(template.getStatus())) {
            throw new BusinessException("只有草稿或已驳回状态的模板才能提交审批，当前状态：" + template.getStatus());
        }

        template.setStatus("pending");
        template.setUpdateTime(LocalDateTime.now());
        templateRepository.save(template);

        log.info("模板 {} 已提交审批", templateId);
        return template;
    }

    /**
     * 审批通过模板
     *
     * @param templateId 模板ID
     * @param userId     审批人用户ID
     * @param comment    审批意见
     * @return 更新后的模板信息
     */
    @Transactional
    public Template approveTemplate(Long templateId, Long userId, String comment) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在"));

        if (!"pending".equals(template.getStatus())) {
            throw new BusinessException("只有待审批状态的模板才能被审批通过，当前状态：" + template.getStatus());
        }

        template.setStatus("published");
        template.setVersion(template.getVersion() + 1);
        template.setCurrentVersion(template.getVersion());
        template.setApprovedBy(userId);
        template.setApprovedAt(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        templateRepository.save(template);

        log.info("模板 {} 审批通过", templateId);
        return template;
    }

    /**
     * 驳回模板
     *
     * @param templateId 模板ID
     * @param userId     审批人用户ID
     * @param reason     驳回原因
     * @return 更新后的模板信息
     */
    @Transactional
    public Template rejectTemplate(Long templateId, Long userId, String reason) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在"));

        if (!"pending".equals(template.getStatus())) {
            throw new BusinessException("只有待审批状态的模板才能被驳回，当前状态：" + template.getStatus());
        }

        template.setStatus("rejected");
        template.setRejectReason(reason);
        template.setUpdateTime(LocalDateTime.now());
        templateRepository.save(template);

        log.info("模板 {} 已驳回", templateId);
        return template;
    }

    /**
     * 导出模板列表
     * 支持导出为 CSV 或 JSON 格式
     *
     * @param tenantId 租户ID
     * @param format   导出格式（csv/json）
     * @param response HTTP 响应对象
     */
    public void exportTemplates(String tenantId, String format, HttpServletResponse response) {
        try {
            if (!"json".equalsIgnoreCase(format) && !"csv".equalsIgnoreCase(format)) {
                throw new BusinessException(400, "不支持的导出格式: " + format);
            }

            // 使用数据库层面过滤查询未软删除的模板
            List<Template> templates = templateRepository.findAllActiveByTenantId(tenantId);

            if ("json".equalsIgnoreCase(format)) {
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Content-Disposition", "attachment;filename=templates.json");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(response.getOutputStream(), templates);
            } else {
                // CSV 格式
                response.setContentType("text/csv;charset=UTF-8");
                response.setHeader("Content-Disposition", "attachment;filename=templates.csv");

                try (OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write("ID,Name,Category,Status,Version,CreateTime\n");
                    for (Template t : templates) {
                        writer.write(String.format("%d,\"%s\",\"%s\",\"%s\",%d,\"%s\"\n",
                                t.getId(),
                                t.getName() != null ? t.getName().replace("\"", "\"\"") : "",
                                t.getCategory() != null ? t.getCategory().replace("\"", "\"\"") : "",
                                t.getStatus() != null ? t.getStatus() : "",
                                t.getVersion(),
                                t.getCreateTime() != null ? t.getCreateTime().toString() : ""));
                    }
                    writer.flush();
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("导出模板列表失败: {}", e.getMessage(), e);
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

    /**
     * 解析模板字段
     * 读取模板文件，提取占位符，更新数据库中的 fields 字段
     *
     * @param id 模板ID
     * @return 解析后的字段列表
     */
    @Transactional
    public List<Map<String, Object>> parseTemplateFields(Long id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模板不存在"));

        // 构建文件路径
        Path filePath = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize()
                .resolve(template.getFileName());

        // 解析字段
        List<Map<String, String>> fields = parseFieldsFromFile(filePath.toString());

        // 更新数据库中的 fields 字段
        String fieldsJson = convertFieldsToJson(fields);
        template.setFields(fieldsJson);
        template.setUpdateTime(LocalDateTime.now());
        templateRepository.save(template);

        log.info("模板 {} 的字段已更新，共 {} 个字段", template.getName(), fields.size());

        // 转换为 List<Map<String, Object>> 以匹配 Controller 的返回类型
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, String> field : fields) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", field.get("name"));
            map.put("type", field.get("type"));
            result.add(map);
        }
        return result;
    }

    /**
     * 从 docx 文件中解析占位符字段（私有方法）
     * 使用 ZipFile 读取 docx 中的 word/document.xml，用正则提取 {xxx}/${xxx} 占位符
     *
     * @param filePath docx 文件路径
     * @return 字段列表，每个字段包含 name 和 type
     */
    private List<Map<String, String>> parseFieldsFromFile(String filePath) {
        List<Map<String, String>> fields = new ArrayList<>();
        Set<String> fieldNames = new LinkedHashSet<>();

        try (ZipFile zipFile = new ZipFile(filePath)) {
            ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) {
                log.warn("docx 文件中未找到 word/document.xml: {}", filePath);
                return fields;
            }

            // 读取 XML 内容
            String xmlContent = new String(zipFile.getInputStream(entry).readAllBytes());

            // 提取所有占位符
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(xmlContent);
            while (matcher.find()) {
                String fieldName = matcher.group(1).trim();
                if (!fieldName.isEmpty()) {
                    fieldNames.add(fieldName);
                }
            }
        } catch (IOException e) {
            log.error("解析 docx 文件失败: {}", filePath, e);
            throw new BusinessException("解析模板文件失败: " + e.getMessage());
        }

        // 构建字段列表
        for (String fieldName : fieldNames) {
            Map<String, String> field = new LinkedHashMap<>();
            field.put("name", fieldName);
            field.put("type", guessFieldType(fieldName));
            fields.add(field);
        }

        return fields;
    }

    /**
     * 根据字段名猜测字段类型（私有方法）
     * 根据字段名中的关键词推断类型：date/number/array/text
     *
     * @param fieldName 字段名称
     * @return 推断的字段类型
     */
    private String guessFieldType(String fieldName) {
        String lowerName = fieldName.toLowerCase();

        // 日期类型
        if (lowerName.contains("date") || lowerName.contains("日期") || lowerName.contains("时间")
                || lowerName.contains("time") || lowerName.contains("year") || lowerName.contains("month")
                || lowerName.contains("day") || lowerName.contains("年") || lowerName.contains("月")) {
            return "date";
        }

        // 数字类型
        if (lowerName.contains("amount") || lowerName.contains("数量") || lowerName.contains("price")
                || lowerName.contains("价格") || lowerName.contains("total") || lowerName.contains("合计")
                || lowerName.contains("count") || lowerName.contains("num") || lowerName.contains("金额")
                || lowerName.contains("rate") || lowerName.contains("费率") || lowerName.contains("tax")) {
            return "number";
        }

        // 数组类型
        if (lowerName.contains("list") || lowerName.contains("items") || lowerName.contains("列表")
                || lowerName.contains("明细") || lowerName.contains("array") || lowerName.contains("详情")) {
            return "array";
        }

        // 默认文本类型
        return "text";
    }

    /**
     * 将字段列表转换为 JSON 字符串
     *
     * @param fields 字段列表
     * @return JSON 字符串
     */
    private String convertFieldsToJson(List<Map<String, String>> fields) {
        if (fields == null || fields.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < fields.size(); i++) {
            Map<String, String> field = fields.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"name\":\"").append(escapeJson(field.get("name")))
                    .append("\",\"type\":\"").append(escapeJson(field.get("type")))
                    .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
