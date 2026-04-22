package com.docgen.service;

import com.docgen.config.FileStorageConfig;
import com.docgen.dto.EditorCallbackRequest;
import com.docgen.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 编辑器服务
 * 对应 Node.js 的 editorService.js
 * 负责与 Euro-Office 在线编辑器的集成，包括配置生成、回调处理和文件路径查找
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EditorService {

    /** Euro-Office 服务地址 */
    @Value("${euro-office.url:}")
    private String euroOfficeUrl;

    /** Euro-Office JWT 密钥 */
    @Value("${euro-office.jwt-secret:}")
    private String euroOfficeJwtSecret;

    private final FileStorageConfig fileStorageConfig;

    /**
     * 检查 Euro-Office 编辑器是否可用
     * 通过检查 URL 是否已配置来判断
     *
     * @return true 表示可用，false 表示不可用
     */
    public boolean isAvailable() {
        boolean available = euroOfficeUrl != null && !euroOfficeUrl.trim().isEmpty();
        log.debug("Euro-Office 编辑器可用状态: {}", available);
        return available;
    }

    /**
     * 获取编辑器配置
     * 如果编辑器不可用，返回 available=false
     * 如果可用，返回包含编辑器 URL 和 JWT Token 的配置信息
     *
     * @param fileKey 文件标识（通常是文件名）
     * @return 编辑器配置 Map
     */
    public Map<String, Object> getEditorConfig(String fileKey) {
        log.info("获取编辑器配置, fileKey={}", fileKey);

        Map<String, Object> config = new HashMap<>();

        if (!isAvailable()) {
            config.put("available", false);
            config.put("message", "Euro-Office 编辑器未配置");
            return config;
        }

        // 查找文件路径
        String filePath = getEditorFileDownloadPath(fileKey);
        if (filePath == null) {
            config.put("available", false);
            config.put("message", "文件不存在: " + fileKey);
            return config;
        }

        // 生成 JWT Token（简化实现）
        String token = generateEditorToken(fileKey);

        // 构建编辑器 URL
        String editorUrl = euroOfficeUrl;
        if (!editorUrl.endsWith("/")) {
            editorUrl += "/";
        }

        config.put("available", true);
        config.put("url", editorUrl);
        config.put("token", token);
        config.put("fileKey", fileKey);
        config.put("callbackUrl", "/api/editor/callback");

        log.info("编辑器配置获取成功, fileKey={}", fileKey);
        return config;
    }

    /**
     * 处理编辑器保存回调
     * 当用户在 Euro-Office 中编辑并保存文件时，编辑器会调用此接口
     *
     * @param callbackData 回调数据，包含文件信息
     * @return 处理结果
     */
    public Map<String, Object> handleEditorCallback(EditorCallbackRequest callbackData) {
        log.info("处理编辑器回调, callbackData={}", callbackData);

        Map<String, Object> result = new HashMap<>();

        try {
            // 提取回调数据中的关键信息
            String fileKey = callbackData.getFileKey();
            String status = callbackData.getStatus();

            if (fileKey == null || fileKey.isEmpty()) {
                result.put("success", false);
                result.put("message", "缺少 fileKey 参数");
                return result;
            }

            // 根据状态处理不同的回调类型
            if ("saved".equals(status)) {
                // 文件已保存
                log.info("编辑器回调: 文件已保存, fileKey={}", fileKey);
                result.put("success", true);
                result.put("message", "文件保存成功");
            } else if ("closed".equals(status)) {
                // 编辑器已关闭
                log.info("编辑器回调: 编辑器已关闭, fileKey={}", fileKey);
                result.put("success", true);
                result.put("message", "编辑器已关闭");
            } else if ("error".equals(status)) {
                // 编辑出错
                log.error("编辑器回调: 编辑出错, fileKey={}", fileKey);
                result.put("success", false);
                result.put("message", "编辑出错");
            } else {
                // 未知状态
                log.warn("编辑器回调: 未知状态, status={}, fileKey={}", status, fileKey);
                result.put("success", true);
                result.put("message", "已收到回调，状态: " + status);
            }

            result.put("fileKey", fileKey);
        } catch (Exception e) {
            log.error("处理编辑器回调异常", e);
            result.put("success", false);
            result.put("message", "处理回调失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 处理编辑器回调（供 Controller 的 handleCallback 调用）
     *
     * @param callbackData 回调数据
     */
    public void handleCallback(EditorCallbackRequest callbackData) {
        handleEditorCallback(callbackData);
    }

    /**
     * 下载编辑器文件（供 Controller 的 downloadFile 调用）
     *
     * @param fileKey  文件标识
     * @param response HTTP 响应对象
     */
    public void downloadFile(String fileKey, HttpServletResponse response) {
        String filePath = getEditorFileDownloadPath(fileKey);
        if (filePath == null) {
            throw new BusinessException("文件不存在: " + fileKey);
        }

        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new BusinessException("文件不存在");
            }

            String fileName = path.getFileName().toString();
            String contentType;
            if (fileName.toLowerCase().endsWith(".pdf")) {
                contentType = "application/pdf";
            } else if (fileName.toLowerCase().endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else {
                contentType = "application/octet-stream";
            }

            response.setContentType(contentType);
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
            log.error("下载编辑器文件失败: {}", e.getMessage(), e);
            throw new BusinessException("文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 查找编辑器文件的下载路径
     * 在 outputs 和 uploads 目录中搜索指定文件名的文件
     *
     * @param fileKey 文件标识（通常是文件名）
     * @return 文件的完整路径，如果未找到则返回 null
     */
    public String getEditorFileDownloadPath(String fileKey) {
        log.info("查找编辑器文件路径, fileKey={}", fileKey);

        // 路径遍历防护
        if (fileKey == null || fileKey.contains("..") || fileKey.contains("/") || fileKey.contains("\\") || fileKey.contains("\0")) {
            throw new BusinessException(400, "非法的文件标识");
        }

        if (fileKey.isEmpty()) {
            return null;
        }

        // 在 outputs 目录中搜索
        String outputPath = searchFile(fileStorageConfig.getOutputDir(), fileKey);
        if (outputPath != null) {
            log.info("在 outputs 目录中找到文件: {}", outputPath);
            return outputPath;
        }

        // 在 uploads 目录中搜索
        String uploadPath = searchFile(fileStorageConfig.getUploadDir(), fileKey);
        if (uploadPath != null) {
            log.info("在 uploads 目录中找到文件: {}", uploadPath);
            return uploadPath;
        }

        log.warn("未找到文件: {}", fileKey);
        return null;
    }

    /**
     * 在指定目录中搜索文件
     * 支持直接匹配文件名，也支持匹配包含文件名的路径
     *
     * @param searchDir 搜索目录
     * @param fileKey   文件标识
     * @return 文件完整路径，未找到返回 null
     */
    private String searchFile(String searchDir, String fileKey) {
        // 路径遍历防护
        if (fileKey == null || fileKey.contains("..") || fileKey.contains("/") || fileKey.contains("\\") || fileKey.contains("\0")) {
            throw new BusinessException(400, "非法的文件标识");
        }

        try {
            Path dirPath = Paths.get(searchDir).toAbsolutePath().normalize();
            if (!Files.exists(dirPath)) {
                return null;
            }

            // 先尝试直接匹配
            Path directPath = dirPath.resolve(fileKey).toAbsolutePath().normalize();
            if (Files.exists(directPath)) {
                return directPath.toString();
            }

            // 递归搜索（限制搜索深度为 3 层）
            return searchFileRecursive(dirPath, fileKey, 0, 3);
        } catch (Exception e) {
            log.error("搜索文件异常, dir={}, fileKey={}", searchDir, fileKey, e);
            return null;
        }
    }

    /**
     * 递归搜索文件
     *
     * @param dir      当前搜索目录
     * @param fileKey  文件标识
     * @param depth    当前深度
     * @param maxDepth 最大搜索深度
     * @return 文件完整路径，未找到返回 null
     */
    private String searchFileRecursive(Path dir, String fileKey, int depth, int maxDepth) {
        if (depth >= maxDepth) {
            return null;
        }

        try {
            // 使用 Files.list 遍历当前目录
            try (var stream = Files.list(dir)) {
                for (Path path : (Iterable<Path>) stream::iterator) {
                    if (Files.isDirectory(path)) {
                        // 递归搜索子目录
                        String result = searchFileRecursive(path, fileKey, depth + 1, maxDepth);
                        if (result != null) {
                            return result;
                        }
                    } else {
                        // 匹配文件名
                        String fileName = path.getFileName().toString();
                        if (fileName.equals(fileKey) || fileName.contains(fileKey)) {
                            return path.toAbsolutePath().normalize().toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("遍历目录失败: {}", dir);
        }

        return null;
    }

    /**
     * 生成编辑器 JWT Token
     * 简化实现：使用 HMAC-SHA256 签名
     *
     * @param fileKey 文件标识
     * @return JWT Token 字符串
     */
    private String generateEditorToken(String fileKey) {
        try {
            if (euroOfficeJwtSecret == null || euroOfficeJwtSecret.isEmpty()) {
                log.error("Euro-Office JWT 密钥未配置");
                throw new BusinessException(500, "编辑器服务未正确配置，请联系管理员");
            }

            // 构建 JWT Header
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String headerEncoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(header.getBytes(StandardCharsets.UTF_8));

            // 构建 JWT Payload
            long now = System.currentTimeMillis() / 1000;
            long exp = now + 3600; // 1小时过期
            String payload = String.format(
                    "{\"fileKey\":\"%s\",\"iat\":%d,\"exp\":%d}",
                    fileKey, now, exp
            );
            String payloadEncoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

            // 生成签名
            String signingInput = headerEncoded + "." + payloadEncoded;
            SecretKey secretKey = new SecretKeySpec(
                    euroOfficeJwtSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String signatureEncoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signature);

            return headerEncoded + "." + payloadEncoded + "." + signatureEncoded;
        } catch (Exception e) {
            log.error("生成编辑器 JWT Token 失败", e);
            throw new BusinessException(500, "编辑器服务未正确配置，请联系管理员");
        }
    }
}
