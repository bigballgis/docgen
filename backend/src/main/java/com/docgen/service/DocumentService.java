package com.docgen.service;

import com.docgen.config.FileStorageConfig;
import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.entity.Document;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.repository.DocumentRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文档服务
 * 提供文档生成、查询、下载、删除等功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final FileStorageConfig fileStorageConfig;
    private final ObjectMapper objectMapper;

    /** 占位符正则表达式，匹配 {xxx} 格式 */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    /**
     * 生成文档（异步版本，直接写入 response）
     * 根据模板和字段数据生成 docx 或 pdf 文件
     *
     * @param request  生成文档请求
     * @param userId   操作用户ID
     * @param tenantId 租户ID
     * @param response HTTP 响应对象
     */
    @Transactional
    public void generateDocument(GenerateDocumentRequest request, Long userId, String tenantId,
                                 HttpServletResponse response) {
        // 查找模板
        Template template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new BusinessException("模板不存在"));

        // 构建模板文件路径
        Path templatePath = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize()
                .resolve(template.getFileName());

        if (!Files.exists(templatePath)) {
            throw new BusinessException("模板文件不存在: " + template.getFileName());
        }

        // 确定输出格式
        String outputFormat = (request.getFormat() != null && !request.getFormat().isBlank())
                ? request.getFormat().toLowerCase() : "docx";
        String extension = outputFormat.equals("pdf") ? ".pdf" : ".docx";

        // 生成输出文件名
        String outputFileName = UUID.randomUUID() + extension;
        Path outputDir = Paths.get(fileStorageConfig.getOutputDir()).toAbsolutePath().normalize();
        Path outputPath = outputDir.resolve(outputFileName);

        try {
            // 确保输出目录存在
            Files.createDirectories(outputDir);

            // 使用 Apache POI 打开模板 docx
            try (FileInputStream fis = new FileInputStream(templatePath.toFile());
                 XWPFDocument document = new XWPFDocument(fis)) {

                // 替换段落中的占位符
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    replacePlaceholdersInParagraph(paragraph, request.getFields());
                }

                // 替换表格中的占位符
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                replacePlaceholdersInParagraph(paragraph, request.getFields());
                            }
                        }
                    }
                }

                // 先保存为 docx
                Path docxOutputPath = outputPath;
                if ("pdf".equals(outputFormat)) {
                    docxOutputPath = outputDir.resolve(UUID.randomUUID() + ".docx");
                }

                try (OutputStream os = Files.newOutputStream(docxOutputPath)) {
                    document.write(os);
                }

                // 如果需要 PDF 格式，使用 LibreOffice 转换
                if ("pdf".equals(outputFormat)) {
                    convertToPdf(docxOutputPath, outputDir);
                    Files.deleteIfExists(docxOutputPath);
                }
            }

            // 创建 Document 记录
            Document doc = Document.builder()
                    .templateId(request.getTemplateId())
                    .templateName(template.getName())
                    .fileKey(outputFileName)
                    .fileName(template.getName() + extension)
                    .outputFormat(outputFormat)
                    .status("completed")
                    .userId(userId)
                    .tenantId(tenantId != null ? tenantId : "default")
                    .createdAt(LocalDateTime.now())
                    .build();
            documentRepository.save(doc);

            // 将生成的文件写入 response
            String contentType;
            if ("pdf".equals(outputFormat)) {
                contentType = "application/pdf";
            } else {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + outputFileName + "\"");
            response.setContentLengthLong(Files.size(outputPath));

            try (OutputStream os = response.getOutputStream();
                 FileInputStream fis = new FileInputStream(outputPath.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }

            log.info("文档生成成功: {} (格式: {})", outputFileName, outputFormat);

        } catch (IOException e) {
            log.error("文档生成失败: {}", e.getMessage(), e);
            throw new BusinessException("文档生成失败: " + e.getMessage());
        }
    }

    /**
     * 同步生成文档
     * 同步方式生成文档，返回生成结果
     *
     * @param request  生成文档请求
     * @param userId   操作用户ID
     * @param tenantId 租户ID
     * @return 生成结果（包含文档信息和下载地址）
     */
    @Transactional
    public Map<String, Object> generateDocumentSync(GenerateDocumentRequest request, Long userId, String tenantId) {
        // 查找模板
        Template template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new BusinessException("模板不存在"));

        // 构建模板文件路径
        Path templatePath = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize()
                .resolve(template.getFileName());

        if (!Files.exists(templatePath)) {
            throw new BusinessException("模板文件不存在: " + template.getFileName());
        }

        // 确定输出格式
        String outputFormat = (request.getFormat() != null && !request.getFormat().isBlank())
                ? request.getFormat().toLowerCase() : "docx";
        String extension = outputFormat.equals("pdf") ? ".pdf" : ".docx";

        // 生成输出文件名
        String outputFileName = UUID.randomUUID() + extension;
        Path outputDir = Paths.get(fileStorageConfig.getOutputDir()).toAbsolutePath().normalize();
        Path outputPath = outputDir.resolve(outputFileName);

        try {
            Files.createDirectories(outputDir);

            try (FileInputStream fis = new FileInputStream(templatePath.toFile());
                 XWPFDocument document = new XWPFDocument(fis)) {

                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    replacePlaceholdersInParagraph(paragraph, request.getFields());
                }

                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                replacePlaceholdersInParagraph(paragraph, request.getFields());
                            }
                        }
                    }
                }

                Path docxOutputPath = outputPath;
                if ("pdf".equals(outputFormat)) {
                    docxOutputPath = outputDir.resolve(UUID.randomUUID() + ".docx");
                }

                try (OutputStream os = Files.newOutputStream(docxOutputPath)) {
                    document.write(os);
                }

                if ("pdf".equals(outputFormat)) {
                    convertToPdf(docxOutputPath, outputDir);
                    Files.deleteIfExists(docxOutputPath);
                }
            }

            // 创建 Document 记录
            Document doc = Document.builder()
                    .templateId(request.getTemplateId())
                    .templateName(template.getName())
                    .fileKey(outputFileName)
                    .fileName(template.getName() + extension)
                    .outputFormat(outputFormat)
                    .status("completed")
                    .userId(userId)
                    .tenantId(tenantId != null ? tenantId : "default")
                    .createdAt(LocalDateTime.now())
                    .build();
            documentRepository.save(doc);

            Map<String, Object> result = new HashMap<>();
            result.put("documentId", doc.getId());
            result.put("fileName", outputFileName);
            result.put("downloadUrl", "/api/v1/documents/download/" + outputFileName);
            result.put("status", "completed");
            result.put("format", outputFormat);

            log.info("文档同步生成成功: {} (格式: {})", outputFileName, outputFormat);
            return result;

        } catch (IOException e) {
            log.error("文档同步生成失败: {}", e.getMessage(), e);
            throw new BusinessException("文档生成失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询文档列表
     *
     * @param tenantId 租户ID
     * @param page     页码
     * @param size     每页大小
     * @return 文档分页列表
     */
    public Page<Document> getDocumentList(String keyword, String status, int page, int size, String tenantId, Long userId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 处理空字符串参数
        String tid = (tenantId != null && !tenantId.isEmpty()) ? tenantId : null;
        String kw = (keyword != null && !keyword.isEmpty()) ? keyword : null;
        String st = (status != null && !status.isEmpty()) ? status : null;

        // 使用数据库层面过滤的查询方法
        return documentRepository.findWithFilters(tid, kw, st, pageable);
    }

    /**
     * 下载文档
     * 验证文件名安全性，设置响应头，以流方式返回文件
     *
     * @param fileName    文件名
     * @param response    HTTP 响应对象
     */
    public void downloadDocument(String fileName, HttpServletResponse response) {
        // 验证 fileName 安全性（防止路径遍历攻击）
        if (!StringUtils.hasText(fileName) || fileName.contains("..") || fileName.contains("/")
                || fileName.contains("\\")) {
            throw new BusinessException("无效的文件名");
        }

        Path filePath = Paths.get(fileStorageConfig.getOutputDir()).toAbsolutePath().normalize()
                .resolve(fileName);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new BusinessException("文件不存在");
        }

        // 确定 Content-Type
        String contentType;
        if (fileName.toLowerCase().endsWith(".pdf")) {
            contentType = "application/pdf";
        } else if (fileName.toLowerCase().endsWith(".docx")) {
            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else {
            contentType = "application/octet-stream";
        }

        // 设置响应头
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentLengthLong(filePath.toFile().length());

        // 以流方式写入文件
        try (OutputStream os = response.getOutputStream();
             FileInputStream fis = new FileInputStream(filePath.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        } catch (IOException e) {
            log.error("文件下载失败: {}", e.getMessage(), e);
            throw new BusinessException("文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 软删除文档
     * 设置 deletedAt 字段标记为已删除
     *
     * @param id 文档ID
     */
    public void deleteDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);
        log.info("文档已软删除: {} (ID: {})", document.getFileName(), id);
    }

    /**
     * 查询任务状态
     * 返回包含文档状态信息的 Map
     *
     * @param taskId 文档的 fileKey（任务ID）
     * @return 任务状态信息 Map
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        Document document = documentRepository.findByFileKey(taskId)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        Map<String, Object> status = new HashMap<>();
        status.put("taskId", taskId);
        status.put("status", document.getStatus());
        status.put("documentId", document.getId());
        status.put("fileName", document.getFileName());
        status.put("createdAt", document.getCreatedAt());
        return status;
    }

    /**
     * 导出文档列表
     * 支持导出为 CSV 或 JSON 格式
     *
     * @param tenantId 租户ID
     * @param format   导出格式（csv/json）
     * @param response HTTP 响应对象
     */
    public void exportDocuments(String tenantId, String format, HttpServletResponse response) {
        try {
            if (!"json".equalsIgnoreCase(format) && !"csv".equalsIgnoreCase(format)) {
                throw new BusinessException(400, "不支持的导出格式: " + format);
            }

            // 使用数据库层面过滤查询未软删除的文档
            String tid = (tenantId != null && !tenantId.isEmpty()) ? tenantId : null;
            List<Document> documents = documentRepository.findAllActive(tid);

            if ("json".equalsIgnoreCase(format)) {
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Content-Disposition", "attachment;filename=documents.json");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(response.getOutputStream(), documents);
            } else {
                // CSV 格式
                response.setContentType("text/csv;charset=UTF-8");
                response.setHeader("Content-Disposition", "attachment;filename=documents.csv");

                try (OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write("ID,TemplateName,FileName,Format,Status,CreateTime\n");
                    for (Document d : documents) {
                        writer.write(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                                d.getId(),
                                d.getTemplateName() != null ? d.getTemplateName().replace("\"", "\"\"") : "",
                                d.getFileName() != null ? d.getFileName().replace("\"", "\"\"") : "",
                                d.getOutputFormat() != null ? d.getOutputFormat() : "",
                                d.getStatus() != null ? d.getStatus() : "",
                                d.getCreatedAt() != null ? d.getCreatedAt().toString() : ""));
                    }
                    writer.flush();
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("导出文档列表失败: {}", e.getMessage(), e);
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

    /**
     * 替换段落中的占位符
     * 遍历段落中的所有 Run，查找并替换 {xxx} 格式的占位符
     *
     * @param paragraph 段落对象
     * @param fields    字段数据
     */
    private void replacePlaceholdersInParagraph(XWPFParagraph paragraph, Map<String, Object> fields) {
        String text = paragraph.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return;
        }

        // 对整个段落文本进行替换
        String newText = text;
        matcher.reset();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = fields != null ? fields.get(placeholder) : null;
            if (value != null) {
                newText = newText.replace("{" + placeholder + "}", String.valueOf(value));
            }
        }

        // 清除原有的 Run，设置新文本
        if (!newText.equals(text)) {
            if (!paragraph.getRuns().isEmpty()) {
                XWPFRun firstRun = paragraph.getRuns().get(0);
                for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
                    paragraph.getRuns().get(i).setText("", 0);
                }
                firstRun.setText(newText, 0);
            }
        }
    }

    /**
     * 使用 LibreOffice 将 docx 转换为 PDF
     *
     * @param docxPath docx 文件路径
     * @param outputDir 输出目录
     * @throws IOException 转换失败时抛出
     */
    private void convertToPdf(Path docxPath, Path outputDir) throws IOException {
        // 路径安全校验
        String docxStr = docxPath.toString();
        String outStr = outputDir.toString();
        if (docxStr.contains("..") || docxStr.contains("\0") || !docxStr.endsWith(".docx")) {
            throw new BusinessException("非法的文件路径");
        }
        if (outStr.contains("..") || outStr.contains("\0")) {
            throw new BusinessException("非法的输出路径");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "libreoffice", "--headless", "--convert-to", "pdf",
                    "--outdir", outputDir.toString(),
                    docxPath.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("LibreOffice 转换超时");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new BusinessException("LibreOffice 转换失败，退出码: " + exitCode);
            }

            log.info("PDF 转换成功: {}", docxPath.getFileName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("PDF 转换被中断");
        }
    }
}
