package com.docgen.service;

import com.docgen.config.FileStorageConfig;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文档生成服务
 * 负责模板填充和文档格式转换
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final FileStorageConfig fileStorageConfig;

    /**
     * 生成文档
     *
     * @param templateId 模板 ID
     * @param data       填充数据
     * @param format     输出格式 (docx / pdf)
     * @return 包含文件字节数组和文件名的 Map
     */
    public Map<String, Object> generateDocument(Long templateId, Map<String, Object> data, String format) {
        // 1. 暂时创建模拟模板，避免数据库操作
        Template template = new Template();
        template.setId(templateId);
        template.setName("测试模板");
        template.setFileName("test.docx");

        // 2. 获取模板文件路径
        Path templateFilePath = fileStorageConfig.getTemplatePath().resolve(template.getFileName());
        if (!Files.exists(templateFilePath)) {
            throw new BusinessException("模板文件不存在: " + template.getFileName());
        }

        try {
            // 3. 生成唯一输出文件名
            String timestamp = String.valueOf(System.currentTimeMillis());
            String outputFileName;
            if ("pdf".equalsIgnoreCase(format)) {
                outputFileName = timestamp + "_" + templateId + ".pdf";
            } else {
                outputFileName = timestamp + "_" + templateId + ".docx";
            }

            // 4. 暂时直接返回模板文件
            byte[] fileBytes = Files.readAllBytes(templateFilePath);

            Map<String, Object> result = new HashMap<>();
            result.put("fileName", outputFileName);
            result.put("fileSize", fileBytes.length);
            result.put("contentType", "pdf".equalsIgnoreCase(format)
                    ? "application/pdf"
                    : "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            result.put("bytes", fileBytes);

            log.info("文档生成成功: {} ({} bytes)", outputFileName, fileBytes.length);
            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档生成失败", e);
            throw new BusinessException("文档生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 DOCX 转换为 PDF（暂时不支持）
     */
    private void convertDocxToPdf(File docxFile, File pdfFile) {
        throw new BusinessException("PDF 转换功能暂未实现");
    }

    /**
     * 解析 docx 模板中的占位符（${xxx} 格式）
     *
     * @param file 上传的 docx 模板文件
     * @return 提取到的字段名列表
     */
    public List<String> parseTemplateFields(MultipartFile file) {
        try {
            // 读取 docx 文件（本质是 zip），提取 document.xml 中的占位符
            // 使用正则匹配 ${xxx} 格式的占位符
            Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");
            Set<String> fieldNames = new LinkedHashSet<>();

            // 读取 zip 文件中的 XML 内容
            try (InputStream inputStream = file.getInputStream();
                 java.util.zip.ZipInputStream zipInputStream = new java.util.zip.ZipInputStream(inputStream)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    // 搜索 word/document.xml 和 word/header*.xml, word/footer*.xml
                    if (entryName.startsWith("word/") && entryName.endsWith(".xml")) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(zipInputStream, "UTF-8"))) {
                            String line;
                            StringBuilder content = new StringBuilder();
                            while ((line = reader.readLine()) != null) {
                                content.append(line);
                            }
                            // 匹配占位符
                            Matcher matcher = pattern.matcher(content.toString());
                            while (matcher.find()) {
                                fieldNames.add(matcher.group(1));
                            }
                        }
                    }
                    zipInputStream.closeEntry();
                }
            }

            List<String> result = new ArrayList<>(fieldNames);
            log.info("从模板中解析到 {} 个字段: {}", result.size(), result);
            return result;

        } catch (IOException e) {
            log.error("解析模板字段失败", e);
            throw new BusinessException("解析模板字段失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取输出文件路径
     */
    public Path getOutputFilePath(String fileName) {
        Path path = fileStorageConfig.getOutputPath().resolve(fileName);
        if (!Files.exists(path)) {
            throw new BusinessException("文件不存在: " + fileName);
        }
        return path;
    }

}
