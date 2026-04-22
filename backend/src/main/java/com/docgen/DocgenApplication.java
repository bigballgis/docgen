package com.docgen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 文档生成平台后端服务启动类
 * 替代原有 Node.js 后端，提供 RESTful API 服务
 */
@SpringBootApplication
@EnableCaching
public class DocgenApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocgenApplication.class, args);
    }
}
