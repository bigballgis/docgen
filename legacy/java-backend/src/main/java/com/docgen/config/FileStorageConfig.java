package com.docgen.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件存储配置
 * 管理模板存储路径和输出路径，并在启动时自动创建目录
 */
@Slf4j
@Getter
@Configuration
public class FileStorageConfig {

    @Value("${file.storage.template-dir:./templates}")
    private String templateDir;

    @Value("${file.storage.output-dir:./output}")
    private String outputDir;

    private Path templatePath;
    private Path outputPath;

    @PostConstruct
    public void init() {
        templatePath = Paths.get(templateDir).toAbsolutePath().normalize();
        outputPath = Paths.get(outputDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(templatePath);
            Files.createDirectories(outputPath);
            log.info("模板存储目录: {}", templatePath);
            log.info("文件输出目录: {}", outputPath);
        } catch (IOException e) {
            throw new RuntimeException("无法创建文件存储目录", e);
        }
    }

}
