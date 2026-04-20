package com.docgen.controller;

import com.docgen.common.Result;
import com.docgen.dto.GenerateRequest;
import com.docgen.service.DocumentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 文档生成 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 生成文档
     * 接收 JSON 格式的 GenerateRequest，返回生成的文件
     */
    @PostMapping("/generate")
    public void generateDocument(@Valid @RequestBody GenerateRequest request,
                                  HttpServletResponse response) throws IOException {
        Map<String, Object> result = documentService.generateDocument(
                request.getTemplateId(),
                request.getData(),
                request.getOutputFormat()
        );

        byte[] fileBytes = (byte[]) result.get("bytes");
        String fileName = (String) result.get("fileName");
        String contentType = (String) result.get("contentType");

        // 设置响应头
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        response.setContentLength(fileBytes.length);

        // 写入响应流
        try (OutputStream out = response.getOutputStream()) {
            out.write(fileBytes);
            out.flush();
        }

        log.info("文档已发送: {}", fileName);
    }

    /**
     * 下载已生成的文档
     */
    @GetMapping("/download/{fileName}")
    public void downloadDocument(@PathVariable String fileName,
                                  HttpServletResponse response) throws IOException {
        java.nio.file.Path filePath = documentService.getOutputFilePath(fileName);
        byte[] fileBytes = java.nio.file.Files.readAllBytes(filePath);

        // 根据扩展名判断 Content-Type
        String contentType;
        if (fileName.endsWith(".pdf")) {
            contentType = "application/pdf";
        } else if (fileName.endsWith(".docx")) {
            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else {
            contentType = "application/octet-stream";
        }

        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        response.setContentLength(fileBytes.length);

        try (OutputStream out = response.getOutputStream()) {
            out.write(fileBytes);
            out.flush();
        }

        log.info("文档已下载: {}", fileName);
    }

}
