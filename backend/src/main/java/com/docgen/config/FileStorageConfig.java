package com.docgen.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件存储配置类
 * 负责管理上传目录和输出目录的创建与路径注入
 */
@Slf4j
@Getter
@Configuration
public class FileStorageConfig {

    /** 文件上传目录路径 */
    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /** 文件输出目录路径 */
    @Value("${file.output-dir:./outputs}")
    private String outputDir;

    /**
     * 应用启动后自动创建上传和输出目录
     * 如果目录不存在则创建，确保文件操作不会因目录缺失而失败
     */
    @PostConstruct
    public void init() {
        createDirectoryIfNotExists(uploadDir, "上传文件");
        createDirectoryIfNotExists(outputDir, "输出文件");
    }

    /**
     * 创建目录（如果不存在）
     *
     * @param dirPath 目录路径
     * @param dirName 目录用途描述（用于日志）
     */
    private void createDirectoryIfNotExists(String dirPath, String dirName) {
        try {
            Path path = Paths.get(dirPath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("{}目录创建成功: {}", dirName, path);
            } else {
                log.info("{}目录已存在: {}", dirName, path);
            }
        } catch (Exception e) {
            log.error("创建{}目录失败: {}", dirName, dirPath, e);
            throw new RuntimeException("无法创建" + dirName + "目录: " + dirPath, e);
        }
    }
}
